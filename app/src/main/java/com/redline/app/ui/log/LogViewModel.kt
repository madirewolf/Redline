package com.redline.app.ui.log

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.WorkoutSession
import com.redline.app.data.repository.SessionState
import com.redline.app.data.repository.WorkoutRepository
import com.redline.app.settings.SettingsStore
import com.redline.app.ui.music.MusicController
import com.redline.app.util.DndManager
import com.redline.app.voice.ParseContext
import com.redline.app.voice.ParsedSet
import com.redline.app.ui.components.withDraft
import com.redline.app.voice.SetDraft
import com.redline.app.voice.SpeechRecognizerManager
import com.redline.app.voice.SpeechState
import com.redline.app.voice.VoiceParser
import com.redline.app.voice.forOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Consecutive identical sets on the confirmation card, such as "4 sets of 135 × 5". */
data class SetRun(val set: ParsedSet, val count: Int, val first: Int) {
    val indices: IntRange get() = first until first + count
}

/** Groups back-to-back identical sets so the card shows "4 sets" once rather than four rows. */
fun List<ParsedSet>.runs(): List<SetRun> {
    val runs = mutableListOf<SetRun>()
    forEachIndexed { i, set ->
        val last = runs.lastOrNull()
        if (last != null && last.set == set) runs[runs.lastIndex] = last.copy(count = last.count + 1)
        else runs += SetRun(set, 1, i)
    }
    return runs
}

/** What the set editor is open for. */
sealed interface SetEditorTarget {
    /** One run of sets waiting on the confirmation card. */
    data class Pending(val run: SetRun) : SetEditorTarget
    /** A set typed from scratch. */
    data object New : SetEditorTarget
    /** A set already in this workout. */
    data class Saved(val set: ExerciseSet) : SetEditorTarget
}

data class LogUiState(
    /** Every set the last thing said described, in order. */
    val pendingSets: List<ParsedSet> = emptyList(),
    val showConfirmation: Boolean = false,
    val isParsing: Boolean = false,
    val parseError: String? = null,
    val endedSessionId: Long? = null,
    val restTimerRemaining: Int = 0,
    val restTimerTotal: Int = 0,
    val isResting: Boolean = false,
    val isLockedIn: Boolean = false,
    val editor: SetEditorTarget? = null
) {
    val pendingRuns: List<SetRun> get() = pendingSets.runs()
}

@HiltViewModel
class LogViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WorkoutRepository,
    private val speechManager: SpeechRecognizerManager,
    private val voiceParser: VoiceParser,
    private val musicController: MusicController,
    private val settingsStore: SettingsStore,
    val dndManager: DndManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    val sessionState: StateFlow<SessionState> = repository.getActiveSessionState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionState.Loading)

    private val activeSession: WorkoutSession?
        get() = (sessionState.value as? SessionState.Active)?.session

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSets: StateFlow<List<ExerciseSet>> = repository.getActiveSession()
        .flatMapLatest { session ->
            if (session != null) repository.getSetsForSession(session.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val speechState: StateFlow<SpeechState> = speechManager.state
        .map { it.forOwner(OWNER) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SpeechState.Idle)

    val micLevel: StateFlow<Float> = speechManager.level

    private var restJob: Job? = null

    init {
        viewModelScope.launch {
            speechManager.state.collectLatest { state ->
                if (state is SpeechState.Result && state.owner == OWNER) {
                    if (isMusicCommand(state.text)) playDefaultPrTrack() else handleVoiceResult(state.text)
                    // Consumed, so a later subscriber can't replay the same set.
                    speechManager.consume(OWNER)
                } else if (state is SpeechState.Error && state.owner == OWNER) {
                    delay(3500)
                    speechManager.consume(OWNER)
                }
            }
        }
    }

    fun startWorkout() {
        viewModelScope.launch { repository.startSession() }
    }

    fun endWorkout() {
        viewModelScope.launch {
            if (_uiState.value.isLockedIn) dndManager.disable()
            restJob?.cancel()
            speechManager.release(OWNER)
            activeSession?.let { session ->
                val kept = repository.endSession(session)
                _uiState.update {
                    it.copy(endedSessionId = session.id.takeIf { kept }, isLockedIn = false, isResting = false)
                }
            }
        }
    }

    fun clearEndedSession() {
        _uiState.update { it.copy(endedSessionId = null) }
    }

    fun startListening() {
        _uiState.update { it.copy(parseError = null) }
        speechManager.startListening(OWNER)
    }

    fun stopListening() {
        speechManager.stopListening()
    }

    private fun handleVoiceResult(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isParsing = true, parseError = null) }
            // A set that names no exercise ("then 225 for 5") continues the last one logged.
            val last = currentSets.value.maxByOrNull { it.timestamp }
            val context = ParseContext(last?.exerciseName, last?.canonicalExerciseKey)
            runCatching { voiceParser.parseAll(text, context) }
                .onSuccess { parsed ->
                    _uiState.update {
                        it.copy(pendingSets = parsed, showConfirmation = parsed.isNotEmpty(), isParsing = false)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isParsing = false, parseError = error.message ?: "Could not parse")
                    }
                }
        }
    }

    private fun isMusicCommand(text: String): Boolean {
        val clean = text.lowercase()
        return clean.contains("play my pr") || clean.contains("pr song") ||
                clean.contains("drop time") || clean.contains("play the drop")
    }

    private fun playDefaultPrTrack() {
        viewModelScope.launch {
            val track = repository.getDefaultPrTrack()
            if (track != null) {
                musicController.play(track)
            } else {
                _uiState.update { it.copy(parseError = "No PR track saved") }
            }
        }
    }

    /** Unit a typed set starts in. */
    val defaultUnit: String get() = settingsStore.defaultUnit.value

    /** Saves every set on the confirmation card. Incomplete sets go through the editor first. */
    fun confirmSet() {
        val pending = _uiState.value.pendingSets
        if (pending.isNotEmpty() && pending.all { it.isComplete }) logSets(pending)
    }

    fun openEditor(target: SetEditorTarget) {
        _uiState.update { it.copy(editor = target) }
    }

    fun closeEditor() {
        _uiState.update { it.copy(editor = null) }
    }

    fun saveFromEditor(draft: SetDraft) {
        when (val target = _uiState.value.editor) {
            is SetEditorTarget.Pending -> {
                val edited = draft.toParsedSet(target.run.set.rawInput) ?: return
                val pending = _uiState.value.pendingSets
                val updated = pending.mapIndexed { i, set -> if (i in target.run.indices) edited else set }
                // A lone set saves straight from the editor, as before; a list goes back to the card.
                if (pending.runs().size <= 1) logSets(updated)
                else _uiState.update { it.copy(pendingSets = updated, editor = null) }
            }
            SetEditorTarget.New -> draft.toParsedSet(rawInput = "")?.let { logSets(listOf(it)) }
            is SetEditorTarget.Saved -> {
                val updated = target.set.withDraft(draft) ?: return
                viewModelScope.launch { repository.updateSet(updated) }
                closeEditor()
            }
            null -> Unit
        }
    }

    fun deleteSet(set: ExerciseSet) {
        viewModelScope.launch { repository.deleteSet(set) }
        closeEditor()
    }

    private fun logSets(sets: List<ParsedSet>) {
        val session = activeSession ?: return
        if (sets.isEmpty()) return
        viewModelScope.launch {
            // A millisecond apart, so they keep the order they were said in.
            val now = System.currentTimeMillis()
            sets.forEachIndexed { i, parsed ->
                repository.logSet(
                    ExerciseSet(
                        sessionId = session.id,
                        exerciseName = parsed.exerciseName,
                        canonicalExerciseKey = parsed.canonicalExerciseKey,
                        weight = parsed.weight,
                        unit = parsed.unit,
                        reps = parsed.reps,
                        rpe = parsed.rpe,
                        notes = parsed.notes,
                        rawVoiceInput = parsed.rawInput.ifBlank { null },
                        timestamp = now + i
                    )
                )
            }
            _uiState.update { LogUiState(isLockedIn = it.isLockedIn) }
            startRestTimer()
        }
    }

    // Rest timer. Counts down against the wall clock so it can't drift, and
    // a new set replaces the running countdown instead of racing it.
    private fun startRestTimer() {
        val duration = settingsStore.restDuration.value
        val endsAt = System.currentTimeMillis() + duration * 1000L
        restJob?.cancel()
        _uiState.update { it.copy(isResting = true, restTimerRemaining = duration, restTimerTotal = duration) }
        restJob = viewModelScope.launch {
            while (true) {
                val left = ((endsAt - System.currentTimeMillis() + 999) / 1000).toInt()
                if (left <= 0) break
                _uiState.update { it.copy(restTimerRemaining = left) }
                delay(200)
            }
            _uiState.update { it.copy(isResting = false, restTimerRemaining = 0) }
            buzz()
        }
    }

    fun skipRestTimer() {
        restJob?.cancel()
        _uiState.update { it.copy(isResting = false, restTimerRemaining = 0) }
    }

    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
    }

    // Lock-in mode
    fun toggleLockIn() {
        val newState = !_uiState.value.isLockedIn
        _uiState.update { it.copy(isLockedIn = newState) }
        if (newState) dndManager.enable() else dndManager.disable()
    }

    fun discardSet() {
        _uiState.update { it.copy(pendingSets = emptyList(), showConfirmation = false, parseError = null) }
    }

    /** Drops one run from the card, and the card with it when nothing is left. */
    fun discardRun(run: SetRun) {
        val left = _uiState.value.pendingSets.filterIndexed { i, _ -> i !in run.indices }
        if (left.isEmpty()) discardSet() else _uiState.update { it.copy(pendingSets = left) }
    }

    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.isLockedIn) dndManager.disable()
        speechManager.release(OWNER)
    }

    private companion object {
        const val OWNER = "log"
    }
}

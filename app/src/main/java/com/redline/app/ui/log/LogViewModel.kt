package com.redline.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.WorkoutSession
import com.redline.app.data.repository.WorkoutRepository
import com.redline.app.settings.SettingsStore
import com.redline.app.ui.music.MusicController
import com.redline.app.util.DndManager
import com.redline.app.voice.ParsedSet
import com.redline.app.voice.SpeechRecognizerManager
import com.redline.app.voice.SpeechState
import com.redline.app.voice.VoiceParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogUiState(
    val lastParsedSet: ParsedSet? = null,
    val showConfirmation: Boolean = false,
    val isParsing: Boolean = false,
    val parseError: String? = null,
    val endedSessionId: Long? = null,
    val restTimerRemaining: Int = 0,
    val isResting: Boolean = false,
    val isLockedIn: Boolean = false
)

@HiltViewModel
class LogViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    val speechManager: SpeechRecognizerManager,
    private val voiceParser: VoiceParser,
    private val musicController: MusicController,
    private val settingsStore: SettingsStore,
    val dndManager: DndManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    val activeSession: StateFlow<WorkoutSession?> = repository.getActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSets: StateFlow<List<ExerciseSet>> = repository.getActiveSession()
        .flatMapLatest { session ->
            if (session != null) repository.getSetsForSession(session.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val speechState: StateFlow<SpeechState> = speechManager.state

    // Elapsed timer (seconds since workout start)
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    init {
        viewModelScope.launch {
            speechManager.state.collect { state ->
                if (state is SpeechState.Result) {
                    if (isMusicCommand(state.text)) {
                        playDefaultPrTrack()
                    } else {
                        handleVoiceResult(state.text)
                    }
                }
            }
        }
        // Timer
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val session = activeSession.value
                if (session != null) {
                    _elapsedSeconds.value = (System.currentTimeMillis() - session.startTime) / 1000
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
            activeSession.value?.let { session ->
                repository.endSession(session)
                _uiState.value = _uiState.value.copy(endedSessionId = session.id, isLockedIn = false, isResting = false)
            }
        }
    }

    fun clearEndedSession() {
        _uiState.value = _uiState.value.copy(endedSessionId = null)
    }

    fun startListening() {
        speechManager.startListening()
    }

    fun stopListening() {
        speechManager.stopListening()
    }

    private fun handleVoiceResult(text: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isParsing = true, parseError = null)
            runCatching { voiceParser.parse(text) }
                .onSuccess { parsed ->
                    _uiState.value = _uiState.value.copy(
                        lastParsedSet = parsed,
                        showConfirmation = true,
                        isParsing = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isParsing = false,
                        parseError = error.message ?: "Could not parse"
                    )
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
                speechManager.resetState()
            } else {
                _uiState.value = _uiState.value.copy(parseError = "No PR track saved")
            }
        }
    }

    fun confirmSet() {
        val parsed = _uiState.value.lastParsedSet ?: return
        val session = activeSession.value ?: return

        viewModelScope.launch {
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
                    rawVoiceInput = parsed.rawInput
                )
            )
            _uiState.value = LogUiState(isLockedIn = _uiState.value.isLockedIn)
            speechManager.resetState()
            startRestTimer()
        }
    }

    // Rest timer
    private fun startRestTimer() {
        val duration = settingsStore.restDuration.value
        _uiState.value = _uiState.value.copy(isResting = true, restTimerRemaining = duration)
        viewModelScope.launch {
            var remaining = duration
            while (remaining > 0) {
                delay(1000)
                remaining--
                _uiState.value = _uiState.value.copy(restTimerRemaining = remaining)
            }
            _uiState.value = _uiState.value.copy(isResting = false, restTimerRemaining = 0)
            // TODO: vibrate on completion
        }
    }

    fun skipRestTimer() {
        _uiState.value = _uiState.value.copy(isResting = false, restTimerRemaining = 0)
    }

    // Lock-in mode
    fun toggleLockIn() {
        val newState = !_uiState.value.isLockedIn
        _uiState.value = _uiState.value.copy(isLockedIn = newState)
        if (newState) dndManager.enable() else dndManager.disable()
    }

    fun discardSet() {
        _uiState.value = LogUiState()
        speechManager.resetState()
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.destroy()
    }
}

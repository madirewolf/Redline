package com.redline.app.ui.tempo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.repository.WorkoutRepository
import com.redline.app.tempo.AudioInput
import com.redline.app.tempo.CueEvent
import com.redline.app.tempo.LiftKind
import com.redline.app.tempo.RepTime
import com.redline.app.tempo.TempoAnalysis
import com.redline.app.tempo.TempoEstimate
import com.redline.app.tempo.TempoRecorder
import com.redline.app.tempo.TempoState
import com.redline.app.tempo.VoiceCueRecorder
import com.redline.app.tempo.VoiceTempoMath
import com.redline.app.tempo.VoiceTempoState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

enum class TempoMode { VOICE, MOTION }

/** A voice-timed set, live or finished, with everything worked out from the cues heard so far. */
data class VoiceSet(
    val input: AudioInput,
    val listening: Boolean,
    val startedNs: Long,
    val endedNs: Long?,
    val cues: List<CueEvent>,
    val reps: List<RepTime>,
    val estimate: TempoEstimate?,
    /** The "bottom" of the rep being lifted right now, if there is one. */
    val openBottom: CueEvent?,
    val kind: LiftKind
) {
    val lastCue: CueEvent? get() = cues.lastOrNull()
}

/**
 * Drives the Tempo screen: the voice cue recorder (the default) or the accelerometer recorder
 * for the set itself, and the most recent logged set so a result can be saved where it belongs.
 */
@HiltViewModel
class TempoViewModel @Inject constructor(
    private val recorder: TempoRecorder,
    private val voiceRecorder: VoiceCueRecorder,
    private val repository: WorkoutRepository
) : ViewModel() {
    val state: StateFlow<TempoState> = recorder.state
    val voiceState: StateFlow<VoiceTempoState> = voiceRecorder.state
    val voiceLevel: StateFlow<Float> = voiceRecorder.level
    val availableInput: StateFlow<AudioInput> = voiceRecorder.availableInput

    private val _mode = MutableStateFlow(TempoMode.VOICE)
    val mode: StateFlow<TempoMode> = _mode.asStateFlow()

    /** The set a result would be saved to: the last one logged, if it was logged recently. */
    val lastSet: StateFlow<ExerciseSet?> = repository.getAllSets()
        .map { sets -> sets.firstOrNull()?.takeIf { System.currentTimeMillis() - it.timestamp < ATTACH_WINDOW_MS } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val kindOverride = MutableStateFlow<LiftKind?>(null)

    /** What kind of lift the set is, which sets how much slowdown means a stall. */
    val liftKind: StateFlow<LiftKind> = combine(lastSet, kindOverride) { set, chosen ->
        chosen ?: LiftKind.of(set?.canonicalExerciseKey ?: set?.exerciseName?.lowercase()?.replace(' ', '_'))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiftKind.OTHER)

    val voiceSet: StateFlow<VoiceSet?> = combine(voiceRecorder.state, liftKind) { s, kind ->
        when (s) {
            is VoiceTempoState.Listening -> voiceSetOf(s.input, true, s.startedNs, null, s.heard, kind)
            is VoiceTempoState.Finished -> voiceSetOf(s.input, false, s.startedNs, s.endedNs, s.heard, kind)
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Id of the set the current result was saved to, so the button can say so. */
    private val _savedTo = MutableStateFlow<Long?>(null)
    val savedTo: StateFlow<Long?> = _savedTo.asStateFlow()

    fun setMode(mode: TempoMode) {
        if (mode == _mode.value) return
        // Only one recorder runs at a time.
        if (recorder.state.value is TempoState.Recording) recorder.clear()
        if (voiceRecorder.state.value is VoiceTempoState.Listening) voiceRecorder.clear()
        _mode.value = mode
    }

    fun setLiftKind(kind: LiftKind) {
        kindOverride.value = kind
    }

    fun start() {
        _savedTo.value = null
        recorder.start()
    }

    fun stop() {
        recorder.stopAndAnalyze()
    }

    fun reset() {
        _savedTo.value = null
        recorder.clear()
    }

    fun startVoice() {
        _savedTo.value = null
        voiceRecorder.start()
    }

    fun stopVoice() {
        voiceRecorder.stop()
    }

    fun resetVoice() {
        _savedTo.value = null
        voiceRecorder.clear()
    }

    /** Writes the scored result onto the last logged set. */
    fun saveToLastSet() {
        val analysis = (state.value as? TempoState.Ready)?.analysis ?: return
        val set = lastSet.value ?: return
        if (!analysis.isScored) return
        viewModelScope.launch {
            repository.updateSet(
                set.copy(
                    tempoAverageRepSeconds = analysis.averageRepSeconds,
                    tempoDegradationPercent = analysis.degradationPercent,
                    failureProximityScore = analysis.failureProximityScore
                )
            )
            _savedTo.value = set.id
        }
    }

    /**
     * Writes a voice-timed set onto the last logged set: the mean lift time, how much slower
     * the last rep was than the set's starting speed, and how close the trend came to a stall.
     * The estimated RPE fills the set's RPE only if none was logged, since the lifter's own call wins.
     */
    fun saveVoiceToLastSet() {
        val voice = voiceSet.value?.takeIf { !it.listening } ?: return
        val result = voiceResultOf(voice) ?: return
        val set = lastSet.value ?: return
        viewModelScope.launch {
            repository.updateSet(
                set.copy(
                    tempoAverageRepSeconds = result.averageSeconds,
                    tempoDegradationPercent = result.slowdownPercent,
                    failureProximityScore = result.proximityScore,
                    rpe = set.rpe ?: result.rpe
                )
            )
            _savedTo.value = set.id
        }
    }

    override fun onCleared() {
        // The recorders are singletons; don't leave the sensor or mic running once nobody is watching.
        if (recorder.state.value is TempoState.Recording) recorder.clear()
        if (voiceRecorder.state.value is VoiceTempoState.Listening) voiceRecorder.stop()
    }

    companion object {
        private const val ATTACH_WINDOW_MS = 3 * 60 * 60 * 1000L
    }
}

/** What a voice-timed set adds up to, once it has enough reps for a trend. */
data class VoiceResult(
    val averageSeconds: Float,
    val slowdownPercent: Float,
    val proximityScore: Int,
    val rpe: Float
)

fun voiceSetOf(
    input: AudioInput,
    listening: Boolean,
    startedNs: Long,
    endedNs: Long?,
    heard: List<com.redline.app.tempo.HeardCue>,
    kind: LiftKind
): VoiceSet {
    val cues = VoiceTempoMath.label(heard)
    val reps = VoiceTempoMath.reps(cues)
    return VoiceSet(
        input = input,
        listening = listening,
        startedNs = startedNs,
        endedNs = endedNs,
        cues = cues,
        reps = reps,
        estimate = VoiceTempoMath.estimate(reps.map { it.seconds }, kind),
        openBottom = VoiceTempoMath.openBottom(cues),
        kind = kind
    )
}

fun voiceResultOf(voice: VoiceSet): VoiceResult? {
    val estimate = voice.estimate ?: return null
    val current = estimate.currentLoss ?: return null
    val rpe = estimate.rpe ?: return null
    val seconds = voice.reps.map { it.seconds }
    return VoiceResult(
        averageSeconds = seconds.average().toFloat(),
        slowdownPercent = (seconds.last() / estimate.referenceSeconds - 1f) * 100f,
        proximityScore = (100f * current / estimate.stallLoss).roundToInt().coerceIn(0, 100),
        rpe = VoiceTempoMath.roundRpe(rpe)
    )
}

/** True when the analyser produced a score rather than a reason it couldn't. */
val TempoAnalysis.isScored: Boolean get() = failureProximityScore != null

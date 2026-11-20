package com.redline.app.ui.tempo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.repository.WorkoutRepository
import com.redline.app.tempo.TempoAnalysis
import com.redline.app.tempo.TempoRecorder
import com.redline.app.tempo.TempoState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Tempo screen: the accelerometer recorder for the set itself, and
 * the most recent logged set so a result can be saved where it belongs.
 */
@HiltViewModel
class TempoViewModel @Inject constructor(
    private val recorder: TempoRecorder,
    private val repository: WorkoutRepository
) : ViewModel() {
    val state: StateFlow<TempoState> = recorder.state

    /** The set a result would be saved to: the last one logged, if it was logged recently. */
    val lastSet: StateFlow<ExerciseSet?> = repository.getAllSets()
        .map { sets -> sets.firstOrNull()?.takeIf { System.currentTimeMillis() - it.timestamp < ATTACH_WINDOW_MS } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Id of the set the current result was saved to, so the button can say so. */
    private val _savedTo = MutableStateFlow<Long?>(null)
    val savedTo: StateFlow<Long?> = _savedTo.asStateFlow()

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

    override fun onCleared() {
        // The recorder is a singleton; don't leave the sensor running once nobody is watching.
        if (recorder.state.value is TempoState.Recording) recorder.clear()
    }

    companion object {
        private const val ATTACH_WINDOW_MS = 3 * 60 * 60 * 1000L
    }
}

/** True when the analyser produced a score rather than a reason it couldn't. */
val TempoAnalysis.isScored: Boolean get() = failureProximityScore != null

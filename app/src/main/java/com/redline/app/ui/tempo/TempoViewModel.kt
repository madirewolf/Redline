package com.redline.app.ui.tempo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PhaseTimestamp(
    val phase: String, // "eccentric" or "concentric"
    val timeMs: Long
)

data class RepTiming(
    val repNumber: Int,
    val eccentricMs: Long,
    val concentricMs: Long
) {
    val totalMs: Long get() = eccentricMs + concentricMs
}

data class TempoResult(
    val reps: List<RepTiming>,
    val averageRepMs: Long,
    val degradationPercent: Float,
    val failureProximityScore: Int,
    val note: String
)

sealed interface VoiceTempoState {
    data object Idle : VoiceTempoState
    data class Tracking(
        val currentPhase: String,
        val repCount: Int,
        val phases: List<PhaseTimestamp>
    ) : VoiceTempoState
    data class Done(val result: TempoResult) : VoiceTempoState
    data class Error(val message: String) : VoiceTempoState
}

@HiltViewModel
class TempoViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow<VoiceTempoState>(VoiceTempoState.Idle)
    val state: StateFlow<VoiceTempoState> = _state.asStateFlow()

    private val phases = mutableListOf<PhaseTimestamp>()
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    fun start() {
        phases.clear()
        _state.value = VoiceTempoState.Tracking("waiting", 0, emptyList())
        startContinuousListening()
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
        analyze()
    }

    fun reset() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
        phases.clear()
        _state.value = VoiceTempoState.Idle
    }

    private fun startContinuousListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceTempoState.Error("Speech recognition not available")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.lowercase() ?: ""
                    handlePhaseCommand(text)
                    // Restart listening for next command
                    if (isListening) startListeningIntent()
                }
                override fun onPartialResults(partial: Bundle?) {
                    val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.lowercase() ?: ""
                    if (text.isNotBlank()) handlePhaseCommand(text)
                }
                override fun onError(error: Int) {
                    if (isListening && error != SpeechRecognizer.ERROR_CLIENT) {
                        startListeningIntent()
                    }
                }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        isListening = true
        startListeningIntent()
    }

    private fun startListeningIntent() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) { }
    }

    private fun handlePhaseCommand(text: String) {
        val clean = text.trim().lowercase()
        when {
            clean.contains("done") || clean.contains("stop") || clean.contains("finish") -> {
                stop()
                return
            }
            clean.contains("eccentric") || clean.contains("down") || clean.contains("lower") -> {
                recordPhase("eccentric")
            }
            clean.contains("concentric") || clean.contains("up") || clean.contains("push") || clean.contains("pull") -> {
                recordPhase("concentric")
            }
        }
    }

    private fun recordPhase(phase: String) {
        val now = System.currentTimeMillis()
        // Prevent duplicate phases within 300ms
        val last = phases.lastOrNull()
        if (last != null && last.phase == phase && now - last.timeMs < 300) return

        phases.add(PhaseTimestamp(phase, now))

        val repCount = phases.count { it.phase == "concentric" }
        _state.value = VoiceTempoState.Tracking(
            currentPhase = phase,
            repCount = repCount,
            phases = phases.toList()
        )
    }

    private fun analyze() {
        if (phases.size < 3) {
            _state.value = VoiceTempoState.Error("Need at least 2 full phases to analyze. Say 'eccentric' then 'concentric' for each rep.")
            return
        }

        val reps = mutableListOf<RepTiming>()
        var i = 0
        var repNum = 1
        while (i < phases.size - 1) {
            val current = phases[i]
            val next = phases[i + 1]
            if (current.phase == "eccentric" && next.phase == "concentric") {
                val eccentricMs = next.timeMs - current.timeMs
                val concentricMs = if (i + 2 < phases.size) {
                    phases[i + 2].timeMs - next.timeMs
                } else {
                    eccentricMs // estimate
                }
                reps.add(RepTiming(repNum, eccentricMs, concentricMs))
                repNum++
                i += 2
            } else {
                i++
            }
        }

        if (reps.isEmpty()) {
            _state.value = VoiceTempoState.Error("Could not detect full rep cycles. Alternate 'eccentric' and 'concentric'.")
            return
        }

        val avgMs = reps.map { it.totalMs }.average().toLong()
        val firstThird = reps.take(maxOf(1, reps.size / 3)).map { it.totalMs }.average()
        val lastThird = reps.takeLast(maxOf(1, reps.size / 3)).map { it.totalMs }.average()
        val degradation = if (firstThird > 0) ((lastThird - firstThird) / firstThird * 100).toFloat() else 0f

        val score = when {
            degradation >= 35f -> minOf(100, 92 + reps.size)
            degradation >= 22f -> minOf(100, 82 + reps.size)
            degradation >= 12f -> minOf(95, 68 + reps.size * 2)
            degradation >= 5f -> minOf(85, 54 + reps.size * 2)
            else -> minOf(50, 34 + reps.size * 2)
        }

        val note = when {
            score >= 85 -> "Tempo degraded significantly — you were close to failure."
            score >= 70 -> "Progressive tempo slowdown detected — good effort."
            score >= 55 -> "Some fatigue visible, but room to push harder."
            else -> "Tempo stayed mostly constant — you likely had reps in reserve."
        }

        _state.value = VoiceTempoState.Done(
            TempoResult(reps, avgMs, degradation, score, note)
        )
    }

    override fun onCleared() {
        recognizer?.destroy()
        isListening = false
    }
}

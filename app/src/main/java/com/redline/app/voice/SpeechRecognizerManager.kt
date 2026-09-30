package com.redline.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

/**
 * Every state except [Idle] names the screen that asked to listen, so a
 * result spoken on the Train screen never reaches the Calories screen (both
 * view models can be alive at once) and vice versa.
 */
sealed interface SpeechState {
    data object Idle : SpeechState
    data class Listening(val owner: String, val partial: String = "") : SpeechState
    data class Result(val owner: String, val text: String) : SpeechState
    data class Error(val owner: String, val message: String) : SpeechState
}

/** This state as [owner] should see it: anything another screen started reads as idle. */
fun SpeechState.forOwner(owner: String): SpeechState = when (this) {
    SpeechState.Idle -> this
    is SpeechState.Listening -> if (this.owner == owner) this else SpeechState.Idle
    is SpeechState.Result -> if (this.owner == owner) this else SpeechState.Idle
    is SpeechState.Error -> if (this.owner == owner) this else SpeechState.Idle
}

@Singleton
class SpeechRecognizerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    /** Input loudness while listening, 0..1, for the mic animation. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recognizer: SpeechRecognizer? = null
    private var owner: String? = null
    private var simulation: Job? = null
    private var armedTranscript: String? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(owner: String) {
        armedTranscript?.let { text ->
            armedTranscript = null
            simulate(text, owner)
            return
        }
        if (!isAvailable) {
            _state.value = SpeechState.Error(owner, "Speech recognition not available on this device")
            return
        }

        destroy()
        this.owner = owner
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener(owner))
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        _state.value = SpeechState.Listening(owner)
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        if (simulation?.isActive == true) {
            simulation?.cancel()
            reset()
        }
    }

    /** Clears [owner]'s result or error once it has been handled. */
    fun consume(owner: String) {
        if (_state.value.forOwner(owner) != SpeechState.Idle) reset()
    }

    /** Called when [owner]'s screen goes away: stops its recognizer but leaves other screens' alone. */
    fun release(owner: String) {
        if (this.owner != owner) return
        destroy()
        this.owner = null
        reset()
    }

    /**
     * Plays [text] through the same states a real recognition produces:
     * partial transcripts word by word, then the final result. Debug builds
     * drive this from a broadcast so the emulator, which has no microphone
     * input, can demo the voice flow. The parser and everything after it are
     * the real code.
     */
    fun simulate(text: String, owner: String = this.owner ?: "log") {
        destroy()
        this.owner = owner
        simulation = scope.launch {
            val words = text.split(" ").filter { it.isNotBlank() }
            _state.value = SpeechState.Listening(owner)
            delay(600)
            words.indices.forEach { i ->
                _state.value = SpeechState.Listening(owner, words.take(i + 1).joinToString(" "))
                repeat(3) { tick ->
                    _level.value = 0.45f + 0.4f * sin((i * 3 + tick) * 1.7f).coerceAtLeast(-0.6f)
                    delay(90)
                }
            }
            _level.value = 0f
            delay(350)
            _state.value = SpeechState.Result(owner, text)
        }
    }

    /** Makes the next [startListening] call [simulate] [text] instead of opening the mic. */
    fun armSimulation(text: String) {
        armedTranscript = text
    }

    private fun reset() {
        _state.value = SpeechState.Idle
        _level.value = 0f
    }

    private fun destroy() {
        simulation?.cancel()
        simulation = null
        recognizer?.destroy()
        recognizer = null
        _level.value = 0f
    }

    private fun createListener(owner: String) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            // SpeechRecognizer reports roughly -2 dB (silence) to 10 dB (loud speech).
            _level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _level.value = 0f
        }

        override fun onResults(results: Bundle?) {
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            _level.value = 0f
            _state.value = if (text.isNotBlank()) {
                SpeechState.Result(owner, text)
            } else {
                SpeechState.Error(owner, "No speech detected")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (partial.isNotBlank() && _state.value is SpeechState.Listening) {
                _state.value = SpeechState.Listening(owner, partial)
            }
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Try again"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Try again"
                else -> "Recognition error"
            }
            _level.value = 0f
            _state.value = SpeechState.Error(owner, message)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}

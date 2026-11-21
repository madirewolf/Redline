package com.redline.app.tempo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.redline.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/** The microphone the cue words are heard through. */
data class AudioInput(val name: String, val kind: Kind) {
    enum class Kind { WIRED, BLUETOOTH, PHONE }
}

sealed interface VoiceTempoState {
    data object Idle : VoiceTempoState
    data class Listening(val input: AudioInput, val startedNs: Long, val heard: List<HeardCue>) : VoiceTempoState
    data class Finished(val input: AudioInput, val startedNs: Long, val endedNs: Long, val heard: List<HeardCue>) : VoiceTempoState
    data class Error(val message: String) : VoiceTempoState
}

/**
 * Listens for the spoken "bottom" and "top" cues through a headset mic, or the phone's own,
 * and times each one by the audio's sample count, so rep times don't depend on how quickly
 * the phone gets round to reading the buffer.
 */
@Singleton
class VoiceCueRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val session = AtomicInteger()

    private val _state = MutableStateFlow<VoiceTempoState>(VoiceTempoState.Idle)
    val state: StateFlow<VoiceTempoState> = _state.asStateFlow()

    /** Loudness above the room's noise, 0 to 1, for a level meter. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    /** The input a set would be heard through right now, kept current as headsets come and go. */
    private val _availableInput = MutableStateFlow(PHONE_MIC)
    val availableInput: StateFlow<AudioInput> = _availableInput.asStateFlow()

    init {
        _availableInput.value = previewInput()
        audioManager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                _availableInput.value = previewInput()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                _availableInput.value = previewInput()
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun previewInput(): AudioInput {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        inputs.firstOrNull { it.type in WIRED_TYPES }?.let { return AudioInput(nameOf(it, "Wired headset"), AudioInput.Kind.WIRED) }
        inputs.firstOrNull { it.type in BLUETOOTH_TYPES }?.let { return AudioInput(nameOf(it, "Bluetooth headset"), AudioInput.Kind.BLUETOOTH) }
        return PHONE_MIC
    }

    fun start() {
        if (job?.isActive == true && _state.value is VoiceTempoState.Listening) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = VoiceTempoState.Error("Microphone access is off")
            return
        }
        val id = session.incrementAndGet()
        val previous = job
        previous?.cancel()
        _state.value = VoiceTempoState.Idle
        job = scope.launch {
            // Let the last set's recorder let go of the mic first.
            previous?.join()
            listen(id)
        }
    }

    /** Stops listening and keeps what was heard. */
    fun stop() {
        session.incrementAndGet()
        job?.cancel()
        _state.update { s ->
            if (s is VoiceTempoState.Listening) VoiceTempoState.Finished(s.input, s.startedNs, System.nanoTime(), s.heard) else s
        }
    }

    fun clear() {
        session.incrementAndGet()
        job?.cancel()
        _state.value = VoiceTempoState.Idle
    }

    /** Changes the state only if [id] is still the current set, so a set that was stopped can't overwrite the next. */
    private fun publish(id: Int, change: (VoiceTempoState) -> VoiceTempoState) {
        _state.update { s -> if (session.get() == id) change(s) else s }
    }

    private suspend fun listen(id: Int) {
        fun fail(message: String) = publish(id) { VoiceTempoState.Error(message) }

        val route = openRoute()
        val record = try {
            val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0) null
            else AudioRecord(route.source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minimum, SAMPLE_RATE / 2))
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            route.close()
            fail("Couldn't open the microphone")
            return
        }
        route.device?.let { record.setPreferredDevice(it) }

        val detector = CueDetector(SAMPLE_RATE)
        val buffer = ShortArray(SAMPLE_RATE / READS_PER_SECOND)
        val timestamp = AudioTimestamp()
        // System.nanoTime of the first sample, so a sample index converts to a time.
        var firstSampleNs: Long? = null
        var fromTimestamp = false
        var samplesRead = 0L
        val capture = if (BuildConfig.DEBUG) DebugCapture.open(context) else null
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                fail("The microphone is in use by another app")
                return
            }
            val startedNs = System.nanoTime()
            publish(id) { VoiceTempoState.Listening(route.input, startedNs, emptyList()) }
            capture?.log("start rate=${record.sampleRate} source=${route.source} input=${route.input.kind}")
            while (coroutineContext.isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read < 0) {
                    fail("The microphone stopped")
                    return
                }
                if (read == 0) continue
                val now = System.nanoTime()
                samplesRead += read
                capture?.write(buffer, read)
                if (firstSampleNs == null) firstSampleNs = now - samplesToNs(samplesRead)
                // The audio clock says exactly when a sample was captured; use it once it's ready.
                if (!fromTimestamp && record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                    capture?.log("timestamp frame=${timestamp.framePosition} read=$samplesRead originShiftMs=${(timestamp.nanoTime - samplesToNs(timestamp.framePosition) - firstSampleNs!!) / 1e6}")
                    firstSampleNs = timestamp.nanoTime - samplesToNs(timestamp.framePosition)
                    fromTimestamp = true
                }
                if (capture != null && samplesRead / SAMPLE_RATE != (samplesRead - read) / SAMPLE_RATE) {
                    capture.log("clock read=$samplesRead audioMs=${samplesToNs(samplesRead) / 1_000_000} wallMs=${(now - startedNs) / 1_000_000}")
                }
                val found = detector.feed(buffer, read)
                _level.value = detector.level
                if (found.isNotEmpty()) {
                    val origin = firstSampleNs!!
                    val heard = found.map { HeardCue(origin + samplesToNs(it.onsetSample), it.bottomLikelihood) }
                    found.forEach {
                        capture?.log("cue onset=${it.onsetSample} atMs=${samplesToNs(it.onsetSample) / 1_000_000} p=${it.bottomLikelihood} hiss=${it.hissFrames} syl=${it.syllables} dur=${it.durationMs}")
                    }
                    publish(id) { s -> if (s is VoiceTempoState.Listening) s.copy(heard = s.heard + heard) else s }
                }
            }
        } catch (e: IllegalStateException) {
            fail("The microphone stopped")
        } finally {
            runCatching { record.stop() }
            record.release()
            route.close()
            capture?.close()
            _level.value = 0f
        }
    }

    private class Route(val input: AudioInput, val source: Int, val device: AudioDeviceInfo?, val close: () -> Unit)

    /**
     * A wired or USB headset is read directly. A Bluetooth headset's mic only works over the
     * call link, so it's switched on for the set and back off after. Otherwise, the phone's mic.
     */
    @Suppress("DEPRECATION")
    private fun openRoute(): Route {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        inputs.firstOrNull { it.type in WIRED_TYPES }?.let {
            return Route(AudioInput(nameOf(it, "Wired headset"), AudioInput.Kind.WIRED), MediaRecorder.AudioSource.VOICE_RECOGNITION, it) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val headset = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            if (headset != null && runCatching { audioManager.setCommunicationDevice(headset) }.getOrDefault(false)) {
                return Route(
                    AudioInput(nameOf(headset, "Bluetooth headset"), AudioInput.Kind.BLUETOOTH),
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    null
                ) { runCatching { audioManager.clearCommunicationDevice() } }
            }
        } else {
            val headset = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            if (headset != null && audioManager.isBluetoothScoAvailableOffCall) {
                val started = runCatching {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                }.isSuccess
                if (started) {
                    return Route(
                        AudioInput(nameOf(headset, "Bluetooth headset"), AudioInput.Kind.BLUETOOTH),
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        null
                    ) {
                        runCatching {
                            audioManager.isBluetoothScoOn = false
                            audioManager.stopBluetoothSco()
                        }
                    }
                }
            }
        }
        return Route(PHONE_MIC, MediaRecorder.AudioSource.VOICE_RECOGNITION, null) {}
    }

    private fun nameOf(device: AudioDeviceInfo, fallback: String): String =
        device.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() && !it.equals(Build.MODEL, ignoreCase = true) } ?: fallback

    private fun samplesToNs(samples: Long): Long = samples * 1_000_000_000L / SAMPLE_RATE

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val READS_PER_SECOND = 50
        private val PHONE_MIC = AudioInput("Phone microphone", AudioInput.Kind.PHONE)
        private val WIRED_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE
        )
        private val BLUETOOTH_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }
}

package com.redline.app.tempo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

sealed interface TempoState {
    data object Idle : TempoState
    data class Recording(val seconds: Int = 0) : TempoState
    data class Ready(val analysis: TempoAnalysis) : TempoState
    data class Error(val message: String) : TempoState
}

@Singleton
class TempoRecorder @Inject constructor(
    @ApplicationContext context: Context
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val analyzer = TempoAnalyzer()
    private val samples = mutableListOf<TempoSample>()
    private var startTimestampNs: Long? = null

    private val _state = MutableStateFlow<TempoState>(TempoState.Idle)
    val state: StateFlow<TempoState> = _state.asStateFlow()

    private var sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun start() {
        val activeSensor = sensor
        if (activeSensor == null) {
            _state.value = TempoState.Error("No motion sensor found")
            return
        }

        samples.clear()
        startTimestampNs = null
        val registered = sensorManager.registerListener(
            this,
            activeSensor,
            SensorManager.SENSOR_DELAY_GAME
        )
        _state.value = if (registered) TempoState.Recording() else TempoState.Error("Could not start motion tracking")
    }

    fun stopAndAnalyze(): TempoAnalysis? {
        sensorManager.unregisterListener(this)
        val analysis = analyzer.analyze(samples.toList())
        _state.value = TempoState.Ready(analysis)
        return analysis
    }

    fun clear() {
        sensorManager.unregisterListener(this)
        samples.clear()
        startTimestampNs = null
        _state.value = TempoState.Idle
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (_state.value !is TempoState.Recording) return

        val firstTimestamp = startTimestampNs ?: event.timestamp.also { startTimestampNs = it }
        val seconds = ((event.timestamp - firstTimestamp) / 1_000_000_000L).toInt()
        val x = event.values.getOrNull(0) ?: 0f
        val y = event.values.getOrNull(1) ?: 0f
        val z = event.values.getOrNull(2) ?: 0f
        samples += TempoSample(event.timestamp, sqrt(x * x + y * y + z * z))
        _state.value = TempoState.Recording(seconds)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

package com.redline.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.settings.SettingsStore
import com.redline.app.voice.RemoteVoiceParser
import com.redline.app.voice.SetDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Result of the "Test endpoint" button. */
sealed interface EndpointTest {
    data object Idle : EndpointTest
    data object Running : EndpointTest
    data class Passed(val heard: String) : EndpointTest
    data class Failed(val reason: String) : EndpointTest
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val remoteParser: RemoteVoiceParser
) : ViewModel() {
    val userName: StateFlow<String> = settingsStore.userName
    val llmEndpoint: StateFlow<String> = settingsStore.llmEndpoint
    val defaultUnit: StateFlow<String> = settingsStore.defaultUnit
    val restDuration: StateFlow<Int> = settingsStore.restDuration
    val usdaApiKey: StateFlow<String> = settingsStore.usdaApiKey

    private val _endpointTest = MutableStateFlow<EndpointTest>(EndpointTest.Idle)
    val endpointTest: StateFlow<EndpointTest> = _endpointTest.asStateFlow()
    private var testJob: Job? = null

    fun setUserName(value: String) = settingsStore.setUserName(value)

    fun setLlmEndpoint(value: String) {
        settingsStore.setLlmEndpoint(value)
        testJob?.cancel()
        _endpointTest.value = EndpointTest.Idle
    }

    fun setDefaultUnit(value: String) = settingsStore.setDefaultUnit(value)

    fun changeRestDuration(by: Int) = settingsStore.setRestDuration(restDuration.value + by)

    fun setUsdaApiKey(value: String) = settingsStore.setUsdaApiKey(value)

    /** Sends a known sentence to the endpoint and shows what came back, or why nothing did. */
    fun testEndpoint() {
        testJob?.cancel()
        _endpointTest.value = EndpointTest.Running
        testJob = viewModelScope.launch {
            _endpointTest.value = remoteParser.probe(llmEndpoint.value).fold(
                onSuccess = { set ->
                    val weight = set.weight?.let { "${SetDraft.formatNumber(it)} ${set.unit} × " }.orEmpty()
                    EndpointTest.Passed("${set.exerciseName}, $weight${set.reps ?: "?"}")
                },
                onFailure = { EndpointTest.Failed(it.message ?: "Request failed") }
            )
        }
    }
}

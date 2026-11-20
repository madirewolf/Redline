package com.redline.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.settings.SettingsStore
import com.redline.app.ui.log.runs
import com.redline.app.voice.ClaudeSetParser
import com.redline.app.voice.ParsedSet
import com.redline.app.voice.RemoteVoiceParser
import com.redline.app.voice.SetDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Result of a Test button (the parser endpoint or the Anthropic key). */
sealed interface EndpointTest {
    data object Idle : EndpointTest
    data object Running : EndpointTest
    data class Passed(val heard: String) : EndpointTest
    data class Failed(val reason: String) : EndpointTest
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val remoteParser: RemoteVoiceParser,
    private val claudeParser: ClaudeSetParser
) : ViewModel() {
    val userName: StateFlow<String> = settingsStore.userName
    val llmEndpoint: StateFlow<String> = settingsStore.llmEndpoint
    val defaultUnit: StateFlow<String> = settingsStore.defaultUnit
    val restDuration: StateFlow<Int> = settingsStore.restDuration
    val usdaApiKey: StateFlow<String> = settingsStore.usdaApiKey
    val anthropicApiKey: StateFlow<String> = settingsStore.anthropicApiKey

    private val _endpointTest = MutableStateFlow<EndpointTest>(EndpointTest.Idle)
    val endpointTest: StateFlow<EndpointTest> = _endpointTest.asStateFlow()
    private var testJob: Job? = null

    private val _claudeTest = MutableStateFlow<EndpointTest>(EndpointTest.Idle)
    val claudeTest: StateFlow<EndpointTest> = _claudeTest.asStateFlow()
    private var claudeJob: Job? = null

    fun setUserName(value: String) = settingsStore.setUserName(value)

    fun setLlmEndpoint(value: String) {
        settingsStore.setLlmEndpoint(value)
        testJob?.cancel()
        _endpointTest.value = EndpointTest.Idle
    }

    fun setDefaultUnit(value: String) = settingsStore.setDefaultUnit(value)

    fun changeRestDuration(by: Int) = settingsStore.setRestDuration(restDuration.value + by)

    fun setUsdaApiKey(value: String) = settingsStore.setUsdaApiKey(value)

    fun setAnthropicApiKey(value: String) {
        settingsStore.setAnthropicApiKey(value)
        claudeJob?.cancel()
        _claudeTest.value = EndpointTest.Idle
    }

    /** Sends a sample sentence to Claude Haiku with the saved key and shows how it was read. */
    fun testClaude() {
        claudeJob?.cancel()
        _claudeTest.value = EndpointTest.Running
        claudeJob = viewModelScope.launch {
            _claudeTest.value = claudeParser.probe(anthropicApiKey.value).fold(
                onSuccess = { sets ->
                    if (sets.isEmpty()) EndpointTest.Failed("Claude answered but found no sets")
                    else EndpointTest.Passed(describe(sets))
                },
                onFailure = { EndpointTest.Failed(it.message ?: "Request failed") }
            )
        }
    }

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

    companion object {
        /** "Bench Press 4×5 at 135 lbs, 1×10 at 122 lbs": the exercise once, then each run. */
        fun describe(sets: List<ParsedSet>): String {
            var lastName: String? = null
            return sets.runs().joinToString(", ") { run ->
                val set = run.set
                buildString {
                    if (set.exerciseName != lastName) append("${set.exerciseName} ")
                    lastName = set.exerciseName
                    append("${run.count}\u00d7${set.reps ?: "?"}")
                    set.weight?.let { append(" at ${SetDraft.formatNumber(it)} ${set.unit}") }
                }
            }
        }
    }
}

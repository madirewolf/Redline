package com.redline.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redline.app.BuildConfig
import com.redline.app.ui.components.RedlineTopBar
import com.redline.app.ui.components.ScreenPadding
import com.redline.app.ui.components.SectionCard
import com.redline.app.ui.components.SectionLabel
import com.redline.app.ui.components.formatClock
import com.redline.app.ui.theme.RedlineTheme
import com.redline.app.ui.theme.Tabular
import com.redline.app.voice.ClaudeSetParser
import com.redline.app.voice.RemoteVoiceParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val savedName by viewModel.userName.collectAsStateWithLifecycle()
    val llmEndpoint by viewModel.llmEndpoint.collectAsStateWithLifecycle()
    val defaultUnit by viewModel.defaultUnit.collectAsStateWithLifecycle()
    val restDuration by viewModel.restDuration.collectAsStateWithLifecycle()
    val usdaApiKey by viewModel.usdaApiKey.collectAsStateWithLifecycle()
    val endpointTest by viewModel.endpointTest.collectAsStateWithLifecycle()
    val anthropicApiKey by viewModel.anthropicApiKey.collectAsStateWithLifecycle()
    val claudeTest by viewModel.claudeTest.collectAsStateWithLifecycle()
    var showKey by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    // The store trims what it saves; the field keeps what's typed, so a space
    // between first and last name doesn't vanish mid-word.
    var name by rememberSaveable { mutableStateOf(savedName) }

    Column(modifier = Modifier.fillMaxSize()) {
        RedlineTopBar(title = "Settings", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = ScreenPadding)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel("Profile", Modifier.padding(top = 4.dp))
            SectionCard {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; viewModel.setUserName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Name") },
                    supportingText = { Text("Used in the greeting on Home.") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
            }

            SectionLabel("Training", Modifier.padding(top = 8.dp))
            SectionCard(contentPadding = 0.dp) {
                SettingRow(title = "Weight unit", body = "What a set without a unit is logged in.") {
                    SingleChoiceSegmentedButtonRow {
                        listOf("lbs", "kg").forEachIndexed { index, unit ->
                            SegmentedButton(
                                selected = defaultUnit == unit,
                                onClick = { viewModel.setDefaultUnit(unit) },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                icon = {}
                            ) { Text(unit) }
                        }
                    }
                }
                HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                SettingRow(title = "Rest timer", body = "Starts after every saved set.") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = { viewModel.changeRestDuration(-REST_STEP) },
                            enabled = restDuration > MIN_REST
                        ) { Icon(Icons.Default.Remove, contentDescription = "Shorter rest") }
                        Text(
                            formatClock(restDuration.toLong()),
                            style = MaterialTheme.typography.titleMedium.merge(Tabular),
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .widthIn(min = 56.dp)
                                .semantics { contentDescription = "Rest ${restDuration / 60} minutes ${restDuration % 60} seconds" }
                        )
                        FilledTonalIconButton(
                            onClick = { viewModel.changeRestDuration(REST_STEP) },
                            enabled = restDuration < MAX_REST
                        ) { Icon(Icons.Default.Add, contentDescription = "Longer rest") }
                    }
                }
            }

            SectionLabel("Claude Haiku", Modifier.padding(top = 8.dp))
            SectionCard {
                Text(
                    "Optional. With your own Anthropic API key, anything not in the \u201cnext\u201d format is read by " +
                        "Claude Haiku, so you can talk normally: \u201c135 for 4 sets of 5, then 122 for 10.\u201d " +
                        "The key stays on this phone and out of backups. Each read is billed to your Anthropic account, " +
                        "and the phone reads the set itself if Claude can't be reached.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = anthropicApiKey,
                    onValueChange = viewModel::setAnthropicApiKey,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Anthropic API key") },
                    placeholder = { Text("sk-ant-\u2026") },
                    supportingText = { Text("Create one at console.anthropic.com") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        if (anthropicApiKey.isNotEmpty()) {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showKey) "Hide key" else "Show key"
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrect = false,
                        imeAction = ImeAction.Done
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = viewModel::testClaude,
                        enabled = anthropicApiKey.isNotBlank() && claudeTest != EndpointTest.Running
                    ) { Text("Test key") }
                    Spacer(modifier = Modifier.width(12.dp))
                    if (claudeTest == EndpointTest.Running) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                val claudeResult = claudeTest
                if (claudeResult is EndpointTest.Passed || claudeResult is EndpointTest.Failed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (claudeResult) {
                            is EndpointTest.Passed -> "Working. \u201c${ClaudeSetParser.PROBE_TRANSCRIPT}\u201d came back as ${claudeResult.heard}."
                            is EndpointTest.Failed -> "Not working: ${claudeResult.reason.trimEnd('.')}."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (claudeResult is EndpointTest.Passed) RedlineTheme.status.success else colors.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }

            SectionLabel("Voice parser", Modifier.padding(top = 8.dp))
            SectionCard {
                Text(
                    "Optional. Sets are read on the phone without one. With an endpoint, anything " +
                        "not in the “next” format goes to it first, and the phone takes over if it doesn't answer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                val invalid = llmEndpoint.isNotBlank() && !RemoteVoiceParser.isValidEndpoint(llmEndpoint)
                OutlinedTextField(
                    value = llmEndpoint,
                    onValueChange = viewModel::setLlmEndpoint,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Endpoint URL") },
                    placeholder = { Text("https://example.com/parse-set") },
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text("Enter a full URL, like https://example.com/parse-set") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = viewModel::testEndpoint,
                        enabled = !invalid && llmEndpoint.isNotBlank() && endpointTest != EndpointTest.Running
                    ) { Text("Test endpoint") }
                    Spacer(modifier = Modifier.width(12.dp))
                    if (endpointTest == EndpointTest.Running) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                val result = endpointTest
                if (result is EndpointTest.Passed || result is EndpointTest.Failed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (result) {
                            is EndpointTest.Passed -> "Working. “${RemoteVoiceParser.PROBE_TRANSCRIPT}” came back as ${result.heard}."
                            is EndpointTest.Failed -> "Not working: ${result.reason}."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result is EndpointTest.Passed) RedlineTheme.status.success else colors.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }

            SectionLabel("Food lookup", Modifier.padding(top = 8.dp))
            SectionCard {
                Text(
                    "Calories come from the USDA FoodData Central database, which needs a free API key.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = usdaApiKey,
                    onValueChange = viewModel::setUsdaApiKey,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("USDA API key") },
                    supportingText = { Text("Get one at fdc.nal.usda.gov/api-key-signup") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done)
                )
            }

            if (BuildConfig.SAMPLE_DATA) SampleDataSection()
        }
    }
}

@Composable
private fun SettingRow(title: String, body: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        control()
    }
}

/** Debug and demo builds only: a month of made-up data to see every chart filled in. */
@Composable
private fun SampleDataSection(viewModel: SampleDataViewModel = hiltViewModel()) {
    val loaded by viewModel.isLoaded.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    SectionLabel("Sample data", Modifier.padding(top = 8.dp))
    SectionCard {
        Text(
            "Fills the app with a month of made-up workouts, meals and videos; removing it leaves your own entries alone.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = viewModel::load,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Load a month of sample workouts") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = viewModel::clear,
            enabled = loaded && !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Remove sample data") }
        message?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

private const val REST_STEP = 15
private const val MIN_REST = 30
private const val MAX_REST = 600

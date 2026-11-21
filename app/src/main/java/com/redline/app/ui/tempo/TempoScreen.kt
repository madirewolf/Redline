package com.redline.app.ui.tempo

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.tempo.AudioInput
import com.redline.app.tempo.LiftKind
import com.redline.app.tempo.TempoAnalysis
import com.redline.app.tempo.TempoState
import com.redline.app.tempo.VoiceTempoState
import com.redline.app.ui.components.LiveDot
import com.redline.app.ui.components.PageHeader
import com.redline.app.ui.components.ScreenPadding
import com.redline.app.ui.components.SectionCard
import com.redline.app.ui.components.cleanNum
import com.redline.app.ui.components.formatClock
import com.redline.app.ui.theme.RedlineTheme
import com.redline.app.ui.theme.Tabular
import kotlin.math.roundToInt

@Composable
fun TempoScreen(viewModel: TempoViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastSet by viewModel.lastSet.collectAsStateWithLifecycle()
    val savedTo by viewModel.savedTo.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val voiceSet by viewModel.voiceSet.collectAsStateWithLifecycle()
    val voiceLevel by viewModel.voiceLevel.collectAsStateWithLifecycle()
    val input by viewModel.availableInput.collectAsStateWithLifecycle()
    val liftKind by viewModel.liftKind.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var micDenied by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted) viewModel.startVoice()
    }
    val startVoice = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            micDenied = false
            viewModel.startVoice()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val listening = voiceState is VoiceTempoState.Listening
    // The screen stays on while listening, so the live graph is there when you look.
    val view = LocalView.current
    DisposableEffect(listening) {
        view.keepScreenOn = listening
        onDispose { view.keepScreenOn = false }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(
                title = "Tempo",
                subtitle = when (mode) {
                    TempoMode.VOICE -> "Times each lift from your “bottom” and “top” cues, and estimates how close you are to failure as you go."
                    TempoMode.MOTION -> "Measures how much your reps slow down, using the phone's motion sensor."
                }
            )
        }
        item {
            ModeToggle(
                mode = mode,
                enabled = !listening && state !is TempoState.Recording,
                onMode = viewModel::setMode
            )
        }
        if (mode == TempoMode.VOICE) {
            voiceItems(
                state = voiceState,
                voice = voiceSet,
                level = voiceLevel,
                input = input,
                liftKind = liftKind,
                micDenied = micDenied,
                lastSet = lastSet,
                saved = savedTo != null && savedTo == lastSet?.id,
                viewModel = viewModel,
                onStart = startVoice
            )
            return@LazyColumn
        }
        when (val s = state) {
            TempoState.Idle -> item { IdleCard(onStart = viewModel::start) }
            is TempoState.Recording -> item { RecordingCard(seconds = s.seconds, onStop = viewModel::stop) }
            is TempoState.Ready -> {
                if (s.analysis.isScored) {
                    item { ResultCard(s.analysis) }
                    item {
                        SaveCard(
                            lastSet = lastSet,
                            saved = savedTo != null && savedTo == lastSet?.id,
                            onSave = viewModel::saveToLastSet
                        )
                    }
                } else {
                    item { UnscoredCard(s.analysis) }
                }
                item {
                    OutlinedButton(
                        onClick = viewModel::start,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Record another set") }
                }
            }
            is TempoState.Error -> item { ErrorCard(s.message, onRetry = viewModel::reset) }
        }
    }
}

private fun LazyListScope.voiceItems(
    state: VoiceTempoState,
    voice: VoiceSet?,
    level: Float,
    input: AudioInput,
    liftKind: LiftKind,
    micDenied: Boolean,
    lastSet: ExerciseSet?,
    saved: Boolean,
    viewModel: TempoViewModel,
    onStart: () -> Unit
) {
    if (state is VoiceTempoState.Error) {
        item { ErrorCard(state.message, onRetry = viewModel::resetVoice) }
        return
    }
    if (voice == null) {
        item { VoiceIdleCard(input, liftKind, viewModel::setLiftKind, micDenied, onStart) }
        return
    }
    if (voice.listening) {
        item { VoiceLiveCard(voice, level, onDone = viewModel::stopVoice, onRestart = onStart) }
        item { VoiceGraphCard(voice) }
        return
    }
    item { VoiceResultCard(voice) }
    item { VoiceGraphCard(voice) }
    if (voiceResultOf(voice) != null) {
        item { SaveCard(lastSet = lastSet, saved = saved, onSave = viewModel::saveVoiceToLastSet) }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onStart,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Time another set") }
            OutlinedButton(
                onClick = viewModel::resetVoice,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Clear") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeToggle(mode: TempoMode, enabled: Boolean, onMode: (TempoMode) -> Unit) {
    val options = listOf(TempoMode.VOICE to "Voice cues", TempoMode.MOTION to "Motion sensor")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onMode(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = enabled
            ) { Text(label) }
        }
    }
}

@Composable
private fun IdleCard(onStart: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SectionCard {
        listOf(
            "Put the phone in a pocket or an armband, on the part of you that moves.",
            "Tap Start, then do the set.",
            "Tap Stop when you rack the weight."
        ).forEachIndexed { index, step ->
            Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Surface(shape = CircleShape, color = colors.surfaceContainerHigh, modifier = Modifier.size(24.dp)) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start recording", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun RecordingCard(seconds: Int, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SectionCard(contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot()
            Spacer(modifier = Modifier.width(10.dp))
            Text("Recording", style = MaterialTheme.typography.titleSmall, color = colors.primary)
        }
        Text(
            formatClock(seconds.toLong()),
            style = MaterialTheme.typography.displayMedium.merge(Tabular),
            color = colors.onSurface,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            "Do your reps. Each rep shows up as a burst of movement.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.onSurface, contentColor = colors.surface)
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Stop and analyse", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun ResultCard(analysis: TempoAnalysis) {
    val colors = MaterialTheme.colorScheme
    val score = analysis.failureProximityScore ?: return
    val (verdict, tone) = verdictFor(score)
    SectionCard(contentPadding = 20.dp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text("Failure proximity", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                score.toString(),
                style = MaterialTheme.typography.displayMedium.merge(Tabular),
                fontWeight = FontWeight.SemiBold,
                color = tone
            )
            Text(
                " / 100",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Text(verdict, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        Text(analysis.notes + ".", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)

        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ResultMetric("Reps", analysis.repCount.toString(), Modifier.weight(1f))
            ResultMetric("Avg rep", analysis.averageRepSeconds?.let { "${it.cleanNum()} s" } ?: "–", Modifier.weight(1f))
            ResultMetric(
                "Slowdown",
                analysis.degradationPercent?.let { "${if (it > 0) "+" else ""}${it.roundToInt()}%" } ?: "–",
                Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        val confidence = analysis.confidence.coerceIn(0f, 1f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Confidence", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${(confidence * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge.merge(Tabular), color = colors.onSurface)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { confidence },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = colors.onSurfaceVariant,
            trackColor = colors.outlineVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        if (confidence < 0.5f) {
            Text(
                "Few clean reps were found, so treat the score as rough.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
internal fun SaveCard(lastSet: ExerciseSet?, saved: Boolean, onSave: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SectionCard {
        if (lastSet == null) {
            Text(
                "Log the set on the Train tab and this result can be saved to it.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            return@SectionCard
        }
        Text("Last logged set", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Text(
            describe(lastSet),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )
        if (saved) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Default.Check, contentDescription = null, tint = RedlineTheme.status.success)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Saved to this set", style = MaterialTheme.typography.labelLarge, color = RedlineTheme.status.success)
            }
        } else {
            FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Save result to this set")
            }
        }
    }
}

@Composable
private fun UnscoredCard(analysis: TempoAnalysis) {
    val colors = MaterialTheme.colorScheme
    SectionCard(contentPadding = 20.dp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text("Couldn't score this set", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text(
            analysis.notes + ".",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (analysis.repCount > 0) {
            Text(
                "Reps detected: ${analysis.repCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Keep the phone against your body, and record at least three reps.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
internal fun ErrorCard(message: String, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SectionCard(contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = colors.error)
            Spacer(modifier = Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry, modifier = Modifier.height(48.dp)) { Text("Try again") }
    }
}

@Composable
internal fun ResultMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge.merge(Tabular), color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun verdictFor(score: Int): Pair<String, Color> = when {
    score >= 85 -> "At or near failure" to MaterialTheme.colorScheme.primary
    score >= 55 -> "Hard set, a rep or two left" to RedlineTheme.status.warning
    else -> "Reps left in the tank" to RedlineTheme.status.success
}

private fun describe(set: ExerciseSet): String = buildString {
    append(set.exerciseName)
    val load = listOfNotNull(
        set.weight?.let { "${it.cleanNum()} ${set.unit}" },
        set.reps?.let { if (it == 1) "1 rep" else "$it reps" }
    ).joinToString(" × ")
    if (load.isNotEmpty()) append(", ").append(load)
}

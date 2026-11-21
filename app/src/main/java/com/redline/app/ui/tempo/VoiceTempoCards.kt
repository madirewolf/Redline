package com.redline.app.ui.tempo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.redline.app.tempo.AudioInput
import com.redline.app.tempo.Cue
import com.redline.app.tempo.LiftKind
import com.redline.app.tempo.TempoEstimate
import com.redline.app.tempo.VoiceTempoMath
import com.redline.app.ui.components.LiveDot
import com.redline.app.ui.components.SectionCard
import com.redline.app.ui.components.cleanNum
import com.redline.app.ui.components.formatClock
import com.redline.app.ui.theme.Tabular
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Below this, a cue's label is a guess, and the result says so. */
private const val CLEAR_CUE = 0.75f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceIdleCard(
    input: AudioInput,
    kind: LiftKind,
    onKind: (LiftKind) -> Unit,
    micDenied: Boolean,
    onStart: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    SectionCard {
        listOf(
            "Say “bottom” as the weight reaches the bottom of each rep, and “top” as you lock it out.",
            "Each lift is timed from bottom to top, and the graph shows how much each one slows.",
            "Tap Done when you rack the weight."
        ).forEachIndexed { index, step ->
            Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Surface(shape = CircleShape, color = colors.surfaceContainerHigh, modifier = Modifier.size(24.dp)) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(iconFor(input), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Listening through", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(input.name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            }
        }
        val inputNote = when (input.kind) {
            AudioInput.Kind.BLUETOOTH ->
                "A Bluetooth mic only works over the call link, so music sounds flatter until you tap Done. A wired headset doesn't have this tradeoff."
            AudioInput.Kind.PHONE ->
                "No headset found. The phone's mic works if it's within a metre or so of you."
            AudioInput.Kind.WIRED -> null
        }
        inputNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Lift", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            LiftKind.entries.forEach { option ->
                FilterChip(
                    selected = option == kind,
                    onClick = { onKind(option) },
                    label = { Text(option.label) }
                )
            }
        }
        Text(
            "The bar is expected to stall once a rep takes ${stallMultiple(kind)} as long as the set's quickest start.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )

        if (micDenied) {
            Text(
                "Microphone access is off. Turn it on for Redline in your phone's settings.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start listening", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
internal fun VoiceLiveCard(voice: VoiceSet, level: Float, onDone: () -> Unit, onRestart: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val palette = proximityPalette()
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(voice.startedNs) {
        while (true) {
            elapsed = (System.nanoTime() - voice.startedNs) / 1_000_000_000L
            delay(250)
        }
    }
    SectionCard(contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot()
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Listening · ${voice.input.name}",
                style = MaterialTheme.typography.titleSmall,
                color = colors.primary,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(formatClock(elapsed), style = MaterialTheme.typography.titleSmall.merge(Tabular), color = colors.onSurfaceVariant)
        }

        // The cue just heard, popping in each time one registers.
        val last = voice.lastCue
        AnimatedContent(
            targetState = voice.cues.size,
            transitionSpec = { (scaleIn(tween(160), initialScale = 0.7f) + fadeIn(tween(160))) togetherWith fadeOut(tween(90)) },
            label = "cue",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) { count ->
            val cue = if (count == voice.cues.size) last else voice.cues.getOrNull(count - 1)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (cue?.cue) {
                        Cue.BOTTOM -> "BOTTOM"
                        Cue.TOP -> "TOP"
                        null -> "READY"
                    },
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        cue == null -> colors.onSurfaceVariant
                        cue.confidence < CLEAR_CUE -> colors.onSurfaceVariant
                        else -> colors.onSurface
                    }
                )
                Text(
                    cueHint(voice, cue?.cue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { level.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = colors.onSurfaceVariant,
            trackColor = colors.outlineVariant,
            strokeCap = StrokeCap.Round
        )

        Spacer(modifier = Modifier.height(18.dp))
        VoiceMetrics(voice, palette)

        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onDone,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.onSurface, contentColor = colors.surface)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Done", style = MaterialTheme.typography.titleSmall)
            }
            OutlinedButton(onClick = onRestart, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Text("Start over")
            }
        }
    }
}

@Composable
internal fun VoiceGraphCard(voice: VoiceSet) {
    val colors = MaterialTheme.colorScheme
    SectionCard(contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lift time per rep", style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text(voice.kind.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(10.dp))
        RepTimeGraph(voice)
    }
}

@Composable
internal fun VoiceResultCard(voice: VoiceSet) {
    val colors = MaterialTheme.colorScheme
    val palette = proximityPalette()
    val estimate = voice.estimate
    val rpe = estimate?.rpe
    SectionCard(contentPadding = 20.dp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        if (estimate == null || rpe == null) {
            Text("Not enough reps to estimate RPE", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(
                when (voice.reps.size) {
                    0 -> "No reps were timed. Say “bottom” at the bottom of each rep and “top” at lockout."
                    else -> "${repsText(voice.reps.size)} timed. It takes three to see a trend."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (voice.reps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                VoiceMetrics(voice, palette)
            }
            return@SectionCard
        }
        val tone = palette.at(estimate.proximity(estimate.currentLoss ?: 0f))
        Text("Estimated RPE", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Text(
            VoiceTempoMath.roundRpe(rpe).cleanNum(),
            style = MaterialTheme.typography.displayMedium.merge(Tabular),
            fontWeight = FontWeight.SemiBold,
            color = tone
        )
        Text(reserveText(estimate), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        Text(
            "From how much your lifts slowed against the set's start, and where that trend reaches the stall point.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(18.dp))
        VoiceMetrics(voice, palette, showRpe = false)
        if (voice.cues.any { it.confidence < CLEAR_CUE }) {
            Text(
                "Some cues were hard to make out, so check the rep times on the graph.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun VoiceMetrics(voice: VoiceSet, palette: ProximityPalette, showRpe: Boolean = true) {
    val estimate = voice.estimate
    val last = voice.reps.lastOrNull()?.seconds
    Row(modifier = Modifier.fillMaxWidth()) {
        ResultMetric("Reps", voice.reps.size.toString(), Modifier.weight(1f))
        ResultMetric("Last lift", last?.let { "${it.cleanNum()} s" } ?: "–", Modifier.weight(1f))
        ResultMetric(
            "Slowdown",
            if (estimate != null && last != null) slowdownText(last / estimate.referenceSeconds - 1f) else "–",
            Modifier.weight(1f)
        )
        if (showRpe) {
            val rpe = estimate?.rpe
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rpe?.let { VoiceTempoMath.roundRpe(it).cleanNum() } ?: "–",
                    style = MaterialTheme.typography.titleLarge.merge(Tabular),
                    fontWeight = FontWeight.SemiBold,
                    color = if (rpe != null && estimate != null) palette.at(estimate.proximity(estimate.currentLoss ?: 0f)) else MaterialTheme.colorScheme.onSurface
                )
                Text("Est. RPE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (showRpe && estimate?.repsInReserve != null) {
        Text(
            reserveText(estimate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    } else if (showRpe && voice.reps.size < VoiceTempoMath.MIN_REPS_FOR_ESTIMATE) {
        Text(
            "RPE shows after ${VoiceTempoMath.MIN_REPS_FOR_ESTIMATE} reps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

private fun cueHint(voice: VoiceSet, cue: Cue?): String = when (cue) {
    null -> "Say “bottom” as the weight reaches the bottom."
    Cue.BOTTOM -> "Lifting rep ${voice.reps.size + 1}. Say “top” at lockout."
    Cue.TOP -> voice.reps.lastOrNull()?.let { "Rep ${it.number}: ${it.seconds.cleanNum()} s" } ?: "Say “bottom” at the bottom of the rep."
}

private fun reserveText(estimate: TempoEstimate): String {
    val reserve = estimate.repsInReserve ?: return ""
    val reps = reserve.roundToInt()
    return when {
        reserve >= VoiceTempoMath.MAX_REPS_IN_RESERVE -> "${VoiceTempoMath.MAX_REPS_IN_RESERVE.roundToInt()}+ reps left"
        reps <= 0 -> "At the stall point"
        else -> "About ${repsText(reps)} left before a stall"
    }
}

private fun slowdownText(fraction: Float): String {
    val percent = (fraction * 100).roundToInt()
    return "${if (percent > 0) "+" else ""}$percent%"
}

private fun repsText(count: Int) = if (count == 1) "1 rep" else "$count reps"

/** How many times the starting lift time a stall takes: 1 / (1 − stall loss). */
private fun stallMultiple(kind: LiftKind): String = "${(1f / (1f - kind.failureLoss)).cleanNum()}×"

private fun iconFor(input: AudioInput): ImageVector = when (input.kind) {
    AudioInput.Kind.WIRED -> Icons.Default.Headset
    AudioInput.Kind.BLUETOOTH -> Icons.Default.BluetoothAudio
    AudioInput.Kind.PHONE -> Icons.Default.PhoneAndroid
}

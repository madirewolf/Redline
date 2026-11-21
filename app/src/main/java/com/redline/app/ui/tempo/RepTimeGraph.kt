package com.redline.app.ui.tempo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.redline.app.tempo.TempoEstimate
import com.redline.app.tempo.VoiceTempoMath
import com.redline.app.ui.components.cleanNum
import com.redline.app.ui.theme.RedlineTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Green while a rep is as quick as the set's start, through amber, to red at the stall point. */
internal data class ProximityPalette(val calm: Color, val near: Color, val stall: Color) {
    fun at(proximity: Float): Color {
        val p = proximity.coerceIn(0f, 1f)
        return if (p < 0.5f) lerp(calm, near, p / 0.5f) else lerp(near, stall, (p - 0.5f) / 0.5f)
    }
}

@Composable
internal fun proximityPalette() = ProximityPalette(
    calm = RedlineTheme.status.success,
    near = RedlineTheme.status.warning,
    stall = MaterialTheme.colorScheme.primary
)

/**
 * Each rep's lift time, in seconds, against rep number. The y axis starts at zero so heights
 * compare honestly. The dashed line is the time at which the lift is expected to stall, and the
 * dotted line extends the least-squares trend of the latest reps until it gets there. While a rep
 * is being lifted, its time so far grows in place.
 */
@Composable
internal fun RepTimeGraph(voice: VoiceSet, modifier: Modifier = Modifier) {
    val palette = proximityPalette()
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val stallStyle = labelStyle.copy(color = palette.stall)
    val frameNs = remember { mutableLongStateOf(System.nanoTime()) }
    val open = voice.openBottom?.takeIf { voice.listening }
    if (open != null) {
        // Redraw every frame while a rep is in the air, so its bar grows smoothly.
        LaunchedEffect(open.timeNs) {
            while (true) withFrameNanos { frameNs.longValue = System.nanoTime() }
        }
    }
    val description = remember(voice.reps, voice.estimate) { describe(voice) }
    val graph = GraphColors(
        palette = palette,
        grid = colors.outlineVariant,
        neutral = colors.onSurfaceVariant,
        trend = colors.onSurfaceVariant.copy(alpha = 0.7f)
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = description }
    ) {
        val live = open?.let { ((frameNs.longValue - it.timeNs) / 1e9f).coerceIn(0f, VoiceTempoMath.MAX_REP_SECONDS) }
        drawGraph(voice, live, graph, measurer, labelStyle, stallStyle)
    }
}

private class GraphColors(val palette: ProximityPalette, val grid: Color, val neutral: Color, val trend: Color)

private fun DrawScope.drawGraph(
    voice: VoiceSet,
    live: Float?,
    colors: GraphColors,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    stallStyle: TextStyle
) {
    val seconds = voice.reps.map { it.seconds }
    val n = seconds.size
    val estimate = voice.estimate

    if (n == 0 && live == null) {
        val text = measurer.measure("Rep times show up here", labelStyle)
        drawText(text, topLeft = Offset((size.width - text.size.width) / 2f, (size.height - text.size.height) / 2f))
        return
    }

    // The trend, by 0-based rep index, until it reaches the stall point.
    val slope = estimate?.lossPerRep
    val reserve = estimate?.repsInReserve
    val projectedEnd = if (slope != null && reserve != null && slope > VoiceTempoMath.FLAT_SLOPE) (n - 1) + reserve else null

    val lastRep = maxOf(5f, n + 1f, projectedEnd?.let { ceil(it) + 1f } ?: 0f)
    var top = 1f
    seconds.maxOrNull()?.let { top = max(top, it) }
    estimate?.let { top = max(top, it.stallSeconds) }
    live?.let { top = max(top, it) }
    top *= 1.12f

    val left = 40.dp.toPx()
    val right = size.width - 12.dp.toPx()
    val plotTop = 10.dp.toPx()
    val bottom = size.height - 22.dp.toPx()
    fun x(rep: Float) = left + (rep - 0.5f) / lastRep * (right - left)
    fun y(sec: Float) = bottom - sec / top * (bottom - plotTop)

    // Grid, with seconds on the left.
    val step = niceStep(top)
    var tick = 0f
    while (tick <= top) {
        drawLine(colors.grid, Offset(left, y(tick)), Offset(right, y(tick)), strokeWidth = if (tick == 0f) 1.5f else 1f)
        val label = measurer.measure("${tick.cleanNum()} s", labelStyle)
        drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y(tick) - label.size.height / 2f))
        tick += step
    }
    // Rep numbers along the bottom.
    val every = if (lastRep > 10) 2 else 1
    for (rep in 1..floor(lastRep).toInt()) {
        if (rep % every != 0 && rep != 1) continue
        val label = measurer.measure("$rep", labelStyle)
        drawText(label, topLeft = Offset(x(rep.toFloat()) - label.size.width / 2f, bottom + 4.dp.toPx()))
    }

    if (estimate != null) drawStall(estimate, left, right, ::y, colors, measurer, stallStyle)

    // The trend line, through the reps it was fitted to and on to the stall point.
    if (estimate != null && projectedEnd != null) {
        val from = max(0, n - VoiceTempoMath.TREND_WINDOW).toFloat()
        val path = Path()
        val steps = 32
        for (k in 0..steps) {
            val index = from + (projectedEnd - from) * k / steps
            val sec = estimate.trendSeconds(index) ?: continue
            val point = Offset(x(index + 1f), y(sec))
            if (k == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(
            path,
            colors.trend,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 5.dp.toPx()))
            )
        )
    }

    fun colorOf(loss: Float): Color = estimate?.let { colors.palette.at(it.proximity(loss)) } ?: colors.neutral
    val points = seconds.mapIndexed { i, sec -> Offset(x(i + 1f), y(sec)) }
    val pointColors = seconds.indices.map { i -> colorOf(estimate?.losses?.getOrNull(i) ?: 0f) }

    // The rep being lifted now: a bar that grows with its time so far.
    if (live != null) {
        val liveColor = estimate?.let { colorOf(VoiceTempoMath.lossOf(live, it.referenceSeconds)) } ?: colors.neutral
        val cx = x(n + 1f)
        val barWidth = 12.dp.toPx()
        drawRoundRect(
            liveColor.copy(alpha = 0.22f),
            topLeft = Offset(cx - barWidth / 2f, y(live)),
            size = Size(barWidth, bottom - y(live)),
            cornerRadius = CornerRadius(barWidth / 2f)
        )
        points.lastOrNull()?.let { last ->
            drawLine(
                liveColor.copy(alpha = 0.6f),
                last,
                Offset(cx, y(live)),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            )
        }
        drawCircle(liveColor, radius = 5.dp.toPx(), center = Offset(cx, y(live)), style = Stroke(2.dp.toPx()))
    }

    for (i in 0 until points.size - 1) {
        drawLine(
            Brush.linearGradient(listOf(pointColors[i], pointColors[i + 1]), points[i], points[i + 1]),
            points[i],
            points[i + 1],
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
    points.forEachIndexed { i, point -> drawCircle(pointColors[i], radius = 4.5.dp.toPx(), center = point) }
}

private fun DrawScope.drawStall(
    estimate: TempoEstimate,
    left: Float,
    right: Float,
    y: (Float) -> Float,
    colors: GraphColors,
    measurer: TextMeasurer,
    style: TextStyle
) {
    val at = y(estimate.stallSeconds)
    drawLine(
        colors.palette.stall.copy(alpha = 0.75f),
        Offset(left, at),
        Offset(right, at),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
    )
    val label = measurer.measure("stall ≈ ${estimate.stallSeconds.cleanNum()} s", style)
    drawText(label, topLeft = Offset(right - label.size.width, at - label.size.height - 2.dp.toPx()))
}

/** A round grid step that gives four or fewer lines up to [top]. */
private fun niceStep(top: Float): Float =
    listOf(0.25f, 0.5f, 1f, 2f, 5f, 10f).firstOrNull { top / it <= 4f } ?: 10f

private fun describe(voice: VoiceSet): String = buildString {
    if (voice.reps.isEmpty()) {
        append("No reps timed yet.")
        return@buildString
    }
    append("Rep times: ")
    append(voice.reps.joinToString { "rep ${it.number}, ${it.seconds.cleanNum()} seconds" })
    append(". ")
    voice.estimate?.let { append("Expected stall at ${it.stallSeconds.cleanNum()} seconds.") }
}

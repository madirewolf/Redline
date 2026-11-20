package com.redline.app.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.redline.app.ui.components.CardShape
import com.redline.app.ui.components.EmptyState
import com.redline.app.ui.components.PageHeader
import com.redline.app.ui.components.ScreenPadding
import com.redline.app.ui.components.compact
import com.redline.app.ui.components.plural
import com.redline.app.ui.navigation.Routes
import com.redline.app.ui.theme.RedlineTheme
import com.redline.app.ui.theme.Tabular
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(navController: NavController, viewModel: ProgressViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(
                title = "Progress",
                subtitle = if (uiState.summaries.isEmpty()) null
                else "${plural(uiState.summaries.size, "lift")}, best estimated 1RM per session"
            )
        }

        if (uiState.isLoading) return@LazyColumn

        if (uiState.summaries.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    title = "No lifts yet",
                    body = "Log sets with a weight and reps, and each lift gets a trend line here.",
                    modifier = Modifier.padding(top = 48.dp)
                )
            }
            return@LazyColumn
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressStatus.entries.forEach { status ->
                    val count = uiState.summaries.count { it.status == status }
                    if (count > 0) StatusChip(status, count)
                }
            }
        }

        items(uiState.summaries, key = { it.exerciseKey }) { summary ->
            ProgressCard(summary, onClick = {
                navController.navigate(Routes.exerciseHistory(summary.exerciseKey))
            })
        }
    }
}

@Composable
private fun ProgressCard(summary: ExerciseProgress, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surfaceContainer)
            .clickable(onClickLabel = "Open history", onClick = onClick)
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(summary.exerciseName, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(modifier = Modifier.height(6.dp))
                StatusChip(summary.status)
            }
            val headline = summary.latestEstimatedOneRepMax?.let { it.roundToInt() to "est. 1RM" }
                ?: summary.latestBestReps?.let { it to "top set reps" }
            headline?.let { (value, label) ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.headlineSmall.merge(Tabular),
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface
                    )
                    Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        if (summary.trendPoints.size >= 2) {
            Spacer(modifier = Modifier.height(12.dp))
            TrendLine(summary.trendPoints, modifier = Modifier.height(56.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (summary.isBodyweight) {
                Metric("Reps", summary.totalReps.toLong().compact())
                Metric("Sets", summary.setCount.toString())
                Metric("Weight", "Bodyweight")
            } else {
                Metric("Volume", summary.totalVolume.toLong().compact())
                Metric("Sets", summary.setCount.toString())
                Metric("Best 1RM", summary.bestEstimatedOneRepMax?.roundToInt()?.toString() ?: "–")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(insight(summary), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

/** Best estimated 1RM per session, oldest to newest. Needs at least two points to draw. */
@Composable
internal fun TrendLine(points: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val description = if (points.size < 2) "Not enough sessions for a trend"
    else "Trend from ${points.first().roundToInt()} to ${points.last().roundToInt()} over ${points.size} sessions"
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
    ) {
        drawLine(
            color = trackColor,
            start = Offset(0f, size.height - 1.dp.toPx()),
            end = Offset(size.width, size.height - 1.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
        if (points.size < 2) return@Canvas

        val min = points.min()
        val max = points.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val inset = 6.dp.toPx()
        val step = (size.width - inset * 2) / (points.size - 1)
        val coords = points.mapIndexed { index, point ->
            Offset(
                inset + index * step,
                size.height - ((point - min) / range) * (size.height - 18.dp.toPx()) - 8.dp.toPx()
            )
        }
        val line = Path().apply {
            coords.forEachIndexed { i, c -> if (i == 0) moveTo(c.x, c.y) else lineTo(c.x, c.y) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(coords.last().x, size.height)
            lineTo(coords.first().x, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.22f), Color.Transparent)))
        drawPath(line, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        coords.dropLast(1).forEach { drawCircle(lineColor, radius = 2.5.dp.toPx(), center = it) }
        drawCircle(lineColor.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = coords.last())
        drawCircle(lineColor, radius = 4.5.dp.toPx(), center = coords.last())
    }
}

@Composable
private fun StatusChip(status: ProgressStatus, count: Int? = null) {
    val color = statusColor(status)
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (count != null) "$count ${status.label.lowercase()}" else status.label,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium.merge(Tabular), color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun statusColor(status: ProgressStatus): Color = when (status) {
    ProgressStatus.Progressing -> RedlineTheme.status.success
    ProgressStatus.Stagnating -> MaterialTheme.colorScheme.primary
    ProgressStatus.Regressing -> RedlineTheme.status.warning
    ProgressStatus.Building -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun insight(summary: ExerciseProgress) = buildAnnotatedString {
    if (summary.lastThreeSame) {
        append("Same load and reps for 3 sessions. Change the weight, reps, tempo or how close you go to failure.")
        return@buildAnnotatedString
    }
    if (summary.isBodyweight) {
        val latest = summary.latestBestReps
        val previous = summary.previousBestReps
        if (latest == null || previous == null) {
            append("Tracked by reps. Log another session to compare.")
            return@buildAnnotatedString
        }
        val diff = latest - previous
        withStyle(SpanStyle(color = statusColor(summary.status), fontWeight = FontWeight.SemiBold)) {
            append(
                when {
                    diff > 0 -> "▲ ${plural(diff, "rep")}"
                    diff < 0 -> "▼ ${plural(-diff, "rep")}"
                    else -> "● Same reps"
                }
            )
        }
        append(if (diff == 0) " as last session" else " since last session")
        return@buildAnnotatedString
    }
    val trend = summary.trendPercent
    if (trend == null) {
        append("Log another session to see a trend.")
        return@buildAnnotatedString
    }
    val arrow = if (trend > 0f) "▲" else if (trend < 0f) "▼" else "●"
    withStyle(SpanStyle(color = statusColor(summary.status), fontWeight = FontWeight.SemiBold)) {
        append("$arrow ${abs(trend).cleanNumber()}%")
    }
    append(" since last session")
    summary.latestFailureScore?.let { append("  ·  failure score $it/100") }
}

private fun Float.cleanNumber(): String {
    val tenths = (this * 10).roundToInt()
    return if (tenths % 10 == 0) (tenths / 10).toString() else "%.1f".format(tenths / 10f)
}

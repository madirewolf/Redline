package com.redline.app.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.redline.app.ui.components.compact
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.redline.app.ui.navigation.Routes
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Outline
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.SurfaceVariant
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(navController: NavController, viewModel: ProgressViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }
    if (uiState.summaries.isEmpty()) {
        EmptyProgress()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Progress",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${uiState.summaries.size} lifts · best estimated 1RM each session",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        item { Spacer(modifier = Modifier.height(112.dp)) }
    }
}

@Composable
private fun EmptyProgress() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Equalizer,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = OnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Progression Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Track your lifts over time. Log a few workouts to see trends appear here.",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProgressCard(summary: ExerciseProgress, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.exerciseName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                StatusChip(summary.status)
            }
            summary.latestEstimatedOneRepMax?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = it.roundToInt().toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "est. 1RM",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        TrendLine(summary.trendPoints)
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Metric("Volume", summary.totalVolume.toLong().compact())
            Metric("Sets", summary.setCount.toString())
            Metric("Best 1RM", summary.bestEstimatedOneRepMax?.roundToInt()?.toString() ?: "-")
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = insight(summary),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant
        )
    }
}

@Composable
private fun TrendLine(points: List<Float>) {
    val lineColor = Red500
    val mutedColor = Outline
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        drawLine(
            color = mutedColor,
            start = Offset(0f, size.height - 1.dp.toPx()),
            end = Offset(size.width, size.height - 1.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )

        if (points.size < 2) return@Canvas

        val min = points.minOrNull() ?: 0f
        val max = points.maxOrNull() ?: min
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

        drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        coords.dropLast(1).forEach { drawCircle(lineColor, radius = 2.5.dp.toPx(), center = it) }
        drawCircle(lineColor.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = coords.last())
        drawCircle(lineColor, radius = 5.dp.toPx(), center = coords.last())
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
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariant
        )
    }
}

private fun statusColor(status: ProgressStatus): Color {
    return when (status) {
        ProgressStatus.Progressing -> Color(0xFF4CAF50)
        ProgressStatus.Stagnating -> Red500
        ProgressStatus.Regressing -> Color(0xFFFFB74D)
        ProgressStatus.Building -> OnSurfaceVariant
    }
}

private fun insight(summary: ExerciseProgress) = buildAnnotatedString {
    if (summary.lastThreeSame) {
        append("Same load and reps for 3 sessions. Change the weight, reps, tempo or how close you go to failure.")
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

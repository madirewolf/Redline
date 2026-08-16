package com.redline.app.ui.progress

import androidx.compose.foundation.Canvas
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
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(navController: NavController, viewModel: ProgressViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.summaries.isEmpty()) {
        EmptyProgress()
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Progress",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${uiState.summaries.size} lifts tracked",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }

        items(uiState.summaries, key = { it.exerciseKey }) { summary ->
            ProgressCard(summary, onClick = {
                navController.navigate(Routes.exerciseHistory(summary.exerciseKey))
            })
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
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
            .background(SurfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
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
                Text(
                    text = summary.status.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor(summary.status)
                )
            }
            summary.latestEstimatedOneRepMax?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = it.cleanNumber(),
                        style = MaterialTheme.typography.headlineMedium,
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

        Spacer(modifier = Modifier.height(12.dp))
        TrendLine(summary.trendPoints)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Metric("Volume", summary.totalVolume.cleanNumber())
            Metric("Sets", summary.setCount.toString())
            Metric("Best", summary.bestEstimatedOneRepMax?.cleanNumber() ?: "-")
        }

        Spacer(modifier = Modifier.height(10.dp))
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
            .height(54.dp)
    ) {
        drawLine(
            color = mutedColor,
            start = Offset(0f, size.height - 2.dp.toPx()),
            end = Offset(size.width, size.height - 2.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )

        if (points.size < 2) return@Canvas

        val min = points.minOrNull() ?: 0f
        val max = points.maxOrNull() ?: min
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val step = size.width / (points.size - 1)
        val path = Path()

        points.forEachIndexed { index, point ->
            val x = index * step
            val y = size.height - ((point - min) / range) * (size.height - 8.dp.toPx()) - 4.dp.toPx()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
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

private fun insight(summary: ExerciseProgress): String {
    if (summary.lastThreeSame) {
        return "Same load and reps for 3 sessions. Change weight, reps, tempo, or proximity to failure."
    }
    summary.trendPercent?.let {
        val sign = if (it > 0f) "+" else ""
        return "$sign${it.cleanNumber()}% since last session" +
            (summary.latestFailureScore?.let { score -> " - latest failure score $score/100" } ?: "")
    }
    return "Log another session to establish a trend."
}

private fun Float.cleanNumber(): String {
    return if (this == roundToInt().toFloat()) roundToInt().toString() else String.format("%.1f", this)
}

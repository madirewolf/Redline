package com.redline.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.ui.components.cleanNum
import com.redline.app.ui.components.grouped
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Red400
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.SurfaceContainer
import com.redline.app.ui.theme.SurfaceVariant
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WorkoutDetailScreen(
    sessionId: Long,
    navController: NavController,
    viewModel: WorkoutDetailViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val groups by viewModel.exerciseGroups.collectAsStateWithLifecycle()
    val setCount by viewModel.setCount.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()

    val s = session ?: return

    val date = Instant.ofEpochMilli(s.startTime).atZone(ZoneId.systemDefault())
    val dateStr = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    val timeStr = date.format(DateTimeFormatter.ofPattern("h:mm a"))
    val durationMin = s.endTime?.let { (it - s.startTime) / 60000 }
    val allSets = groups.flatMap { it.sets }
    val volume = allSets.sumOf { ((it.weight ?: 0f) * (it.reps ?: 0)).toDouble() }.toLong()
    val unit = allSets.firstOrNull { it.weight != null }?.unit ?: "lbs"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(dateStr, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text("Started $timeStr", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                }
                IconButton(onClick = { viewModel.toggleEditing() }) {
                    Icon(
                        if (isEditing) Icons.Default.EditOff else Icons.Default.Edit,
                        contentDescription = "Toggle edit",
                        tint = if (isEditing) Red500 else OnSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariant, RoundedCornerShape(20.dp))
                    .padding(vertical = 16.dp)
            ) {
                Stat(durationMin?.let { "$it min" } ?: "-", "Duration", Modifier.weight(1f))
                Stat(setCount.toString(), "Sets", Modifier.weight(1f))
                Stat(if (volume > 0) "${volume.grouped()} $unit" else "-", "Volume", Modifier.weight(1f))
            }
        }

        // Exercise groups
        groups.forEach { group ->
            item(key = "header_${group.exerciseName}") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group.exerciseName,
                        style = MaterialTheme.typography.labelLarge,
                        color = Red500,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${group.sets.size} ${if (group.sets.size == 1) "set" else "sets"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariant
                    )
                }
            }
            items(group.sets, key = { it.id }) { set ->
                SetRow(
                    set = set,
                    number = group.sets.indexOf(set) + 1,
                    isEditing = isEditing,
                    onDelete = { viewModel.deleteSet(set) }
                )
            }
        }

        item { Spacer(modifier = Modifier.navigationBarsPadding().height(24.dp)) }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
    }
}

@Composable
private fun SetRow(
    set: ExerciseSet,
    number: Int,
    isEditing: Boolean,
    onDelete: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariant, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = if (isEditing) 4.dp else 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(26.dp).background(SurfaceContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    set.weight?.let {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = onSurface)) { append(it.cleanNum()) }
                        withStyle(SpanStyle(color = OnSurfaceVariant, fontSize = 13.sp)) { append(" ${set.unit}") }
                    }
                    set.reps?.let {
                        if (set.weight != null) withStyle(SpanStyle(color = OnSurfaceVariant)) { append("  ×  ") }
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = onSurface)) { append(it.toString()) }
                        withStyle(SpanStyle(color = OnSurfaceVariant, fontSize = 13.sp)) { append(if (it == 1) " rep" else " reps") }
                    }
                },
                style = MaterialTheme.typography.bodyLarge
            )
            set.failureProximityScore?.let {
                Text("Failure score $it/100", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
            }
        }
        set.rpe?.let {
            Text(
                "RPE ${it.cleanNum()}",
                style = MaterialTheme.typography.labelMedium,
                color = Red400,
                modifier = Modifier
                    .background(Red500.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        if (isEditing) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete set", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

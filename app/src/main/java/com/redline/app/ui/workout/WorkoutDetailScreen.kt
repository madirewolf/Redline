package com.redline.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Red500
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Row {
                        Text(timeStr, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                        durationMin?.let {
                            Text(" · ${it}min", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                        }
                        Text(" · $setCount sets", style = MaterialTheme.typography.bodyMedium, color = Red500)
                    }
                }
                IconButton(onClick = { viewModel.toggleEditing() }) {
                    Icon(
                        if (isEditing) Icons.Default.EditOff else Icons.Default.Edit,
                        contentDescription = "Toggle edit",
                        tint = if (isEditing) Red500 else OnSurfaceVariant
                    )
                }
            }
        }

        // Exercise groups
        groups.forEach { group ->
            item(key = "header_${group.exerciseName}") {
                Text(
                    text = group.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(group.sets, key = { it.id }) { set ->
                SetRow(set = set, isEditing = isEditing, onDelete = { viewModel.deleteSet(set) })
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun SetRow(
    set: ExerciseSet,
    isEditing: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row {
                set.weight?.let {
                    Text(
                        "${it.cleanNum()} ${set.unit}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Red500,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                set.reps?.let {
                    Text(
                        "x $it reps",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Row {
                set.rpe?.let {
                    Text("RPE ${it.cleanNum()}", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                set.failureProximityScore?.let {
                    Text("Failure $it/100", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                }
            }
        }

        if (isEditing) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete set", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun Float.cleanNum(): String = if (this == toLong().toFloat()) toLong().toString() else toString()

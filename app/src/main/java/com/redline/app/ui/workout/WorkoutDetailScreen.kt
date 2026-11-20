package com.redline.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.HorizontalDivider
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.ui.components.EmptyState
import com.redline.app.ui.components.RedlineTopBar
import com.redline.app.ui.components.ScreenPadding
import com.redline.app.ui.components.SectionCard
import com.redline.app.ui.components.SetEditorDialog
import com.redline.app.ui.components.cleanNum
import com.redline.app.ui.components.formatDuration
import com.redline.app.ui.components.grouped
import com.redline.app.ui.components.plural
import com.redline.app.ui.components.toDraft
import com.redline.app.ui.theme.Tabular
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WorkoutDetailScreen(onBack: () -> Unit, viewModel: WorkoutDetailViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val groups by viewModel.exerciseGroups.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    val start = session?.let { Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()) }
    val title = start?.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")) ?: "Workout"

    editing?.let { set ->
        SetEditorDialog(
            title = "Edit set",
            initial = set.toDraft(),
            onSave = viewModel::save,
            onDismiss = viewModel::closeEditor,
            onDelete = viewModel::deleteEditing
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        RedlineTopBar(title = title, onBack = onBack)
        val s = session ?: return@Column

        val allSets = groups.flatMap { it.sets }
        val duration = s.endTime?.let { formatDuration((it - s.startTime) / 1000) }
        val volume = allSets.sumOf { ((it.weight ?: 0f) * (it.reps ?: 0)).toDouble() }.toLong()
        val unit = allSets.firstOrNull { it.weight != null }?.unit ?: "lbs"

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Started ${start?.format(DateTimeFormatter.ofPattern("h:mm a"))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
            item {
                SectionCard(contentPadding = 0.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                        Stat(duration ?: "–", "Duration", Modifier.weight(1f))
                        Stat(allSets.size.toString(), "Sets", Modifier.weight(1f))
                        Stat(if (volume > 0) "${volume.grouped()} $unit" else "–", "Volume", Modifier.weight(1f))
                    }
                }
            }

            if (groups.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.FitnessCenter,
                        title = "No sets in this workout",
                        body = "It was ended before anything was logged."
                    )
                }
            } else {
                item {
                    Text(
                        "Tap a set to fix or delete it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            items(groups, key = { it.sets.first().id }) { group ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            group.exerciseName,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(plural(group.sets.size, "set"), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                    SectionCard(contentPadding = 0.dp) {
                        group.sets.forEachIndexed { index, set ->
                            if (index > 0) HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                            SetRow(set = set, number = index + 1, onEdit = { viewModel.openEditor(set) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.merge(Tabular),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SetRow(set: ExerciseSet, number: Int, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit set", onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(colors.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelMedium.merge(Tabular), color = colors.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    val strong = SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    val soft = SpanStyle(color = colors.onSurfaceVariant)
                    set.weight?.let {
                        withStyle(strong) { append(it.cleanNum()) }
                        withStyle(soft) { append(" ${set.unit}") }
                    }
                    set.reps?.let {
                        if (set.weight != null) withStyle(soft) { append("  ×  ") }
                        withStyle(strong) { append(it.toString()) }
                        withStyle(soft) { append(if (it == 1) " rep" else " reps") }
                    }
                },
                style = MaterialTheme.typography.bodyLarge.merge(Tabular)
            )
            val details = listOfNotNull(
                set.failureProximityScore?.let { "Failure score $it/100" },
                set.notes
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        set.rpe?.let {
            Text(
                "RPE ${it.cleanNum()}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onPrimaryContainer,
                modifier = Modifier
                    .background(colors.primaryContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

package com.redline.app.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.WorkoutSession
import com.redline.app.data.repository.SessionState
import com.redline.app.ui.components.CalendarStrip
import com.redline.app.ui.components.CardShape
import com.redline.app.ui.components.EmptyState
import com.redline.app.ui.components.LiveDot
import com.redline.app.ui.components.ScreenPadding
import com.redline.app.ui.components.SectionCard
import com.redline.app.ui.components.compact
import com.redline.app.ui.components.formatClock
import com.redline.app.ui.components.grouped
import com.redline.app.ui.components.plural
import com.redline.app.ui.components.rememberNow
import com.redline.app.ui.navigation.Routes
import com.redline.app.ui.theme.Tabular
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val needsName by viewModel.needsName.collectAsStateWithLifecycle()
    val recentWorkouts by viewModel.recentWorkouts.collectAsStateWithLifecycle()
    val workoutDays by viewModel.workoutDays.collectAsStateWithLifecycle()
    val weekStats by viewModel.weekStats.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val liveSets by viewModel.liveSets.collectAsStateWithLifecycle()
    val caloriesToday by viewModel.caloriesToday.collectAsStateWithLifecycle()

    if (needsName) {
        NameDialog(onDone = viewModel::setUserName)
    }

    val today = LocalDate.now()
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = if (userName.isBlank()) "Hello" else "Hi, $userName",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = colors.onSurfaceVariant)
                }
            }
        }

        item {
            SectionCard(contentPadding = 0.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "This week",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        plural(weekStats.thisWeek, "workout"),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp)) {
                    CalendarStrip(workoutDays = workoutDays, today = today)
                }
            }
        }

        // Live workout, or a way to start one. Nothing while Room is still
        // answering, so a running workout never flashes "Start workout".
        item {
            Box(modifier = Modifier.animateContentSize()) {
                when (val state = sessionState) {
                    SessionState.Loading -> Spacer(modifier = Modifier.height(56.dp))
                    is SessionState.Active -> LiveWorkoutCard(
                        session = state.session,
                        sets = liveSets,
                        onClick = { navController.navigate(Routes.TRAIN) }
                    )
                    SessionState.None -> Button(
                        onClick = {
                            viewModel.startWorkout()
                            navController.navigate(Routes.TRAIN)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start workout", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        item {
            SectionCard(contentPadding = 0.dp) {
                ShortcutRow(
                    icon = Icons.Default.MusicNote,
                    label = "PR music",
                    detail = "A song for the big lift",
                    onClick = { navController.navigate(Routes.MUSIC) }
                )
                HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(start = 68.dp))
                ShortcutRow(
                    icon = Icons.Default.Restaurant,
                    label = "Calories",
                    detail = if (caloriesToday > 0) "${caloriesToday.toLong().grouped()} cal today" else "Say what you ate",
                    onClick = { navController.navigate(Routes.CALORIES) }
                )
                HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(start = 68.dp))
                ShortcutRow(
                    icon = Icons.Default.PlayCircle,
                    label = "Motivation",
                    detail = "Videos to get you going",
                    onClick = { navController.navigate(Routes.MOTIVATION) }
                )
            }
        }

        if (weekStats.total > 0) {
            item {
                SectionCard(contentPadding = 0.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("Workouts", weekStats.total.toString())
                        StatItem("This week", weekStats.thisWeek.toString())
                        StatItem("Week streak", weekStats.streakWeeks.toString())
                    }
                }
            }
        }

        item {
            Text(
                text = "Recent workouts",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() }
            )
        }

        if (recentWorkouts.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.History,
                    title = "No workouts yet",
                    body = "Finished workouts land here, with their sets and volume."
                )
            }
        }

        items(recentWorkouts, key = { it.session.id }) { summary ->
            WorkoutCard(
                summary = summary,
                onClick = { navController.navigate(Routes.workoutDetail(summary.session.id)) }
            )
        }

        // Room to scroll the last card clear of the nav pill
        item { Spacer(modifier = Modifier.height(112.dp)) }
    }
}

@Composable
private fun LiveWorkoutCard(session: WorkoutSession, sets: List<ExerciseSet>, onClick: () -> Unit) {
    val now = rememberNow()
    val elapsed = ((now - session.startTime) / 1000).coerceAtLeast(0)
    val exercises = sets.map { it.exerciseName }.distinct()
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.primary.copy(alpha = 0.5f), CardShape)
            .clickable(onClickLabel = "Continue workout", onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot()
                Spacer(modifier = Modifier.width(8.dp))
                Text("Workout in progress", style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                formatClock(elapsed),
                style = MaterialTheme.typography.displaySmall.merge(Tabular),
                color = colors.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                buildString {
                    append(plural(sets.size, "set"))
                    if (exercises.isNotEmpty()) append("  ·  ${exercises.joinToString(", ")}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier.size(52.dp).background(colors.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun ShortcutRow(icon: ImageVector, label: String, detail: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(colors.surfaceContainerHigh, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall.merge(Tabular), color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WorkoutCard(summary: WorkoutSummary, onClick: () -> Unit) {
    val session = summary.session
    val start = Instant.ofEpochMilli(session.startTime).atZone(ZoneId.systemDefault())
    val dayName = start.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val time = start.format(DateTimeFormatter.ofPattern("h:mm a"))
    val durationMin = session.endTime?.let { (it - session.startTime) / 60000 }
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.size(52.dp).background(colors.surfaceContainerHigh, RoundedCornerShape(14.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                start.format(DateTimeFormatter.ofPattern("MMM")).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
                lineHeight = 13.sp
            )
            Text(
                start.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                lineHeight = 24.sp
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(dayName, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(
                text = buildString {
                    append(time)
                    durationMin?.let { append("  ·  $it min") }
                    if (summary.volume > 0) append("  ·  ${summary.volume.compact()} ${summary.unit}")
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = summary.exerciseNames.joinToString(" · ").ifEmpty { "No sets logged" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(summary.setCount.toString(), style = MaterialTheme.typography.titleLarge.merge(Tabular), color = colors.onSurface)
            Text(if (summary.setCount == 1) "set" else "sets", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

/** Asked once. A blank answer is fine: the greeting just leaves the name out. */
@Composable
private fun NameDialog(onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onDone(name) },
        title = { Text("Welcome to Redline") },
        text = {
            Column {
                Text("What should we call you? You can change it later in Settings.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(name) }) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = { onDone("") }) { Text("Skip") }
        }
    )
}

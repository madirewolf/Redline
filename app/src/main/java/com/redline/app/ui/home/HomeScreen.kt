package com.redline.app.ui.home

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import com.redline.app.ui.components.LiveDot
import com.redline.app.ui.components.compact
import com.redline.app.ui.components.formatClock
import com.redline.app.ui.components.grouped
import com.redline.app.ui.components.rememberNow
import com.redline.app.ui.navigation.Routes
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.SurfaceContainer
import com.redline.app.ui.theme.SurfaceVariant
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
    val recentWorkouts by viewModel.recentWorkouts.collectAsStateWithLifecycle()
    val workoutDays by viewModel.workoutDays.collectAsStateWithLifecycle()
    val weekStats by viewModel.weekStats.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val liveSets by viewModel.liveSets.collectAsStateWithLifecycle()
    val caloriesToday by viewModel.caloriesToday.collectAsStateWithLifecycle()

    var showNameDialog by remember { mutableStateOf(viewModel.isFirstLaunch) }

    if (showNameDialog) {
        NameDialog(
            onConfirm = { name ->
                viewModel.setUserName(name)
                showNameDialog = false
            }
        )
    }

    val today = LocalDate.now()
    val greeting = userName.ifBlank { "there" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Hey $greeting",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurfaceVariant
                    )
                }
                IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = OnSurfaceVariant
                    )
                }
            }
        }

        // This week
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariant, RoundedCornerShape(20.dp))
                    .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "This week",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        plural(weekStats.thisWeek, "workout"),
                        style = MaterialTheme.typography.labelLarge,
                        color = Red500
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                CalendarStrip(workoutDays = workoutDays, today = today)
            }
        }

        // Live workout, or a way to start one. Nothing while Room is still
        // answering, so a running workout never flashes "Start Workout".
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Red500),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Workout", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Quick access cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickCard(
                    icon = Icons.Default.MusicNote,
                    label = "PR Music",
                    detail = "Song for the big lift",
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Routes.MUSIC) }
                )
                QuickCard(
                    icon = Icons.Default.Speed,
                    label = "Tempo",
                    detail = "Rep speed and fatigue",
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Routes.TEMPO) }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickCard(
                    icon = Icons.Default.Restaurant,
                    label = "Calories",
                    detail = if (caloriesToday > 0) "${caloriesToday.toLong().grouped()} cal today" else "Say what you ate",
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Routes.CALORIES) }
                )
                QuickCard(
                    icon = Icons.Default.PlayCircle,
                    label = "Motivation",
                    detail = "Videos to get going",
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Routes.MOTIVATION) }
                )
            }
        }

        // Stats row
        if (weekStats.total > 0) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariant, RoundedCornerShape(20.dp))
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("Workouts", weekStats.total.toString())
                    StatItem("This week", weekStats.thisWeek.toString())
                    StatItem("Week streak", weekStats.streakWeeks.toString())
                }
            }
        }

        // Recent workouts header
        if (recentWorkouts.isNotEmpty()) {
            item {
                Text(
                    text = "Recent workouts",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // Workout cards
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
    val white = Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFF1744), Color(0xFFA30F2D))))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot(color = white)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "WORKOUT IN PROGRESS",
                    style = MaterialTheme.typography.labelMedium,
                    color = white.copy(alpha = 0.85f),
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                formatClock(elapsed),
                style = MaterialTheme.typography.displayLarge.merge(Tabular),
                fontSize = 40.sp,
                lineHeight = 44.sp,
                color = white
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                buildString {
                    append(plural(sets.size, "set"))
                    if (exercises.isNotEmpty()) append("  ·  ${exercises.joinToString(", ")}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = white.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(white.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Continue workout", tint = white, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun QuickCard(
    icon: ImageVector,
    label: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Red500.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Red500, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            detail,
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
    }
}

@Composable
private fun WorkoutCard(summary: WorkoutSummary, onClick: () -> Unit) {
    val session = summary.session
    val start = Instant.ofEpochMilli(session.startTime).atZone(ZoneId.systemDefault())
    val dayName = start.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val time = start.format(DateTimeFormatter.ofPattern("h:mm a"))
    val durationMin = session.endTime?.let { (it - session.startTime) / 60000 }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date tile
        Column(
            modifier = Modifier
                .size(52.dp)
                .background(SurfaceContainer, RoundedCornerShape(14.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                start.format(DateTimeFormatter.ofPattern("MMM")).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Red500,
                fontSize = 11.sp,
                lineHeight = 13.sp
            )
            Text(
                start.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = buildString {
                    append(time)
                    durationMin?.let { append("  ·  $it min") }
                    if (summary.volume > 0) append("  ·  ${summary.volume.compact()} ${summary.unit}")
                },
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = summary.exerciseNames.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                summary.setCount.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = Red500
            )
            Text("sets", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OnSurfaceVariant.copy(alpha = 0.5f))
    }
}

private fun plural(count: Int, noun: String) = "$count ${if (count == 1) noun else noun + "s"}"

@Composable
private fun NameDialog(onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onConfirm(name.ifBlank { "Athlete" }) },
        title = { Text("Welcome to Redline") },
        text = {
            Column {
                Text("What should we call you?", color = OnSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.ifBlank { "Athlete" }) }) {
                Text("Let's go", color = Red500)
            }
        },
        containerColor = SurfaceVariant
    )
}

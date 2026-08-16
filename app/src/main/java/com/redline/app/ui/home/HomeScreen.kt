package com.redline.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restaurant
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.redline.app.ui.components.CalendarStrip
import com.redline.app.ui.navigation.Routes
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Outline
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.SurfaceVariant
import java.time.LocalDate
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
    val sessionCount by viewModel.sessionCount.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()

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
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

        // Calendar
        item {
            CalendarStrip(
                workoutDays = workoutDays,
                onDayClick = { /* future: filter workouts by day */ }
            )
        }

        // Active session or Start button
        item {
            if (activeSession != null) {
                Button(
                    onClick = { navController.navigate(Routes.TRAIN) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red500),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Continue Workout", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
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
                    Text("Start Workout", fontWeight = FontWeight.SemiBold)
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
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Routes.MUSIC) }
                )
                QuickCard(
                    icon = Icons.Default.Speed,
                    label = "Tempo",
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
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Routes.CALORIES) }
                )
                QuickCard(
                    icon = Icons.Default.PlayCircle,
                    label = "Motivation",
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Routes.MOTIVATION) }
                )
            }
        }

        // Stats row
        if (sessionCount > 0) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("Workouts", sessionCount.toString())
                    StatItem("This week", workoutDays.count {
                        it.isAfter(today.minusDays(7))
                    }.toString())
                }
            }
        }

        // Recent workouts header
        if (recentWorkouts.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Workouts",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
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

        // Bottom spacer for nav pill
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun QuickCard(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Red500, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
    val date = java.time.Instant.ofEpochMilli(session.startTime)
        .atZone(java.time.ZoneId.systemDefault())
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val formatted = date.format(DateTimeFormatter.ofPattern("MMM d"))
    val durationMin = session.endTime?.let { (it - session.startTime) / 60000 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$dayName, $formatted",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            durationMin?.let {
                Text(
                    text = "${it}min",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = summary.exerciseNames.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${summary.setCount} sets",
            style = MaterialTheme.typography.labelMedium,
            color = Red500
        )
    }
}

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

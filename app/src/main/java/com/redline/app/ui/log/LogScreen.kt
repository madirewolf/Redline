package com.redline.app.ui.log

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.ui.navigation.Routes
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Outline
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.SurfaceVariant
import com.redline.app.voice.SpeechState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen(
    navController: NavController,
    viewModel: LogViewModel = hiltViewModel()
) {
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val sets by viewModel.currentSets.collectAsStateWithLifecycle()
    val speechState by viewModel.speechState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Navigate to detail after ending workout
    LaunchedEffect(uiState.endedSessionId) {
        uiState.endedSessionId?.let { id ->
            viewModel.clearEndedSession()
            navController.navigate(Routes.workoutDetail(id)) {
                popUpTo(Routes.HOME)
            }
        }
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    if (activeSession == null) {
        IdleView(onStartWorkout = { viewModel.startWorkout() })
    } else {
        ActiveWorkoutView(
            sets = sets,
            elapsed = elapsed,
            speechState = speechState,
            uiState = uiState,
            hasAudioPermission = hasAudioPermission,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onMicTap = { viewModel.startListening() },
            onStopListening = { viewModel.stopListening() },
            onConfirmSet = { viewModel.confirmSet() },
            onDiscardSet = { viewModel.discardSet() },
            onEndWorkout = { viewModel.endWorkout() },
            onSkipRest = { viewModel.skipRestTimer() },
            onToggleLockIn = { viewModel.toggleLockIn() },
            hasDndPermission = viewModel.dndManager.hasPermission,
            onRequestDndPermission = { viewModel.dndManager.openPermissionSettings() }
        )
    }
}

@Composable
private fun IdleView(onStartWorkout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.FitnessCenter, null, modifier = Modifier.size(64.dp), tint = OnSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Ready to train?", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Start a workout and log sets with your voice.", style = MaterialTheme.typography.bodyLarge, color = OnSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onStartWorkout,
            colors = ButtonDefaults.buttonColors(containerColor = Red500),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Start Workout", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun ActiveWorkoutView(
    sets: List<ExerciseSet>,
    elapsed: Long,
    speechState: SpeechState,
    uiState: LogUiState,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onMicTap: () -> Unit,
    onStopListening: () -> Unit,
    onConfirmSet: () -> Unit,
    onDiscardSet: () -> Unit,
    onEndWorkout: () -> Unit,
    onSkipRest: () -> Unit,
    onToggleLockIn: () -> Unit,
    hasDndPermission: Boolean,
    onRequestDndPermission: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(sets.size) {
        if (sets.isNotEmpty()) listState.animateScrollToItem(sets.size - 1)
    }

    // Group sets by exercise for display
    val grouped = sets.groupBy { it.canonicalExerciseKey ?: it.exerciseName }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // Header with timer + lock-in
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Workout", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(formatElapsed(elapsed), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = {
                    if (hasDndPermission) onToggleLockIn() else onRequestDndPermission()
                }) {
                    Icon(
                        if (uiState.isLockedIn) Icons.Default.DoNotDisturb else Icons.Default.NotificationsActive,
                        contentDescription = "Lock In",
                        tint = if (uiState.isLockedIn) Red500 else OnSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onEndWorkout, shape = RoundedCornerShape(12.dp)) {
                    Text("End")
                }
            }
        }

        Text("${sets.size} sets logged", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

        // Timeline grouped by exercise
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            grouped.forEach { (_, exerciseSets) ->
                item(key = "h_${exerciseSets.first().id}") {
                    Text(
                        exerciseSets.first().exerciseName,
                        style = MaterialTheme.typography.labelLarge,
                        color = Red500,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                items(exerciseSets, key = { it.id }) { set -> SetCard(set) }
            }
        }

        // Confirmation card
        AnimatedVisibility(
            visible = uiState.showConfirmation && uiState.lastParsedSet != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            uiState.lastParsedSet?.let { parsed ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        .background(SurfaceVariant, RoundedCornerShape(16.dp))
                        .border(1.dp, Outline, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(parsed.exerciseName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        parsed.weight?.let {
                            Text("${it.cleanNum()} ${parsed.unit}", style = MaterialTheme.typography.bodyLarge, color = Red500, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        parsed.reps?.let { Text("$it reps", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }
                        parsed.rpe?.let {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("RPE ${it.cleanNum()}", style = MaterialTheme.typography.bodyLarge, color = OnSurfaceVariant)
                        }
                    }
                    parsed.notes?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                    if (parsed.needsClarification) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Low confidence — check before saving", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onConfirmSet, colors = ButtonDefaults.buttonColors(containerColor = Red500), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save")
                        }
                        OutlinedButton(onClick = onDiscardSet, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Discard")
                        }
                    }
                }
            }
        }

        // Rest Timer
        AnimatedVisibility(visible = uiState.isResting) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    .background(SurfaceVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Rest", style = MaterialTheme.typography.labelLarge, color = OnSurfaceVariant)
                Text(
                    formatElapsed(uiState.restTimerRemaining.toLong()),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (uiState.restTimerRemaining <= 5) Red500 else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onSkipRest, shape = RoundedCornerShape(10.dp)) {
                    Text("Skip")
                }
            }
        }

        // Status
        when {
            uiState.isParsing -> StatusText("Parsing set...", Red500)
            uiState.parseError != null -> StatusText(uiState.parseError, MaterialTheme.colorScheme.error)
            speechState is SpeechState.Listening -> StatusText("Listening...", Red500)
            speechState is SpeechState.Error -> StatusText((speechState as SpeechState.Error).message, MaterialTheme.colorScheme.error)
            else -> Spacer(modifier = Modifier.height(20.dp))
        }

        // Format hint
        Text(
            text = "Say: exercise → next → weight → next → reps",
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )

        // Mic button
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
            val isListening = speechState is SpeechState.Listening
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape)
                    .background(if (isListening) Red500 else SurfaceVariant)
                    .border(2.dp, if (isListening) Red500 else Outline, CircleShape)
                    .clickable {
                        if (!hasAudioPermission) onRequestPermission()
                        else if (isListening) onStopListening()
                        else onMicTap()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop" else "Record",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun StatusText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}

@Composable
private fun SetCard(set: ExerciseSet) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth().background(SurfaceVariant, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row {
                set.weight?.let { Text("${it.cleanNum()} ${set.unit}", style = MaterialTheme.typography.bodyMedium, color = Red500, fontWeight = FontWeight.Bold) }
                set.reps?.let {
                    if (set.weight != null) Text(" x ", color = OnSurfaceVariant)
                    Text("$it reps", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                set.rpe?.let { Text("  RPE ${it.cleanNum()}", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant) }
            }
        }
        Text(timeFormat.format(Date(set.timestamp)), style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
    }
}

private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "${m}:${s.toString().padStart(2, '0')}"
}

private fun Float.cleanNum(): String = if (this == toLong().toFloat()) toLong().toString() else toString()

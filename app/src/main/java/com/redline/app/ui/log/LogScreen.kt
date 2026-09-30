package com.redline.app.ui.log

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.repository.SessionState
import com.redline.app.ui.components.LiveDot
import com.redline.app.ui.components.cleanNum
import com.redline.app.ui.components.formatClock
import com.redline.app.ui.components.grouped
import com.redline.app.ui.components.rememberNow
import com.redline.app.ui.navigation.Routes
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Outline
import com.redline.app.ui.theme.Red400
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.SurfaceContainer
import com.redline.app.ui.theme.SurfaceVariant
import com.redline.app.ui.theme.Tabular
import com.redline.app.voice.SpeechState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen(
    navController: NavController,
    viewModel: LogViewModel = hiltViewModel()
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val sets by viewModel.currentSets.collectAsStateWithLifecycle()
    val speechState by viewModel.speechState.collectAsStateWithLifecycle()
    val micLevel by viewModel.micLevel.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    when (val state = sessionState) {
        // Wait for Room rather than flash "Ready to train?" over a running workout.
        SessionState.Loading -> Box(Modifier.fillMaxSize())
        SessionState.None -> IdleView(onStartWorkout = { viewModel.startWorkout() })
        is SessionState.Active -> ActiveWorkoutView(
            startTime = state.session.startTime,
            sets = sets,
            speechState = speechState,
            micLevel = micLevel,
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
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(96.dp).background(Red500.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.FitnessCenter, null, modifier = Modifier.size(44.dp), tint = Red500)
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text("Ready to train?", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Start a workout and log every set by voice.", style = MaterialTheme.typography.bodyLarge, color = OnSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onStartWorkout,
            colors = ButtonDefaults.buttonColors(containerColor = Red500),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Start Workout", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/** One row of the workout timeline: an exercise heading or one of its sets. */
private sealed interface TimelineRow {
    val key: String

    data class Heading(val name: String, val setCount: Int, override val key: String) : TimelineRow
    data class SetRow(val set: ExerciseSet, val number: Int) : TimelineRow {
        override val key = "s_${set.id}"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActiveWorkoutView(
    startTime: Long,
    sets: List<ExerciseSet>,
    speechState: SpeechState,
    micLevel: Float,
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
    val now = rememberNow()
    val elapsed = ((now - startTime) / 1000).coerceAtLeast(0)
    var confirmEnd by remember { mutableStateOf(false) }

    // Group sets by exercise, in the order each exercise was first done.
    val rows = remember(sets) {
        sets.groupBy { it.canonicalExerciseKey ?: it.exerciseName }
            .flatMap { (key, exerciseSets) ->
                listOf<TimelineRow>(TimelineRow.Heading(exerciseSets.first().exerciseName, exerciseSets.size, "h_$key")) +
                    exerciseSets.mapIndexed { i, set -> TimelineRow.SetRow(set, i + 1) }
            }
    }
    val volume = sets.sumOf { ((it.weight ?: 0f) * (it.reps ?: 0)).toDouble() }.toLong()
    val unit = sets.groupingBy { it.unit }.eachCount().maxByOrNull { it.value }?.key ?: "lbs"

    // Keep the newest set in view. It isn't always the last row: a set of an
    // earlier exercise lands inside that exercise's group.
    val listState = rememberLazyListState()
    var scrolledOnce by remember { mutableStateOf(false) }
    LaunchedEffect(sets.size) {
        val newest = sets.maxByOrNull { it.timestamp } ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is TimelineRow.SetRow && it.set.id == newest.id }
        if (index < 0) return@LaunchedEffect
        if (scrolledOnce) listState.animateScrollToItem(index) else listState.scrollToItem(index)
        scrolledOnce = true
    }
    val openedAt = remember { System.currentTimeMillis() }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End workout?") },
            text = {
                Text(
                    "${sets.size} ${if (sets.size == 1) "set" else "sets"} in ${elapsed / 60} min. It'll be saved to your history.",
                    color = OnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; onEndWorkout() }) { Text("End workout", color = Red500) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("Keep going", color = OnSurfaceVariant) }
            },
            containerColor = SurfaceVariant
        )
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp)) {
        // Header: live timer, running totals, lock-in and end
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Workout", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        formatClock(elapsed),
                        style = MaterialTheme.typography.bodyMedium.merge(Tabular),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        buildString {
                            append("  ·  ${sets.size} ${if (sets.size == 1) "set" else "sets"}")
                            if (volume > 0) append("  ·  ${volume.grouped()} $unit")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (hasDndPermission) onToggleLockIn() else onRequestDndPermission()
                }) {
                    Icon(
                        if (uiState.isLockedIn) Icons.Default.DoNotDisturb else Icons.Default.NotificationsActive,
                        contentDescription = "Lock In",
                        tint = if (uiState.isLockedIn) Red500 else OnSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { confirmEnd = true }, shape = RoundedCornerShape(12.dp)) {
                    Text("End")
                }
            }
        }

        // Timeline grouped by exercise
        Box(modifier = Modifier.weight(1f)) {
            if (rows.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No sets yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tap the mic and say your first set.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is TimelineRow.Heading -> Row(
                            modifier = Modifier.fillMaxWidth().animateItemPlacement().padding(top = 10.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                row.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = Red500,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${row.setCount} ${if (row.setCount == 1) "set" else "sets"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = OnSurfaceVariant
                            )
                        }
                        is TimelineRow.SetRow -> SetCard(
                            set = row.set,
                            number = row.number,
                            isNew = row.set.timestamp > maxOf(openedAt, System.currentTimeMillis() - 4000),
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
        }

        // Parsed set waiting for confirmation
        AnimatedVisibility(
            visible = uiState.showConfirmation && uiState.lastParsedSet != null,
            enter = fadeIn(tween(220)) + expandVertically(tween(260)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(220))
        ) {
            uiState.lastParsedSet?.let { parsed ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        .background(SurfaceVariant, RoundedCornerShape(20.dp))
                        .border(1.dp, Red500.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Text(
                        "“${parsed.rawInput}”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(parsed.exerciseName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        parsed.weight?.let { BigValue(it.cleanNum(), parsed.unit, highlight = true) }
                        parsed.reps?.let { BigValue(it.toString(), if (it == 1) "rep" else "reps") }
                        parsed.rpe?.let { BigValue(it.cleanNum(), "RPE") }
                    }
                    parsed.notes?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                    if (parsed.needsClarification) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Low confidence. Check before saving.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onConfirmSet,
                            colors = ButtonDefaults.buttonColors(containerColor = Red500),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save set")
                        }
                        OutlinedButton(
                            onClick = onDiscardSet,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(18.dp), tint = OnSurfaceVariant); Spacer(Modifier.width(6.dp))
                            Text("Discard", color = OnSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Rest timer
        AnimatedVisibility(
            visible = uiState.isResting,
            enter = fadeIn(tween(220)) + expandVertically(tween(260)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(220))
        ) {
            RestCard(
                remaining = uiState.restTimerRemaining,
                total = uiState.restTimerTotal,
                onSkip = onSkipRest
            )
        }

        // What the mic heard, or what to say
        val isListening = speechState is SpeechState.Listening
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                speechState is SpeechState.Listening && speechState.partial.isNotBlank() ->
                    Prompt("“${speechState.partial}”", MaterialTheme.colorScheme.onSurface, large = true)
                isListening -> Prompt("Listening…", Red400)
                uiState.isParsing -> Prompt("Reading your set…", Red400)
                uiState.showConfirmation -> Prompt("Look right? Save it, or tap to say it again", OnSurfaceVariant.copy(alpha = 0.7f))
                uiState.parseError != null -> Prompt(uiState.parseError, MaterialTheme.colorScheme.error)
                speechState is SpeechState.Error -> Prompt(speechState.message, MaterialTheme.colorScheme.error)
                else -> Prompt("Tap and say “bench 185 for 8, RPE 8”", OnSurfaceVariant.copy(alpha = 0.7f))
            }
        }

        // Mic
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
            MicButton(
                isListening = isListening,
                level = micLevel,
                onClick = {
                    if (!hasAudioPermission) onRequestPermission()
                    else if (isListening) onStopListening()
                    else onMicTap()
                }
            )
        }
    }
}

@Composable
private fun Prompt(text: String, color: Color, large: Boolean = false) {
    Text(
        text,
        style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun BigValue(value: String, label: String, highlight: Boolean = false) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) Red500 else MaterialTheme.colorScheme.onSurface
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
    }
}

@Composable
private fun MicButton(isListening: Boolean, level: Float, onClick: () -> Unit) {
    val rings = rememberInfiniteTransition(label = "rings")
    val phase by rings.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "ringPhase"
    )
    val loudness by animateFloatAsState(if (isListening) level else 0f, tween(120), label = "loudness")
    val ringsAlpha by animateFloatAsState(if (isListening) 1f else 0f, tween(250), label = "ringsAlpha")

    Box(modifier = Modifier.size(124.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val base = 38.dp.toPx()
            if (ringsAlpha > 0f) {
                // Two rings a half-cycle apart, pushed out further when the voice is louder.
                for (offset in listOf(0f, 0.5f)) {
                    val p = (phase + offset) % 1f
                    drawCircle(
                        color = Red500.copy(alpha = (1f - p) * 0.35f * ringsAlpha),
                        radius = base + p * 24.dp.toPx() + loudness * 8.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                drawCircle(
                    color = Red500.copy(alpha = 0.18f * ringsAlpha),
                    radius = base + loudness * 14.dp.toPx()
                )
            } else {
                drawCircle(color = Red500.copy(alpha = 0.10f), radius = base + 8.dp.toPx())
            }
        }
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Red500)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop" else "Record",
                modifier = Modifier.size(34.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun RestCard(remaining: Int, total: Int, onSkip: () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (total > 0) remaining / total.toFloat() else 0f,
        animationSpec = tween(1000, easing = LinearEasing),
        label = "rest"
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .background(SurfaceVariant, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(Outline, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                drawArc(
                    if (remaining <= 5) Red400 else Red500,
                    -90f, 360f * progress, false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Text(
                formatClock(remaining.toLong()),
                style = MaterialTheme.typography.titleMedium.merge(Tabular),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Rest", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("${formatClock(total.toLong())} between sets", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        }
        TextButton(onClick = onSkip) { Text("Skip", color = Red500) }
    }
}

@Composable
private fun SetCard(set: ExerciseSet, number: Int, isNew: Boolean, modifier: Modifier = Modifier) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    // A just-saved set slides in with a red glow that fades out.
    val glow = remember { Animatable(if (isNew) 1f else 0f) }
    val appear = remember { Animatable(if (isNew) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (isNew) {
            appear.animateTo(1f, tween(280))
            glow.animateTo(0f, tween(2400))
        }
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 16.dp.toPx()
            }
            .background(lerp(SurfaceVariant, Color(0xFF3A0E16), glow.value), RoundedCornerShape(14.dp))
            .border(1.dp, Red500.copy(alpha = 0.6f * glow.value), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(26.dp).background(SurfaceContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
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
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        set.rpe?.let {
            Text(
                "RPE ${it.cleanNum()}",
                style = MaterialTheme.typography.labelMedium,
                color = Red400,
                modifier = Modifier
                    .background(Red500.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(timeFormat.format(Date(set.timestamp)), style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
    }
}

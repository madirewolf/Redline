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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
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
import com.redline.app.ui.components.RedlineTopBar
import com.redline.app.ui.components.ScreenPadding
import com.redline.app.ui.components.SetEditorDialog
import com.redline.app.ui.components.toDraft
import com.redline.app.ui.components.plural
import com.redline.app.ui.components.cleanNum
import com.redline.app.ui.components.formatClock
import com.redline.app.ui.components.formatDuration
import com.redline.app.ui.components.grouped
import com.redline.app.ui.components.rememberNow
import com.redline.app.ui.navigation.Routes
import com.redline.app.ui.theme.RedlineTheme
import com.redline.app.ui.theme.Tabular
import com.redline.app.voice.ExerciseCatalog
import com.redline.app.voice.ParseSource
import com.redline.app.voice.ParsedSet
import com.redline.app.voice.SetDraft
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

    uiState.editor?.let { target ->
        when (target) {
            SetEditorTarget.Pending -> SetEditorDialog(
                title = "Edit set",
                initial = uiState.lastParsedSet?.let(SetDraft::from) ?: SetDraft(unit = viewModel.defaultUnit),
                confirmLabel = "Save set",
                onSave = viewModel::saveFromEditor,
                onDismiss = viewModel::closeEditor
            )
            SetEditorTarget.New -> SetEditorDialog(
                title = "Add a set",
                initial = SetDraft(unit = viewModel.defaultUnit),
                confirmLabel = "Save set",
                onSave = viewModel::saveFromEditor,
                onDismiss = viewModel::closeEditor
            )
            is SetEditorTarget.Saved -> SetEditorDialog(
                title = "Edit set",
                initial = target.set.toDraft(),
                onSave = viewModel::saveFromEditor,
                onDismiss = viewModel::closeEditor,
                onDelete = { viewModel.deleteSet(target.set) }
            )
        }
    }

    when (val state = sessionState) {
        // Wait for Room rather than flash "Ready to train?" over a running workout.
        SessionState.Loading -> Box(Modifier.fillMaxSize())
        SessionState.None -> IdleView(
            onStartWorkout = { viewModel.startWorkout() },
            onBack = { navController.popBackStack() }
        )
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
            onEditPending = { viewModel.openEditor(SetEditorTarget.Pending) },
            onTypeSet = { viewModel.openEditor(SetEditorTarget.New) },
            onEditSaved = { viewModel.openEditor(SetEditorTarget.Saved(it)) },
            onEndWorkout = { viewModel.endWorkout() },
            onSkipRest = { viewModel.skipRestTimer() },
            onToggleLockIn = { viewModel.toggleLockIn() },
            hasDndPermission = viewModel.dndManager.hasPermission,
            onRequestDndPermission = { viewModel.dndManager.openPermissionSettings() }
        )
    }
}

@Composable
private fun IdleView(onStartWorkout: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        RedlineTopBar(title = "", onBack = onBack)
        IdleContent(onStartWorkout, Modifier.weight(1f))
    }
}

@Composable
private fun IdleContent(onStartWorkout: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = ScreenPadding, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(88.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.FitnessCenter, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Ready to train?", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Start a workout, then log each set by saying it.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onStartWorkout,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Start workout", style = MaterialTheme.typography.titleMedium) }
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
    onEditPending: () -> Unit,
    onTypeSet: () -> Unit,
    onEditSaved: (ExerciseSet) -> Unit,
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
        sets.groupBy { ExerciseCatalog.groupingKey(it.canonicalExerciseKey, it.exerciseName) }
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
                Text("${plural(sets.size, "set")} in ${formatDuration(elapsed)}. It'll be saved to your history.")
            },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; onEndWorkout() }) { Text("End workout") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("Keep going") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = ScreenPadding)) {
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
                            append("  ·  ${plural(sets.size, "set")}")
                            if (volume > 0) append("  ·  ${volume.grouped()} $unit")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (hasDndPermission) onToggleLockIn() else onRequestDndPermission()
                }) {
                    Icon(
                        if (uiState.isLockedIn) Icons.Default.DoNotDisturb else Icons.Default.NotificationsActive,
                        contentDescription = if (uiState.isLockedIn) "Lock-in on: notifications silenced" else "Lock in: silence notifications",
                        tint = if (uiState.isLockedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text(
                        "Tap the mic and say your first set,\nor type it with the keyboard button.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
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
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                plural(row.setCount, "set"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is TimelineRow.SetRow -> SetCard(
                            set = row.set,
                            number = row.number,
                            isNew = row.set.timestamp > maxOf(openedAt, System.currentTimeMillis() - 4000),
                            onClick = { onEditSaved(row.set) },
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
                ConfirmationCard(
                    parsed = parsed,
                    onSave = onConfirmSet,
                    onEdit = onEditPending,
                    onDiscard = onDiscardSet
                )
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
                isListening -> Prompt("Listening…", MaterialTheme.colorScheme.primary)
                uiState.isParsing -> Prompt("Reading your set…", MaterialTheme.colorScheme.primary)
                uiState.showConfirmation -> Prompt("Look right? Save it, or tap the mic to say it again", MaterialTheme.colorScheme.onSurfaceVariant)
                uiState.parseError != null -> Prompt(uiState.parseError, MaterialTheme.colorScheme.error)
                speechState is SpeechState.Error -> Prompt(speechState.message, MaterialTheme.colorScheme.error)
                else -> Prompt("Tap and say “bench 185 for 8, RPE 8”", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Mic
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                FilledTonalIconButton(onClick = onTypeSet, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Type a set")
                }
            }
            MicButton(
                isListening = isListening,
                level = micLevel,
                onClick = {
                    if (!hasAudioPermission) onRequestPermission()
                    else if (isListening) onStopListening()
                    else onMicTap()
                }
            )
            Spacer(modifier = Modifier.weight(1f))
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
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    val red = MaterialTheme.colorScheme.primary
    val onRed = MaterialTheme.colorScheme.onPrimary
    Box(modifier = Modifier.size(124.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val base = 38.dp.toPx()
            if (ringsAlpha > 0f) {
                // Two rings a half-cycle apart, pushed out further when the voice is louder.
                for (offset in listOf(0f, 0.5f)) {
                    val p = (phase + offset) % 1f
                    drawCircle(
                        color = red.copy(alpha = (1f - p) * 0.35f * ringsAlpha),
                        radius = base + p * 24.dp.toPx() + loudness * 8.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                drawCircle(
                    color = red.copy(alpha = 0.18f * ringsAlpha),
                    radius = base + loudness * 14.dp.toPx()
                )
            } else {
                drawCircle(color = red.copy(alpha = 0.10f), radius = base + 8.dp.toPx())
            }
        }
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(red)
                .clickable(onClick = onClick, onClickLabel = if (isListening) "Stop listening" else "Say a set"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop listening" else "Say a set",
                modifier = Modifier.size(34.dp),
                tint = onRed
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
    val track = MaterialTheme.colorScheme.outlineVariant
    val red = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                drawArc(
                    red,
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
            Text("${formatClock(total.toLong())} between sets", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onSkip) { Text("Skip") }
    }
}

@Composable
private fun SetCard(set: ExerciseSet, number: Int, isNew: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
    val colors = MaterialTheme.colorScheme
    val onSurface = colors.onSurface
    val muted = colors.onSurfaceVariant
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 16.dp.toPx()
            }
            .clip(shape)
            .background(lerp(colors.surfaceContainer, colors.primaryContainer, glow.value))
            .border(1.dp, colors.primary.copy(alpha = 0.6f * glow.value), shape)
            .clickable(onClickLabel = "Edit set", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(26.dp).background(colors.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = muted)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    set.weight?.let {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = onSurface)) { append(it.cleanNum()) }
                        withStyle(SpanStyle(color = muted, fontSize = 13.sp)) { append(" ${set.unit}") }
                    }
                    set.reps?.let {
                        if (set.weight != null) withStyle(SpanStyle(color = muted)) { append("  ×  ") }
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = onSurface)) { append(it.toString()) }
                        withStyle(SpanStyle(color = muted, fontSize = 13.sp)) { append(if (it == 1) " rep" else " reps") }
                    }
                },
                style = MaterialTheme.typography.bodyLarge
            )
            // Notes and a saved tempo score, so the row shows everything the set holds.
            val detail = listOfNotNull(
                set.notes?.takeIf { it.isNotBlank() },
                set.failureProximityScore?.let { "Failure score $it" }
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        set.rpe?.let {
            RpeChip(it)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(timeFormat.format(Date(set.timestamp)), style = MaterialTheme.typography.labelMedium, color = muted)
    }
}

@Composable
private fun RpeChip(rpe: Float) {
    Text(
        "RPE ${rpe.cleanNum()}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** The parsed set, waiting for a yes. Incomplete sets can't be saved as they are. */
@Composable
private fun ConfirmationCard(
    parsed: ParsedSet,
    onSave: () -> Unit,
    onEdit: () -> Unit,
    onDiscard: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .background(colors.surfaceContainer, shape)
            .border(1.dp, colors.outlineVariant, shape)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                sourceLabel(parsed.source),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDiscard) {
                Icon(Icons.Default.Close, contentDescription = "Discard set", tint = colors.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.padding(end = 12.dp)) {
            if (parsed.rawInput.isNotBlank()) {
                Text(
                    "“${parsed.rawInput}”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                if (parsed.hasKnownExercise) parsed.exerciseName else "Exercise?",
                style = MaterialTheme.typography.titleLarge,
                color = if (parsed.hasKnownExercise) colors.onSurface else colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                parsed.weight?.let { BigValue(it.cleanNum(), parsed.unit, highlight = true) }
                BigValue(parsed.reps?.toString() ?: "?", if (parsed.reps == 1) "rep" else "reps")
                parsed.rpe?.let { BigValue(it.cleanNum(), "RPE") }
            }
            parsed.notes?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            val missing = listOfNotNull(
                "the exercise".takeIf { !parsed.hasKnownExercise },
                "the reps".takeIf { parsed.reps == null || parsed.reps <= 0 }
            )
            if (missing.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Didn't catch ${missing.joinToString(" or ")}. Fill it in, or say the set again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error
                )
            } else if (parsed.confidence < 0.6f) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Not sure about this one. Check it before saving.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RedlineTheme.status.warning
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (parsed.isComplete) {
                    Button(
                        onClick = onSave,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save set")
                    }
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Edit")
                    }
                } else {
                    Button(
                        onClick = onEdit,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Fill in the set")
                    }
                }
            }
        }
    }
}

private fun sourceLabel(source: ParseSource) = when (source) {
    ParseSource.SEPARATED -> "Read with the “next” format"
    ParseSource.REMOTE -> "Read by your parser endpoint"
    ParseSource.LOCAL -> "Read on this phone"
    ParseSource.MANUAL -> "Typed"
}

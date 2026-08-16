package com.redline.app.ui.tempo

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Outline
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.SurfaceVariant

@Composable
fun TempoScreen(viewModel: TempoViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Tempo Tracker", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Track your rep tempo with voice commands to measure fatigue and failure proximity.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )
        }

        // Instructions
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text("How to use", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                InstructionStep("1", "Tap Start, then begin your set")
                InstructionStep("2", "Say \"eccentric\" or \"down\" at the start of the lowering phase")
                InstructionStep("3", "Say \"concentric\" or \"up\" at the start of the lifting phase")
                InstructionStep("4", "Repeat for each rep, then say \"done\" or tap Stop")
            }
        }

        // State-based content
        when (val s = state) {
            is VoiceTempoState.Idle -> {
                item {
                    Button(
                        onClick = {
                            if (!hasPermission) launcher.launch(Manifest.permission.RECORD_AUDIO)
                            else viewModel.start()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Red500),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Start Tracking", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }

            is VoiceTempoState.Tracking -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceVariant, RoundedCornerShape(12.dp))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = s.currentPhase.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.headlineLarge,
                            color = if (s.currentPhase == "eccentric") Color(0xFF64B5F6) else Red500
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${s.repCount} reps detected",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Listening for commands...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { viewModel.stop() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Stop & Analyze")
                    }
                }
            }

            is VoiceTempoState.Done -> {
                // Summary card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceVariant, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Failure Score", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                                Text(
                                    "${s.result.failureProximityScore}/100",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = scoreColor(s.result.failureProximityScore)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Degradation", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                                Text(
                                    "+${s.result.degradationPercent.toInt()}%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(s.result.note, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                }

                // Rep bars
                item {
                    Text("Rep Timing", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                items(s.result.reps) { rep ->
                    RepTimingBar(rep = rep, maxMs = s.result.reps.maxOf { it.totalMs })
                }

                // Actions
                item {
                    Button(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Red500),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("New Set")
                    }
                }
            }

            is VoiceTempoState.Error -> {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(s.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.reset() },
                            colors = ButtonDefaults.buttonColors(containerColor = Red500),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Try Again")
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun InstructionStep(number: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(number, style = MaterialTheme.typography.labelLarge, color = Red500, modifier = Modifier.width(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
    }
}

@Composable
private fun RepTimingBar(rep: RepTiming, maxMs: Long) {
    val eccentricFraction = if (maxMs > 0) rep.eccentricMs.toFloat() / maxMs else 0f
    val concentricFraction = if (maxMs > 0) rep.concentricMs.toFloat() / maxMs else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "R${rep.repNumber}",
            style = MaterialTheme.typography.labelLarge,
            color = OnSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            // Eccentric bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(eccentricFraction.coerceIn(0.05f, 1f))
                    .height(12.dp)
                    .background(Color(0xFF64B5F6), RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(3.dp))
            // Concentric bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(concentricFraction.coerceIn(0.05f, 1f))
                    .height(12.dp)
                    .background(Red500, RoundedCornerShape(4.dp))
            )
        }
        Column(
            modifier = Modifier.width(56.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text("${rep.totalMs / 1000f}s", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun scoreColor(score: Int): Color {
    return when {
        score >= 85 -> Color(0xFF4CAF50)
        score >= 65 -> Color(0xFFFFB74D)
        else -> Red500
    }
}

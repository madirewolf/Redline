package com.redline.app.ui.calories

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redline.app.data.local.entity.CalorieEntry
import com.redline.app.nutrition.FoodResult
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Outline
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.SurfaceVariant
import com.redline.app.voice.SpeechState

@Composable
fun CaloriesScreen(viewModel: CaloriesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val todayEntries by viewModel.todayEntries.collectAsStateWithLifecycle()
    val speechState by viewModel.speechState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    val totalCal = todayEntries.sumOf { it.calories.toDouble() }.toInt()
    val totalPro = todayEntries.sumOf { (it.protein ?: 0f).toDouble() }.toInt()
    val totalCarb = todayEntries.sumOf { (it.carbs ?: 0f).toDouble() }.toInt()
    val totalFat = todayEntries.sumOf { (it.fat ?: 0f).toDouble() }.toInt()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Calories", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("Voice-log your meals. Say what you ate.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        }

        // Daily totals
        item {
            Row(
                modifier = Modifier.fillMaxWidth().background(SurfaceVariant, RoundedCornerShape(12.dp)).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MacroStat("Cal", totalCal.toString(), Red500)
                MacroStat("Protein", "${totalPro}g", MaterialTheme.colorScheme.onSurface)
                MacroStat("Carbs", "${totalCarb}g", MaterialTheme.colorScheme.onSurface)
                MacroStat("Fat", "${totalFat}g", MaterialTheme.colorScheme.onSurface)
            }
        }

        // Mic button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isListening = speechState is SpeechState.Listening
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            if (!hasPermission) launcher.launch(Manifest.permission.RECORD_AUDIO)
                            else viewModel.startListening()
                        },
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(if (isListening) Red500 else SurfaceVariant)
                    ) {
                        Icon(Icons.Default.Mic, "Record", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (isListening) "Listening..." else "Tap to log food",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isListening) Red500 else OnSurfaceVariant
                    )
                }
            }
        }

        // Search status
        if (uiState.isSearching) {
            item { Text("Looking up \"${uiState.lastQuery}\"...", style = MaterialTheme.typography.bodyMedium, color = Red500, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
        if (uiState.searchError != null) {
            item { Text(uiState.searchError!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }

        // Search results
        if (uiState.searchResults.isNotEmpty()) {
            item { Text("Results", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) }
            items(uiState.searchResults) { result ->
                FoodResultCard(result) { viewModel.logFood(result) }
            }
        }

        // Today's entries
        if (todayEntries.isNotEmpty()) {
            item { Text("Today", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp)) }
            items(todayEntries, key = { it.id }) { entry ->
                EntryCard(entry) { viewModel.deleteEntry(entry) }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun MacroStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
    }
}

@Composable
private fun FoodResultCard(result: FoodResult, onTap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceVariant).clickable(onClick = onTap).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(result.name.take(60), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                result.protein?.let { Text("P:${it.toInt()}g", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant) }
                result.carbs?.let { Text("C:${it.toInt()}g", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant) }
                result.fat?.let { Text("F:${it.toInt()}g", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant) }
            }
        }
        Text("${result.calories.toInt()} cal", style = MaterialTheme.typography.titleMedium, color = Red500, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EntryCard(entry: CalorieEntry, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(SurfaceVariant, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.foodName.take(50), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
        Text("${entry.calories.toInt()} cal", style = MaterialTheme.typography.bodyMedium, color = Red500, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Delete, "Delete", tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}


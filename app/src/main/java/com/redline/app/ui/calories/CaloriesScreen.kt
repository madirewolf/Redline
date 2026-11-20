package com.redline.app.ui.calories

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redline.app.data.local.entity.CalorieEntry
import com.redline.app.nutrition.FoodResult
import com.redline.app.ui.components.EmptyState
import com.redline.app.ui.components.RedlineTopBar
import com.redline.app.ui.components.ScreenPadding
import com.redline.app.ui.components.SectionCard
import com.redline.app.ui.components.SectionLabel
import com.redline.app.ui.components.grouped
import com.redline.app.ui.theme.Tabular
import com.redline.app.voice.SpeechState

@Composable
fun CaloriesScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: CaloriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val todayEntries by viewModel.todayEntries.collectAsStateWithLifecycle()
    val speechState by viewModel.speechState.collectAsStateWithLifecycle()
    val hasKey by viewModel.hasKey.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) viewModel.startListening()
    }
    var query by rememberSaveable { mutableStateOf("") }
    val isListening = speechState is SpeechState.Listening

    val totalCal = todayEntries.sumOf { it.calories.toDouble() }.toLong()
    val totalPro = todayEntries.sumOf { (it.protein ?: 0f).toDouble() }.toInt()
    val totalCarb = todayEntries.sumOf { (it.carbs ?: 0f).toDouble() }.toInt()
    val totalFat = todayEntries.sumOf { (it.fat ?: 0f).toDouble() }.toInt()

    val search = {
        focus.clearFocus()
        viewModel.searchFood(query)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        RedlineTopBar(title = "Calories", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(contentPadding = 0.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MacroStat("Calories", totalCal.grouped(), highlight = true)
                        MacroStat("Protein", "${totalPro} g")
                        MacroStat("Carbs", "${totalCarb} g")
                        MacroStat("Fat", "${totalFat} g")
                    }
                }
            }

            if (!hasKey || uiState.needsKey) {
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = colors.primary, modifier = Modifier.padding(top = 2.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Add a food database key", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                                Text(
                                    "Lookups use USDA FoodData Central. Its API key is free; paste it in Settings.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        FilledTonalButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.End)) {
                            Text("Open Settings")
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = hasKey,
                        placeholder = { Text("Search a food, like “oatmeal”") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() })
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (!hasPermission) launcher.launch(Manifest.permission.RECORD_AUDIO)
                            else if (isListening) viewModel.stopListening()
                            else viewModel.startListening()
                        },
                        enabled = hasKey,
                        modifier = Modifier.size(56.dp),
                        colors = if (isListening) IconButtonDefaults.filledIconButtonColors()
                        else IconButtonDefaults.filledTonalIconButtonColors()
                    ) {
                        Icon(
                            if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop listening" else "Say a food"
                        )
                    }
                }
            }

            when {
                isListening -> item { StatusLine("Listening. Say what you ate, like “two eggs”.") }
                uiState.isSearching -> item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        StatusLine("Looking up “${uiState.lastQuery}”")
                    }
                }
                uiState.searchError != null -> item {
                    Text(uiState.searchError!!, style = MaterialTheme.typography.bodyMedium, color = colors.error)
                }
            }

            if (uiState.searchResults.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        SectionLabel("Results for “${uiState.lastQuery}”", Modifier.weight(1f))
                        Text("per 100 g", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                }
                item {
                    SectionCard(contentPadding = 0.dp) {
                        uiState.searchResults.forEachIndexed { index, result ->
                            if (index > 0) HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                            FoodResultRow(result) { viewModel.logFood(result) }
                        }
                    }
                }
            }

            item { SectionLabel("Today", Modifier.padding(top = 8.dp)) }
            if (todayEntries.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Restaurant,
                        title = "Nothing logged today",
                        body = "Search or say a food, then tap a result to add it."
                    )
                }
            } else {
                item {
                    SectionCard(contentPadding = 0.dp) {
                        todayEntries.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                            EntryRow(entry) { viewModel.deleteEntry(entry) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MacroStat(label: String, value: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.merge(Tabular),
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun macroLine(protein: Float?, carbs: Float?, fat: Float?): String = listOfNotNull(
    protein?.let { "Protein ${it.toInt()} g" },
    carbs?.let { "Carbs ${it.toInt()} g" },
    fat?.let { "Fat ${it.toInt()} g" }
).joinToString("  ·  ")

@Composable
private fun FoodResultRow(result: FoodResult, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Add to today", onClick = onAdd)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                macroLine(result.protein, result.carbs, result.fat),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("${result.calories.toInt()} cal", style = MaterialTheme.typography.titleSmall.merge(Tabular), color = colors.onSurface)
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.padding(start = 8.dp).size(24.dp)
        )
    }
}

@Composable
private fun EntryRow(entry: CalorieEntry, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.foodName.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            entry.servingSize?.let { size ->
                Text(
                    "${size.toInt()} ${entry.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
        Text("${entry.calories.toInt()} cal", style = MaterialTheme.typography.titleSmall.merge(Tabular), color = colors.onSurface)
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Remove ${entry.foodName}", tint = colors.onSurfaceVariant)
        }
    }
}

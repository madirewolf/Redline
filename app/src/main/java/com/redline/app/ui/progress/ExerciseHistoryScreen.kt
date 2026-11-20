package com.redline.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.repository.WorkoutRepository
import com.redline.app.ui.components.EmptyState
import com.redline.app.ui.components.RedlineTopBar
import com.redline.app.ui.components.ScreenPadding
import com.redline.app.ui.components.SectionCard
import com.redline.app.ui.components.SectionLabel
import com.redline.app.ui.components.cleanNum
import com.redline.app.ui.components.plural
import com.redline.app.ui.theme.Tabular
import com.redline.app.voice.ExerciseCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class HistoryUiState(
    val title: String,
    /** Newest session first; sets inside a session in the order they were done. */
    val sessions: List<List<ExerciseSet>> = emptyList(),
    /** Best estimated 1RM per session, oldest first. */
    val trend: List<Float> = emptyList(),
    val isLoading: Boolean = true
) {
    val setCount: Int get() = sessions.sumOf { it.size }
}

@HiltViewModel
class ExerciseHistoryViewModel @Inject constructor(
    repository: WorkoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    /** A catalog key ("bench_press"), or the lower-cased name for a lift the catalog doesn't know. */
    private val exerciseKey: String = savedStateHandle["exerciseKey"] ?: ""
    private val catalogName: String? = ExerciseCatalog.displayNameForKey(exerciseKey)

    // Same grouping as the Progress list, so every card there opens a non-empty history.
    val uiState: StateFlow<HistoryUiState> = repository.getAllSets()
        .map { all ->
            val sets = all.filter { ExerciseCatalog.groupingKey(it.canonicalExerciseKey, it.exerciseName) == exerciseKey }
            val sessions = sets
                .groupBy { it.sessionId }
                .values
                .map { session -> session.sortedBy { it.timestamp } }
                .sortedByDescending { it.first().timestamp }
            HistoryUiState(
                title = catalogName ?: sets.firstOrNull()?.exerciseName ?: exerciseKey,
                sessions = sessions,
                trend = sessions.reversed().mapNotNull { s -> s.mapNotNull { it.estimatedOneRepMax() }.maxOrNull() },
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState(title = catalogName ?: exerciseKey))
}

@Composable
fun ExerciseHistoryScreen(onBack: () -> Unit, viewModel: ExerciseHistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        RedlineTopBar(title = state.title, onBack = onBack)
        if (state.isLoading) return@Column

        if (state.sessions.isEmpty()) {
            EmptyState(
                icon = Icons.Default.History,
                title = "No sets for this lift",
                body = "Sets you log for ${state.title} show up here."
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "${plural(state.setCount, "set")} across ${plural(state.sessions.size, "session")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }

            if (state.trend.isNotEmpty()) {
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Estimated 1RM", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                                Text(
                                    state.trend.last().roundToInt().toString(),
                                    style = MaterialTheme.typography.headlineMedium.merge(Tabular),
                                    color = colors.onSurface
                                )
                            }
                            Text(
                                "Best ${state.trend.max().roundToInt()}",
                                style = MaterialTheme.typography.labelLarge.merge(Tabular),
                                color = colors.onSurfaceVariant
                            )
                        }
                        if (state.trend.size >= 2) {
                            Spacer(modifier = Modifier.height(12.dp))
                            TrendLine(state.trend, modifier = Modifier.height(96.dp))
                        } else {
                            Text(
                                "Log this lift in another session to see a trend.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            items(state.sessions, key = { it.first().sessionId }) { session ->
                Column {
                    SectionLabel(
                        dateFormat.format(Date(session.first().timestamp)),
                        Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    SectionCard(contentPadding = 0.dp) {
                        session.forEachIndexed { index, set ->
                            if (index > 0) HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                            SetRow(index + 1, set)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetRow(number: Int, set: ExerciseSet) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelLarge.merge(Tabular),
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        Text(
            buildString {
                set.weight?.let { append("${it.cleanNum()} ${set.unit}") }
                set.reps?.let { if (isNotEmpty()) append(" × "); append(plural(it, "rep")) }
                if (isEmpty()) append("No weight or reps")
            },
            style = MaterialTheme.typography.bodyLarge.merge(Tabular),
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        set.rpe?.let {
            Text("RPE ${it.cleanNum()}", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        }
    }
}

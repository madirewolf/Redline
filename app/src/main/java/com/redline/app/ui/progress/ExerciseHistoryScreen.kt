package com.redline.app.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.SurfaceVariant
import com.redline.app.voice.ExerciseCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExerciseHistoryViewModel @Inject constructor(
    repository: WorkoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val exerciseKey: String = savedStateHandle["exerciseKey"] ?: ""
    val displayName: String = ExerciseCatalog.displayNameForKey(exerciseKey) ?: exerciseKey

    val sets: StateFlow<List<ExerciseSet>> = repository.getSetsForCanonicalExercise(exerciseKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun ExerciseHistoryScreen(
    exerciseKey: String,
    viewModel: ExerciseHistoryViewModel = hiltViewModel()
) {
    val sets by viewModel.sets.collectAsStateWithLifecycle()
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    // Group by session
    val grouped = sets.groupBy { it.sessionId }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                viewModel.displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${sets.size} sets across ${grouped.size} sessions",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        grouped.entries.forEachIndexed { idx, (_, sessionSets) ->
            item(key = "session_$idx") {
                Text(
                    dateFormat.format(Date(sessionSets.first().timestamp)),
                    style = MaterialTheme.typography.labelLarge,
                    color = Red500,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(sessionSets, key = { it.id }) { set ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    set.weight?.let {
                        Text("${it.clean()} ${set.unit}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    set.reps?.let {
                        Text("x $it", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                    set.rpe?.let {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RPE ${it.clean()}", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

private fun Float.clean(): String = if (this == toLong().toFloat()) toLong().toString() else toString()

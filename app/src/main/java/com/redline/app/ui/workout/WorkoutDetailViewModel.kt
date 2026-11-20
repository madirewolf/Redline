package com.redline.app.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.WorkoutSession
import com.redline.app.data.repository.WorkoutRepository
import com.redline.app.ui.components.withDraft
import com.redline.app.voice.ExerciseCatalog
import com.redline.app.voice.SetDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseGroup(
    val exerciseName: String,
    val sets: List<ExerciseSet>
)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: Long = savedStateHandle["sessionId"] ?: 0L

    private val _session = MutableStateFlow<WorkoutSession?>(null)
    val session: StateFlow<WorkoutSession?> = _session.asStateFlow()

    /** The set open in the editor, if any. */
    private val _editing = MutableStateFlow<ExerciseSet?>(null)
    val editing: StateFlow<ExerciseSet?> = _editing.asStateFlow()

    // Same grouping as the live log, so "Pull-ups" and "pull ups" stay one exercise.
    val exerciseGroups: StateFlow<List<ExerciseGroup>> = repository.getSetsForSession(sessionId)
        .map { sets ->
            sets.groupBy { ExerciseCatalog.groupingKey(it.canonicalExerciseKey, it.exerciseName) }
                .map { (_, groupSets) ->
                    val ordered = groupSets.sortedBy { it.timestamp }
                    ExerciseGroup(exerciseName = ordered.first().exerciseName, sets = ordered)
                }
                .sortedBy { it.sets.first().timestamp }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _session.value = repository.getSessionById(sessionId)
        }
    }

    fun openEditor(set: ExerciseSet) {
        _editing.value = set
    }

    fun closeEditor() {
        _editing.value = null
    }

    fun save(draft: SetDraft) {
        val updated = _editing.value?.withDraft(draft) ?: return
        viewModelScope.launch { repository.updateSet(updated) }
        closeEditor()
    }

    fun deleteEditing() {
        val set = _editing.value ?: return
        viewModelScope.launch { repository.deleteSet(set) }
        closeEditor()
    }
}

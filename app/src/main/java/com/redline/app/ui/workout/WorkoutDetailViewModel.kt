package com.redline.app.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.WorkoutSession
import com.redline.app.data.repository.WorkoutRepository
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

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    val exerciseGroups: StateFlow<List<ExerciseGroup>> = repository.getSetsForSession(sessionId)
        .map { sets ->
            sets.groupBy { it.canonicalExerciseKey ?: it.exerciseName }
                .map { (_, groupSets) ->
                    ExerciseGroup(
                        exerciseName = groupSets.first().exerciseName,
                        sets = groupSets.sortedBy { it.timestamp }
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val setCount: StateFlow<Int> = repository.getSetsForSession(sessionId)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            _session.value = repository.getSessionById(sessionId)
        }
    }

    fun toggleEditing() {
        _isEditing.value = !_isEditing.value
    }

    fun updateSet(set: ExerciseSet) {
        viewModelScope.launch { repository.updateSet(set) }
    }

    fun deleteSet(set: ExerciseSet) {
        viewModelScope.launch { repository.deleteSet(set) }
    }
}

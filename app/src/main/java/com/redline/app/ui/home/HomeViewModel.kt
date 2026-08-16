package com.redline.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.WorkoutSession
import com.redline.app.data.repository.WorkoutRepository
import com.redline.app.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class WorkoutSummary(
    val session: WorkoutSession,
    val exerciseNames: List<String>,
    val setCount: Int
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val userName: StateFlow<String> = settingsStore.userName
    val isFirstLaunch: Boolean = settingsStore.isFirstLaunch

    val activeSession: StateFlow<WorkoutSession?> = repository.getActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _recentWorkouts = MutableStateFlow<List<WorkoutSummary>>(emptyList())
    val recentWorkouts: StateFlow<List<WorkoutSummary>> = _recentWorkouts.asStateFlow()

    val workoutDays: StateFlow<Set<LocalDate>> = repository.getAllCompletedSessionStartTimes()
        .map { times ->
            times.map { ms ->
                Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
            }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val sessionCount: StateFlow<Int> = repository.getCompletedSessionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            repository.getRecentCompletedSessions(8).collect { sessions ->
                _recentWorkouts.value = sessions.map { session ->
                    WorkoutSummary(
                        session = session,
                        exerciseNames = repository.getExerciseNamesForSession(session.id),
                        setCount = repository.getSetCountForSession(session.id)
                    )
                }
            }
        }
    }

    fun setUserName(name: String) {
        settingsStore.setUserName(name)
    }

    fun startWorkout(): Long {
        var id = 0L
        viewModelScope.launch {
            id = repository.startSession()
        }
        return id
    }
}

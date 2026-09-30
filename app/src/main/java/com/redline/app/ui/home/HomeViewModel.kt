package com.redline.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.WorkoutSession
import com.redline.app.data.repository.SessionState
import com.redline.app.data.repository.WorkoutRepository
import com.redline.app.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class WorkoutSummary(
    val session: WorkoutSession,
    val exerciseNames: List<String>,
    val setCount: Int,
    val volume: Long,
    val unit: String
)

data class WeekStats(
    val total: Int = 0,
    val thisWeek: Int = 0,
    /** Weeks in a row with at least one workout, counting this one once it has a workout. */
    val streakWeeks: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val userName: StateFlow<String> = settingsStore.userName
    val isFirstLaunch: Boolean = settingsStore.isFirstLaunch

    val sessionState: StateFlow<SessionState> = repository.getActiveSessionState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveSets: StateFlow<List<ExerciseSet>> = repository.getActiveSession()
        .flatMapLatest { session ->
            if (session != null) repository.getSetsForSession(session.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Built from the sets table too, so editing a past workout updates its card.
    val recentWorkouts: StateFlow<List<WorkoutSummary>> = combine(
        repository.getRecentCompletedSessions(8),
        repository.getAllSets()
    ) { sessions, sets ->
        val bySession = sets.groupBy { it.sessionId }
        sessions.map { session ->
            val sessionSets = bySession[session.id].orEmpty().sortedBy { it.timestamp }
            WorkoutSummary(
                session = session,
                exerciseNames = sessionSets.map { it.exerciseName }.distinct(),
                setCount = sessionSets.size,
                volume = sessionSets.sumOf { ((it.weight ?: 0f) * (it.reps ?: 0)).toDouble() }.toLong(),
                unit = sessionSets.groupingBy { it.unit }.eachCount().maxByOrNull { it.value }?.key ?: "lbs"
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workoutDays: StateFlow<Set<LocalDate>> = repository.getAllCompletedSessionStartTimes()
        .map { times ->
            times.map { ms ->
                Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
            }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val weekStats: StateFlow<WeekStats> = repository.getAllCompletedSessionStartTimes()
        .map { times -> weekStats(times) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeekStats())

    val caloriesToday: StateFlow<Int> = run {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        repository.getCalorieEntriesForDay(start, start + 86_400_000L)
            .map { entries -> entries.sumOf { it.calories.toDouble() }.toInt() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    }

    fun setUserName(name: String) {
        settingsStore.setUserName(name)
    }

    fun startWorkout() {
        viewModelScope.launch { repository.startSession() }
    }

    private fun weekStats(startTimes: List<Long>): WeekStats {
        val zone = ZoneId.systemDefault()
        val weekOf = { date: LocalDate -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        val thisWeek = weekOf(LocalDate.now())
        val weeks = startTimes.map { weekOf(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) }
        val activeWeeks = weeks.toSet()

        // A week that hasn't had a workout yet doesn't break the streak.
        var week = if (thisWeek in activeWeeks) thisWeek else thisWeek.minusWeeks(1)
        var streak = 0
        while (week in activeWeeks) {
            streak++
            week = week.minusWeeks(1)
        }
        return WeekStats(
            total = startTimes.size,
            thisWeek = weeks.count { it == thisWeek },
            streakWeeks = streak
        )
    }
}

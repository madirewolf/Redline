package com.redline.app.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

data class ProgressUiState(
    val summaries: List<ExerciseProgress> = emptyList()
)

data class ExerciseProgress(
    val exerciseKey: String,
    val exerciseName: String,
    val status: ProgressStatus,
    val latestEstimatedOneRepMax: Float?,
    val bestEstimatedOneRepMax: Float?,
    val trendPercent: Float?,
    val totalVolume: Float,
    val setCount: Int,
    val latestFailureScore: Int?,
    val lastThreeSame: Boolean,
    val trendPoints: List<Float>
)

enum class ProgressStatus(val label: String) {
    Progressing("Progressing"),
    Stagnating("Stagnating"),
    Regressing("Regressing"),
    Building("Building")
}

@HiltViewModel
class ProgressViewModel @Inject constructor(
    repository: WorkoutRepository
) : ViewModel() {
    val uiState: StateFlow<ProgressUiState> = repository.getAllSets()
        .map { sets -> ProgressUiState(buildSummaries(sets)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressUiState())

    private fun buildSummaries(sets: List<ExerciseSet>): List<ExerciseProgress> {
        return sets
            .groupBy { it.canonicalExerciseKey ?: it.exerciseName.lowercase().trim() }
            .map { (key, exerciseSets) -> buildSummary(key, exerciseSets) }
            .sortedWith(
                compareByDescending<ExerciseProgress> { it.status == ProgressStatus.Stagnating }
                    .thenByDescending { it.latestEstimatedOneRepMax ?: 0f }
                    .thenBy { it.exerciseName }
            )
    }

    private fun buildSummary(key: String, sets: List<ExerciseSet>): ExerciseProgress {
        val ordered = sets.sortedByDescending { it.timestamp }
        val sessionBests = ordered
            .groupBy { it.sessionId }
            .map { (_, sessionSets) ->
                sessionSets.maxByOrNull { it.estimatedOneRepMax() ?: 0f }
            }
            .filterNotNull()
            .sortedByDescending { it.timestamp }

        val latest = sessionBests.firstOrNull()
        val previous = sessionBests.drop(1).firstOrNull()
        val latestE1rm = latest?.estimatedOneRepMax()
        val previousE1rm = previous?.estimatedOneRepMax()
        val bestE1rm = ordered.mapNotNull { it.estimatedOneRepMax() }.maxOrNull()
        val trendPercent = if (latestE1rm != null && previousE1rm != null && previousE1rm > 0f) {
            ((latestE1rm - previousE1rm) / previousE1rm) * 100f
        } else {
            null
        }
        val lastThreeSame = sessionBests.take(3).size == 3 &&
            sessionBests.take(3).map { it.loadSignature() }.distinct().size == 1
        val status = when {
            lastThreeSame -> ProgressStatus.Stagnating
            trendPercent == null -> ProgressStatus.Building
            trendPercent >= 2.5f -> ProgressStatus.Progressing
            trendPercent <= -2.5f -> ProgressStatus.Regressing
            abs(trendPercent) < 2.5f -> ProgressStatus.Building
            else -> ProgressStatus.Building
        }

        return ExerciseProgress(
            exerciseKey = key,
            exerciseName = ordered.firstOrNull()?.exerciseName ?: "Unknown Exercise",
            status = status,
            latestEstimatedOneRepMax = latestE1rm,
            bestEstimatedOneRepMax = bestE1rm,
            trendPercent = trendPercent,
            totalVolume = ordered.sumOf { ((it.weight ?: 0f) * (it.reps ?: 0)).toDouble() }.toFloat(),
            setCount = ordered.size,
            latestFailureScore = ordered.firstOrNull { it.failureProximityScore != null }?.failureProximityScore,
            lastThreeSame = lastThreeSame,
            trendPoints = sessionBests.reversed().mapNotNull { it.estimatedOneRepMax() }.takeLast(8)
        )
    }

    private fun ExerciseSet.estimatedOneRepMax(): Float? {
        val w = weight ?: return null
        val r = reps ?: return null
        if (w <= 0f || r <= 0) return null
        return w * (1f + (r / 30f))
    }

    private fun ExerciseSet.loadSignature(): String {
        val weightPart = weight?.roundToInt()?.toString() ?: "bodyweight"
        val repsPart = reps?.toString() ?: "unknown"
        return "$weightPart:$repsPart"
    }
}

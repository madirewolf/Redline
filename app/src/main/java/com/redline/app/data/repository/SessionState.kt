package com.redline.app.data.repository

import com.redline.app.data.local.entity.WorkoutSession

/**
 * The in-progress workout as a screen should see it. [Loading] lasts until
 * Room first answers, so screens can wait instead of briefly showing
 * "Start Workout" for a workout that is already running.
 */
sealed interface SessionState {
    data object Loading : SessionState
    data object None : SessionState
    data class Active(val session: WorkoutSession) : SessionState
}

package com.redline.app.ui

import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.ui.progress.ProgressStatus
import com.redline.app.ui.progress.buildSummaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressSummaryTest {

    private var nextId = 1L

    private fun set(session: Long, name: String, key: String?, weight: Float?, reps: Int, at: Long) = ExerciseSet(
        id = nextId++, sessionId = session, exerciseName = name, canonicalExerciseKey = key,
        weight = weight, reps = reps, timestamp = at
    )

    @Test
    fun `bodyweight lifts get a card measured in reps`() {
        val summaries = buildSummaries(
            listOf(
                set(1, "Pull Up", "pull_up", null, 8, 100),
                set(1, "Pull Up", "pull_up", null, 6, 110),
                set(2, "Pull Up", "pull_up", null, 10, 200)
            )
        )

        val pullUps = summaries.single()
        assertTrue(pullUps.isBodyweight)
        assertNull(pullUps.latestEstimatedOneRepMax)
        assertEquals(24, pullUps.totalReps)
        assertEquals(10, pullUps.latestBestReps)
        assertEquals(8, pullUps.previousBestReps) // the better of session 1's sets
        assertEquals(ProgressStatus.Progressing, pullUps.status)
    }

    @Test
    fun `weighted lifts trend on estimated max`() {
        val bench = buildSummaries(
            listOf(
                set(1, "Bench Press", "bench_press", 185f, 8, 100),
                set(2, "Bench Press", "bench_press", 205f, 5, 200)
            )
        ).single()

        assertFalse(bench.isBodyweight)
        assertEquals(2, bench.trendPoints.size)
        assertTrue((bench.trendPercent ?: 0f) > 0f)
    }

    @Test
    fun `three sessions at the same load stall the lift and list it first`() {
        val sets = (1L..3L).map { set(it, "Back Squat", "back_squat", 225f, 5, it * 100) } +
            set(4, "Deadlift", "deadlift", 405f, 3, 400)

        val summaries = buildSummaries(sets)

        assertEquals("Back Squat", summaries.first().exerciseName)
        assertEquals(ProgressStatus.Stagnating, summaries.first().status)
    }
}

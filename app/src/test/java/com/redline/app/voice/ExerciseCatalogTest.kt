package com.redline.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseCatalogTest {

    private fun key(input: String) = ExerciseCatalog.match(input)?.key

    @Test
    fun `README incline aliases land on one row`() {
        assertEquals("incline_dumbbell_press", key("incline dumbbell press"))
        assertEquals("incline_dumbbell_press", key("incline db press"))
        assertEquals("incline_dumbbell_press", key("incline press"))
    }

    @Test
    fun `longest alias wins`() {
        assertEquals("incline_bench_press", key("incline bench press 135 for 10"))
        assertEquals("dumbbell_bench_press", key("db bench 70s for 10"))
        assertEquals("bench_press", key("bench 185 for 8"))
        assertEquals("romanian_deadlift", key("romanian deadlift"))
        assertEquals("hammer_curl", key("hammer curls"))
    }

    @Test
    fun `plurals and hyphens match`() {
        assertEquals("pull_up", key("pull ups"))
        assertEquals("pull_up", key("pull-ups"))
        assertEquals("tricep_extension", key("skull crushers"))
        assertEquals("lunge", key("walking lunges"))
        assertEquals("dip", key("dips"))
    }

    @Test
    fun `aliases need word boundaries`() {
        assertNull(key("rowing machine"))
        assertNull(key("benchmark"))
        assertEquals("barbell_row", key("rows"))
    }

    @Test
    fun `grouping key falls back to the spoken name`() {
        assertEquals("bench_press", ExerciseCatalog.groupingKey("bench_press", "Bench Press"))
        assertEquals("landmine press", ExerciseCatalog.groupingKey(null, "  Landmine Press "))
    }
}

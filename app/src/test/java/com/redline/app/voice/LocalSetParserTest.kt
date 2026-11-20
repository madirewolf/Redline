package com.redline.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSetParserTest {

    private fun separated(input: String, unit: String = "lbs") = LocalSetParser.parseSeparated(input, unit)
    private fun freeform(input: String, unit: String = "lbs") = LocalSetParser.parseFreeform(input, unit)

    // Separated tier

    @Test
    fun `README separator format reads weight then reps`() {
        val set = separated("bench next 185 next 8")!!
        assertEquals("bench_press", set.canonicalExerciseKey)
        assertEquals(185f, set.weight)
        assertEquals(8, set.reps)
        assertEquals(ParseSource.SEPARATED, set.source)
        assertTrue(set.confidence >= SetParsePipeline.SEPARATED_THRESHOLD)
        assertFalse(set.needsClarification)
    }

    @Test
    fun `separator notes segment is kept and units are read`() {
        val set = separated("squat next 100 kg next 5 next felt slow out of the hole")!!
        assertEquals("back_squat", set.canonicalExerciseKey)
        assertEquals(100f, set.weight)
        assertEquals("kg", set.unit)
        assertEquals(5, set.reps)
        assertEquals("Felt slow out of the hole", set.notes)
    }

    @Test
    fun `separator segments can hold more than one field`() {
        val set = separated("bench 185 for 8 next felt good")!!
        assertEquals(185f, set.weight)
        assertEquals(8, set.reps)
        assertEquals("Felt good", set.notes)
    }

    @Test
    fun `bodyweight separator set has reps and no weight`() {
        val set = separated("pull ups next 10")!!
        assertEquals("pull_up", set.canonicalExerciseKey)
        assertNull(set.weight)
        assertEquals(10, set.reps)
    }

    @Test
    fun `separator with no numbers falls through`() {
        assertNull(separated("bench next felt heavy"))
        assertNull(separated("next set please"))
        assertNull(separated("bench 185 for 8"))
    }

    @Test
    fun `separator keeps an uncatalogued exercise name`() {
        val set = separated("landmine press next 90 next 10")!!
        assertEquals("Landmine Press", set.exerciseName)
        assertNull(set.canonicalExerciseKey)
        assertTrue(set.isComplete)
    }

    @Test
    fun `separator weight without reps asks for clarification`() {
        val set = separated("deadlift next 405")!!
        assertEquals(405f, set.weight)
        assertNull(set.reps)
        assertTrue(set.needsClarification)
        assertFalse(set.isComplete)
    }

    @Test
    fun `separator uses the default unit when none is spoken`() {
        assertEquals("kg", separated("bench next 80 next 5", unit = "kg")!!.unit)
    }

    // Freeform tier

    @Test
    fun `freeform reads weight for reps and RPE`() {
        val set = freeform("bench 185 for 8 rpe 8")
        assertEquals("bench_press", set.canonicalExerciseKey)
        assertEquals(185f, set.weight)
        assertEquals(8, set.reps)
        assertEquals(8f, set.rpe)
        assertFalse(set.needsClarification)
        assertEquals(ParseSource.LOCAL, set.source)
    }

    @Test
    fun `RPE number is never taken as weight or reps`() {
        val set = freeform("rpe 9 squat 225 for 5")
        assertEquals(9f, set.rpe)
        assertEquals(225f, set.weight)
        assertEquals(5, set.reps)
    }

    @Test
    fun `plurals and hyphens match the catalog`() {
        assertEquals("pull_up", freeform("pull ups for 10").canonicalExerciseKey)
        assertEquals(10, freeform("pull ups for 10").reps)
        assertEquals("push_up", freeform("20 push-ups").canonicalExerciseKey)
        assertEquals("bicep_curl", freeform("curls 30 for 12").canonicalExerciseKey)
    }

    @Test
    fun `compact 185x8 is weight times reps`() {
        val set = freeform("bench 185x8")
        assertEquals(185f, set.weight)
        assertEquals(8, set.reps)
    }

    @Test
    fun `explicit reps word wins over position`() {
        val set = freeform("8 reps of bench at 185")
        assertEquals(185f, set.weight)
        assertEquals(8, set.reps)
    }

    @Test
    fun `explicit unit is kept and normalised`() {
        val set = freeform("deadlift 140 kilos for 3")
        assertEquals(140f, set.weight)
        assertEquals("kg", set.unit)
        assertEquals(3, set.reps)
    }

    @Test
    fun `decimal weights survive`() {
        val set = freeform("overhead press 62.5 kg for 5.")
        assertEquals(62.5f, set.weight)
        assertEquals(5, set.reps)
    }

    @Test
    fun `spoken numbers become digits`() {
        val set = freeform("squat two twenty five for eight")
        assertEquals(225f, set.weight)
        assertEquals(8, set.reps)
        assertEquals(1, freeform("deadlift 405 for one rep").reps)
        assertEquals(12, freeform("lunges for twelve").reps)
    }

    @Test
    fun `effort keywords infer RPE`() {
        assertEquals(9f, freeform("bench 185 for 8 that was a grind").rpe)
        assertEquals(6f, freeform("bench 135 for 10 felt easy").rpe)
        assertNull(freeform("bench 135 for 10").rpe)
    }

    @Test
    fun `notes start at the first note word`() {
        assertEquals("Felt heavy on the last rep", freeform("bench 185 for 8 felt heavy on the last rep").notes)
    }

    @Test
    fun `unknown exercise needs clarification`() {
        val set = freeform("zottman thing 20 for 12")
        assertEquals(LocalSetParser.UNKNOWN_EXERCISE, set.exerciseName)
        assertTrue(set.needsClarification)
        assertFalse(set.isComplete)
    }

    @Test
    fun `missing reps needs clarification`() {
        val set = freeform("bench 185")
        assertEquals(185f, set.weight)
        assertNull(set.reps)
        assertTrue(set.needsClarification)
    }

    @Test
    fun `lat is not read as the word at`() {
        val set = freeform("lat pulldown 120 for 10")
        assertEquals("lat_pulldown", set.canonicalExerciseKey)
        assertEquals(120f, set.weight)
        assertEquals(10, set.reps)
    }

    @Test
    fun `normalize handles spoken forms`() {
        assertEquals("bench 185 x 8", LocalSetParser.normalize("Bench 185x8."))
        assertEquals("squat 315 for 3", LocalSetParser.normalize("squat three fifteen for three"))
        assertEquals("curl 25 for 10", LocalSetParser.normalize("curl twenty-five, for ten"))
    }
}

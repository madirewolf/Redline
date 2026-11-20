package com.redline.app.voice

import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.ui.components.withDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetDraftTest {

    private val valid = SetDraft(exercise = "Bench Press", weight = "185", reps = "8")

    @Test
    fun `a named exercise with reps is valid`() {
        assertTrue(valid.isValid)
        assertTrue(valid.copy(weight = "").isValid) // bodyweight sets have no weight
    }

    @Test
    fun `blank or unknown exercise is rejected`() {
        assertEquals("Name the exercise", valid.copy(exercise = "  ").exerciseError)
        assertEquals("Name the exercise", valid.copy(exercise = LocalSetParser.UNKNOWN_EXERCISE).exerciseError)
    }

    @Test
    fun `reps must be a whole number in range`() {
        assertNotNull(valid.copy(reps = "").repsError)
        assertNotNull(valid.copy(reps = "8.5").repsError)
        assertNotNull(valid.copy(reps = "0").repsError)
        assertNotNull(valid.copy(reps = "1000").repsError)
        assertNull(valid.copy(reps = " 12 ").repsError)
    }

    @Test
    fun `weight and rpe are optional but checked when given`() {
        assertEquals("Weight must be a number", valid.copy(weight = "heavy").weightError)
        assertEquals("Weight looks wrong", valid.copy(weight = "-5").weightError)
        assertEquals("RPE runs from 1 to 10", valid.copy(rpe = "11").rpeError)
        assertNull(valid.copy(rpe = "8.5").rpeError)
        assertFalse(valid.copy(rpe = "eight").isValid)
    }

    @Test
    fun `typed catalog name gets its key and spelling`() {
        val parsed = valid.copy(exercise = "pull-ups", weight = "", reps = "10").toParsedSet("")

        assertNotNull(parsed)
        assertEquals("pull_up", parsed!!.canonicalExerciseKey)
        assertEquals(ExerciseCatalog.displayNameForKey("pull_up"), parsed.exerciseName)
        assertEquals(ParseSource.MANUAL, parsed.source)
        assertNull(parsed.weight)
    }

    @Test
    fun `typed name outside the catalog is kept as typed`() {
        val parsed = valid.copy(exercise = "  Landmine   Press ", unit = "kilograms", notes = " ").toParsedSet("")!!

        assertEquals("Landmine Press", parsed.exerciseName)
        assertNull(parsed.canonicalExerciseKey)
        assertEquals("kg", parsed.unit)
        assertNull(parsed.notes)
    }

    @Test
    fun `invalid draft produces no set`() {
        assertNull(valid.copy(reps = "").toParsedSet(""))
    }

    @Test
    fun `editor prefill leaves an unknown exercise blank`() {
        val heard = ParsedSet(exerciseName = LocalSetParser.UNKNOWN_EXERCISE, weight = 185f, reps = 8, rawInput = "185 for 8")

        val draft = SetDraft.from(heard)

        assertEquals("", draft.exercise)
        assertEquals("185", draft.weight)
        assertEquals("8", draft.reps)
    }

    @Test
    fun `numbers drop a trailing zero only`() {
        assertEquals("185", SetDraft.formatNumber(185f))
        assertEquals("182.5", SetDraft.formatNumber(182.5f))
    }

    @Test
    fun `editing a saved set keeps its identity and tempo data`() {
        val saved = ExerciseSet(
            id = 7, sessionId = 3, exerciseName = "Bench Press", canonicalExerciseKey = "bench_press",
            weight = 185f, reps = 8, failureProximityScore = 88, rawVoiceInput = "bench 185 for 8", timestamp = 1000L
        )

        val edited = saved.withDraft(SetDraft(exercise = "Bench Press", weight = "190", reps = "6", rpe = "9"))!!

        assertEquals(7L, edited.id)
        assertEquals(3L, edited.sessionId)
        assertEquals(1000L, edited.timestamp)
        assertEquals(88, edited.failureProximityScore)
        assertEquals(190f, edited.weight)
        assertEquals(6, edited.reps)
        assertEquals(9f, edited.rpe)
        assertNull(saved.withDraft(SetDraft(exercise = "Bench Press", reps = "")))
    }
}

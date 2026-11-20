package com.redline.app.voice

import com.redline.app.ui.log.runs
import com.redline.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSetParseTest {

    private val bench = ParseContext(lastExerciseName = "Bench Press", lastExerciseKey = "bench_press")

    // ClaudeSetPrompt.parseResponse

    @Test
    fun `the example sentence becomes four sets and one`() {
        val json = """
            {"sets":[
              {"exercise":"bench press","count":4,"weight":135,"unit":"lbs","reps":5,"rpe":null,"notes":null},
              {"exercise":"","count":1,"weight":122,"unit":"lbs","reps":10,"rpe":null,"notes":null}
            ]}
        """.trimIndent()
        val sets = ClaudeSetPrompt.parseResponse(json, "raw", ParseContext(), "lbs")

        assertEquals(5, sets.size)
        sets.take(4).forEach {
            assertEquals("Bench Press", it.exerciseName)
            assertEquals(135f, it.weight)
            assertEquals(5, it.reps)
        }
        // The second entry names no exercise, so it carries the bench over.
        assertEquals("Bench Press", sets[4].exerciseName)
        assertEquals("bench_press", sets[4].canonicalExerciseKey)
        assertEquals(122f, sets[4].weight)
        assertEquals(10, sets[4].reps)
        assertTrue(sets.all { it.source == ParseSource.CLAUDE && it.isComplete && it.rawInput == "raw" })
    }

    @Test
    fun `an unnamed first entry continues the last exercise logged`() {
        val json = """{"sets":[{"exercise":"","count":1,"weight":225,"unit":"lbs","reps":5,"rpe":8,"notes":"paused"}]}"""
        val set = ClaudeSetPrompt.parseResponse(json, "", bench, "lbs").single()
        assertEquals("Bench Press", set.exerciseName)
        assertEquals(8f, set.rpe)
        assertEquals("Paused", set.notes)
    }

    @Test
    fun `out of range numbers are dropped and the count is capped`() {
        val json = """{"sets":[{"exercise":"squat","count":50,"weight":99999,"unit":"kg","reps":0,"rpe":14,"notes":null}]}"""
        val sets = ClaudeSetPrompt.parseResponse(json, "", ParseContext(), "lbs")
        assertEquals(LocalSetParser.MAX_SET_COUNT, sets.size)
        val set = sets.first()
        assertNull(set.weight)
        assertNull(set.reps)
        assertEquals("kg", set.unit)
        assertEquals(10f, set.rpe)
        assertTrue(set.needsClarification)
    }

    @Test
    fun `an unknown exercise keeps its spoken name`() {
        val json = """{"sets":[{"exercise":"zercher carry","count":1,"weight":null,"unit":"lbs","reps":3,"rpe":null,"notes":null}]}"""
        val set = ClaudeSetPrompt.parseResponse(json, "", ParseContext(), "lbs").single()
        assertEquals("Zercher Carry", set.exerciseName)
        assertNull(set.canonicalExerciseKey)
        assertNull(set.weight)
    }

    @Test
    fun `text that isn't the expected JSON throws`() {
        assertThrows(Exception::class.java) { ClaudeSetPrompt.parseResponse("not json", "", ParseContext(), "lbs") }
        assertThrows(IllegalArgumentException::class.java) {
            ClaudeSetPrompt.parseResponse("""{"other":1}""", "", ParseContext(), "lbs")
        }
    }

    @Test
    fun `every schema field is required`() {
        @Suppress("UNCHECKED_CAST")
        val item = ((ClaudeSetPrompt.schema["properties"] as Map<String, Any>)["sets"] as Map<String, Any>)["items"] as Map<String, Any>
        assertEquals((item["properties"] as Map<*, *>).keys, (item["required"] as List<*>).toSet())
        assertEquals(false, item["additionalProperties"])
    }

    // LocalSetParser.parseMany, the offline fallback

    @Test
    fun `the example sentence splits offline too`() {
        val sets = LocalSetParser.parseMany("i did 135 for 4 sets of 5 reps and then 122 for 10 reps", "lbs", bench)
        assertEquals(5, sets.size)
        assertTrue(sets.all { it.exerciseName == "Bench Press" })
        assertEquals(listOf(135f, 135f, 135f, 135f, 122f), sets.map { it.weight })
        assertEquals(listOf(5, 5, 5, 5, 10), sets.map { it.reps })
    }

    @Test
    fun `one set with an and in it stays one set`() {
        val sets = LocalSetParser.parseMany("bench 135 and 5 reps", "lbs")
        assertEquals(1, sets.size)
        assertEquals(135f, sets.single().weight)
        assertEquals(5, sets.single().reps)
    }

    // SetParsePipeline's Claude tier

    private var remoteCalls = 0
    private var claudeCalls = 0

    private fun pipeline(claudeAnswer: List<ParsedSet>?) = SetParsePipeline(
        remote = { remoteCalls++; null },
        defaultUnit = { "lbs" },
        claude = { _, _ -> claudeCalls++; claudeAnswer }
    )

    private val claudeSet = ParsedSet(
        exerciseName = "Bench Press",
        canonicalExerciseKey = "bench_press",
        weight = 135f,
        reps = 5,
        confidence = 0.9f,
        rawInput = "x",
        source = ParseSource.CLAUDE
    )

    @Test
    fun `claude's sets are used before the endpoint`() = runBlocking {
        val sets = pipeline(listOf(claudeSet, claudeSet)).parseAll("bench 135 for 2 sets of 5")
        assertEquals(2, sets.size)
        assertTrue(sets.all { it.source == ParseSource.CLAUDE })
        assertEquals(0, remoteCalls)
    }

    @Test
    fun `no claude answer falls through to the phone`() = runBlocking {
        val sets = pipeline(null).parseAll("bench 185 for 8")
        assertEquals(ParseSource.LOCAL, sets.single().source)
        assertEquals(1, claudeCalls)
        assertEquals(1, remoteCalls)
    }

    @Test
    fun `the next format skips claude`() = runBlocking {
        val sets = pipeline(listOf(claudeSet)).parseAll("bench next 185 next 8")
        assertEquals(ParseSource.SEPARATED, sets.single().source)
        assertEquals(0, claudeCalls)
    }

    // Grouping for the confirmation card

    @Test
    fun `identical sets in a row group into one run`() {
        val other = claudeSet.copy(weight = 122f, reps = 10)
        val runs = listOf(claudeSet, claudeSet, claudeSet, other, claudeSet).runs()
        assertEquals(listOf(3, 1, 1), runs.map { it.count })
        assertEquals(listOf(0, 3, 4), runs.map { it.first })
        assertEquals(0..2, runs.first().indices)
        assertEquals("Bench Press 3×5 at 135 lbs, 1×10 at 122 lbs, 1×5 at 135 lbs",
            SettingsViewModel.describe(listOf(claudeSet, claudeSet, claudeSet, other, claudeSet)))
    }
}

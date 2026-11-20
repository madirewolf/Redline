package com.redline.app.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetParsePipelineTest {

    private var remoteCalls = 0

    private fun pipeline(remoteAnswer: ParsedSet?) = SetParsePipeline(
        remote = { remoteCalls++; remoteAnswer },
        defaultUnit = { "lbs" }
    )

    private fun remoteSet(confidence: Float, needsClarification: Boolean = false) = ParsedSet(
        exerciseName = "Romanian Deadlift",
        canonicalExerciseKey = "romanian_deadlift",
        weight = 225f,
        reps = 6,
        confidence = confidence,
        needsClarification = needsClarification,
        rawInput = "remote",
        source = ParseSource.REMOTE
    )

    @Test
    fun `separated format short-circuits before the endpoint`() = runBlocking {
        val result = pipeline(remoteSet(0.95f)).parse("bench next 185 next 8")
        assertEquals(ParseSource.SEPARATED, result.source)
        assertEquals(0, remoteCalls)
    }

    @Test
    fun `no endpoint configured falls back to the local parser`() = runBlocking {
        val result = pipeline(remoteAnswer = null).parse("bench 185 for 8")
        assertEquals(ParseSource.LOCAL, result.source)
        assertEquals(185f, result.weight)
        assertEquals(8, result.reps)
        assertEquals(1, remoteCalls)
    }

    @Test
    fun `confident remote answer is used for messy speech`() = runBlocking {
        val result = pipeline(remoteSet(0.8f)).parse("uh the hinge thing, two and a quarter, six")
        assertEquals(ParseSource.REMOTE, result.source)
        assertEquals("romanian_deadlift", result.canonicalExerciseKey)
    }

    @Test
    fun `remote below threshold is rejected`() = runBlocking {
        val result = pipeline(remoteSet(0.5f)).parse("bench 185 for 8")
        assertEquals(ParseSource.LOCAL, result.source)
    }

    @Test
    fun `remote asking for clarification is rejected`() = runBlocking {
        val result = pipeline(remoteSet(0.9f, needsClarification = true)).parse("bench 185 for 8")
        assertEquals(ParseSource.LOCAL, result.source)
        assertTrue(result.isComplete)
    }

    @Test
    fun `separator with nothing usable still reaches the endpoint`() = runBlocking {
        val result = pipeline(remoteSet(0.8f)).parse("bench next felt heavy")
        assertEquals(ParseSource.REMOTE, result.source)
        assertEquals(1, remoteCalls)
    }

    @Test
    fun `endpoint validation`() {
        assertTrue(RemoteVoiceParser.isValidEndpoint("https://example.com/parse"))
        assertTrue(RemoteVoiceParser.isValidEndpoint("http://10.0.2.2:8080/parse"))
        assertFalse(RemoteVoiceParser.isValidEndpoint("example.com/parse"))
        assertFalse(RemoteVoiceParser.isValidEndpoint("ftp://example.com"))
        assertFalse(RemoteVoiceParser.isValidEndpoint(""))
    }
}

package com.redline.app.ui.tempo

import com.redline.app.tempo.AudioInput
import com.redline.app.tempo.HeardCue
import com.redline.app.tempo.LiftKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceResultTest {
    private val input = AudioInput("Headset", AudioInput.Kind.WIRED)

    /** A clear "bottom" then "top" for each lift time, three seconds apart. */
    private fun setOf(seconds: List<Float>, kind: LiftKind, listening: Boolean = false): VoiceSet {
        val heard = seconds.flatMapIndexed { i, lift ->
            val bottom = (i * 3_000_000_000L) + 1_000_000_000L
            listOf(HeardCue(bottom, 0.95f), HeardCue(bottom + (lift * 1e9f).toLong(), 0.05f))
        }
        return voiceSetOf(input, listening, 0L, if (listening) null else 20_000_000_000L, heard, kind)
    }

    @Test
    fun savesTheAverageSlowdownProximityAndRpe() {
        // Speed loss 0, .1, .2, .3, .4 against a squat's stall loss of .5.
        val seconds = List(5) { 1f / (1f - 0.1f * it) }
        val result = voiceResultOf(setOf(seconds, LiftKind.LOWER))!!
        assertEquals(seconds.average().toFloat(), result.averageSeconds, 1e-3f)
        assertEquals(66.67f, result.slowdownPercent, 0.05f)
        assertEquals(80, result.proximityScore)
        assertEquals(9f, result.rpe, 0f)
    }

    @Test
    fun nothingToSaveBeforeThreeReps() {
        val voice = setOf(listOf(1f, 1.2f), LiftKind.OTHER)
        assertEquals(2, voice.reps.size)
        assertNull(voiceResultOf(voice))
    }

    @Test
    fun aBottomWithoutATopIsTheRepInTheAir() {
        val voice = voiceSetOf(
            input,
            listening = true,
            startedNs = 0L,
            endedNs = null,
            heard = listOf(HeardCue(1_000_000_000L, 0.95f), HeardCue(2_000_000_000L, 0.05f), HeardCue(4_000_000_000L, 0.95f)),
            kind = LiftKind.OTHER
        )
        assertEquals(1, voice.reps.size)
        assertNotNull(voice.openBottom)
        assertEquals(4_000_000_000L, voice.openBottom!!.timeNs)
    }
}

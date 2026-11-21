package com.redline.app.tempo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Synthetic words with the features the detector listens for: "top" as a burst of hiss (the
 * aspirated "t") then one voiced vowel, "bottom" as two voiced syllables with a dip between.
 */
class CueDetectorTest {
    private val rate = 16_000

    private class Track(seconds: Double, noise: Double, seed: Int) {
        val rate = 16_000
        val samples = DoubleArray((seconds * rate).toInt())
        private val random = Random(seed)

        init {
            for (i in samples.indices) samples[i] = gaussian() * noise
        }

        fun gaussian(): Double {
            var u = 0.0
            while (u == 0.0) u = random.nextDouble()
            return sqrt(-2.0 * ln(u)) * cos(2 * PI * random.nextDouble())
        }

        /** 10 ms ramps in and out, so the word starts where it says. */
        private fun envelope(i: Int, length: Int): Double {
            val ramp = rate / 100
            return min(1.0, min(i, length - 1 - i).toDouble() / ramp).coerceAtLeast(0.0)
        }

        /** A voiced sound: a 140 Hz buzz with falling harmonics, like a vowel. */
        fun voiced(start: Double, length: Double, amplitude: Double) {
            val from = (start * rate).toInt()
            val count = (length * rate).toInt()
            for (i in 0 until count) {
                val t = (from + i).toDouble() / rate
                var buzz = 0.0
                for (k in 1..8) buzz += sin(2 * PI * 140.0 * k * t) / k
                samples[from + i] += amplitude * envelope(i, count) * buzz
            }
        }

        fun hiss(start: Double, length: Double, amplitude: Double) {
            val from = (start * rate).toInt()
            val count = (length * rate).toInt()
            for (i in 0 until count) samples[from + i] += amplitude * envelope(i, count) * gaussian()
        }

        fun top(start: Double) {
            hiss(start, 0.07, 3000.0)
            voiced(start + 0.06, 0.2, 5000.0)
        }

        fun bottom(start: Double) {
            voiced(start, 0.16, 5000.0)
            voiced(start + 0.16, 0.08, 400.0)
            voiced(start + 0.24, 0.2, 4500.0)
        }

        fun pcm() = ShortArray(samples.size) { samples[it].coerceIn(-32768.0, 32767.0).toInt().toShort() }
    }

    private fun detect(track: Track): List<DetectedCue> {
        val detector = CueDetector(rate)
        val pcm = track.pcm()
        val found = mutableListOf<DetectedCue>()
        // In the 20 ms reads the recorder makes.
        pcm.toList().chunked(320).forEach { found += detector.feed(it.toShortArray()) }
        return found
    }

    @Test
    fun hissThenOneVowelIsTop() {
        val track = Track(2.0, 30.0, 1).apply { top(0.8) }
        val cue = detect(track).single()
        assertTrue("hiss ${cue.hissFrames}", cue.hissFrames >= 2)
        assertEquals(1, cue.syllables)
        assertTrue("p=${cue.bottomLikelihood}", cue.bottomLikelihood < 0.2f)
    }

    @Test
    fun twoVoicedSyllablesIsBottom() {
        val track = Track(2.0, 30.0, 2).apply { bottom(0.8) }
        val cue = detect(track).single()
        assertEquals(0, cue.hissFrames)
        assertEquals(2, cue.syllables)
        assertTrue("p=${cue.bottomLikelihood}", cue.bottomLikelihood > 0.8f)
    }

    @Test
    fun timesTheWordFromItsStart() {
        val track = Track(3.0, 30.0, 3).apply {
            top(0.8)
            bottom(2.0)
        }
        val cues = detect(track)
        assertEquals(2, cues.size)
        assertTrue(abs(cues[0].onsetSample - (0.8 * rate).toLong()) <= rate / 50)
        assertTrue(abs(cues[1].onsetSample - (2.0 * rate).toLong()) <= rate / 50)
    }

    @Test
    fun ignoresAClank() {
        val track = Track(2.0, 30.0, 4).apply { hiss(0.8, 0.04, 12000.0) }
        assertTrue(detect(track).isEmpty())
    }

    @Test
    fun ignoresTalkingThatGoesOn() {
        val track = Track(3.0, 30.0, 5).apply { voiced(0.6, 1.6, 4000.0) }
        assertTrue(detect(track).isEmpty())
    }

    @Test
    fun timesAWholeSetWithinAFewMilliseconds() {
        val lifts = listOf(0.9, 1.0, 1.15, 1.4, 1.8)
        val track = Track(16.0, 60.0, 6)
        var t = 0.8
        val truth = mutableListOf<Double>()
        for (lift in lifts) {
            track.bottom(t)
            truth += lift
            track.top(t + lift)
            t += lift + 1.5
        }
        val heard = detect(track).map { HeardCue(it.onsetSample * 1_000_000_000L / rate, it.bottomLikelihood) }
        val cues = VoiceTempoMath.label(heard)
        assertEquals(List(lifts.size) { listOf(Cue.BOTTOM, Cue.TOP) }.flatten(), cues.map { it.cue })
        val reps = VoiceTempoMath.reps(cues)
        assertEquals(lifts.size, reps.size)
        reps.zip(truth).forEach { (rep, lift) -> assertEquals(lift, rep.seconds.toDouble(), 0.03) }
    }
}

package com.redline.app.tempo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TempoAnalyzerTest {
    private val analyzer = TempoAnalyzer()

    @Test
    fun constantTempoScoresLowFailureProximity() {
        val samples = samplesWithPeaks(intervalSeconds = listOf(1.0f, 1.0f, 1.0f, 1.0f))

        val analysis = analyzer.analyze(samples)

        assertEquals(5, analysis.repCount)
        assertNotNull(analysis.failureProximityScore)
        assertTrue((analysis.failureProximityScore ?: 100) < 55)
    }

    @Test
    fun slowingTempoScoresHigherFailureProximity() {
        val samples = samplesWithPeaks(intervalSeconds = listOf(0.8f, 1.0f, 1.35f, 1.75f))

        val analysis = analyzer.analyze(samples)

        assertEquals(5, analysis.repCount)
        assertNotNull(analysis.degradationPercent)
        assertTrue((analysis.degradationPercent ?: 0f) > 35f)
        assertTrue((analysis.failureProximityScore ?: 0) >= 85)
    }

    @Test
    fun tooFewSamplesIsNotScored() {
        val analysis = analyzer.analyze(List(10) { TempoSample(it * 20_000_000L, 1f) })

        assertNull(analysis.failureProximityScore)
        assertEquals(0, analysis.repCount)
        assertEquals(0f, analysis.confidence)
        assertEquals("Not enough movement data", analysis.notes)
    }

    @Test
    fun flatSignalIsTooSubtleToScore() {
        val analysis = analyzer.analyze(List(200) { TempoSample(it * 20_000_000L, 0.1f) })

        assertNull(analysis.failureProximityScore)
        assertEquals(0, analysis.repCount)
        assertEquals("Movement was too subtle to score", analysis.notes)
    }

    @Test
    fun repsTooFarApartAreNotScored() {
        // Two clear bursts ten seconds apart: longer than any real rep.
        val peaks = listOf(1_000_000_000L, 11_000_000_000L)
        val samples = mutableListOf<TempoSample>()
        var t = 0L
        while (t <= 12_000_000_000L) {
            if (peaks.none { kotlin.math.abs(it - t) < 250_000_000L }) samples += TempoSample(t, 0.1f)
            t += 500_000_000L
        }
        peaks.forEach { peak ->
            samples += TempoSample(peak - 90_000_000L, 0.4f)
            samples += TempoSample(peak, 2.0f)
            samples += TempoSample(peak + 90_000_000L, 0.4f)
        }

        val analysis = analyzer.analyze(samples.sortedBy { it.timestampNs })

        assertNull(analysis.failureProximityScore)
        assertEquals(2, analysis.repCount)
        assertEquals("Rep timing was inconsistent", analysis.notes)
    }

    @Test
    fun moreCleanRepsRaiseConfidence() {
        val short = analyzer.analyze(samplesWithPeaks(listOf(1f, 1f)))
        val long = analyzer.analyze(samplesWithPeaks(listOf(1f, 1f, 1f, 1f, 1f, 1f)))

        assertTrue(long.confidence > short.confidence)
        assertTrue(long.confidence <= 1f)
    }

    private fun samplesWithPeaks(intervalSeconds: List<Float>): List<TempoSample> {
        val peakTimes = mutableListOf(0L)
        var currentNs = 0L
        intervalSeconds.forEach { seconds ->
            currentNs += (seconds * 1_000_000_000L).toLong()
            peakTimes += currentNs
        }

        val samples = mutableListOf<TempoSample>()
        for (time in peakTimes) {
            samples += TempoSample(time - 180_000_000L, 0.1f)
            samples += TempoSample(time - 90_000_000L, 0.4f)
            samples += TempoSample(time, 2.0f)
            samples += TempoSample(time + 90_000_000L, 0.4f)
            samples += TempoSample(time + 180_000_000L, 0.1f)
        }
        return samples.sortedBy { it.timestampNs }
    }
}

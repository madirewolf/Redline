package com.redline.app.tempo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

package com.redline.app.tempo

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class TempoSample(
    val timestampNs: Long,
    val magnitude: Float
)

data class TempoAnalysis(
    val repCount: Int,
    val averageRepSeconds: Float?,
    val degradationPercent: Float?,
    val failureProximityScore: Int?,
    val confidence: Float,
    val notes: String
)

class TempoAnalyzer {
    fun analyze(samples: List<TempoSample>): TempoAnalysis {
        if (samples.size < MIN_SAMPLES) {
            return TempoAnalysis(
                repCount = 0,
                averageRepSeconds = null,
                degradationPercent = null,
                failureProximityScore = null,
                confidence = 0f,
                notes = "Not enough movement data"
            )
        }

        val smoothed = smooth(samples)
        val magnitudes = smoothed.map { it.magnitude }
        val mean = magnitudes.average().toFloat()
        val variance = magnitudes.map { (it - mean).toDouble().pow(2) }.average().toFloat()
        val standardDeviation = sqrt(variance)
        val threshold = mean + max(0.18f, standardDeviation * 0.75f)
        val peaks = findPeaks(smoothed, threshold)

        if (peaks.size < 2) {
            return TempoAnalysis(
                repCount = peaks.size,
                averageRepSeconds = null,
                degradationPercent = null,
                failureProximityScore = null,
                confidence = 0.15f,
                notes = "Movement was too subtle to score"
            )
        }

        val intervals = peaks.zipWithNext { first, second ->
            (second.timestampNs - first.timestampNs) / 1_000_000_000f
        }.filter { it in 0.35f..8f }

        if (intervals.isEmpty()) {
            return TempoAnalysis(
                repCount = peaks.size,
                averageRepSeconds = null,
                degradationPercent = null,
                failureProximityScore = null,
                confidence = 0.2f,
                notes = "Rep timing was inconsistent"
            )
        }

        val average = intervals.average().toFloat()
        val first = intervals.take(max(1, intervals.size / 3)).average().toFloat()
        val last = intervals.takeLast(max(1, intervals.size / 3)).average().toFloat()
        val degradation = if (first > 0f) ((last - first) / first) * 100f else 0f
        val score = scoreFailureProximity(degradation, intervals.size)
        val confidence = min(1f, 0.25f + intervals.size * 0.12f)

        return TempoAnalysis(
            repCount = intervals.size + 1,
            averageRepSeconds = average,
            degradationPercent = degradation,
            failureProximityScore = score,
            confidence = confidence,
            notes = notesFor(degradation, score)
        )
    }

    private fun smooth(samples: List<TempoSample>): List<TempoSample> {
        return samples.mapIndexed { index, sample ->
            val window = samples.subList(max(0, index - 1), min(samples.size, index + 2))
            sample.copy(magnitude = window.map { it.magnitude }.average().toFloat())
        }
    }

    private fun findPeaks(samples: List<TempoSample>, threshold: Float): List<TempoSample> {
        val peaks = mutableListOf<TempoSample>()
        var lastPeakNs: Long? = null
        for (index in 1 until samples.lastIndex) {
            val prev = samples[index - 1]
            val current = samples[index]
            val next = samples[index + 1]
            val farEnough = lastPeakNs?.let { current.timestampNs - it >= MIN_PEAK_DISTANCE_NS } ?: true
            if (current.magnitude > threshold &&
                current.magnitude >= prev.magnitude &&
                current.magnitude >= next.magnitude &&
                farEnough
            ) {
                peaks += current
                lastPeakNs = current.timestampNs
            }
        }
        return peaks
    }

    private fun scoreFailureProximity(degradationPercent: Float, intervals: Int): Int {
        val base = when {
            degradationPercent >= 35f -> 92
            degradationPercent >= 22f -> 82
            degradationPercent >= 12f -> 68
            degradationPercent >= 5f -> 54
            else -> 34
        }
        val sampleBonus = min(8, intervals * 2)
        return (base + sampleBonus).coerceIn(0, 100)
    }

    private fun notesFor(degradationPercent: Float, score: Int): String {
        return when {
            score >= 85 -> "Tempo slowed hard near the end"
            score >= 70 -> "Tempo degraded progressively"
            score >= 55 && degradationPercent > 0f -> "Some fatigue showed up"
            else -> "Tempo stayed mostly constant"
        }
    }

    companion object {
        private const val MIN_SAMPLES = 20
        private const val MIN_PEAK_DISTANCE_NS = 450_000_000L
    }
}

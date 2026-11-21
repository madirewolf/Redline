package com.redline.app.tempo

import kotlin.math.max
import kotlin.math.min

enum class Cue { BOTTOM, TOP }

/** A cue word heard at [timeNs], before deciding which word it was. */
data class HeardCue(val timeNs: Long, val bottomLikelihood: Float)

/**
 * One spoken cue. [timeNs] is on the System.nanoTime clock, taken from the audio's own sample
 * count. [confidence] is the probability the label is right, given everything heard in the set.
 */
data class CueEvent(val cue: Cue, val timeNs: Long, val confidence: Float)

/**
 * How much of its starting rep speed a lift loses before the bar stalls. Mean concentric
 * velocity at failure is close to fixed for a given lift, so from a typical working-set
 * starting speed, presses stall at about 70% speed loss and squats and pulls nearer 50%.
 */
enum class LiftKind(val failureLoss: Float, val label: String) {
    PRESS(0.70f, "Press"),
    LOWER(0.50f, "Squat or pull"),
    OTHER(0.60f, "Other");

    companion object {
        fun of(canonicalKey: String?): LiftKind {
            val key = canonicalKey ?: return OTHER
            return when {
                LOWER_HINTS.any { key.contains(it) } -> LOWER
                PRESS_HINTS.any { key.contains(it) } -> PRESS
                else -> OTHER
            }
        }

        private val LOWER_HINTS = listOf("squat", "deadlift", "lunge", "leg_press", "hip_thrust", "clean", "row")
        private val PRESS_HINTS = listOf("press", "bench", "dip", "push")
    }
}

/** One lift: the "bottom" cue to the "top" cue after it. */
data class RepTime(val number: Int, val bottomNs: Long, val topNs: Long) {
    val seconds: Float get() = (topNs - bottomNs) / 1e9f
}

/**
 * Where a set stands, from how long each lift took.
 *
 * With the same range of motion every rep, mean lifting speed is proportional to 1 / time,
 * so the speed lost on rep i against the reference rep is L = 1 − T_ref / T_i. Speed loss
 * climbs close to linearly with reps done, so a least-squares line through the latest reps
 * says how many more fit before L reaches the lift's stall point: RIR = (L_stall − L_now) / slope,
 * and RPE = 10 − RIR.
 */
data class TempoEstimate(
    /** Fastest of the first two reps, in seconds: the speed the set started at. */
    val referenceSeconds: Float,
    /** The rep time at which the bar is expected to stall. */
    val stallSeconds: Float,
    /** Speed lost on each rep against the reference, 0 to 1. */
    val losses: List<Float>,
    val stallLoss: Float,
    /** The trend line, L = intercept + slope × rep index (0-based), once there are enough reps. */
    val trendIntercept: Float?,
    val lossPerRep: Float?,
    /** The trend's value at the last rep. */
    val currentLoss: Float?,
    val repsInReserve: Float?,
    val rpe: Float?
) {
    /** 0 when a rep is as fast as the reference, 1 at the stall point. Drives the colour. */
    fun proximity(loss: Float): Float = (loss / stallLoss).coerceIn(0f, 1f)

    /** The trend's predicted rep time at a (possibly fractional) rep index. */
    fun trendSeconds(index: Float): Float? {
        val a = trendIntercept ?: return null
        val b = lossPerRep ?: return null
        val loss = (a + b * index).coerceIn(0f, MAX_PLOTTED_LOSS)
        return referenceSeconds / (1f - loss)
    }

    private companion object {
        const val MAX_PLOTTED_LOSS = 0.95f
    }
}

object VoiceTempoMath {
    /** Lifts quicker than this are two cues run together; slower ones are a missed cue. */
    const val MIN_REP_SECONDS = 0.15f
    const val MAX_REP_SECONDS = 12f
    const val MIN_REPS_FOR_ESTIMATE = 3
    /** The trend follows the latest reps, since the slowdown steepens toward the end of a set. */
    const val TREND_WINDOW = 5
    const val MAX_REPS_IN_RESERVE = 6f
    /** A slope under this is no measurable slowdown, so there is nothing to extrapolate. */
    const val FLAT_SLOPE = 0.005f

    /** How likely the next cue is the other word. */
    const val ALTERNATION = 0.85f
    /** Sets start at the top and go down first, so the first cue is usually "bottom". */
    const val FIRST_IS_BOTTOM = 0.6f

    /**
     * Decides which word each cue was, using the whole set so far. The words form a two-state
     * hidden Markov chain that usually alternates; forward-backward gives each cue's probability
     * of being "bottom" from its own sound and from every other cue, so a later clear word can
     * correct an earlier unclear one, and a missed cue costs one rep rather than the rest.
     */
    fun label(heard: List<HeardCue>): List<CueEvent> {
        val n = heard.size
        if (n == 0) return emptyList()
        val sorted = heard.sortedBy { it.timeNs }
        // Index 0 is "bottom", 1 is "top". Each step is normalised to stay in range.
        val forward = Array(n) { DoubleArray(2) }
        val backward = Array(n) { DoubleArray(2) }
        fun emission(i: Int, state: Int): Double {
            val p = sorted[i].bottomLikelihood.toDouble().coerceIn(1e-3, 1 - 1e-3)
            return if (state == 0) p else 1 - p
        }
        fun transition(from: Int, to: Int): Double = if (from == to) 1.0 - ALTERNATION else ALTERNATION.toDouble()

        forward[0][0] = FIRST_IS_BOTTOM * emission(0, 0)
        forward[0][1] = (1 - FIRST_IS_BOTTOM) * emission(0, 1)
        normalise(forward[0])
        for (i in 1 until n) {
            for (to in 0..1) {
                forward[i][to] = emission(i, to) * (0..1).sumOf { from -> forward[i - 1][from] * transition(from, to) }
            }
            normalise(forward[i])
        }
        backward[n - 1][0] = 1.0
        backward[n - 1][1] = 1.0
        for (i in n - 2 downTo 0) {
            for (from in 0..1) {
                backward[i][from] = (0..1).sumOf { to -> transition(from, to) * emission(i + 1, to) * backward[i + 1][to] }
            }
            normalise(backward[i])
        }
        return sorted.mapIndexed { i, cue ->
            val bottom = forward[i][0] * backward[i][0]
            val top = forward[i][1] * backward[i][1]
            val pBottom = (bottom / (bottom + top)).toFloat()
            if (pBottom >= 0.5f) CueEvent(Cue.BOTTOM, cue.timeNs, pBottom) else CueEvent(Cue.TOP, cue.timeNs, 1f - pBottom)
        }
    }

    private fun normalise(p: DoubleArray) {
        val sum = p.sum()
        if (sum > 0) for (i in p.indices) p[i] /= sum
    }

    /** Pairs each "bottom" with the next "top". A bottom with no top after it is dropped. */
    fun reps(cues: List<CueEvent>): List<RepTime> {
        val reps = mutableListOf<RepTime>()
        var bottom: CueEvent? = null
        for (cue in cues.sortedBy { it.timeNs }) {
            when (cue.cue) {
                Cue.BOTTOM -> bottom = cue
                Cue.TOP -> {
                    val start = bottom ?: continue
                    val seconds = (cue.timeNs - start.timeNs) / 1e9f
                    if (seconds in MIN_REP_SECONDS..MAX_REP_SECONDS) {
                        reps += RepTime(reps.size + 1, start.timeNs, cue.timeNs)
                    }
                    bottom = null
                }
            }
        }
        return reps
    }

    /** The "bottom" of a rep still being lifted, if the latest cue is one. */
    fun openBottom(cues: List<CueEvent>): CueEvent? = cues.maxByOrNull { it.timeNs }?.takeIf { it.cue == Cue.BOTTOM }

    fun lossOf(seconds: Float, referenceSeconds: Float): Float =
        if (seconds <= 0f) 0f else max(0f, 1f - referenceSeconds / seconds)

    fun estimate(seconds: List<Float>, kind: LiftKind): TempoEstimate? {
        if (seconds.isEmpty()) return null
        val reference = seconds.take(2).min()
        val losses = seconds.map { lossOf(it, reference) }
        val stallSeconds = reference / (1f - kind.failureLoss)
        if (seconds.size < MIN_REPS_FOR_ESTIMATE) {
            return TempoEstimate(reference, stallSeconds, losses, kind.failureLoss, null, null, null, null, null)
        }

        val window = losses.indices.toList().takeLast(TREND_WINDOW)
        val (intercept, slope) = leastSquares(window.map { it.toFloat() }, window.map { losses[it] })
        val current = max(0f, intercept + slope * losses.lastIndex)
        val reserve = when {
            current >= kind.failureLoss -> 0f
            slope <= FLAT_SLOPE -> MAX_REPS_IN_RESERVE
            else -> min(MAX_REPS_IN_RESERVE, (kind.failureLoss - current) / slope)
        }
        val rpe = (10f - reserve).coerceIn(MIN_RPE, 10f)
        return TempoEstimate(reference, stallSeconds, losses, kind.failureLoss, intercept, slope, current, reserve, rpe)
    }

    /** Ordinary least squares: the (intercept, slope) of the line closest to the points. */
    fun leastSquares(x: List<Float>, y: List<Float>): Pair<Float, Float> {
        require(x.size == y.size && x.isNotEmpty())
        val meanX = x.average()
        val meanY = y.average()
        var sxy = 0.0
        var sxx = 0.0
        for (i in x.indices) {
            val dx = x[i] - meanX
            sxy += dx * (y[i] - meanY)
            sxx += dx * dx
        }
        val slope = if (sxx == 0.0) 0.0 else sxy / sxx
        return (meanY - slope * meanX).toFloat() to slope.toFloat()
    }

    /** RPE to the nearest half point, the way it's usually written. */
    fun roundRpe(rpe: Float): Float = Math.round(rpe * 2f) / 2f

    const val MIN_RPE = 5f
}

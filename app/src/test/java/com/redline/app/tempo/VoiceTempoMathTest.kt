package com.redline.app.tempo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTempoMathTest {
    private fun cue(cue: Cue, seconds: Double) = CueEvent(cue, (seconds * 1e9).toLong(), 1f)

    /** Rep times whose speed loss against 1 s climbs by [step] per rep, starting at zero. */
    private fun linearLossSeconds(reps: Int, step: Float) = List(reps) { 1f / (1f - step * it) }

    @Test
    fun pairsEachBottomWithTheNextTop() {
        val reps = VoiceTempoMath.reps(
            listOf(
                cue(Cue.TOP, 0.5), // a top before any bottom is ignored
                cue(Cue.BOTTOM, 1.0),
                cue(Cue.TOP, 2.2),
                cue(Cue.BOTTOM, 4.0),
                cue(Cue.BOTTOM, 4.5), // a repeated bottom restarts the rep
                cue(Cue.TOP, 6.0),
                cue(Cue.BOTTOM, 8.0) // still being lifted
            )
        )
        assertEquals(listOf(1.2f, 1.5f), reps.map { it.seconds })
        assertEquals(listOf(1, 2), reps.map { it.number })
    }

    @Test
    fun dropsRepsTooQuickOrTooSlowToBeReal() {
        val reps = VoiceTempoMath.reps(
            listOf(cue(Cue.BOTTOM, 0.0), cue(Cue.TOP, 0.1), cue(Cue.BOTTOM, 1.0), cue(Cue.TOP, 20.0))
        )
        assertTrue(reps.isEmpty())
    }

    @Test
    fun openBottomIsTheLatestCueWhenItIsABottom() {
        assertEquals(3_000_000_000L, VoiceTempoMath.openBottom(listOf(cue(Cue.TOP, 2.0), cue(Cue.BOTTOM, 3.0)))?.timeNs)
        assertNull(VoiceTempoMath.openBottom(listOf(cue(Cue.BOTTOM, 2.0), cue(Cue.TOP, 3.0))))
    }

    @Test
    fun speedLossIsOneMinusTheTimeRatio() {
        assertEquals(0.5f, VoiceTempoMath.lossOf(2f, 1f), 1e-6f)
        assertEquals(0f, VoiceTempoMath.lossOf(0.8f, 1f), 1e-6f)
    }

    @Test
    fun leastSquaresRecoversAnExactLine() {
        val x = listOf(0f, 1f, 2f, 3f)
        val (intercept, slope) = VoiceTempoMath.leastSquares(x, x.map { 0.1f + 0.05f * it })
        assertEquals(0.1f, intercept, 1e-5f)
        assertEquals(0.05f, slope, 1e-5f)
    }

    @Test
    fun linearSlowdownExtrapolatesToTheStallPoint() {
        // Loss 0, .1, .2, .3, .4; squats stall at .5, so one rep is left: RPE 9.
        val estimate = VoiceTempoMath.estimate(linearLossSeconds(5, 0.1f), LiftKind.LOWER)!!
        assertEquals(1f, estimate.referenceSeconds, 1e-5f)
        assertEquals(2f, estimate.stallSeconds, 1e-5f)
        assertEquals(0.1f, estimate.lossPerRep!!, 1e-4f)
        assertEquals(0.4f, estimate.currentLoss!!, 1e-4f)
        assertEquals(1f, estimate.repsInReserve!!, 1e-3f)
        assertEquals(9f, estimate.rpe!!, 1e-3f)
        // The trend reaches the stall time exactly one rep after the last.
        assertEquals(2f, estimate.trendSeconds(5f)!!, 1e-3f)
    }

    @Test
    fun pressesHaveMoreRoomBeforeTheyStall() {
        // Same slowdown, but presses stall at .7: three reps left, RPE 7.
        val estimate = VoiceTempoMath.estimate(linearLossSeconds(5, 0.1f), LiftKind.PRESS)!!
        assertEquals(3f, estimate.repsInReserve!!, 1e-3f)
        assertEquals(7f, estimate.rpe!!, 1e-3f)
    }

    @Test
    fun steadyRepsReadAsEasy() {
        val estimate = VoiceTempoMath.estimate(List(6) { 1.2f }, LiftKind.OTHER)!!
        assertEquals(VoiceTempoMath.MAX_REPS_IN_RESERVE, estimate.repsInReserve!!, 1e-6f)
        assertEquals(VoiceTempoMath.MIN_RPE, estimate.rpe!!, 1e-6f)
    }

    @Test
    fun aGrindPastTheStallPointIsRpeTen() {
        val estimate = VoiceTempoMath.estimate(listOf(1f, 1f, 1.3f, 1.8f, 2.6f), LiftKind.LOWER)!!
        assertEquals(0f, estimate.repsInReserve!!, 1e-6f)
        assertEquals(10f, estimate.rpe!!, 1e-6f)
    }

    @Test
    fun usesTheQuickerOfTheFirstTwoRepsAsTheReference() {
        val estimate = VoiceTempoMath.estimate(listOf(1.3f, 1.0f), LiftKind.OTHER)!!
        assertEquals(1f, estimate.referenceSeconds, 1e-6f)
        assertEquals(listOf(0.23077f, 0f).map { it }, estimate.losses.map { Math.round(it * 1e5f) / 1e5f })
        assertNull("Two reps aren't enough for a trend", estimate.rpe)
        assertEquals(2.5f, estimate.stallSeconds, 1e-5f)
    }

    @Test
    fun followsOnlyTheLatestRepsForTheTrend() {
        // A slow first stretch then a steep finish: the fit should follow the last five.
        val seconds = listOf(1f, 1f, 1f, 1f, 1f) + linearLossSeconds(5, 0.08f).drop(1)
        val estimate = VoiceTempoMath.estimate(seconds, LiftKind.OTHER)!!
        assertTrue(estimate.lossPerRep!! > 0.06f)
    }

    @Test
    fun roundsRpeToHalfPoints() {
        assertEquals(8.5f, VoiceTempoMath.roundRpe(8.3f), 0f)
        assertEquals(8f, VoiceTempoMath.roundRpe(8.2f), 0f)
    }

    @Test
    fun liftKindComesFromTheExerciseKey() {
        assertEquals(LiftKind.PRESS, LiftKind.of("bench_press"))
        assertEquals(LiftKind.PRESS, LiftKind.of("overhead_press"))
        assertEquals(LiftKind.LOWER, LiftKind.of("back_squat"))
        assertEquals(LiftKind.LOWER, LiftKind.of("leg_press"))
        assertEquals(LiftKind.LOWER, LiftKind.of("barbell_row"))
        assertEquals(LiftKind.OTHER, LiftKind.of("bicep_curl"))
        assertEquals(LiftKind.OTHER, LiftKind.of(null))
    }

    private fun heard(vararg likelihoods: Float) = likelihoods.mapIndexed { i, p -> HeardCue(i * 1_000_000_000L, p) }

    @Test
    fun labelsClearCuesAsHeard() {
        val labels = VoiceTempoMath.label(heard(0.95f, 0.05f, 0.9f, 0.1f))
        assertEquals(listOf(Cue.BOTTOM, Cue.TOP, Cue.BOTTOM, Cue.TOP), labels.map { it.cue })
        assertTrue(labels.all { it.confidence > 0.9f })
    }

    @Test
    fun alternationSettlesAnUnclearCue() {
        val labels = VoiceTempoMath.label(heard(0.9f, 0.1f, 0.5f, 0.1f, 0.9f, 0.1f))
        assertEquals(Cue.BOTTOM, labels[2].cue)
    }

    @Test
    fun laterCuesCorrectAnEarlierMisheardOne() {
        // The fourth word sounded a little like "bottom", but sits between two clear bottoms.
        val labels = VoiceTempoMath.label(heard(0.9f, 0.1f, 0.9f, 0.6f, 0.9f, 0.1f))
        assertEquals(listOf(Cue.BOTTOM, Cue.TOP, Cue.BOTTOM, Cue.TOP, Cue.BOTTOM, Cue.TOP), labels.map { it.cue })
        assertTrue(labels[3].confidence < labels[2].confidence)
    }

    @Test
    fun aMissedCueCostsOneRepNotTheRest() {
        // B T B [missed T] B T: two clear bottoms in a row stay bottoms.
        val heard = listOf(0.95f, 0.05f, 0.95f, 0.95f, 0.05f).mapIndexed { i, p -> HeardCue(i * 2_000_000_000L, p) }
        val labels = VoiceTempoMath.label(heard)
        assertEquals(listOf(Cue.BOTTOM, Cue.TOP, Cue.BOTTOM, Cue.BOTTOM, Cue.TOP), labels.map { it.cue })
        assertEquals(2, VoiceTempoMath.reps(labels).size)
    }

    @Test
    fun labelsSortByTime() {
        val labels = VoiceTempoMath.label(listOf(HeardCue(2_000L, 0.1f), HeardCue(1_000L, 0.9f)))
        assertEquals(listOf(1_000L, 2_000L), labels.map { it.timeNs })
        assertNotNull(labels.firstOrNull())
        assertTrue(VoiceTempoMath.label(emptyList()).isEmpty())
    }
}

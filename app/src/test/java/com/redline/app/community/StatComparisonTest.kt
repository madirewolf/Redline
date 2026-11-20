package com.redline.app.community

import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class StatComparisonTest {

    private val zone: ZoneId = ZoneOffset.UTC

    // A Friday; its week starts on Monday the 16th.
    private val today = LocalDate.of(2026, 11, 20)

    private fun at(date: LocalDate, hour: Int = 18): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun finished(id: Long, date: LocalDate) =
        WorkoutSession(id = id, startTime = at(date), endTime = at(date, 19))

    private fun set(
        name: String,
        key: String?,
        weight: Float?,
        reps: Int?,
        unit: String = "lbs",
        timestamp: Long = 0L
    ) = ExerciseSet(
        sessionId = 1,
        exerciseName = name,
        canonicalExerciseKey = key,
        weight = weight,
        unit = unit,
        reps = reps,
        timestamp = timestamp
    )

    /* Bars */

    @Test
    fun `zero against zero draws no bars`() {
        assertEquals(0f to 0f, barFractions(0.0, 0.0))
    }

    @Test
    fun `the leader fills the track and the other side is a share of it`() {
        assertEquals(0.5f to 1f, barFractions(50.0, 100.0))
        assertEquals(1f to 0.25f, barFractions(400.0, 100.0))
        assertEquals(1f to 1f, barFractions(7.0, 7.0))
    }

    @Test
    fun `a negative value draws as empty`() {
        assertEquals(0f to 1f, barFractions(-5.0, 10.0))
    }

    /* Who leads */

    @Test
    fun `leader follows the bigger number`() {
        assertEquals(Leader.YOU, leaderOf(3.0, 2.0))
        assertEquals(Leader.THEM, leaderOf(2.0, 3.0))
        assertEquals(Leader.TIED, leaderOf(2.0, 2.0))
    }

    @Test
    fun `stats compare the four numbers in a fixed order`() {
        val mine = AthleteStats(workoutsThisWeek = 3, workoutsThisMonth = 10, totalVolumeLbs = 5_000.0, streakWeeks = 4)
        val theirs = AthleteStats(workoutsThisWeek = 2, workoutsThisMonth = 12, totalVolumeLbs = 5_000.0, streakWeeks = 9)

        val rows = compareStats(mine, theirs)

        assertEquals(
            listOf("Workouts this week", "Workouts this month", "Week streak", "Total volume"),
            rows.map { it.label }
        )
        assertEquals(listOf(StatKind.COUNT, StatKind.COUNT, StatKind.WEEKS, StatKind.VOLUME), rows.map { it.kind })
        assertEquals(listOf(Leader.YOU, Leader.THEM, Leader.THEM, Leader.TIED), rows.map { it.leader })
    }

    @Test
    fun `lifts both people have come first, then theirs, then yours`() {
        val mine = listOf(
            BestLift("bench_press", "Bench Press", 200f),
            BestLift("back_squat", "Back Squat", 300f),
            BestLift("curl", "Curl", 50f)
        )
        val theirs = listOf(
            BestLift("bench_press", "Bench Press", 250f),
            BestLift("deadlift", "Deadlift", 400f),
            BestLift("back_squat", "Back Squat", 280f)
        )

        val rows = compareLifts(mine, theirs)

        assertEquals(listOf("back_squat", "bench_press", "deadlift", "curl"), rows.map { it.key })
        assertEquals(LiftComparison("back_squat", "Back Squat", 300f, 280f), rows[0])
        assertEquals(LiftComparison("deadlift", "Deadlift", null, 400f), rows[2])
        assertEquals(LiftComparison("curl", "Curl", 50f, null), rows[3])
    }

    @Test
    fun `lift rows stop at the limit`() {
        val mine = (1..10).map { BestLift("lift$it", "Lift $it", it * 10f) }
        val theirs = (1..10).map { BestLift("lift$it", "Lift $it", it * 11f) }

        val rows = compareLifts(mine, theirs, limit = 3)

        assertEquals(listOf("lift10", "lift9", "lift8"), rows.map { it.key })
    }

    @Test
    fun `a lift only one person has has no leader`() {
        assertNull(LiftComparison("deadlift", "Deadlift", null, 400f).leader)
        assertNull(LiftComparison("curl", "Curl", 50f, null).leader)
        assertEquals(Leader.YOU, LiftComparison("bench_press", "Bench Press", 260f, 250f).leader)
    }

    @Test
    fun `head to head counts stats and shared lifts only`() {
        val stats = listOf(
            StatComparison("a", StatKind.COUNT, 3.0, 1.0),
            StatComparison("b", StatKind.COUNT, 5.0, 2.0),
            StatComparison("c", StatKind.WEEKS, 1.0, 4.0),
            StatComparison("d", StatKind.VOLUME, 10.0, 10.0)
        )
        val lifts = listOf(
            LiftComparison("bench_press", "Bench Press", 200f, 250f),
            LiftComparison("deadlift", "Deadlift", null, 400f),
            LiftComparison("curl", "Curl", 50f, null)
        )

        val score = headToHead(stats, lifts)

        assertEquals(HeadToHead(youLead = 2, theyLead = 2, tied = 1), score)
        assertEquals(5, score.total)
    }

    /* Your own numbers */

    @Test
    fun `only finished workouts count toward the week and the month`() {
        val sessions = listOf(
            finished(1, LocalDate.of(2026, 11, 17)),
            WorkoutSession(id = 2, startTime = at(LocalDate.of(2026, 11, 18)), endTime = null),
            finished(3, LocalDate.of(2026, 11, 10)),
            finished(4, LocalDate.of(2026, 11, 3)),
            finished(5, LocalDate.of(2026, 10, 20))
        )

        val stats = athleteStatsFrom(sessions, emptyList(), today, zone)

        assertEquals(1, stats.workoutsThisWeek)
        assertEquals(3, stats.workoutsThisMonth)
    }

    @Test
    fun `the streak runs back until a week with no workout`() {
        val sessions = listOf(
            finished(1, LocalDate.of(2026, 11, 17)),
            finished(2, LocalDate.of(2026, 11, 16)),
            finished(3, LocalDate.of(2026, 11, 10)),
            finished(4, LocalDate.of(2026, 11, 3)),
            // The week of Oct 26 is empty, so this one doesn't count.
            finished(5, LocalDate.of(2026, 10, 20))
        )

        assertEquals(3, athleteStatsFrom(sessions, emptyList(), today, zone).streakWeeks)
    }

    @Test
    fun `an empty week so far doesn't break the streak`() {
        val sessions = listOf(
            finished(1, LocalDate.of(2026, 11, 12)),
            finished(2, LocalDate.of(2026, 11, 4))
        )

        assertEquals(2, athleteStatsFrom(sessions, emptyList(), today, zone).streakWeeks)
    }

    @Test
    fun `missing last week as well ends the streak`() {
        val sessions = listOf(finished(1, LocalDate.of(2026, 11, 4)))

        assertEquals(0, athleteStatsFrom(sessions, emptyList(), today, zone).streakWeeks)
    }

    @Test
    fun `no workouts at all is all zeros`() {
        assertEquals(AthleteStats(), athleteStatsFrom(emptyList(), emptyList(), today, zone))
    }

    @Test
    fun `volume and best lifts are in pounds whatever the set was logged in`() {
        val sets = listOf(
            set("Bench Press", "bench_press", 100f, 5, unit = "kg"),
            set("Bench Press", "bench_press", 200f, 3),
            set("Cable Fly", null, 40f, 12, timestamp = 1),
            set("cable fly", null, 50f, 10, timestamp = 2),
            set("Pull Ups", "pull_up", null, 8),
            set("Back Squat", "back_squat", 0f, 5)
        )

        val stats = athleteStatsFrom(emptyList(), sets, today, zone)

        // 100 kg is 220.462 lbs.
        assertEquals(220.462 * 5 + 200 * 3 + 40 * 12 + 50 * 10, stats.totalVolumeLbs, 0.01)
        assertEquals(listOf("bench_press", "cable fly"), stats.bestLifts.map { it.key })
        assertEquals("Bench Press", stats.bestLifts[0].name)
        assertEquals(220.462f, stats.bestLifts[0].weightLbs, 0.01f)
        // Not in the catalog, so it takes the name from the latest set.
        assertEquals("cable fly", stats.bestLifts[1].name)
        assertEquals(50f, stats.bestLifts[1].weightLbs, 0f)
    }

    @Test
    fun `pounds and kilograms convert both ways`() {
        assertEquals(220.462, toLbs(100f, "kg"), 1e-6)
        assertEquals(220.462, toLbs(100f, "KG"), 1e-6)
        assertEquals(135.0, toLbs(135f, "lbs"), 1e-6)
        assertEquals(100.0, fromLbs(220.462, "kg"), 1e-6)
        assertEquals(135.0, fromLbs(135.0, "lbs"), 1e-6)
    }
}

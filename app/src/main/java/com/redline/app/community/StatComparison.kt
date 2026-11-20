package com.redline.app.community

import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.WorkoutSession
import com.redline.app.voice.ExerciseCatalog
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private const val LBS_PER_KG = 2.20462

/** A weight in pounds, whichever unit it was logged in. */
fun toLbs(weight: Float, unit: String): Double =
    if (unit.equals("kg", ignoreCase = true)) weight * LBS_PER_KG else weight.toDouble()

/** Pounds shown in the unit you train in. */
fun fromLbs(lbs: Double, unit: String): Double =
    if (unit.equals("kg", ignoreCase = true)) lbs / LBS_PER_KG else lbs

/**
 * Your numbers, worked out the same way a friend's are: finished workouts
 * this week (from Monday) and this month, a streak of weeks with at least one
 * workout, all-time volume, and the heaviest weight on each lift.
 */
fun athleteStatsFrom(
    sessions: List<WorkoutSession>,
    sets: List<ExerciseSet>,
    today: LocalDate,
    zone: ZoneId
): AthleteStats {
    val days = sessions
        .filter { it.endTime != null }
        .map { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
    val weekOf = { date: LocalDate -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val thisWeek = weekOf(today)
    val activeWeeks = days.map(weekOf).toSet()

    // Same rule as Home: a week with no workout yet doesn't break the streak.
    var week = if (thisWeek in activeWeeks) thisWeek else thisWeek.minusWeeks(1)
    var streak = 0
    while (week in activeWeeks) {
        streak++
        week = week.minusWeeks(1)
    }

    val weighted = sets.filter { (it.weight ?: 0f) > 0f }
    val bestLifts = weighted
        .groupBy { ExerciseCatalog.groupingKey(it.canonicalExerciseKey, it.exerciseName) }
        .map { (key, liftSets) ->
            BestLift(
                key = key,
                name = ExerciseCatalog.displayNameForKey(key) ?: liftSets.maxBy { it.timestamp }.exerciseName,
                weightLbs = liftSets.maxOf { toLbs(it.weight ?: 0f, it.unit) }.toFloat()
            )
        }
        .sortedByDescending { it.weightLbs }

    return AthleteStats(
        workoutsThisWeek = days.count { weekOf(it) == thisWeek },
        workoutsThisMonth = days.count { YearMonth.from(it) == YearMonth.from(today) },
        totalVolumeLbs = weighted.sumOf { toLbs(it.weight ?: 0f, it.unit) * (it.reps ?: 0) },
        streakWeeks = streak,
        bestLifts = bestLifts
    )
}

enum class Leader { YOU, THEM, TIED }

enum class StatKind { COUNT, WEEKS, VOLUME }

data class StatComparison(
    val label: String,
    val kind: StatKind,
    val mine: Double,
    val theirs: Double
) {
    val leader: Leader get() = leaderOf(mine, theirs)
}

/** One lift side by side. A side is null when that person hasn't logged the lift. */
data class LiftComparison(
    val key: String,
    val name: String,
    val mineLbs: Float?,
    val theirsLbs: Float?
) {
    val leader: Leader?
        get() = if (mineLbs == null || theirsLbs == null) null
        else leaderOf(mineLbs.toDouble(), theirsLbs.toDouble())
}

data class HeadToHead(val youLead: Int, val theyLead: Int, val tied: Int) {
    val total: Int get() = youLead + theyLead + tied
}

fun leaderOf(mine: Double, theirs: Double): Leader = when {
    mine > theirs -> Leader.YOU
    theirs > mine -> Leader.THEM
    else -> Leader.TIED
}

fun compareStats(mine: AthleteStats, theirs: AthleteStats): List<StatComparison> = listOf(
    StatComparison("Workouts this week", StatKind.COUNT, mine.workoutsThisWeek.toDouble(), theirs.workoutsThisWeek.toDouble()),
    StatComparison("Workouts this month", StatKind.COUNT, mine.workoutsThisMonth.toDouble(), theirs.workoutsThisMonth.toDouble()),
    StatComparison("Week streak", StatKind.WEEKS, mine.streakWeeks.toDouble(), theirs.streakWeeks.toDouble()),
    StatComparison("Total volume", StatKind.VOLUME, mine.totalVolumeLbs, theirs.totalVolumeLbs)
)

/**
 * Lifts you both have first, heaviest first; then theirs you haven't logged;
 * then yours they haven't. At most [limit] rows.
 */
fun compareLifts(mine: List<BestLift>, theirs: List<BestLift>, limit: Int = 6): List<LiftComparison> {
    val myLifts = mine.associateBy { it.key }
    val theirLifts = theirs.associateBy { it.key }
    val rows = (theirLifts.keys + myLifts.keys).map { key ->
        val m = myLifts[key]
        val t = theirLifts[key]
        LiftComparison(key, (t ?: m)!!.name, m?.weightLbs, t?.weightLbs)
    }
    return rows
        .sortedWith(
            compareBy<LiftComparison> {
                when {
                    it.mineLbs != null && it.theirsLbs != null -> 0
                    it.theirsLbs != null -> 1
                    else -> 2
                }
            }.thenByDescending { maxOf(it.mineLbs ?: 0f, it.theirsLbs ?: 0f) }
        )
        .take(limit)
}

/** Who's ahead on how many rows. Lifts only one of you has don't count. */
fun headToHead(stats: List<StatComparison>, lifts: List<LiftComparison>): HeadToHead {
    val leaders = stats.map { it.leader } + lifts.mapNotNull { it.leader }
    return HeadToHead(
        youLead = leaders.count { it == Leader.YOU },
        theyLead = leaders.count { it == Leader.THEM },
        tied = leaders.count { it == Leader.TIED }
    )
}

/**
 * How long to draw each bar, as a share of the longer one. The leader fills
 * the track; zero against zero draws nothing.
 */
fun barFractions(mine: Double, theirs: Double): Pair<Float, Float> {
    val top = maxOf(mine, theirs)
    if (top <= 0.0) return 0f to 0f
    return (mine.coerceAtLeast(0.0) / top).toFloat() to (theirs.coerceAtLeast(0.0) / top).toFloat()
}

package com.redline.app.data.sample

import com.redline.app.data.local.entity.CalorieEntry
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.MotivationalVideo
import com.redline.app.data.local.entity.PrTrack
import com.redline.app.data.local.entity.WorkoutSession
import com.redline.app.voice.ExerciseCatalog
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * How sample rows are told apart from real ones, without a schema change.
 * Workouts carry [MARKER] at the start of the session notes (never shown in
 * the app), their sets and meals at the start of rawVoiceInput (never shown
 * either). Videos and PR tracks show their prefix in the title, and their
 * link is a search page, so a row needs both to count as sample.
 */
object SampleData {
    const val MARKER = "[sample]"
    const val TITLE_PREFIX = "Placeholder · "
    const val VIDEO_URL_PREFIX = "https://www.youtube.com/results?search_query="
    const val TRACK_URI_PREFIX = "https://open.spotify.com/search/"

    /** Days of history, ending today. */
    const val DAYS = 30
}

enum class SplitDay(val label: String) {
    Push("Push day"),
    Pull("Pull day"),
    Legs("Leg day")
}

data class PlannedSet(
    val exerciseKey: String,
    val exerciseName: String,
    /** Null for a bodyweight set. */
    val weight: Float?,
    val reps: Int,
    val rpe: Float?,
    val notes: String?,
    val tempoAverageRepSeconds: Float?,
    val tempoDegradationPercent: Float?,
    val failureProximityScore: Int?,
    /** What the set would have sounded like, starting with [SampleData.MARKER]. */
    val rawVoiceInput: String,
    val time: LocalDateTime
) {
    fun toEntity(sessionId: Long, zone: ZoneId) = ExerciseSet(
        sessionId = sessionId,
        exerciseName = exerciseName,
        canonicalExerciseKey = exerciseKey,
        weight = weight,
        unit = "lbs",
        reps = reps,
        rpe = rpe,
        notes = notes,
        tempoAverageRepSeconds = tempoAverageRepSeconds,
        tempoDegradationPercent = tempoDegradationPercent,
        failureProximityScore = failureProximityScore,
        rawVoiceInput = rawVoiceInput,
        timestamp = time.atZone(zone).toInstant().toEpochMilli()
    )
}

data class PlannedSession(
    val day: SplitDay,
    val isDeload: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
    /** Starts with [SampleData.MARKER]. */
    val notes: String,
    val sets: List<PlannedSet>
) {
    fun toEntity(zone: ZoneId) = WorkoutSession(
        startTime = start.atZone(zone).toInstant().toEpochMilli(),
        endTime = end.atZone(zone).toInstant().toEpochMilli(),
        notes = notes
    )
}

data class PlannedMeal(
    val foodName: String,
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val servingSize: Float,
    val unit: String,
    val time: LocalDateTime,
    /** Starts with [SampleData.MARKER]. */
    val rawVoiceInput: String
) {
    fun toEntity(zone: ZoneId) = CalorieEntry(
        foodName = foodName,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        servingSize = servingSize,
        unit = unit,
        timestamp = time.atZone(zone).toInstant().toEpochMilli(),
        rawVoiceInput = rawVoiceInput
    )
}

/** A YouTube search page, not a made-up video id that may not exist. */
data class PlannedVideo(val title: String, val url: String, val addedAt: LocalDateTime) {
    fun toEntity(zone: ZoneId) = MotivationalVideo(
        title = title,
        url = url,
        type = "youtube",
        addedAt = addedAt.atZone(zone).toInstant().toEpochMilli()
    )
}

/** A Spotify search page for the song, for the same reason. */
data class PlannedTrack(val label: String, val uri: String, val createdAt: LocalDateTime) {
    fun toEntity(zone: ZoneId, isDefault: Boolean) = PrTrack(
        label = label,
        trackUri = uri,
        dropTimestampMs = 0,
        isDefault = isDefault,
        createdAt = createdAt.atZone(zone).toInstant().toEpochMilli()
    )
}

data class SamplePlan(
    val sessions: List<PlannedSession>,
    val meals: List<PlannedMeal>,
    val videos: List<PlannedVideo>,
    val tracks: List<PlannedTrack>
) {
    val setCount: Int get() = sessions.sumOf { it.sets.size }
}

/**
 * Builds a month of believable training without touching the database, so it
 * can be tested. Four sessions a week on a push / pull / legs rotation, ending
 * today; loads creep up, a deload week dips in the middle, and the lifts are
 * shaped so the Progress screen shows every status: most climb, overhead press
 * stalls for its last three sessions, and barbell row slips on its last.
 *
 * Deterministic: the same day and time always give the same plan.
 */
object SamplePlanner {

    /** Training days repeat weekly, counted back from the last day: 0, 2, 4 and 5 days before. */
    private val TRAINING_OFFSETS = setOf(0, 2, 4, 5)

    /** One planned session is missed, the way real months go. */
    private const val MISSED_DAYS_AGO = 18

    /** Sessions this many days back are the deload week. */
    private val DELOAD_DAYS_AGO = 14..20

    private const val DELOAD_FACTOR = 0.85f
    private const val SEED = 20_241_120L

    /** Earliest a session may start on the morning it's squeezed in before "now". */
    private val EARLIEST_START = LocalTime.of(6, 0)

    /** A session shown as today's ends at least this long before now. */
    private const val FINISHED_MINUTES_AGO = 10L

    fun build(today: LocalDate, now: LocalTime = LocalTime.of(23, 59)): SamplePlan {
        // Today's session needs to have finished already. If it can't have
        // (just after midnight), the month ends yesterday instead.
        val sessions = sessions(lastDay = today, today = today, now = now)
            ?: sessions(lastDay = today.minusDays(1), today = today, now = now)!!
        return SamplePlan(
            sessions = sessions,
            meals = meals(today, now),
            videos = videos(today),
            tracks = tracks(today)
        )
    }

    // ---- Workouts ----

    private enum class Shape {
        /** Adds load (or reps) every session. */
        Climb,
        /** Climbs, then repeats the same top set for its last three sessions. */
        Stall,
        /** Climbs, then drops below the session before on its last. */
        SlipLast
    }

    private class Lift(
        val key: String,
        val sets: Int,
        /** One number for straight sets; a range for double progression (reps first, then load). */
        val reps: IntRange,
        /** First working weight in lbs, or null for bodyweight (then [reps] grows instead). */
        val start: Float?,
        val increment: Float = 5f,
        /** Sessions per increment, for straight-set and bodyweight lifts. */
        val stepEvery: Int = 1,
        val shape: Shape = Shape.Climb,
        /** Main lifts log RPE on every set and sometimes a tempo reading. */
        val main: Boolean = false,
        val restSeconds: Int = 90,
        val spoken: String
    ) {
        val name: String = ExerciseCatalog.displayNameForKey(key)
            ?: error("$key is not in the exercise catalog")
    }

    private val bench = Lift("bench_press", 4, 5..5, 165f, main = true, restSeconds = 180, spoken = "bench press")
    private val ohp = Lift("overhead_press", 3, 5..5, 100f, shape = Shape.Stall, main = true, restSeconds = 150, spoken = "overhead press")
    private val inclineDb = Lift("incline_dumbbell_press", 3, 8..10, 55f, spoken = "incline dumbbell press")
    private val lateral = Lift("lateral_raise", 3, 12..15, 20f, restSeconds = 60, spoken = "lateral raise")
    private val pushdown = Lift("tricep_pushdown", 3, 10..12, 50f, restSeconds = 60, spoken = "tricep pushdown")
    private val cableFly = Lift("cable_fly", 3, 12..15, 30f, restSeconds = 60, spoken = "cable fly")
    private val dip = Lift("dip", 3, 10..10, null, restSeconds = 90, spoken = "dips")

    private val deadlift = Lift("deadlift", 3, 5..5, 275f, increment = 10f, main = true, restSeconds = 210, spoken = "deadlift")
    private val pullUp = Lift("pull_up", 3, 8..8, null, stepEvery = 2, restSeconds = 120, spoken = "pull ups")
    private val row = Lift("barbell_row", 4, 8..8, 135f, shape = Shape.SlipLast, restSeconds = 120, spoken = "barbell row")
    private val facePull = Lift("face_pull", 3, 12..15, 40f, restSeconds = 60, spoken = "face pull")
    private val curl = Lift("bicep_curl", 3, 8..12, 30f, restSeconds = 60, spoken = "dumbbell curl")
    private val pulldown = Lift("lat_pulldown", 3, 12..15, 120f, increment = 10f, spoken = "lat pulldown")
    private val hammer = Lift("hammer_curl", 3, 8..12, 30f, restSeconds = 60, spoken = "hammer curl")

    private val squat = Lift("back_squat", 4, 5..5, 225f, increment = 10f, main = true, restSeconds = 210, spoken = "squat")
    private val rdl = Lift("romanian_deadlift", 3, 8..10, 185f, increment = 10f, restSeconds = 120, spoken = "rdl")
    private val legPress = Lift("leg_press", 3, 10..12, 360f, increment = 20f, spoken = "leg press")
    private val legCurl = Lift("leg_curl", 3, 10..12, 90f, increment = 10f, restSeconds = 60, spoken = "leg curl")
    private val calf = Lift("calf_raise", 4, 12..15, 135f, increment = 10f, restSeconds = 60, spoken = "calf raise")
    private val splitSquat = Lift("bulgarian_split_squat", 3, 8..10, 40f, spoken = "bulgarian split squat")
    private val legExtension = Lift("leg_extension", 3, 12..15, 100f, increment = 10f, restSeconds = 60, spoken = "leg extension")

    /** A and B versions of each day, alternating, 5 or 6 lifts each. Main lift first. */
    private val templates: Map<SplitDay, List<List<Lift>>> = mapOf(
        SplitDay.Push to listOf(
            listOf(bench, ohp, inclineDb, lateral, pushdown),
            listOf(bench, ohp, inclineDb, cableFly, dip, lateral)
        ),
        SplitDay.Pull to listOf(
            listOf(deadlift, pullUp, row, facePull, curl),
            listOf(deadlift, pullUp, row, pulldown, hammer, facePull)
        ),
        SplitDay.Legs to listOf(
            listOf(squat, rdl, legPress, legCurl, calf),
            listOf(squat, rdl, splitSquat, legExtension, calf)
        )
    )

    private class Slot(val date: LocalDate, val daysAgo: Int, val day: SplitDay, val lifts: List<Lift>, val isDeload: Boolean)

    /** Null when the last day is today and its session can't have finished by [now]. */
    private fun sessions(lastDay: LocalDate, today: LocalDate, now: LocalTime): List<PlannedSession>? {
        val firstDay = today.minusDays((SampleData.DAYS - 1).toLong())
        val dates = (0 until SampleData.DAYS)
            .filter { it % 7 in TRAINING_OFFSETS && it != MISSED_DAYS_AGO }
            .map { lastDay.minusDays(it.toLong()) to it }
            .filter { (date, _) -> !date.isBefore(firstDay) }
            .reversed()

        val dayCounts = mutableMapOf<SplitDay, Int>()
        val slots = dates.mapIndexed { index, (date, daysAgo) ->
            val day = SplitDay.entries[index % SplitDay.entries.size]
            val variant = dayCounts.getOrDefault(day, 0)
            dayCounts[day] = variant + 1
            val lifts = templates.getValue(day)[variant % 2]
            Slot(date, daysAgo, day, lifts, isDeload = daysAgo in DELOAD_DAYS_AGO)
        }

        val totals = slots.flatMap { it.lifts }.groupingBy { it.key }.eachCount()
        val seen = mutableMapOf<String, Int>()
        val random = Random(SEED)

        return slots.map { slot ->
            val session = planSession(slot, random) { lift ->
                val n = seen.getOrDefault(lift.key, 0)
                seen[lift.key] = n + 1
                n to totals.getValue(lift.key)
            }
            if (slot.date != today) return@map session

            // Today's workout has to be over already: move it earlier if needed.
            val latestEnd = today.atTime(now).minusMinutes(FINISHED_MINUTES_AGO)
            if (!session.end.isAfter(latestEnd)) return@map session
            val shift = Duration.between(session.end, latestEnd)
            val moved = session.shiftedBy(shift)
            if (moved.start.toLocalTime().isBefore(EARLIEST_START) || moved.start.toLocalDate() != today) return null
            moved
        }
    }

    private fun PlannedSession.shiftedBy(shift: Duration) = copy(
        start = start.plus(shift),
        end = end.plus(shift),
        sets = sets.map { it.copy(time = it.time.plus(shift)) }
    )

    private fun planSession(slot: Slot, random: Random, occurrence: (Lift) -> Pair<Int, Int>): PlannedSession {
        val weekend = slot.date.dayOfWeek == DayOfWeek.SATURDAY || slot.date.dayOfWeek == DayOfWeek.SUNDAY
        val startTime = if (weekend) {
            LocalTime.of(9, 30).plusMinutes(5L * random.nextInt(-6, 13))
        } else {
            LocalTime.of(17, 30).plusMinutes(5L * random.nextInt(-4, 9))
        }
        val start = slot.date.atTime(startTime)

        // Warm-up first, then each lift's sets with its rest between them.
        var clock = start.plusMinutes(8L + random.nextInt(0, 5))
        val sets = mutableListOf<PlannedSet>()
        slot.lifts.forEachIndexed { liftIndex, lift ->
            if (liftIndex > 0) clock = clock.plusSeconds(90L + random.nextInt(0, 90))
            val (n, total) = occurrence(lift)
            val liftSets = planLift(lift, n, total, slot, random)
            liftSets.forEachIndexed { setIndex, set ->
                if (setIndex > 0) clock = clock.plusSeconds(lift.restSeconds + random.nextInt(-20, 41).toLong())
                clock = clock.plusSeconds(30L + set.reps * 3L)
                sets += set.copy(time = clock)
            }
        }
        val end = clock.plusMinutes(4L + random.nextInt(0, 4))
        val label = slot.day.label + if (slot.isDeload) " · deload" else ""
        return PlannedSession(
            day = slot.day,
            isDeload = slot.isDeload,
            start = start,
            end = end,
            notes = "${SampleData.MARKER} $label",
            sets = sets
        )
    }

    /** The sets for one lift in one session; times are filled in by the caller. */
    private fun planLift(lift: Lift, n: Int, total: Int, slot: Slot, random: Random): List<PlannedSet> {
        // Where this session sits on the lift's progression.
        val step = when (lift.shape) {
            Shape.Climb -> n
            Shape.Stall -> min(n, total - 3)
            Shape.SlipLast -> if (n == total - 1 && total >= 3) n - 2 else n
        }.coerceAtLeast(0)
        val span = lift.reps.last - lift.reps.first + 1
        val straight = span == 1
        val deload = slot.isDeload

        val baseWeight = lift.start?.let { start ->
            val steps = if (straight) step / lift.stepEvery else step / span
            start + steps * lift.increment
        }
        val weight = baseWeight?.let { if (deload) floorTo(it * DELOAD_FACTOR, 5f) else it }
        val targetReps = when {
            lift.start == null -> lift.reps.first + step / lift.stepEvery - if (deload) 1 else 0
            straight -> lift.reps.first
            else -> lift.reps.first + step % span
        }

        val isLast = n == total - 1
        // A rep PR on the last set: always in a climbing main lift's latest
        // session, now and then before, never where it would blur the last two.
        val repPr = lift.main && lift.shape == Shape.Climb && !deload &&
            (isLast || (n in 1 until total - 2 && random.nextInt(100) < 25))
        val slipped = lift.shape == Shape.SlipLast && isLast && total >= 3
        val stalledLast = lift.shape == Shape.Stall && isLast && total >= 3

        val setCount = if (deload) min(lift.sets, 3) else lift.sets
        val reps = MutableList(setCount) { targetReps }
        for (i in 1 until setCount) {
            reps[i] = when {
                lift.start == null -> targetReps - i + (if (random.nextBoolean()) 1 else 0).coerceAtMost(i)
                !straight -> reps[i - 1] - (if (random.nextInt(100) < 45) 1 else 0)
                i == setCount - 1 && !deload && random.nextInt(100) < 30 -> targetReps - 1
                else -> targetReps
            }.coerceAtLeast(1)
        }
        if (repPr) reps[setCount - 1] = targetReps + 1
        if (slipped) for (i in 1 until setCount) reps[i] = (targetReps - i / 2).coerceAtLeast(1)

        return reps.mapIndexed { i, r ->
            val lastSet = i == setCount - 1
            val rpe = when {
                lift.main -> rpeFor(i, setCount, deload, lift.shape == Shape.Stall && n >= total - 3, random)
                lastSet && !deload && random.nextInt(100) < 30 -> listOf(8f, 8.5f, 9f)[random.nextInt(3)]
                else -> null
            }
            val pr = repPr && lastSet
            val notes = when {
                pr -> "Rep PR"
                slipped && i == 0 -> "Lower back tight, went lighter"
                stalledLast && lastSet -> "Stuck at ${weight?.roundToInt()} again"
                deload && i == 0 && lift.main -> "Deload week"
                else -> null
            }
            // Tempo readings: the last set of the main lift through the last two
            // weeks, and the row that slipped.
            val tempo = when {
                lift.main && lastSet && !deload && slot.daysAgo < 14 -> tempoFor(rpe ?: 8f, r, lift, random)
                slipped && i == 0 -> tempoFor(9.5f, r, lift, random)
                else -> null
            }
            PlannedSet(
                exerciseKey = lift.key,
                exerciseName = lift.name,
                weight = weight,
                reps = r,
                rpe = rpe,
                notes = notes,
                tempoAverageRepSeconds = tempo?.first,
                tempoDegradationPercent = tempo?.second,
                failureProximityScore = tempo?.third,
                rawVoiceInput = spoken(lift, weight, r, rpe),
                time = slot.date.atStartOfDay()
            )
        }
    }

    private fun rpeFor(setIndex: Int, setCount: Int, deload: Boolean, grinding: Boolean, random: Random): Float {
        if (deload) return 6f
        val first = if (grinding) 8.5f else 7f + 0.5f * random.nextInt(0, 2)
        val climb = 0.5f * setIndex * (if (setCount > 3) 0.75f else 1f)
        return roundToHalf(min(first + climb, 9.5f))
    }

    /** Average rep time, slowdown percent and a failure score on the same scale as TempoAnalyzer. */
    private fun tempoFor(rpe: Float, reps: Int, lift: Lift, random: Random): Triple<Float, Float, Int> {
        val degradation = when {
            rpe >= 9f -> 24f + random.nextInt(0, 13)
            rpe >= 8f -> 12f + random.nextInt(0, 10)
            else -> 4f + random.nextInt(0, 8)
        } + random.nextInt(0, 10) / 10f
        val baseSeconds = when (lift.key) {
            "deadlift" -> 3.1f
            "back_squat" -> 2.8f
            else -> 2.2f
        }
        val average = baseSeconds * (1f + degradation / 200f)
        val base = when {
            degradation >= 35f -> 92
            degradation >= 22f -> 82
            degradation >= 12f -> 68
            degradation >= 5f -> 54
            else -> 34
        }
        val score = (base + min(8, (reps - 1) * 2)).coerceIn(0, 100)
        return Triple((average * 100).roundToInt() / 100f, (degradation * 10).roundToInt() / 10f, score)
    }

    private fun spoken(lift: Lift, weight: Float?, reps: Int, rpe: Float?): String = buildString {
        append(SampleData.MARKER).append(' ').append(lift.spoken)
        if (weight != null) append(' ').append(formatNumber(weight)).append(" for ").append(reps)
        else append(' ').append(reps).append(" reps")
        if (rpe != null) append(" rpe ").append(formatNumber(rpe))
    }

    // ---- Meals ----

    private class Food(
        val name: String,
        val calories: Float,
        val protein: Float,
        val carbs: Float,
        val fat: Float,
        val serving: Float,
        val unit: String = "g"
    )

    private val breakfasts = listOf(
        Food("Oatmeal with whey and banana", 520f, 38f, 72f, 9f, 380f),
        Food("Scrambled eggs on toast", 480f, 30f, 34f, 24f, 300f),
        Food("Greek yogurt with granola", 410f, 32f, 48f, 10f, 320f)
    )
    private val lunches = listOf(
        Food("Chicken and rice bowl", 680f, 52f, 78f, 14f, 450f),
        Food("Turkey sandwich", 560f, 38f, 58f, 18f, 300f),
        Food("Tuna pasta salad", 610f, 42f, 66f, 18f, 400f)
    )
    private val snacks = listOf(
        Food("Protein shake", 220f, 40f, 8f, 3f, 400f, unit = "ml"),
        Food("Apple with peanut butter", 290f, 8f, 30f, 16f, 200f),
        Food("Protein bar", 210f, 20f, 23f, 7f, 60f)
    )
    private val dinners = listOf(
        Food("Salmon with potatoes and greens", 720f, 46f, 58f, 30f, 500f),
        Food("Beef chili", 650f, 48f, 52f, 24f, 450f),
        Food("Chicken stir fry with noodles", 700f, 50f, 80f, 18f, 480f),
        Food("Steak and sweet potato", 760f, 55f, 60f, 30f, 480f)
    )

    /** Three or four meals a day for the month; today's only up to [now]. */
    private fun meals(today: LocalDate, now: LocalTime): List<PlannedMeal> {
        val random = Random(SEED + 1)
        val limit = today.atTime(now)
        return (SampleData.DAYS - 1 downTo 0).flatMap { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val day = buildList {
                add(breakfasts.random(random) to LocalTime.of(7, 30).plusMinutes(5L * random.nextInt(-6, 10)))
                add(lunches.random(random) to LocalTime.of(12, 30).plusMinutes(5L * random.nextInt(-6, 10)))
                if (random.nextInt(100) < 70) add(snacks.random(random) to LocalTime.of(15, 45).plusMinutes(5L * random.nextInt(-6, 10)))
                add(dinners.random(random) to LocalTime.of(19, 30).plusMinutes(5L * random.nextInt(-6, 10)))
            }
            day.map { (food, time) ->
                // Portions wander a little, the way they do.
                val scale = 0.9f + random.nextInt(0, 21) / 100f
                PlannedMeal(
                    foodName = food.name,
                    calories = (food.calories * scale).roundToInt().toFloat(),
                    protein = (food.protein * scale).roundToInt().toFloat(),
                    carbs = (food.carbs * scale).roundToInt().toFloat(),
                    fat = (food.fat * scale).roundToInt().toFloat(),
                    servingSize = floorTo(food.serving * scale, 5f),
                    unit = food.unit,
                    time = date.atTime(time),
                    rawVoiceInput = "${SampleData.MARKER} ${food.name.lowercase()}"
                )
            }
        }.filter { !it.time.isAfter(limit) }
    }

    // ---- Videos and PR tracks ----

    private val videoSearches = listOf(
        "Gym motivation speech",
        "Leg day hype mix",
        "Deadlift PR compilation",
        "Early morning workout motivation",
        "Powerlifting meet highlights",
        "Bench press technique breakdown",
        "No excuses training montage",
        "Squat form cues",
        "Discipline over motivation",
        "Pre-workout pump-up playlist"
    )

    private fun videos(today: LocalDate): List<PlannedVideo> = videoSearches.mapIndexed { index, query ->
        PlannedVideo(
            title = SampleData.TITLE_PREFIX + query,
            url = SampleData.VIDEO_URL_PREFIX + URLEncoder.encode(query.lowercase(), "UTF-8"),
            // Spread over the month, newest first in the list.
            addedAt = today.minusDays(1L + index * 3L).atTime(21, 15).minusMinutes(index * 7L)
        )
    }

    private val trackSearches = listOf("Lose Yourself", "Till I Collapse", "Eye of the Tiger")

    private fun tracks(today: LocalDate): List<PlannedTrack> = trackSearches.mapIndexed { index, song ->
        PlannedTrack(
            label = SampleData.TITLE_PREFIX + song,
            uri = SampleData.TRACK_URI_PREFIX + URLEncoder.encode(song.lowercase(), "UTF-8").replace("+", "%20"),
            createdAt = today.minusDays(2L + index * 9L).atTime(20, 0)
        )
    }

    // ---- Helpers ----

    private fun floorTo(value: Float, step: Float): Float = floor(value / step) * step

    private fun roundToHalf(value: Float): Float = (value * 2).roundToInt() / 2f

    private fun formatNumber(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()
}

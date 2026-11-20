package com.redline.app.ui.community

import com.redline.app.community.ChatMessage
import com.redline.app.community.HeadToHead
import com.redline.app.community.StatKind
import com.redline.app.community.fromLbs
import com.redline.app.community.toLbs
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.WorkoutSession
import com.redline.app.ui.components.cleanNum
import com.redline.app.ui.components.compact
import com.redline.app.ui.components.plural
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val MINUTE_MS = 60_000L

/** How old a post is: "Just now", "5m", "3h", "2d", then the date. */
internal fun postAge(then: Long, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val minutes = (now - then).coerceAtLeast(0) / MINUTE_MS
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}d"
        else -> shortDate(toDate(then, zone), toDate(now, zone), locale)
    }
}

/** Beside a thread in the list: the time today, "Yesterday", a weekday this week, then the date. */
internal fun threadTime(then: Long, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val date = toDate(then, zone)
    val today = toDate(now, zone)
    val daysAgo = ChronoUnit.DAYS.between(date, today)
    return when {
        daysAgo <= 0L -> Instant.ofEpochMilli(then).atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a", locale))
        daysAgo == 1L -> "Yesterday"
        daysAgo < 7L -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        else -> shortDate(date, today, locale)
    }
}

/** The header above a day's messages. */
internal fun dayLabel(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
    val daysAgo = ChronoUnit.DAYS.between(date, today)
    return when {
        daysAgo == 0L -> "Today"
        daysAgo == 1L -> "Yesterday"
        daysAgo in 2L..6L -> date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        else -> date.format(
            DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE, MMM d" else "MMM d, yyyy", locale)
        )
    }
}

internal fun messageTime(at: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
    Instant.ofEpochMilli(at).atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a", locale))

private fun shortDate(date: LocalDate, today: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "MMM d" else "MMM d, yyyy", locale))

private fun toDate(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

/* Chat layout */

sealed interface ChatRow {
    val key: String
}

data class DayRow(val date: LocalDate) : ChatRow {
    override val key: String get() = "day-$date"
}

/**
 * One message. Messages from the same person a few minutes apart form a run:
 * they sit close together and only the last one shows the time.
 */
data class BubbleRow(
    val message: ChatMessage,
    val startsRun: Boolean,
    val endsRun: Boolean
) : ChatRow {
    override val key: String get() = "msg-${message.id}"
}

private const val RUN_GAP_MS = 5 * MINUTE_MS

/** Messages (oldest first) laid out with a header at the start of each day. */
internal fun chatRows(messages: List<ChatMessage>, zone: ZoneId): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    messages.forEachIndexed { i, message ->
        val previous = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)
        val day = toDate(message.sentAt, zone)
        if (previous == null || toDate(previous.sentAt, zone) != day) rows += DayRow(day)
        rows += BubbleRow(
            message = message,
            startsRun = previous == null || !sameRun(previous, message, zone),
            endsRun = next == null || !sameRun(message, next, zone)
        )
    }
    return rows
}

private fun sameRun(a: ChatMessage, b: ChatMessage, zone: ZoneId): Boolean =
    a.fromMe == b.fromMe &&
        b.sentAt - a.sentAt <= RUN_GAP_MS &&
        toDate(a.sentAt, zone) == toDate(b.sentAt, zone)

/* Gyms */

enum class CrowdLevel(val label: String) { QUIET("Quiet"), MODERATE("Moderate"), BUSY("Busy") }

internal fun crowdLevel(percent: Int): CrowdLevel = when {
    percent < 40 -> CrowdLevel.QUIET
    percent < 70 -> CrowdLevel.MODERATE
    else -> CrowdLevel.BUSY
}

/** Miles for people who log in pounds, kilometres for kilograms. */
internal fun distanceLabel(miles: Float, unit: String): String =
    if (unit.equals("kg", ignoreCase = true)) "${(miles * 1.609344f).cleanNum()} km" else "${miles.cleanNum()} mi"

/** "Maya trains here", "Maya and Jonah train here", "Maya, Jonah and 2 more train here". */
internal fun friendsHereLine(names: List<String>): String? = when (names.size) {
    0 -> null
    1 -> "${names[0]} trains here"
    2 -> "${names[0]} and ${names[1]} train here"
    else -> "${names[0]}, ${names[1]} and ${names.size - 2} more train here"
}

/* People */

/** "MK" for "Maya Kim", "M" for "Maya". */
internal fun initials(name: String): String {
    val words = name.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}

internal fun firstName(name: String): String = name.trim().substringBefore(' ').ifEmpty { name }

/* Stats */

/** A compared value in your unit: "4", "23 wk", "412.0k lbs". */
internal fun statValue(kind: StatKind, value: Double, unit: String): String = when (kind) {
    StatKind.COUNT -> value.toLong().toString()
    StatKind.WEEKS -> "${value.toLong()} wk"
    StatKind.VOLUME -> "${fromLbs(value, unit).toLong().compact()} $unit"
}

/** "You lead 5–3", "Maya leads 4–2", "Level at 3–3". Null when there's nothing to compare. */
internal fun headToHeadLine(score: HeadToHead, theirName: String): String? = when {
    score.total == 0 -> null
    score.youLead > score.theyLead -> "You lead ${score.youLead}–${score.theyLead}"
    score.theyLead > score.youLead -> "$theirName leads ${score.theyLead}–${score.youLead}"
    else -> "Level at ${score.youLead}–${score.theyLead}"
}

internal fun liftValue(lbs: Float?, unit: String): String =
    if (lbs == null) "–" else "${fromLbs(lbs.toDouble(), unit).toFloat().cleanNum()} $unit"

/**
 * Your latest finished workout as one line for a post:
 * "Tuesday workout · 14 sets · 12,400 lbs".
 */
internal fun workoutLine(
    session: WorkoutSession,
    sets: List<ExerciseSet>,
    zone: ZoneId,
    locale: Locale = Locale.getDefault()
): String? {
    if (sets.isEmpty()) return null
    val day = Instant.ofEpochMilli(session.startTime).atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    // Shown in the unit most sets were logged in; sets in the other unit are converted.
    val unit = sets.groupingBy { it.unit }.eachCount().maxByOrNull { it.value }?.key ?: "lbs"
    val volumeLbs = sets.sumOf { toLbs(it.weight ?: 0f, it.unit) * (it.reps ?: 0) }
    val volume = fromLbs(volumeLbs, unit).toLong()
    return buildString {
        append("$day workout · ${plural(sets.size, "set")}")
        if (volume > 0) append(" · ${volume.compact()} $unit")
    }
}

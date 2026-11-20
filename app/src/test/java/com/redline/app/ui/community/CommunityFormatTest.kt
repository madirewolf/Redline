package com.redline.app.ui.community

import com.redline.app.community.ChatMessage
import com.redline.app.community.HeadToHead
import com.redline.app.community.StatKind
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class CommunityFormatTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val locale = Locale.US

    // Friday, Nov 20 2026, 3:30 PM.
    private val now = ms(LocalDateTime.of(2026, 11, 20, 15, 30))
    private val today = LocalDate.of(2026, 11, 20)

    private fun ms(time: LocalDateTime): Long = time.atZone(zone).toInstant().toEpochMilli()
    private fun ms(date: LocalDate, hour: Int, minute: Int) = ms(date.atTime(hour, minute))

    /* Times */

    @Test
    fun `post age counts up in minutes, hours and days, then shows the date`() {
        assertEquals("Just now", postAge(now, now, zone, locale))
        assertEquals("Just now", postAge(now - 59_000, now, zone, locale))
        assertEquals("Just now", postAge(now + 60_000, now, zone, locale))
        assertEquals("5m", postAge(now - 5 * MINUTE, now, zone, locale))
        assertEquals("3h", postAge(now - 3 * HOUR - 10 * MINUTE, now, zone, locale))
        assertEquals("2d", postAge(now - 2 * DAY, now, zone, locale))
        assertEquals("Nov 10", postAge(now - 10 * DAY, now, zone, locale))
        assertEquals("Dec 25, 2025", postAge(ms(LocalDate.of(2025, 12, 25), 9, 0), now, zone, locale))
    }

    @Test
    fun `thread time is the clock today, then yesterday, a weekday, a date`() {
        assertEquals("9:05 AM", threadTime(ms(today, 9, 5), now, zone, locale))
        assertEquals("Yesterday", threadTime(ms(today.minusDays(1), 23, 59), now, zone, locale))
        assertEquals("Tue", threadTime(ms(today.minusDays(3), 12, 0), now, zone, locale))
        assertEquals("Nov 10", threadTime(ms(today.minusDays(10), 12, 0), now, zone, locale))
    }

    @Test
    fun `day headers name recent days and date older ones`() {
        assertEquals("Today", dayLabel(today, today, locale))
        assertEquals("Yesterday", dayLabel(today.minusDays(1), today, locale))
        assertEquals("Tuesday", dayLabel(today.minusDays(3), today, locale))
        assertEquals("Saturday", dayLabel(today.minusDays(6), today, locale))
        assertEquals("Fri, Nov 13", dayLabel(today.minusDays(7), today, locale))
        assertEquals("Dec 31, 2025", dayLabel(LocalDate.of(2025, 12, 31), today, locale))
    }

    /* Chat layout */

    private fun chat(id: Int, fromMe: Boolean, at: Long) =
        ChatMessage(id = "$id", conversationId = "dm-a", fromMe = fromMe, text = "m$id", sentAt = at)

    @Test
    fun `chat rows put a header on each day and group runs from one person`() {
        val day = today.minusDays(1)
        val messages = listOf(
            chat(1, fromMe = false, at = ms(day, 10, 0)),
            chat(2, fromMe = false, at = ms(day, 10, 3)),
            // A different sender starts a new run.
            chat(3, fromMe = true, at = ms(day, 10, 4)),
            // So does a long gap.
            chat(4, fromMe = true, at = ms(day, 23, 58)),
            // And a new day, however close in time.
            chat(5, fromMe = true, at = ms(today, 0, 1))
        )

        val rows = chatRows(messages, zone)

        assertEquals(
            listOf(
                DayRow(day),
                BubbleRow(messages[0], startsRun = true, endsRun = false),
                BubbleRow(messages[1], startsRun = false, endsRun = true),
                BubbleRow(messages[2], startsRun = true, endsRun = true),
                BubbleRow(messages[3], startsRun = true, endsRun = true),
                DayRow(today),
                BubbleRow(messages[4], startsRun = true, endsRun = true)
            ),
            rows
        )
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun `no messages means no rows`() {
        assertEquals(emptyList<ChatRow>(), chatRows(emptyList(), zone))
    }

    /* Gyms */

    @Test
    fun `crowd levels split at 40 and 70 percent`() {
        assertEquals(CrowdLevel.QUIET, crowdLevel(0))
        assertEquals(CrowdLevel.QUIET, crowdLevel(39))
        assertEquals(CrowdLevel.MODERATE, crowdLevel(40))
        assertEquals(CrowdLevel.MODERATE, crowdLevel(69))
        assertEquals(CrowdLevel.BUSY, crowdLevel(70))
        assertEquals(CrowdLevel.BUSY, crowdLevel(100))
    }

    @Test
    fun `distance is in miles for pounds and kilometres for kilograms`() {
        assertEquals("0.8 mi", distanceLabel(0.8f, "lbs"))
        assertEquals("2 mi", distanceLabel(2f, "lbs"))
        assertEquals("1.6 km", distanceLabel(1f, "kg"))
        assertEquals("1.6 km", distanceLabel(1f, "KG"))
    }

    @Test
    fun `friends at a gym read as a short sentence`() {
        assertNull(friendsHereLine(emptyList()))
        assertEquals("Maya trains here", friendsHereLine(listOf("Maya")))
        assertEquals("Maya and Jonah train here", friendsHereLine(listOf("Maya", "Jonah")))
        assertEquals("Maya, Jonah and 2 more train here", friendsHereLine(listOf("Maya", "Jonah", "Theo", "Priya")))
    }

    /* People */

    @Test
    fun `initials take the first and last names`() {
        assertEquals("MK", initials("Maya Kim"))
        assertEquals("M", initials("maya"))
        assertEquals("AC", initials("  Ana  de la Cruz "))
        assertEquals("?", initials("   "))
    }

    @Test
    fun `first name is the first word`() {
        assertEquals("Maya", firstName("Maya Kim"))
        assertEquals("Theo", firstName("Theo"))
        assertEquals("Ana", firstName("  Ana de la Cruz"))
    }

    /* Stats */

    @Test
    fun `stat values read as counts, weeks or volume in your unit`() {
        assertEquals("4", statValue(StatKind.COUNT, 4.0, "lbs"))
        assertEquals("23 wk", statValue(StatKind.WEEKS, 23.0, "lbs"))
        assertEquals("412.0k lbs", statValue(StatKind.VOLUME, 412_000.0, "lbs"))
        // 412,000 lbs is about 186,880 kg.
        assertEquals("186.9k kg", statValue(StatKind.VOLUME, 412_000.0, "kg"))
        assertEquals("0 lbs", statValue(StatKind.VOLUME, 0.0, "lbs"))
    }

    @Test
    fun `head to head says who leads, or nothing when there's nothing to compare`() {
        assertNull(headToHeadLine(HeadToHead(0, 0, 0), "Maya"))
        assertEquals("You lead 5–3", headToHeadLine(HeadToHead(5, 3, 0), "Maya"))
        assertEquals("Maya leads 4–2", headToHeadLine(HeadToHead(2, 4, 1), "Maya"))
        assertEquals("Level at 3–3", headToHeadLine(HeadToHead(3, 3, 1), "Maya"))
    }

    @Test
    fun `a lift reads in your unit, or a dash when it isn't logged`() {
        assertEquals("–", liftValue(null, "lbs"))
        assertEquals("225 lbs", liftValue(225f, "lbs"))
        assertEquals("100 kg", liftValue(220.462f, "kg"))
        assertEquals("102.5 kg", liftValue(225.97f, "kg"))
    }

    /* Workout line */

    private fun set(weight: Float?, reps: Int?, unit: String = "lbs") =
        ExerciseSet(sessionId = 1, exerciseName = "Bench Press", weight = weight, unit = unit, reps = reps)

    // A Tuesday.
    private val session = WorkoutSession(id = 1, startTime = ms(LocalDate.of(2026, 11, 17), 18, 0), endTime = 0)

    @Test
    fun `a workout line has the day, the sets and the volume`() {
        val sets = List(14) { set(100f, 10) }

        assertEquals("Tuesday workout · 14 sets · 14.0k lbs", workoutLine(session, sets, zone, locale))
    }

    @Test
    fun `a workout with no weights leaves out the volume`() {
        assertEquals("Tuesday workout · 1 set", workoutLine(session, listOf(set(null, 12)), zone, locale))
    }

    @Test
    fun `the volume is in the unit most sets were logged in`() {
        // 20,000 kg plus 1,000 lbs (about 454 kg).
        val sets = listOf(set(100f, 100, "kg"), set(100f, 100, "kg"), set(100f, 10, "lbs"))

        assertEquals("Tuesday workout · 3 sets · 20.5k kg", workoutLine(session, sets, zone, locale))
    }

    @Test
    fun `a workout with no sets has no line`() {
        assertNull(workoutLine(session, emptyList(), zone, locale))
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}

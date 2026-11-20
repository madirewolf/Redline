package com.redline.app.data.sample

import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.ui.motivation.MotivationViewModel
import com.redline.app.ui.music.MusicViewModel
import com.redline.app.ui.progress.ProgressStatus
import com.redline.app.ui.progress.buildSummaries
import com.redline.app.ui.settings.SampleDataViewModel
import com.redline.app.voice.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class SamplePlanTest {

    /** A Friday. */
    private val today = LocalDate.of(2026, 11, 20)
    private val evening = LocalTime.of(20, 0)
    private val plan = SamplePlanner.build(today, evening)
    private val zone = ZoneOffset.UTC

    private fun entities(plan: SamplePlan): List<ExerciseSet> =
        plan.sessions.flatMapIndexed { index, session -> session.sets.map { it.toEntity(index + 1L, zone) } }

    @Test
    fun `workouts cover the last thirty days and end today`() {
        val first = today.minusDays(SampleData.DAYS - 1L)
        plan.sessions.forEach { session ->
            val date = session.start.toLocalDate()
            assertFalse("$date is before the month", date.isBefore(first))
            assertFalse("$date is after today", date.isAfter(today))
            assertTrue(session.end.isAfter(session.start))
            assertFalse("a session ends in the future", session.end.isAfter(today.atTime(evening)))
        }
        assertEquals(today, plan.sessions.last().start.toLocalDate())
        assertTrue(plan.sessions.first().start.toLocalDate() <= first.plusDays(3))
        assertEquals(plan.sessions.sortedBy { it.start }, plan.sessions)
    }

    @Test
    fun `about four sessions a week on a push pull legs rotation`() {
        assertEquals(16, plan.sessions.size)
        for (week in 0 until SampleData.DAYS / 7) {
            val end = today.minusDays(7L * week)
            val start = end.minusDays(6)
            val count = plan.sessions.count { it.start.toLocalDate() in start..end }
            assertTrue("week ending $end has $count sessions", count in 3..5)
        }
        plan.sessions.zipWithNext().forEach { (a, b) -> assertTrue(a.day != b.day) }
        assertEquals(SplitDay.entries.toSet(), plan.sessions.map { it.day }.toSet())
        // Training at believable hours, about an hour long.
        plan.sessions.forEach { session ->
            assertTrue(session.start.hour in 6..19)
            val minutes = java.time.Duration.between(session.start, session.end).toMinutes()
            assertTrue("$minutes minute session", minutes in 35..110)
        }
    }

    @Test
    fun `each session has four to six lifts of three or four sets`() {
        plan.sessions.forEach { session ->
            val lifts = session.sets.groupBy { it.exerciseKey }
            assertTrue(lifts.size in 4..6)
            lifts.values.forEach { sets -> assertTrue(sets.size in 3..4) }
            assertEquals(session.sets.sortedBy { it.time }, session.sets)
            session.sets.zipWithNext().forEach { (a, b) -> assertTrue(b.time.isAfter(a.time)) }
            assertTrue(session.sets.first().time.isAfter(session.start))
            assertTrue(session.sets.last().time.isBefore(session.end))
            session.sets.forEach { assertTrue(it.reps in 1..20) }
        }
    }

    @Test
    fun `lifts use the exercise catalog's keys and names`() {
        plan.sessions.flatMap { it.sets }.forEach { set ->
            assertEquals(set.exerciseName, ExerciseCatalog.displayNameForKey(set.exerciseKey))
        }
    }

    @Test
    fun `main lifts climb outside the deload and dip during it`() {
        listOf("bench_press", "back_squat", "deadlift").forEach { key ->
            val sessions = plan.sessions.filter { session -> session.sets.any { it.exerciseKey == key } }
            val tops = sessions.map { session -> session.sets.filter { it.exerciseKey == key }.maxOf { it.weight!! } }
            val working = sessions.zip(tops).filter { !it.first.isDeload }.map { it.second }
            assertEquals("$key never drops between working sessions", working.sorted(), working)
            assertTrue("$key ends heavier than it starts", working.last() > working.first())

            val deload = sessions.zip(tops).firstOrNull { it.first.isDeload }
            assertNotNull("$key has a deload session", deload)
            val before = sessions.zip(tops).last { it.first.start < deload!!.first.start }.second
            assertTrue("$key is lighter in the deload", deload!!.second < before)
        }
        assertTrue(plan.sessions.any { it.isDeload })
        assertTrue(plan.sessions.flatMap { it.sets }.any { it.notes == "Rep PR" })
    }

    @Test
    fun `every row carries the sample marker`() {
        plan.sessions.forEach { session ->
            assertTrue(session.notes.startsWith(SampleData.MARKER))
            assertTrue(session.toEntity(zone).notes!!.startsWith(SampleData.MARKER))
            session.sets.forEach { assertTrue(it.rawVoiceInput.startsWith(SampleData.MARKER)) }
        }
        entities(plan).forEach { assertTrue(it.rawVoiceInput!!.startsWith(SampleData.MARKER)) }
        plan.meals.forEach { assertTrue(it.toEntity(zone).rawVoiceInput!!.startsWith(SampleData.MARKER)) }
        plan.videos.forEach {
            assertTrue(it.title.startsWith(SampleData.TITLE_PREFIX))
            assertTrue(it.url.startsWith(SampleData.VIDEO_URL_PREFIX))
        }
        plan.tracks.forEach {
            assertTrue(it.label.startsWith(SampleData.TITLE_PREFIX))
            assertTrue(it.uri.startsWith(SampleData.TRACK_URI_PREFIX))
        }
    }

    @Test
    fun `the progress screen shows every status`() {
        val summaries = buildSummaries(entities(plan)).associateBy { it.exerciseKey }
        assertEquals(ProgressStatus.Stagnating, summaries.getValue("overhead_press").status)
        assertEquals(ProgressStatus.Regressing, summaries.getValue("barbell_row").status)
        listOf("bench_press", "back_squat", "deadlift", "pull_up", "dip").forEach {
            assertEquals(it, ProgressStatus.Progressing, summaries.getValue(it).status)
        }
        listOf("lateral_raise", "cable_fly", "leg_press", "leg_curl", "lat_pulldown").forEach {
            assertEquals(it, ProgressStatus.Building, summaries.getValue(it).status)
        }
        assertTrue(summaries.getValue("pull_up").isBodyweight)
        assertTrue(summaries.getValue("bench_press").trendPoints.size >= 5)
        assertNotNull(summaries.getValue("bench_press").latestFailureScore)
    }

    @Test
    fun `some sets have rpe and a few have tempo readings`() {
        val sets = plan.sessions.flatMap { it.sets }
        val rated = sets.filter { it.rpe != null }
        assertTrue(rated.size in sets.size / 5 until sets.size)
        rated.forEach { assertTrue(it.rpe!! in 6f..10f && (it.rpe!! * 2) % 1f == 0f) }

        val timed = sets.filter { it.failureProximityScore != null }
        assertTrue("${timed.size} tempo sets", timed.size in 3..12)
        timed.forEach {
            assertTrue(it.failureProximityScore!! in 0..100)
            assertNotNull(it.tempoAverageRepSeconds)
            assertNotNull(it.tempoDegradationPercent)
        }
        // Today's workout shows a failure score on its main lift.
        assertTrue(plan.sessions.last().sets.any { it.failureProximityScore != null })
    }

    @Test
    fun `ten placeholder videos link to youtube searches`() {
        assertEquals(10, plan.videos.size)
        assertEquals(10, plan.videos.map { it.url }.toSet().size)
        plan.videos.forEach { video ->
            assertTrue(MotivationViewModel.isVideoLink(video.url))
            assertTrue(MotivationViewModel.isYouTube(video.url))
            assertTrue(MotivationViewModel.isYouTubeSearch(video.url))
            assertFalse("no invented video ids", video.url.contains("watch?v=") || video.url.contains("youtu.be"))
            assertFalse(video.url.contains(' '))
            assertEquals("youtube", video.toEntity(zone).type)
            assertEquals("YouTube search", MotivationViewModel.sourceLabel(video.toEntity(zone)))
        }
        plan.tracks.forEach { assertTrue(MusicViewModel.isTrackLink(it.uri)) }
        assertEquals(1, plan.tracks.mapIndexed { i, t -> t.toEntity(zone, isDefault = i == 0) }.count { it.isDefault })
    }

    @Test
    fun `meals fill the month and stop at now`() {
        val days = plan.meals.map { it.time.toLocalDate() }.toSet()
        assertEquals(SampleData.DAYS, days.size)
        plan.meals.forEach { assertFalse(it.time.isAfter(today.atTime(evening))) }
        val perDay = plan.meals.groupBy { it.time.toLocalDate() }.filterKeys { it != today }
        perDay.values.forEach { meals ->
            assertTrue(meals.size in 3..4)
            assertTrue(meals.sumOf { it.calories.toDouble() } in 1400.0..3200.0)
        }
        assertTrue(plan.meals.any { it.time.toLocalDate() == today })
    }

    @Test
    fun `the same day gives the same plan`() {
        assertEquals(plan, SamplePlanner.build(today, evening))
    }

    @Test
    fun `just after midnight the month ends yesterday`() {
        val early = SamplePlanner.build(today, LocalTime.of(0, 30))
        assertEquals(today.minusDays(1), early.sessions.last().start.toLocalDate())
        assertTrue(early.meals.none { it.time.toLocalDate() == today })
    }

    @Test
    fun `in the morning today's session is moved before now`() {
        val now = LocalTime.of(10, 0)
        val morning = SamplePlanner.build(today, now)
        val last = morning.sessions.last()
        assertEquals(today, last.start.toLocalDate())
        assertFalse(last.end.isAfter(today.atTime(now).minusMinutes(10)))
        assertFalse(last.start.toLocalTime().isBefore(LocalTime.of(6, 0)))
        last.sets.forEach { assertTrue(it.time.isAfter(last.start) && it.time.isBefore(last.end)) }
    }

    @Test
    fun `settings describes what was loaded or removed`() {
        assertEquals(
            "16 workouts, 101 meals, 10 videos and 3 PR tracks",
            SampleDataViewModel.describe(SampleDataCounts(workouts = 16, sets = 300, meals = 101, videos = 10, tracks = 3))
        )
        assertEquals("1 video", SampleDataViewModel.describe(SampleDataCounts(videos = 1)))
        assertEquals("nothing", SampleDataViewModel.describe(SampleDataCounts()))
        assertTrue(SampleDataCounts().isEmpty)
    }
}

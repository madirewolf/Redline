package com.redline.app.data.sample

import androidx.room.withTransaction
import com.redline.app.data.local.RedlineDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Rows written or removed, per kind. */
data class SampleDataCounts(
    val workouts: Int = 0,
    val sets: Int = 0,
    val meals: Int = 0,
    val videos: Int = 0,
    val tracks: Int = 0
) {
    val isEmpty: Boolean get() = workouts + sets + meals + videos + tracks == 0
}

/**
 * Loads and removes a month of made-up training, meals, videos and PR tracks
 * for debug and demo builds, so every screen has something to show. The rows
 * are marked as described on [SampleData]; [clear] removes only those, and
 * real data is never touched.
 */
@Singleton
class SampleDataSeeder @Inject constructor(
    private val database: RedlineDatabase
) {
    private val dao get() = database.sampleDataDao()

    /** True while any sample row is in the database. */
    val isLoaded: Flow<Boolean> = dao.observeCount(
        marker = SampleData.MARKER,
        titlePrefix = SampleData.TITLE_PREFIX,
        videoUrlPrefix = SampleData.VIDEO_URL_PREFIX,
        trackUriPrefix = SampleData.TRACK_URI_PREFIX
    ).map { it > 0 }.distinctUntilChanged()

    /**
     * Writes the month ending today. Each kind is written only if none of its
     * sample rows are there yet, so pressing it twice adds nothing the second
     * time. Returns what was written.
     */
    suspend fun load(zone: ZoneId = ZoneId.systemDefault()): SampleDataCounts {
        val plan = SamplePlanner.build(today = LocalDate.now(zone), now = LocalTime.now(zone))
        return database.withTransaction {
            var workouts = 0
            var sets = 0
            if (dao.countSessions(SampleData.MARKER) == 0) {
                plan.sessions.forEach { session ->
                    val sessionId = dao.insertSession(session.toEntity(zone))
                    dao.insertSets(session.sets.map { it.toEntity(sessionId, zone) })
                    workouts++
                    sets += session.sets.size
                }
            }

            var meals = 0
            if (dao.countCalorieEntries(SampleData.MARKER) == 0) {
                dao.insertCalorieEntries(plan.meals.map { it.toEntity(zone) })
                meals = plan.meals.size
            }

            var videos = 0
            if (dao.countVideos(SampleData.TITLE_PREFIX, SampleData.VIDEO_URL_PREFIX) == 0) {
                dao.insertVideos(plan.videos.map { it.toEntity(zone) })
                videos = plan.videos.size
            }

            var tracks = 0
            if (dao.countPrTracks(SampleData.TITLE_PREFIX, SampleData.TRACK_URI_PREFIX) == 0) {
                // A sample track only becomes the default when there's no real one.
                val makeDefault = dao.countDefaultPrTracks() == 0
                dao.insertPrTracks(plan.tracks.mapIndexed { index, track ->
                    track.toEntity(zone, isDefault = makeDefault && index == 0)
                })
                tracks = plan.tracks.size
            }

            SampleDataCounts(workouts, sets, meals, videos, tracks)
        }
    }

    /** Removes every sample row, sets first, and nothing else. Returns what was removed. */
    suspend fun clear(): SampleDataCounts = database.withTransaction {
        val sets = dao.deleteSets(SampleData.MARKER)
        val workouts = dao.deleteSessions(SampleData.MARKER)
        val meals = dao.deleteCalorieEntries(SampleData.MARKER)
        val videos = dao.deleteVideos(SampleData.TITLE_PREFIX, SampleData.VIDEO_URL_PREFIX)
        val tracks = dao.deletePrTracks(SampleData.TITLE_PREFIX, SampleData.TRACK_URI_PREFIX)
        if (tracks > 0) dao.ensureDefaultPrTrack()
        SampleDataCounts(workouts, sets, meals, videos, tracks)
    }
}

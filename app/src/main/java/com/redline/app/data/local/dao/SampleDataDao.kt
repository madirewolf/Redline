package com.redline.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.redline.app.data.local.entity.CalorieEntry
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.MotivationalVideo
import com.redline.app.data.local.entity.PrTrack
import com.redline.app.data.local.entity.WorkoutSession
import kotlinx.coroutines.flow.Flow

/**
 * Debug and demo builds' sample data (see SampleDataSeeder). Rows are found by
 * an exact, case-sensitive prefix on an existing text column, so the schema
 * stays as it is and nothing real is ever matched by accident.
 */
@Dao
interface SampleDataDao {

    @Insert
    suspend fun insertSession(session: WorkoutSession): Long

    @Insert
    suspend fun insertSets(sets: List<ExerciseSet>)

    @Insert
    suspend fun insertCalorieEntries(entries: List<CalorieEntry>)

    @Insert
    suspend fun insertVideos(videos: List<MotivationalVideo>)

    @Insert
    suspend fun insertPrTracks(tracks: List<PrTrack>)

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE substr(notes, 1, length(:marker)) = :marker")
    suspend fun countSessions(marker: String): Int

    @Query("SELECT COUNT(*) FROM calorie_entries WHERE substr(rawVoiceInput, 1, length(:marker)) = :marker")
    suspend fun countCalorieEntries(marker: String): Int

    @Query("""
        SELECT COUNT(*) FROM motivational_videos
        WHERE substr(title, 1, length(:titlePrefix)) = :titlePrefix
          AND substr(url, 1, length(:urlPrefix)) = :urlPrefix
    """)
    suspend fun countVideos(titlePrefix: String, urlPrefix: String): Int

    @Query("""
        SELECT COUNT(*) FROM pr_tracks
        WHERE substr(label, 1, length(:labelPrefix)) = :labelPrefix
          AND substr(trackUri, 1, length(:uriPrefix)) = :uriPrefix
    """)
    suspend fun countPrTracks(labelPrefix: String, uriPrefix: String): Int

    @Query("SELECT COUNT(*) FROM pr_tracks WHERE isDefault = 1")
    suspend fun countDefaultPrTracks(): Int

    /** Every sample row, live; zero when there's nothing to remove. */
    @Query("""
        SELECT
            (SELECT COUNT(*) FROM workout_sessions WHERE substr(notes, 1, length(:marker)) = :marker)
          + (SELECT COUNT(*) FROM calorie_entries WHERE substr(rawVoiceInput, 1, length(:marker)) = :marker)
          + (SELECT COUNT(*) FROM motivational_videos
                WHERE substr(title, 1, length(:titlePrefix)) = :titlePrefix
                  AND substr(url, 1, length(:videoUrlPrefix)) = :videoUrlPrefix)
          + (SELECT COUNT(*) FROM pr_tracks
                WHERE substr(label, 1, length(:titlePrefix)) = :titlePrefix
                  AND substr(trackUri, 1, length(:trackUriPrefix)) = :trackUriPrefix)
    """)
    fun observeCount(marker: String, titlePrefix: String, videoUrlPrefix: String, trackUriPrefix: String): Flow<Int>

    /** Sets in sample sessions, plus any sample set that somehow sits elsewhere. */
    @Query("""
        DELETE FROM exercise_sets
        WHERE sessionId IN (SELECT id FROM workout_sessions WHERE substr(notes, 1, length(:marker)) = :marker)
           OR substr(rawVoiceInput, 1, length(:marker)) = :marker
    """)
    suspend fun deleteSets(marker: String): Int

    @Query("DELETE FROM workout_sessions WHERE substr(notes, 1, length(:marker)) = :marker")
    suspend fun deleteSessions(marker: String): Int

    @Query("DELETE FROM calorie_entries WHERE substr(rawVoiceInput, 1, length(:marker)) = :marker")
    suspend fun deleteCalorieEntries(marker: String): Int

    @Query("""
        DELETE FROM motivational_videos
        WHERE substr(title, 1, length(:titlePrefix)) = :titlePrefix
          AND substr(url, 1, length(:urlPrefix)) = :urlPrefix
    """)
    suspend fun deleteVideos(titlePrefix: String, urlPrefix: String): Int

    @Query("""
        DELETE FROM pr_tracks
        WHERE substr(label, 1, length(:labelPrefix)) = :labelPrefix
          AND substr(trackUri, 1, length(:uriPrefix)) = :uriPrefix
    """)
    suspend fun deletePrTracks(labelPrefix: String, uriPrefix: String): Int

    /** If the default track was a sample one, the newest real track takes over, as on the Music screen. */
    @Query("""
        UPDATE pr_tracks SET isDefault = 1
        WHERE id = (SELECT id FROM pr_tracks ORDER BY createdAt DESC LIMIT 1)
          AND NOT EXISTS (SELECT 1 FROM pr_tracks WHERE isDefault = 1)
    """)
    suspend fun ensureDefaultPrTrack(): Int
}

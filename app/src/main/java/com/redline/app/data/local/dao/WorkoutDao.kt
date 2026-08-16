package com.redline.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.redline.app.data.local.entity.CalorieEntry
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.MotivationalVideo
import com.redline.app.data.local.entity.PrTrack
import com.redline.app.data.local.entity.WorkoutSession
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // Sessions
    @Insert
    suspend fun insertSession(session: WorkoutSession): Long

    @Update
    suspend fun updateSession(session: WorkoutSession)

    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): WorkoutSession?

    @Query("SELECT * FROM workout_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun getActiveSession(): Flow<WorkoutSession?>

    // Sets
    @Insert
    suspend fun insertSet(exerciseSet: ExerciseSet): Long

    @Query("SELECT * FROM exercise_sets WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getSetsForSession(sessionId: Long): Flow<List<ExerciseSet>>

    @Query("SELECT DISTINCT exerciseName FROM exercise_sets ORDER BY exerciseName ASC")
    fun getAllExerciseNames(): Flow<List<String>>

    @Query("SELECT * FROM exercise_sets ORDER BY timestamp DESC")
    fun getAllSets(): Flow<List<ExerciseSet>>

    @Query("""
        SELECT * FROM exercise_sets
        WHERE exerciseName = :name
        ORDER BY timestamp DESC
    """)
    fun getSetsForExercise(name: String): Flow<List<ExerciseSet>>

    @Query("""
        SELECT * FROM exercise_sets
        WHERE canonicalExerciseKey = :key
        ORDER BY timestamp DESC
    """)
    fun getSetsForCanonicalExercise(key: String): Flow<List<ExerciseSet>>

    // Music
    @Insert
    suspend fun insertPrTrack(track: PrTrack): Long

    @Update
    suspend fun updatePrTrack(track: PrTrack)

    @Delete
    suspend fun deletePrTrack(track: PrTrack)

    @Query("SELECT * FROM pr_tracks ORDER BY isDefault DESC, createdAt DESC")
    fun getPrTracks(): Flow<List<PrTrack>>

    @Query("SELECT * FROM pr_tracks WHERE isDefault = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getDefaultPrTrack(): PrTrack?

    // Home / Calendar queries
    @Query("SELECT * FROM workout_sessions WHERE endTime IS NOT NULL ORDER BY startTime DESC LIMIT :limit")
    fun getRecentCompletedSessions(limit: Int): Flow<List<WorkoutSession>>

    @Query("SELECT DISTINCT exerciseName FROM exercise_sets WHERE sessionId = :sessionId ORDER BY timestamp ASC LIMIT 5")
    suspend fun getExerciseNamesForSession(sessionId: Long): List<String>

    @Query("SELECT COUNT(*) FROM exercise_sets WHERE sessionId = :sessionId")
    suspend fun getSetCountForSession(sessionId: Long): Int

    @Query("SELECT startTime FROM workout_sessions WHERE endTime IS NOT NULL")
    fun getAllCompletedSessionStartTimes(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE endTime IS NOT NULL")
    fun getCompletedSessionCount(): Flow<Int>

    // Editing
    @Update
    suspend fun updateSet(exerciseSet: ExerciseSet)

    @Delete
    suspend fun deleteSet(exerciseSet: ExerciseSet)

    // Motivational Videos
    @Insert
    suspend fun insertVideo(video: MotivationalVideo): Long

    @Delete
    suspend fun deleteVideo(video: MotivationalVideo)

    @Query("SELECT * FROM motivational_videos ORDER BY addedAt DESC")
    fun getVideos(): Flow<List<MotivationalVideo>>

    // Calories
    @Insert
    suspend fun insertCalorieEntry(entry: CalorieEntry): Long

    @Delete
    suspend fun deleteCalorieEntry(entry: CalorieEntry)

    @Query("SELECT * FROM calorie_entries WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp DESC")
    fun getCalorieEntriesForDay(startMs: Long, endMs: Long): Flow<List<CalorieEntry>>

    @Query("SELECT * FROM calorie_entries ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentCalorieEntries(limit: Int): Flow<List<CalorieEntry>>
}

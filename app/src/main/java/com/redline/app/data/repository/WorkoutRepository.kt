package com.redline.app.data.repository

import com.redline.app.data.local.dao.WorkoutDao
import com.redline.app.data.local.entity.CalorieEntry
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.MotivationalVideo
import com.redline.app.data.local.entity.PrTrack
import com.redline.app.data.local.entity.WorkoutSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao
) {
    fun getActiveSession(): Flow<WorkoutSession?> = workoutDao.getActiveSession()

    fun getAllSessions(): Flow<List<WorkoutSession>> = workoutDao.getAllSessions()

    fun getSetsForSession(sessionId: Long): Flow<List<ExerciseSet>> =
        workoutDao.getSetsForSession(sessionId)

    fun getAllExerciseNames(): Flow<List<String>> = workoutDao.getAllExerciseNames()

    fun getAllSets(): Flow<List<ExerciseSet>> = workoutDao.getAllSets()

    fun getSetsForExercise(name: String): Flow<List<ExerciseSet>> =
        workoutDao.getSetsForExercise(name)

    fun getSetsForCanonicalExercise(key: String): Flow<List<ExerciseSet>> =
        workoutDao.getSetsForCanonicalExercise(key)

    suspend fun getSessionById(id: Long): WorkoutSession? =
        workoutDao.getSessionById(id)

    suspend fun startSession(): Long {
        return workoutDao.insertSession(WorkoutSession())
    }

    suspend fun endSession(session: WorkoutSession) {
        workoutDao.updateSession(session.copy(endTime = System.currentTimeMillis()))
    }

    suspend fun logSet(set: ExerciseSet): Long {
        return workoutDao.insertSet(set)
    }

    fun getPrTracks(): Flow<List<PrTrack>> = workoutDao.getPrTracks()

    suspend fun getDefaultPrTrack(): PrTrack? = workoutDao.getDefaultPrTrack()

    suspend fun addPrTrack(track: PrTrack): Long {
        return workoutDao.insertPrTrack(track)
    }

    suspend fun updatePrTrack(track: PrTrack) {
        workoutDao.updatePrTrack(track)
    }

    suspend fun deletePrTrack(track: PrTrack) {
        workoutDao.deletePrTrack(track)
    }

    // Home / Calendar
    fun getRecentCompletedSessions(limit: Int): Flow<List<WorkoutSession>> =
        workoutDao.getRecentCompletedSessions(limit)

    suspend fun getExerciseNamesForSession(sessionId: Long): List<String> =
        workoutDao.getExerciseNamesForSession(sessionId)

    suspend fun getSetCountForSession(sessionId: Long): Int =
        workoutDao.getSetCountForSession(sessionId)

    fun getAllCompletedSessionStartTimes(): Flow<List<Long>> =
        workoutDao.getAllCompletedSessionStartTimes()

    fun getCompletedSessionCount(): Flow<Int> =
        workoutDao.getCompletedSessionCount()

    // Editing
    suspend fun updateSet(set: ExerciseSet) {
        workoutDao.updateSet(set)
    }

    suspend fun deleteSet(set: ExerciseSet) {
        workoutDao.deleteSet(set)
    }

    // Videos
    fun getVideos(): Flow<List<MotivationalVideo>> = workoutDao.getVideos()
    suspend fun addVideo(video: MotivationalVideo) = workoutDao.insertVideo(video)
    suspend fun deleteVideo(video: MotivationalVideo) = workoutDao.deleteVideo(video)

    // Calories
    fun getCalorieEntriesForDay(startMs: Long, endMs: Long): Flow<List<CalorieEntry>> =
        workoutDao.getCalorieEntriesForDay(startMs, endMs)
    fun getRecentCalorieEntries(limit: Int): Flow<List<CalorieEntry>> =
        workoutDao.getRecentCalorieEntries(limit)
    suspend fun logCalorie(entry: CalorieEntry) = workoutDao.insertCalorieEntry(entry)
    suspend fun deleteCalorieEntry(entry: CalorieEntry) = workoutDao.deleteCalorieEntry(entry)
}

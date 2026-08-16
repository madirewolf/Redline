package com.redline.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.redline.app.data.local.dao.WorkoutDao
import com.redline.app.data.local.entity.CalorieEntry
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.MotivationalVideo
import com.redline.app.data.local.entity.PrTrack
import com.redline.app.data.local.entity.WorkoutSession

@Database(
    entities = [WorkoutSession::class, ExerciseSet::class, PrTrack::class, MotivationalVideo::class, CalorieEntry::class],
    version = 3,
    exportSchema = false
)
abstract class RedlineDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}

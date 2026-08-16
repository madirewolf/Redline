package com.redline.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.redline.app.data.local.RedlineDatabase
import com.redline.app.data.local.dao.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RedlineDatabase {
        return Room.databaseBuilder(
            context,
            RedlineDatabase::class.java,
            "redline.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideWorkoutDao(database: RedlineDatabase): WorkoutDao {
        return database.workoutDao()
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS motivational_videos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    url TEXT NOT NULL,
                    type TEXT NOT NULL DEFAULT 'mp4',
                    thumbnailUrl TEXT,
                    addedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS calorie_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    foodName TEXT NOT NULL,
                    calories REAL NOT NULL,
                    protein REAL,
                    carbs REAL,
                    fat REAL,
                    servingSize REAL,
                    unit TEXT NOT NULL DEFAULT 'g',
                    timestamp INTEGER NOT NULL,
                    rawVoiceInput TEXT
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercise_sets ADD COLUMN canonicalExerciseKey TEXT")
            db.execSQL("ALTER TABLE exercise_sets ADD COLUMN tempoAverageRepSeconds REAL")
            db.execSQL("ALTER TABLE exercise_sets ADD COLUMN tempoDegradationPercent REAL")
            db.execSQL("ALTER TABLE exercise_sets ADD COLUMN failureProximityScore INTEGER")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pr_tracks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    label TEXT NOT NULL,
                    trackUri TEXT NOT NULL,
                    dropTimestampMs INTEGER NOT NULL,
                    isDefault INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }
}

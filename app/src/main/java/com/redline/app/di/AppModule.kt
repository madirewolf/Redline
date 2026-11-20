package com.redline.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.redline.app.community.local.CommunityDao
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    @Provides
    fun provideWorkoutDao(database: RedlineDatabase): WorkoutDao {
        return database.workoutDao()
    }

    @Provides
    fun provideCommunityDao(database: RedlineDatabase): CommunityDao {
        return database.communityDao()
    }

    /** Community tables. Empty here; the sample content is written the first time the tab opens. */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS community_members (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    handle TEXT NOT NULL,
                    bio TEXT NOT NULL,
                    homeGymId TEXT,
                    isFriend INTEGER NOT NULL,
                    workoutsThisWeek INTEGER NOT NULL,
                    workoutsThisMonth INTEGER NOT NULL,
                    totalVolumeLbs INTEGER NOT NULL,
                    streakWeeks INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS community_best_lifts (
                    memberId TEXT NOT NULL,
                    exerciseKey TEXT NOT NULL,
                    exerciseName TEXT NOT NULL,
                    weightLbs REAL NOT NULL,
                    PRIMARY KEY(memberId, exerciseKey)
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS community_gyms (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    area TEXT NOT NULL,
                    distanceMiles REAL NOT NULL,
                    crowdPercent INTEGER NOT NULL,
                    tags TEXT NOT NULL,
                    hours TEXT NOT NULL,
                    memberCount INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS community_posts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    authorId TEXT NOT NULL,
                    text TEXT NOT NULL,
                    workoutSummary TEXT,
                    gymId TEXT,
                    createdAt INTEGER NOT NULL,
                    likeCount INTEGER NOT NULL,
                    commentCount INTEGER NOT NULL,
                    likedByMe INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS community_conversations (
                    id TEXT NOT NULL,
                    memberId TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    lastReadAt INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
            """.trimIndent())
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_community_conversations_memberId " +
                    "ON community_conversations (memberId)"
            )
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS community_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    conversationId TEXT NOT NULL,
                    senderId TEXT NOT NULL,
                    text TEXT NOT NULL,
                    sentAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_community_messages_conversationId " +
                    "ON community_messages (conversationId)"
            )
        }
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

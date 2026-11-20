package com.redline.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.redline.app.community.local.CommunityConversationEntity
import com.redline.app.community.local.CommunityDao
import com.redline.app.community.local.CommunityGymEntity
import com.redline.app.community.local.CommunityLiftEntity
import com.redline.app.community.local.CommunityMemberEntity
import com.redline.app.community.local.CommunityMessageEntity
import com.redline.app.community.local.CommunityPostEntity
import com.redline.app.data.local.dao.SampleDataDao
import com.redline.app.data.local.dao.WorkoutDao
import com.redline.app.data.local.entity.CalorieEntry
import com.redline.app.data.local.entity.ExerciseSet
import com.redline.app.data.local.entity.MotivationalVideo
import com.redline.app.data.local.entity.PrTrack
import com.redline.app.data.local.entity.WorkoutSession

@Database(
    entities = [
        WorkoutSession::class, ExerciseSet::class, PrTrack::class, MotivationalVideo::class, CalorieEntry::class,
        CommunityMemberEntity::class, CommunityLiftEntity::class, CommunityGymEntity::class,
        CommunityPostEntity::class, CommunityConversationEntity::class, CommunityMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class RedlineDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun sampleDataDao(): SampleDataDao
    abstract fun communityDao(): CommunityDao
}

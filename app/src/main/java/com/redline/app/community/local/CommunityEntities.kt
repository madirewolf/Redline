package com.redline.app.community.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Tables behind LocalCommunityRepository. Added in database version 4; the
 * CREATE TABLE statements in AppModule's MIGRATION_3_4 must match these
 * exactly, or Room refuses to open an upgraded database.
 */

/** Members are other people. You are not a row here; your id is [ME]. */
@Entity(tableName = "community_members")
data class CommunityMemberEntity(
    @PrimaryKey val id: String,
    val name: String,
    val handle: String,
    val bio: String,
    val homeGymId: String?,
    val isFriend: Boolean,
    val workoutsThisWeek: Int,
    val workoutsThisMonth: Int,
    val totalVolumeLbs: Long,
    val streakWeeks: Int
)

@Entity(tableName = "community_best_lifts", primaryKeys = ["memberId", "exerciseKey"])
data class CommunityLiftEntity(
    val memberId: String,
    val exerciseKey: String,
    val exerciseName: String,
    val weightLbs: Float
)

@Entity(tableName = "community_gyms")
data class CommunityGymEntity(
    @PrimaryKey val id: String,
    val name: String,
    val area: String,
    val distanceMiles: Float,
    val crowdPercent: Int,
    /** Comma-separated. */
    val tags: String,
    val hours: String,
    val memberCount: Int
)

@Entity(tableName = "community_posts")
data class CommunityPostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val authorId: String,
    val text: String,
    val workoutSummary: String?,
    val gymId: String?,
    val createdAt: Long,
    val likeCount: Int,
    val commentCount: Int,
    val likedByMe: Boolean
)

/** One direct-message thread per member. */
@Entity(
    tableName = "community_conversations",
    indices = [Index(value = ["memberId"], unique = true)]
)
data class CommunityConversationEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val createdAt: Long,
    /** Messages from them after this are unread. */
    val lastReadAt: Long
)

@Entity(tableName = "community_messages", indices = [Index("conversationId")])
data class CommunityMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    /** A member id, or [ME]. */
    val senderId: String,
    val text: String,
    val sentAt: Long
)

/** The sender and author id used for you. */
const val ME = "me"

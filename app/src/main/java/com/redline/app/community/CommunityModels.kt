package com.redline.app.community

/*
 * What the Community screens work with. Nothing here knows where the data
 * lives, so a server-backed repository can produce the same types.
 */

/** Someone else on Redline: a friend or a suggestion. */
data class Member(
    val id: String,
    val name: String,
    val handle: String,
    val bio: String,
    /** Name of the gym they train at, if they set one. */
    val homeGym: String?,
    val isFriend: Boolean,
    val stats: AthleteStats
)

/** Training numbers two people can be compared on. Weights are in pounds. */
data class AthleteStats(
    val workoutsThisWeek: Int = 0,
    val workoutsThisMonth: Int = 0,
    val totalVolumeLbs: Double = 0.0,
    /** Weeks in a row with at least one workout. */
    val streakWeeks: Int = 0,
    val bestLifts: List<BestLift> = emptyList()
)

/** Heaviest weight moved on one lift, keyed like the rest of the app (see ExerciseCatalog.groupingKey). */
data class BestLift(
    val key: String,
    val name: String,
    val weightLbs: Float
)

data class PostAuthor(
    val id: String,
    val name: String,
    val handle: String,
    val isMe: Boolean
)

data class FeedPost(
    val id: String,
    val author: PostAuthor,
    val text: String,
    /** One line about a workout shared with the post, e.g. "Leg day · 16 sets · 14.2k lbs". */
    val workoutSummary: String?,
    val gymName: String?,
    val createdAt: Long,
    val likeCount: Int,
    val commentCount: Int,
    val likedByMe: Boolean
)

data class Gym(
    val id: String,
    val name: String,
    val area: String,
    val distanceMiles: Float,
    /** How full it usually is around now, 0 to 100. */
    val crowdPercent: Int,
    val tags: List<String>,
    val hours: String,
    /** People on Redline who train here. */
    val memberCount: Int,
    /** First names of your friends who train here. */
    val friendsHere: List<String>
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val fromMe: Boolean,
    val text: String,
    val sentAt: Long
)

/** A direct-message thread with one member. */
data class Conversation(
    val id: String,
    val member: Member,
    val lastMessage: ChatMessage?,
    val unreadCount: Int
)

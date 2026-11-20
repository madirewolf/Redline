package com.redline.app.community

import kotlinx.coroutines.flow.Flow

/**
 * Everything the Community tab reads and writes. [com.redline.app.community.local.LocalCommunityRepository]
 * answers from the phone's database with sample people; a server-backed one
 * (accounts, real friends, real delivery) can replace it behind this interface
 * without the screens changing.
 *
 * Flows emit again whenever the data changes, so a sent message or a new post
 * shows up without a refresh.
 */
interface CommunityRepository {

    /** Posts from everyone, newest first. */
    fun feed(): Flow<List<FeedPost>>

    /** Gyms nearby, nearest first. */
    fun gyms(): Flow<List<Gym>>

    /** People you've added, by name. */
    fun friends(): Flow<List<Member>>

    /** People you haven't added yet, by name. */
    fun suggestions(): Flow<List<Member>>

    /** One member, or null if there's no such person. */
    fun member(id: String): Flow<Member?>

    /** Threads with at least one message, most recent activity first. */
    fun conversations(): Flow<List<Conversation>>

    /** One thread, including one with no messages yet. */
    fun conversation(id: String): Flow<Conversation?>

    /** A thread's messages, oldest first. */
    fun messages(conversationId: String): Flow<List<ChatMessage>>

    /** Sends [text] to the thread. Blank text is ignored. */
    suspend fun sendMessage(conversationId: String, text: String)

    /** The direct-message thread with [memberId], started if there isn't one. Returns its id. */
    suspend fun openConversation(memberId: String): String

    /** Marks everything in the thread as seen. */
    suspend fun markRead(conversationId: String)

    /** Posts [text] to the feed as you, optionally with a workout line attached. Blank text is ignored. */
    suspend fun createPost(text: String, workoutSummary: String?)

    /** Likes the post, or takes the like back. */
    suspend fun toggleLike(postId: String)

    suspend fun addFriend(memberId: String)
}

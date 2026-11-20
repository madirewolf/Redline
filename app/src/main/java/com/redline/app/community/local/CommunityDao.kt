package com.redline.app.community.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Plain reads and writes. Sorting and joining happen in LocalCommunityRepository,
 * where they can be unit tested without a database.
 */
@Dao
abstract class CommunityDao {

    @Query("SELECT COUNT(*) FROM community_members")
    abstract suspend fun memberCount(): Int

    @Query("SELECT * FROM community_members")
    abstract fun members(): Flow<List<CommunityMemberEntity>>

    @Query("SELECT * FROM community_best_lifts")
    abstract fun lifts(): Flow<List<CommunityLiftEntity>>

    @Query("SELECT * FROM community_gyms")
    abstract fun gyms(): Flow<List<CommunityGymEntity>>

    @Query("SELECT * FROM community_posts")
    abstract fun posts(): Flow<List<CommunityPostEntity>>

    @Query("SELECT * FROM community_conversations")
    abstract fun conversations(): Flow<List<CommunityConversationEntity>>

    @Query("SELECT * FROM community_messages")
    abstract fun allMessages(): Flow<List<CommunityMessageEntity>>

    @Query("SELECT * FROM community_messages WHERE conversationId = :conversationId")
    abstract fun messagesIn(conversationId: String): Flow<List<CommunityMessageEntity>>

    @Query("SELECT * FROM community_posts WHERE id = :id")
    abstract suspend fun post(id: Long): CommunityPostEntity?

    @Query("SELECT * FROM community_conversations WHERE id = :id")
    abstract suspend fun conversation(id: String): CommunityConversationEntity?

    @Query("SELECT * FROM community_conversations WHERE memberId = :memberId LIMIT 1")
    abstract suspend fun conversationWith(memberId: String): CommunityConversationEntity?

    @Query("SELECT * FROM community_members WHERE id = :id")
    abstract suspend fun member(id: String): CommunityMemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMembers(members: List<CommunityMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLifts(lifts: List<CommunityLiftEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertGyms(gyms: List<CommunityGymEntity>)

    @Insert
    abstract suspend fun insertPosts(posts: List<CommunityPostEntity>)

    @Insert
    abstract suspend fun insertPost(post: CommunityPostEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertConversations(conversations: List<CommunityConversationEntity>)

    @Insert
    abstract suspend fun insertMessages(messages: List<CommunityMessageEntity>)

    @Insert
    abstract suspend fun insertMessage(message: CommunityMessageEntity): Long

    @Update
    abstract suspend fun updatePost(post: CommunityPostEntity)

    @Query("UPDATE community_members SET isFriend = 1 WHERE id = :id")
    abstract suspend fun markFriend(id: String)

    @Query("UPDATE community_conversations SET lastReadAt = :at WHERE id = :id")
    abstract suspend fun markRead(id: String, at: Long)

    /** All of the sample content in one go, so a crash halfway can't leave half of it. */
    @Transaction
    open suspend fun seed(seed: CommunitySeed) {
        insertGyms(seed.gyms)
        insertMembers(seed.members)
        insertLifts(seed.lifts)
        insertPosts(seed.posts)
        insertConversations(seed.conversations)
        insertMessages(seed.messages)
    }
}

package com.redline.app.community.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** CommunityDao kept in memory, with the same conflict rules as the Room tables. */
internal class FakeCommunityDao : CommunityDao() {

    val memberRows = MutableStateFlow<List<CommunityMemberEntity>>(emptyList())
    val liftRows = MutableStateFlow<List<CommunityLiftEntity>>(emptyList())
    val gymRows = MutableStateFlow<List<CommunityGymEntity>>(emptyList())
    val postRows = MutableStateFlow<List<CommunityPostEntity>>(emptyList())
    val conversationRows = MutableStateFlow<List<CommunityConversationEntity>>(emptyList())
    val messageRows = MutableStateFlow<List<CommunityMessageEntity>>(emptyList())

    var seedCalls = 0
        private set

    private var lastPostId = 0L
    private var lastMessageId = 0L

    override suspend fun memberCount(): Int = memberRows.value.size

    override fun members(): Flow<List<CommunityMemberEntity>> = memberRows
    override fun lifts(): Flow<List<CommunityLiftEntity>> = liftRows
    override fun gyms(): Flow<List<CommunityGymEntity>> = gymRows
    override fun posts(): Flow<List<CommunityPostEntity>> = postRows
    override fun conversations(): Flow<List<CommunityConversationEntity>> = conversationRows
    override fun allMessages(): Flow<List<CommunityMessageEntity>> = messageRows

    override fun messagesIn(conversationId: String): Flow<List<CommunityMessageEntity>> =
        messageRows.map { rows -> rows.filter { it.conversationId == conversationId } }

    override suspend fun post(id: Long): CommunityPostEntity? = postRows.value.firstOrNull { it.id == id }

    override suspend fun conversation(id: String): CommunityConversationEntity? =
        conversationRows.value.firstOrNull { it.id == id }

    override suspend fun conversationWith(memberId: String): CommunityConversationEntity? =
        conversationRows.value.firstOrNull { it.memberId == memberId }

    override suspend fun member(id: String): CommunityMemberEntity? = memberRows.value.firstOrNull { it.id == id }

    override suspend fun insertMembers(members: List<CommunityMemberEntity>) {
        val ids = members.map { it.id }.toSet()
        memberRows.value = memberRows.value.filterNot { it.id in ids } + members
    }

    override suspend fun insertLifts(lifts: List<CommunityLiftEntity>) {
        val keys = lifts.map { it.memberId to it.exerciseKey }.toSet()
        liftRows.value = liftRows.value.filterNot { (it.memberId to it.exerciseKey) in keys } + lifts
    }

    override suspend fun insertGyms(gyms: List<CommunityGymEntity>) {
        val ids = gyms.map { it.id }.toSet()
        gymRows.value = gymRows.value.filterNot { it.id in ids } + gyms
    }

    override suspend fun insertPosts(posts: List<CommunityPostEntity>) {
        posts.forEach { insertPost(it) }
    }

    override suspend fun insertPost(post: CommunityPostEntity): Long {
        val id = if (post.id == 0L) lastPostId + 1 else post.id
        require(postRows.value.none { it.id == id }) { "post $id already exists" }
        lastPostId = maxOf(lastPostId, id)
        postRows.value = postRows.value + post.copy(id = id)
        return id
    }

    /** IGNORE on conflict, including the unique index on memberId. */
    override suspend fun insertConversations(conversations: List<CommunityConversationEntity>) {
        var rows = conversationRows.value
        conversations.forEach { new ->
            if (rows.none { it.id == new.id || it.memberId == new.memberId }) rows = rows + new
        }
        conversationRows.value = rows
    }

    override suspend fun insertMessages(messages: List<CommunityMessageEntity>) {
        messages.forEach { insertMessage(it) }
    }

    override suspend fun insertMessage(message: CommunityMessageEntity): Long {
        val id = if (message.id == 0L) lastMessageId + 1 else message.id
        require(messageRows.value.none { it.id == id }) { "message $id already exists" }
        lastMessageId = maxOf(lastMessageId, id)
        messageRows.value = messageRows.value + message.copy(id = id)
        return id
    }

    override suspend fun updatePost(post: CommunityPostEntity) {
        postRows.value = postRows.value.map { if (it.id == post.id) post else it }
    }

    override suspend fun markFriend(id: String) {
        memberRows.value = memberRows.value.map { if (it.id == id) it.copy(isFriend = true) else it }
    }

    override suspend fun markRead(id: String, at: Long) {
        conversationRows.value = conversationRows.value.map { if (it.id == id) it.copy(lastReadAt = at) else it }
    }

    override suspend fun seed(seed: CommunitySeed) {
        seedCalls++
        super.seed(seed)
    }
}

package com.redline.app.community.local

import com.redline.app.community.AthleteStats
import com.redline.app.community.BestLift
import com.redline.app.community.ChatMessage
import com.redline.app.community.CommunityRepository
import com.redline.app.community.Conversation
import com.redline.app.community.FeedPost
import com.redline.app.community.Gym
import com.redline.app.community.Member
import com.redline.app.community.PostAuthor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Community on this phone only: sample people, gyms and posts written to the
 * database the first time the tab opens, plus whatever you post or send.
 * Nobody receives your messages and nobody replies; that needs a server.
 */
@Singleton
class LocalCommunityRepository internal constructor(
    private val dao: CommunityDao,
    private val clock: () -> Long
) : CommunityRepository {

    @Inject
    constructor(dao: CommunityDao) : this(dao, System::currentTimeMillis)

    private val seedLock = Mutex()
    private val writeLock = Mutex()

    @Volatile
    private var seeded = false

    /** Writes the sample content once, on an empty database. */
    private suspend fun ensureSeeded() {
        if (seeded) return
        seedLock.withLock {
            if (seeded) return
            if (dao.memberCount() == 0) dao.seed(sampleCommunity(clock()))
            seeded = true
        }
    }

    private fun allMembers(): Flow<List<Member>> =
        combine(dao.members(), dao.lifts(), dao.gyms(), ::toMembers)
            .onStart { ensureSeeded() }

    override fun feed(): Flow<List<FeedPost>> =
        combine(dao.posts(), dao.members(), dao.gyms(), ::toFeed)
            .onStart { ensureSeeded() }

    override fun gyms(): Flow<List<Gym>> =
        combine(dao.gyms(), dao.members(), ::toGyms)
            .onStart { ensureSeeded() }

    override fun friends(): Flow<List<Member>> =
        allMembers().map { members -> members.filter { it.isFriend } }.distinctUntilChanged()

    override fun suggestions(): Flow<List<Member>> =
        allMembers().map { members -> members.filterNot { it.isFriend } }.distinctUntilChanged()

    override fun member(id: String): Flow<Member?> =
        allMembers().map { members -> members.firstOrNull { it.id == id } }.distinctUntilChanged()

    // Seed before reading any table, so the first list isn't threads from an empty database.
    override fun conversations(): Flow<List<Conversation>> =
        combine(dao.conversations(), dao.allMessages(), allMembers(), ::toConversations)
            .onStart { ensureSeeded() }
            .map { threads -> threads.filter { it.lastMessage != null } }
            .distinctUntilChanged()

    override fun conversation(id: String): Flow<Conversation?> =
        combine(dao.conversations(), dao.allMessages(), allMembers(), ::toConversations)
            .onStart { ensureSeeded() }
            .map { threads -> threads.firstOrNull { it.id == id } }
            .distinctUntilChanged()

    override fun messages(conversationId: String): Flow<List<ChatMessage>> =
        dao.messagesIn(conversationId)
            .map(::toChatMessages)
            .onStart { ensureSeeded() }

    override suspend fun sendMessage(conversationId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        ensureSeeded()
        writeLock.withLock {
            dao.conversation(conversationId) ?: return
            val now = clock()
            dao.insertMessage(
                CommunityMessageEntity(conversationId = conversationId, senderId = ME, text = clean, sentAt = now)
            )
            // Replying means you've seen what came before.
            dao.markRead(conversationId, now)
        }
    }

    override suspend fun openConversation(memberId: String): String {
        ensureSeeded()
        return writeLock.withLock {
            dao.conversationWith(memberId)?.id ?: directThreadId(memberId).also { id ->
                val now = clock()
                dao.insertConversations(listOf(CommunityConversationEntity(id, memberId, createdAt = now, lastReadAt = now)))
            }
        }
    }

    override suspend fun markRead(conversationId: String) {
        dao.markRead(conversationId, clock())
    }

    override suspend fun createPost(text: String, workoutSummary: String?) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        ensureSeeded()
        dao.insertPost(
            CommunityPostEntity(
                authorId = ME,
                text = clean,
                workoutSummary = workoutSummary?.trim()?.takeIf { it.isNotEmpty() },
                gymId = null,
                createdAt = clock(),
                likeCount = 0,
                commentCount = 0,
                likedByMe = false
            )
        )
    }

    override suspend fun toggleLike(postId: String) {
        val id = postId.toLongOrNull() ?: return
        // Read and write under one lock so a quick double tap lands as two toggles.
        writeLock.withLock {
            val post = dao.post(id) ?: return
            dao.updatePost(post.withLikeToggled())
        }
    }

    override suspend fun addFriend(memberId: String) {
        ensureSeeded()
        dao.markFriend(memberId)
    }
}

/* Mapping from rows to the screens' types. Plain functions, so tests can call them. */

internal fun CommunityPostEntity.withLikeToggled(): CommunityPostEntity = copy(
    likedByMe = !likedByMe,
    likeCount = (likeCount + if (likedByMe) -1 else 1).coerceAtLeast(0)
)

internal fun toMembers(
    members: List<CommunityMemberEntity>,
    lifts: List<CommunityLiftEntity>,
    gyms: List<CommunityGymEntity>
): List<Member> {
    val liftsByMember = lifts.groupBy { it.memberId }
    val gymNames = gyms.associate { it.id to it.name }
    return members
        .map { row ->
            Member(
                id = row.id,
                name = row.name,
                handle = row.handle,
                bio = row.bio,
                homeGym = row.homeGymId?.let(gymNames::get),
                isFriend = row.isFriend,
                stats = AthleteStats(
                    workoutsThisWeek = row.workoutsThisWeek,
                    workoutsThisMonth = row.workoutsThisMonth,
                    totalVolumeLbs = row.totalVolumeLbs.toDouble(),
                    streakWeeks = row.streakWeeks,
                    bestLifts = liftsByMember[row.id].orEmpty()
                        .map { BestLift(it.exerciseKey, it.exerciseName, it.weightLbs) }
                        .sortedByDescending { it.weightLbs }
                )
            )
        }
        .sortedBy { it.name.lowercase() }
}

internal fun toFeed(
    posts: List<CommunityPostEntity>,
    members: List<CommunityMemberEntity>,
    gyms: List<CommunityGymEntity>
): List<FeedPost> {
    val byId = members.associateBy { it.id }
    val gymNames = gyms.associate { it.id to it.name }
    return posts
        .sortedWith(compareByDescending<CommunityPostEntity> { it.createdAt }.thenByDescending { it.id })
        .mapNotNull { row ->
            val author = if (row.authorId == ME) {
                PostAuthor(ME, name = "You", handle = "you", isMe = true)
            } else {
                val member = byId[row.authorId] ?: return@mapNotNull null
                PostAuthor(member.id, member.name, member.handle, isMe = false)
            }
            FeedPost(
                id = row.id.toString(),
                author = author,
                text = row.text,
                workoutSummary = row.workoutSummary,
                gymName = row.gymId?.let(gymNames::get),
                createdAt = row.createdAt,
                likeCount = row.likeCount,
                commentCount = row.commentCount,
                likedByMe = row.likedByMe
            )
        }
}

internal fun toGyms(gyms: List<CommunityGymEntity>, members: List<CommunityMemberEntity>): List<Gym> {
    val friendsByGym = members.filter { it.isFriend && it.homeGymId != null }.groupBy { it.homeGymId }
    return gyms
        .sortedBy { it.distanceMiles }
        .map { row ->
            Gym(
                id = row.id,
                name = row.name,
                area = row.area,
                distanceMiles = row.distanceMiles,
                crowdPercent = row.crowdPercent.coerceIn(0, 100),
                tags = row.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                hours = row.hours,
                memberCount = row.memberCount,
                friendsHere = friendsByGym[row.id].orEmpty().map { it.name }.sorted()
            )
        }
}

/** Oldest first. Messages sent in the same millisecond keep the order they were saved in. */
internal fun toChatMessages(rows: List<CommunityMessageEntity>): List<ChatMessage> =
    rows.sortedWith(compareBy<CommunityMessageEntity> { it.sentAt }.thenBy { it.id })
        .map { row ->
            ChatMessage(
                id = row.id.toString(),
                conversationId = row.conversationId,
                fromMe = row.senderId == ME,
                text = row.text,
                sentAt = row.sentAt
            )
        }

/** Every thread whose member still exists, most recent activity first, with last message and unread count. */
internal fun toConversations(
    conversations: List<CommunityConversationEntity>,
    messages: List<CommunityMessageEntity>,
    members: List<Member>
): List<Conversation> {
    val byConversation = messages.groupBy { it.conversationId }
    val membersById = members.associateBy { it.id }
    return conversations
        .mapNotNull { row ->
            val member = membersById[row.memberId] ?: return@mapNotNull null
            val thread = toChatMessages(byConversation[row.id].orEmpty())
            Conversation(
                id = row.id,
                member = member,
                lastMessage = thread.lastOrNull(),
                unreadCount = thread.count { !it.fromMe && it.sentAt > row.lastReadAt }
            )
        }
        .sortedWith(
            compareByDescending<Conversation> { it.lastMessage?.sentAt ?: Long.MIN_VALUE }
                .thenBy { it.member.name.lowercase() }
        )
}

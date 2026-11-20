package com.redline.app.community.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityMappingTest {

    private fun member(id: String, name: String, gymId: String? = null, friend: Boolean = true) =
        CommunityMemberEntity(id, name, id, "", gymId, friend, 0, 0, 0, 0)

    private fun gym(id: String, miles: Float, crowd: Int = 50, tags: String = "") =
        CommunityGymEntity(id, id.uppercase(), "Area", miles, crowd, tags, "Open", 10)

    private fun message(id: Long, conversationId: String, senderId: String, at: Long) =
        CommunityMessageEntity(id, conversationId, senderId, "m$id", at)

    @Test
    fun `messages sort by time, then by the order they were saved`() {
        val rows = listOf(
            message(3, "dm-a", "a", 100),
            message(1, "dm-a", ME, 200),
            message(2, "dm-a", "a", 100)
        )

        val thread = toChatMessages(rows)

        assertEquals(listOf("2", "3", "1"), thread.map { it.id })
        assertEquals(listOf(false, false, true), thread.map { it.fromMe })
    }

    @Test
    fun `a like toggles on and off and never goes below zero`() {
        val post = CommunityPostEntity(1, "a", "Hi", null, null, 0, likeCount = 4, commentCount = 0, likedByMe = false)

        val liked = post.withLikeToggled()
        assertTrue(liked.likedByMe)
        assertEquals(5, liked.likeCount)
        assertEquals(post, liked.withLikeToggled())

        val odd = post.copy(likeCount = 0, likedByMe = true).withLikeToggled()
        assertFalse(odd.likedByMe)
        assertEquals(0, odd.likeCount)
    }

    @Test
    fun `gyms come nearest first with clean tags and friends who train there`() {
        val gyms = listOf(gym("far", 3f, crowd = 140, tags = "Pool, ,Sauna,"), gym("near", 0.5f, crowd = -5))
        val members = listOf(
            member("zoe", "Zoe", "far"),
            member("amir", "Amir", "far"),
            member("kai", "Kai", "far", friend = false),
            member("ned", "Ned", null)
        )

        val result = toGyms(gyms, members)

        assertEquals(listOf("near", "far"), result.map { it.id })
        assertEquals(listOf("Pool", "Sauna"), result[1].tags)
        assertEquals(100, result[1].crowdPercent)
        assertEquals(0, result[0].crowdPercent)
        assertEquals(listOf("Amir", "Zoe"), result[1].friendsHere)
        assertEquals(emptyList<String>(), result[0].friendsHere)
    }

    @Test
    fun `your posts are from You and posts by unknown people are dropped`() {
        val posts = listOf(
            CommunityPostEntity(1, "ghost", "Boo", null, null, 300, 0, 0, false),
            CommunityPostEntity(2, ME, "Mine", "Push · 3 sets", "g", 200, 0, 0, false),
            CommunityPostEntity(3, "a", "Theirs", null, "missing", 100, 0, 0, false)
        )

        val feed = toFeed(posts, listOf(member("a", "Ana")), listOf(gym("g", 1f)))

        assertEquals(listOf("2", "3"), feed.map { it.id })
        assertTrue(feed[0].author.isMe)
        assertEquals("You", feed[0].author.name)
        assertEquals("G", feed[0].gymName)
        assertEquals("Ana", feed[1].author.name)
        assertNull(feed[1].gymName)
    }

    @Test
    fun `members are sorted by name and know their gym`() {
        val members = toMembers(
            listOf(member("b", "bea", "g"), member("a", "Ana", "missing")),
            listOf(
                CommunityLiftEntity("b", "bench_press", "Bench Press", 100f),
                CommunityLiftEntity("b", "deadlift", "Deadlift", 200f)
            ),
            listOf(gym("g", 1f))
        )

        assertEquals(listOf("Ana", "bea"), members.map { it.name })
        assertNull(members[0].homeGym)
        assertEquals("G", members[1].homeGym)
        assertEquals(listOf("deadlift", "bench_press"), members[1].stats.bestLifts.map { it.key })
    }

    @Test
    fun `only their messages after you last read count as unread`() {
        val people = toMembers(listOf(member("a", "Ana"), member("b", "Ben")), emptyList(), emptyList())
        val threads = listOf(
            CommunityConversationEntity("dm-a", "a", createdAt = 0, lastReadAt = 150),
            CommunityConversationEntity("dm-b", "b", createdAt = 0, lastReadAt = 0),
            CommunityConversationEntity("dm-gone", "gone", createdAt = 0, lastReadAt = 0)
        )
        val messages = listOf(
            message(1, "dm-a", "a", 100),
            message(2, "dm-a", "a", 200),
            message(3, "dm-a", ME, 300),
            message(4, "dm-a", "a", 250),
            message(5, "dm-gone", "gone", 900)
        )

        val result = toConversations(threads, messages, people)

        // The thread with someone who's gone is dropped; an empty one sorts last.
        assertEquals(listOf("dm-a", "dm-b"), result.map { it.id })
        assertEquals(2, result[0].unreadCount)
        assertEquals("3", result[0].lastMessage?.id)
        assertNull(result[1].lastMessage)
        assertEquals(0, result[1].unreadCount)
    }

    @Test
    fun `the sample content hangs together`() {
        val seed = sampleCommunity(1_800_000_000_000L)
        val memberIds = seed.members.map { it.id }.toSet()
        val gymIds = seed.gyms.map { it.id }.toSet()
        val threadIds = seed.conversations.map { it.id }.toSet()

        assertEquals(10, seed.posts.size)
        assertTrue(seed.posts.all { it.authorId in memberIds && it.gymId in gymIds })
        assertTrue(seed.members.all { it.homeGymId in gymIds })
        assertTrue(seed.lifts.all { it.memberId in memberIds })
        assertTrue(seed.conversations.all { it.memberId in memberIds && it.id == directThreadId(it.memberId) })
        assertTrue(seed.messages.all { it.conversationId in threadIds && (it.senderId == ME || it.senderId in memberIds) })
        assertEquals(seed.members.size, seed.members.map { it.handle }.toSet().size)
        assertEquals(seed.lifts.size, seed.lifts.map { it.memberId to it.exerciseKey }.toSet().size)
        assertTrue(seed.members.any { it.isFriend } && seed.members.any { !it.isFriend })
        assertTrue(seed.posts.all { it.createdAt <= 1_800_000_000_000L })
    }
}

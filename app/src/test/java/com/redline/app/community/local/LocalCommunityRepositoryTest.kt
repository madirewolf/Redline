package com.redline.app.community.local

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCommunityRepositoryTest {

    private val dao = FakeCommunityDao()
    private var now = START
    private val repo = LocalCommunityRepository(dao) { now }

    private suspend fun messagesIn(id: String) = repo.messages(id).first()
    private suspend fun thread(id: String) = repo.conversation(id).first()

    /* Seeding */

    @Test
    fun `sample content is written once, the first time something is read`() = runBlocking {
        assertEquals(0, dao.seedCalls)

        repo.feed().first()
        repo.gyms().first()
        repo.conversations().first()
        // A new repository over the same database finds it filled already.
        LocalCommunityRepository(dao) { now }.friends().first()

        assertEquals(1, dao.seedCalls)
        assertEquals(10, repo.feed().first().size)
    }

    @Test
    fun `a database that already has people isn't seeded again`() = runBlocking {
        dao.insertMembers(listOf(sampleCommunity(START).members.first()))

        repo.feed().first()

        assertEquals(0, dao.seedCalls)
    }

    @Test
    fun `the first list of threads already has the sample threads`() = runBlocking {
        assertEquals(listOf("dm-maya", "dm-priya", "dm-theo"), repo.conversations().first().map { it.id })
    }

    /* Messages */

    @Test
    fun `sample threads start with unread messages from them`() = runBlocking {
        val unread = repo.conversations().first().associate { it.id to it.unreadCount }

        assertEquals(mapOf("dm-maya" to 2, "dm-priya" to 1, "dm-theo" to 0), unread)
    }

    @Test
    fun `messages read oldest first`() = runBlocking {
        val thread = messagesIn("dm-maya")

        assertEquals(thread.sortedBy { it.sentAt }, thread)
        assertEquals("Want to train together Saturday at Ironworks?", thread.last().text)
    }

    @Test
    fun `messages sent in the same millisecond stay in the order they were sent`() = runBlocking {
        repo.sendMessage("dm-theo", "one")
        repo.sendMessage("dm-theo", "two")
        repo.sendMessage("dm-theo", "three")

        assertEquals(listOf("one", "two", "three"), messagesIn("dm-theo").takeLast(3).map { it.text })
    }

    @Test
    fun `a sent message shows up at once and marks the thread read`() = runBlocking {
        now += MINUTE

        repo.sendMessage("dm-maya", "  See you Saturday  ")

        val last = messagesIn("dm-maya").last()
        assertEquals("See you Saturday", last.text)
        assertTrue(last.fromMe)
        assertEquals(now, last.sentAt)
        val maya = thread("dm-maya")!!
        assertEquals(0, maya.unreadCount)
        assertEquals(last, maya.lastMessage)
    }

    @Test
    fun `the thread you last wrote in moves to the top`() = runBlocking {
        now += MINUTE

        repo.sendMessage("dm-theo", "Upper/lower works well")

        assertEquals(listOf("dm-theo", "dm-maya", "dm-priya"), repo.conversations().first().map { it.id })
    }

    @Test
    fun `blank messages and unknown threads are ignored`() = runBlocking {
        repo.feed().first()
        val before = dao.messageRows.value.size

        repo.sendMessage("dm-maya", "   \n  ")
        repo.sendMessage("dm-nobody", "Hello?")

        assertEquals(before, dao.messageRows.value.size)
        assertNull(thread("dm-nobody"))
    }

    @Test
    fun `opening a thread marks it read`() = runBlocking {
        assertEquals(2, thread("dm-maya")!!.unreadCount)
        now += MINUTE

        repo.markRead("dm-maya")

        assertEquals(0, thread("dm-maya")!!.unreadCount)
        assertEquals(1, thread("dm-priya")!!.unreadCount)
    }

    @Test
    fun `messaging someone again reuses their thread`() = runBlocking {
        assertEquals("dm-maya", repo.openConversation("maya"))
        assertEquals(3, dao.conversationRows.value.size)

        val first = repo.openConversation("marcus")
        val second = repo.openConversation("marcus")

        assertEquals("dm-marcus", first)
        assertEquals(first, second)
        assertEquals(4, dao.conversationRows.value.size)
    }

    @Test
    fun `a new thread is hidden from the list until it has a message`() = runBlocking {
        val id = repo.openConversation("marcus")

        assertFalse(repo.conversations().first().any { it.id == id })
        val empty = thread(id)
        assertNotNull(empty)
        assertEquals("Marcus", empty!!.member.name)
        assertNull(empty.lastMessage)

        now += MINUTE
        repo.sendMessage(id, "Still need a 6 AM partner?")

        assertEquals(id, repo.conversations().first().first().id)
    }

    /* Feed */

    @Test
    fun `a new post goes to the top of the feed`() = runBlocking {
        now += MINUTE

        repo.createPost("  Deadlift day  ", "  Pull day · 10 sets  ")

        val top = repo.feed().first().first()
        assertTrue(top.author.isMe)
        assertEquals("Deadlift day", top.text)
        assertEquals("Pull day · 10 sets", top.workoutSummary)
        assertEquals(now, top.createdAt)
        assertEquals(0, top.likeCount)
        assertEquals(11, repo.feed().first().size)
    }

    @Test
    fun `posts made in the same millisecond show the newest first`() = runBlocking {
        repo.createPost("First", null)
        repo.createPost("Second", "   ")

        val feed = repo.feed().first()
        assertEquals(listOf("Second", "First"), feed.take(2).map { it.text })
        assertNull(feed.first().workoutSummary)
    }

    @Test
    fun `a blank post is ignored`() = runBlocking {
        repo.createPost("   ", "Leg day")

        assertEquals(10, repo.feed().first().size)
    }

    @Test
    fun `liking twice puts the count back`() = runBlocking {
        val post = repo.feed().first().first()

        repo.toggleLike(post.id)
        val liked = repo.feed().first().first { it.id == post.id }
        repo.toggleLike(post.id)
        val unliked = repo.feed().first().first { it.id == post.id }

        assertTrue(liked.likedByMe)
        assertEquals(post.likeCount + 1, liked.likeCount)
        assertFalse(unliked.likedByMe)
        assertEquals(post.likeCount, unliked.likeCount)
    }

    @Test
    fun `liking a post that doesn't exist does nothing`() = runBlocking {
        val before = repo.feed().first()

        repo.toggleLike("not-a-number")
        repo.toggleLike("99999")

        assertEquals(before, repo.feed().first())
    }

    /* People */

    @Test
    fun `friends and suggestions are split and sorted by name`() = runBlocking {
        assertEquals(listOf("Jonah", "Maya", "Priya", "Sofia", "Theo"), repo.friends().first().map { it.name })
        assertEquals(listOf("Dev", "Lena", "Marcus", "Rosa"), repo.suggestions().first().map { it.name })
    }

    @Test
    fun `adding a friend moves them from suggestions to friends`() = runBlocking {
        repo.addFriend("marcus")

        assertTrue(repo.friends().first().any { it.id == "marcus" })
        assertFalse(repo.suggestions().first().any { it.id == "marcus" })
        assertTrue(repo.member("marcus").first()!!.isFriend)
    }

    @Test
    fun `a member comes with their gym and heaviest lifts first`() = runBlocking {
        val maya = repo.member("maya").first()!!

        assertEquals("Ironworks Barbell Club", maya.homeGym)
        assertEquals("deadlift", maya.stats.bestLifts.first().key)
        assertEquals(maya.stats.bestLifts.sortedByDescending { it.weightLbs }, maya.stats.bestLifts)
    }

    @Test
    fun `someone who isn't there is null`() = runBlocking {
        assertNull(repo.member("nobody").first())
    }

    private companion object {
        const val START = 1_800_000_000_000L
        const val MINUTE = 60_000L
    }
}

package com.redline.app.ui

import com.redline.app.data.local.entity.MotivationalVideo
import com.redline.app.ui.motivation.MotivationViewModel
import com.redline.app.ui.music.MusicViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkValidationTest {

    @Test
    fun `music accepts spotify uris and web links`() {
        assertTrue(MusicViewModel.isTrackLink("spotify:track:4uLU6hMCjMI75M1A2tKUQC"))
        assertTrue(MusicViewModel.isTrackLink(" https://open.spotify.com/track/abc "))
        assertFalse(MusicViewModel.isTrackLink("my gym playlist"))
    }

    @Test
    fun `videos need a full web link`() {
        assertTrue(MotivationViewModel.isVideoLink("https://youtu.be/dQw4w9WgXcQ"))
        assertFalse(MotivationViewModel.isVideoLink("youtube.com/watch?v=1"))
        assertFalse(MotivationViewModel.isVideoLink("https://"))
        assertTrue(MotivationViewModel.isYouTube("https://www.youtube.com/watch?v=1"))
        assertFalse(MotivationViewModel.isYouTube("https://vimeo.com/1"))
    }

    @Test
    fun `youtube search pages open like any other video link`() {
        val search = "https://www.youtube.com/results?search_query=gym+motivation+speech"
        assertTrue(MotivationViewModel.isVideoLink(search))
        assertTrue(MotivationViewModel.isYouTube(search))
        assertTrue(MotivationViewModel.isYouTubeSearch(search))
        assertFalse(MotivationViewModel.isYouTubeSearch("https://www.youtube.com/watch?v=1"))
        assertEquals("YouTube search", MotivationViewModel.sourceLabel(video(search, "youtube")))
        assertEquals("YouTube", MotivationViewModel.sourceLabel(video("https://youtu.be/1", "youtube")))
        assertEquals("vimeo.com", MotivationViewModel.sourceLabel(video("https://www.vimeo.com/1", "mp4")))
        assertEquals("Video", MotivationViewModel.sourceLabel(video("https://exa mple", "mp4")))
    }

    private fun video(url: String, type: String) = MotivationalVideo(title = "t", url = url, type = type)
}

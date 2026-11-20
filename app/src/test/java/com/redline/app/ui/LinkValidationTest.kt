package com.redline.app.ui

import com.redline.app.ui.motivation.MotivationViewModel
import com.redline.app.ui.music.MusicViewModel
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
}

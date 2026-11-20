package com.redline.app.ui

import com.redline.app.ui.components.cleanNum
import com.redline.app.ui.components.compact
import com.redline.app.ui.components.formatClock
import com.redline.app.ui.components.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `durations read as minutes, never zero`() {
        assertEquals("<1 min", formatDuration(48))
        assertEquals("1 min", formatDuration(60))
        assertEquals("42 min", formatDuration(42 * 60 + 59))
        assertEquals("1 h", formatDuration(3600))
        assertEquals("1 h 5 min", formatDuration(3900))
    }

    @Test
    fun `clock drops the hour until it is needed`() {
        assertEquals("0:42", formatClock(42))
        assertEquals("21:14", formatClock(21 * 60 + 14))
        assertEquals("1:05:09", formatClock(3909))
    }

    @Test
    fun `numbers stay short`() {
        assertEquals("185", 185f.cleanNum())
        assertEquals("182.5", 182.5f.cleanNum())
        assertEquals("8,980", 8980L.compact())
        assertEquals("45.9k", 45_900L.compact())
    }
}

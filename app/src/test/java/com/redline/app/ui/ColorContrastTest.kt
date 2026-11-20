package com.redline.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.redline.app.ui.theme.RedlineDarkColors
import com.redline.app.ui.theme.RedlineLightColors
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.x contrast for every text and icon pairing the screens use, in both themes. */
class ColorContrastTest {

    @Test
    fun `dark scheme text clears AA`() = assertText(RedlineDarkColors, "dark")

    @Test
    fun `light scheme text clears AA`() = assertText(RedlineLightColors, "light")

    @Test
    fun `outlines stay visible on cards`() {
        listOf(RedlineDarkColors to "dark", RedlineLightColors to "light").forEach { (s, name) ->
            val ratio = contrast(s.outline, s.surfaceContainer)
            assertTrue("$name outline on card is ${"%.2f".format(ratio)}:1", ratio >= 3.0)
        }
    }

    private fun assertText(s: ColorScheme, name: String) {
        val pairs = mapOf(
            "onSurface / surface" to (s.onSurface to s.surface),
            "onSurface / card" to (s.onSurface to s.surfaceContainer),
            "onSurface / dialog" to (s.onSurface to s.surfaceContainerHigh),
            "muted / surface" to (s.onSurfaceVariant to s.surface),
            "muted / card" to (s.onSurfaceVariant to s.surfaceContainer),
            "muted / dialog" to (s.onSurfaceVariant to s.surfaceContainerHigh),
            "primary / surface" to (s.primary to s.surface),
            "primary / card" to (s.primary to s.surfaceContainer),
            "primary / dialog" to (s.primary to s.surfaceContainerHigh),
            "onPrimary / primary" to (s.onPrimary to s.primary),
            "onPrimaryContainer / primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
            "onSecondaryContainer / secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
            "onTertiaryContainer / tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
            "error / card" to (s.error to s.surfaceContainer),
            "onErrorContainer / errorContainer" to (s.onErrorContainer to s.errorContainer)
        )
        val failures = pairs.mapNotNull { (label, pair) ->
            val ratio = contrast(pair.first, pair.second)
            if (ratio < 4.5) "$label ${"%.2f".format(ratio)}:1" else null
        }
        assertTrue("$name scheme below 4.5:1: $failures", failures.isEmpty())
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double =
            if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }
}

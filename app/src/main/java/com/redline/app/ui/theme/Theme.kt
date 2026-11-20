package com.redline.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalStatusColors = staticCompositionLocalOf { DarkStatusColors }

/** Follows the system light/dark setting. System bar icons are handled by enableEdgeToEdge. */
@Composable
fun RedlineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) RedlineDarkColors else RedlineLightColors,
            typography = RedlineTypography,
            content = content
        )
    }
}

object RedlineTheme {
    val status: RedlineStatusColors
        @Composable @ReadOnlyComposable
        get() = LocalStatusColors.current
}

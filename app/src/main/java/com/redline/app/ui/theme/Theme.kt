package com.redline.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RedlineDarkScheme = darkColorScheme(
    primary = Red500,
    onPrimary = Color.White,
    primaryContainer = Red600,
    onPrimaryContainer = Red200,
    secondary = Red400,
    onSecondary = Color.White,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerHigh = SurfaceContainer,
    outline = Outline,
    error = Color(0xFFCF6679),
    onError = Color.Black
)

@Composable
fun RedlineTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Surface.toArgb()
            window.navigationBarColor = Surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = RedlineDarkScheme,
        typography = RedlineTypography,
        content = content
    )
}

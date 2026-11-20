package com.redline.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Two schemes built on one idea: neutral greys carry the layout, and red is
 * kept for the one thing on screen you should act on or notice.
 *
 * Surfaces step up in tone instead of using shadows:
 *   background/surface  the page
 *   surfaceContainer    cards
 *   surfaceContainerHigh  chips and wells inside a card, dialogs
 *
 * Every text and icon pair here clears WCAG AA (4.5:1 for text, 3:1 for
 * outlines and icons); ColorContrastTest checks it.
 */

internal val RedlineDarkColors = darkColorScheme(
    primary = Color(0xFFF25757),
    onPrimary = Color(0xFF1F0306),
    primaryContainer = Color(0xFF5C1116),
    onPrimaryContainer = Color(0xFFFFD9D7),
    inversePrimary = Color(0xFFC1272D),
    secondary = Color(0xFFC4C4CC),
    onSecondary = Color(0xFF1C1C1F),
    secondaryContainer = Color(0xFF2D2D32),
    onSecondaryContainer = Color(0xFFEDEDEF),
    tertiary = Color(0xFF8AB4F8),
    onTertiary = Color(0xFF0A1F44),
    tertiaryContainer = Color(0xFF1E3358),
    onTertiaryContainer = Color(0xFFD6E3FF),
    background = Color(0xFF111113),
    onBackground = Color(0xFFEDEDEF),
    surface = Color(0xFF111113),
    onSurface = Color(0xFFEDEDEF),
    surfaceVariant = Color(0xFF2D2D32),
    onSurfaceVariant = Color(0xFFA3A3AB),
    surfaceTint = Color(0xFF111113),
    inverseSurface = Color(0xFFEDEDEF),
    inverseOnSurface = Color(0xFF1C1C1F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF6E6E78),
    outlineVariant = Color(0xFF2E2E33),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF37373C),
    surfaceDim = Color(0xFF111113),
    surfaceContainerLowest = Color(0xFF0C0C0E),
    surfaceContainerLow = Color(0xFF17171A),
    surfaceContainer = Color(0xFF1C1C1F),
    surfaceContainerHigh = Color(0xFF242428),
    surfaceContainerHighest = Color(0xFF2D2D32)
)

internal val RedlineLightColors = lightColorScheme(
    primary = Color(0xFFC1272D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD7),
    onPrimaryContainer = Color(0xFF410005),
    inversePrimary = Color(0xFFF25757),
    secondary = Color(0xFF5C5C64),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E6EA),
    onSecondaryContainer = Color(0xFF18181B),
    tertiary = Color(0xFF2F5DA8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6E3FF),
    onTertiaryContainer = Color(0xFF001B3E),
    background = Color(0xFFF4F4F6),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFF4F4F6),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFE6E6EA),
    onSurfaceVariant = Color(0xFF5C5C64),
    surfaceTint = Color(0xFFF4F4F6),
    inverseSurface = Color(0xFF2A2A2E),
    inverseOnSurface = Color(0xFFF2F2F4),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF8A8A93),
    outlineVariant = Color(0xFFDDDDE2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAFAFB),
    surfaceDim = Color(0xFFDCDCE0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEEEEF1),
    surfaceContainerHighest = Color(0xFFE6E6EA)
)

/** Status colours Material 3 has no role for. */
@Immutable
data class RedlineStatusColors(
    val success: Color,
    val warning: Color
)

internal val DarkStatusColors = RedlineStatusColors(
    success = Color(0xFF6FCF97),
    warning = Color(0xFFF2B45A)
)

internal val LightStatusColors = RedlineStatusColors(
    success = Color(0xFF1E7A46),
    warning = Color(0xFF9A5B00)
)

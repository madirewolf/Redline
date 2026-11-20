package com.redline.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.redline.app.ui.home.HomeScreen
import com.redline.app.ui.log.LogScreen
import com.redline.app.ui.music.MusicScreen
import com.redline.app.ui.progress.ProgressScreen
import com.redline.app.ui.settings.SettingsScreen
import com.redline.app.ui.tempo.TempoScreen
import com.redline.app.ui.calories.CaloriesScreen
import com.redline.app.ui.motivation.MotivationScreen
import com.redline.app.ui.progress.ExerciseHistoryScreen
import com.redline.app.ui.workout.WorkoutDetailScreen

object Routes {
    const val HOME = "home"
    const val TRAIN = "train"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val MUSIC = "music"
    const val TEMPO = "tempo"
    const val WORKOUT_DETAIL = "workout/{sessionId}"
    const val EXERCISE_HISTORY = "exercise/{exerciseKey}"
    const val CALORIES = "calories"
    const val MOTIVATION = "motivation"

    fun workoutDetail(sessionId: Long) = "workout/$sessionId"
    // Uri.encode, not URLEncoder: Navigation decodes with Uri.decode, which leaves "+" as a plus.
    fun exerciseHistory(key: String) = "exercise/${android.net.Uri.encode(key)}"
}

private data class PillItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

@Composable
fun RedlineNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Top-level screens get the pill; everything you drill into gets a back arrow instead.
    val pillScreens = setOf(Routes.HOME, Routes.TEMPO, Routes.INSIGHTS)
    val showPill = currentRoute in pillScreens
    val back: () -> Unit = { navController.popBackStack() }
    val page = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(page)
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            enterTransition = { fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(320), initialScale = 0.98f) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(260, delayMillis = 60)) },
            popExitTransition = { fadeOut(tween(160)) }
        ) {
            composable(Routes.HOME) { HomeScreen(navController) }
            composable(Routes.TRAIN) { LogScreen(navController) }
            composable(Routes.INSIGHTS) { ProgressScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
            composable(Routes.MUSIC) { MusicScreen(onBack = back) }
            composable(Routes.TEMPO) { TempoScreen() }
            composable(Routes.CALORIES) { CaloriesScreen(onBack = back, onOpenSettings = { navController.navigate(Routes.SETTINGS) }) }
            composable(Routes.MOTIVATION) { MotivationScreen(onBack = back) }
            composable(
                Routes.WORKOUT_DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) {
                WorkoutDetailScreen(onBack = back)
            }
            composable(
                Routes.EXERCISE_HISTORY,
                arguments = listOf(navArgument("exerciseKey") { type = NavType.StringType })
            ) {
                ExerciseHistoryScreen(onBack = back)
            }
        }

        AnimatedVisibility(
            visible = showPill,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        // Opaque from the pill's middle down, so nothing shows
                        // beside its rounded ends.
                        Brush.verticalGradient(
                            0f to page.copy(alpha = 0f),
                            0.4f to page.copy(alpha = 0.88f),
                            0.62f to page
                        )
                    )
            )
        }

        AnimatedVisibility(
            visible = showPill,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FloatingNavPill(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
private fun FloatingNavPill(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        PillItem(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
        PillItem(Routes.TRAIN, "Train", Icons.Outlined.Mic, Icons.Filled.Mic),
        PillItem(Routes.TEMPO, "Tempo", Icons.Outlined.Speed, Icons.Filled.Speed),
        PillItem(Routes.INSIGHTS, "Progress", Icons.Outlined.Insights, Icons.Filled.Insights)
    )

    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(colors.surfaceContainerHigh)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(32.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (selected) colors.primaryContainer else Color.Transparent)
                    .clickable(role = Role.Tab, onClickLabel = item.label) { onNavigate(item.route) }
                    .animateContentSize(tween(220))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        // The label says it when selected; don't read it twice.
                        contentDescription = if (selected) null else item.label,
                        modifier = Modifier.size(22.dp),
                        tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
                    )
                    if (selected) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

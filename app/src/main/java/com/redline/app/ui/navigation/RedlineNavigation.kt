package com.redline.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Outline
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.Surface
import com.redline.app.ui.theme.SurfaceVariant
import com.redline.app.ui.calories.CaloriesScreen
import com.redline.app.ui.motivation.MotivationScreen
import com.redline.app.ui.social.SocialScreen
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
    const val SOCIAL = "social"
    const val CALORIES = "calories"
    const val MOTIVATION = "motivation"

    fun workoutDetail(sessionId: Long) = "workout/$sessionId"
    fun exerciseHistory(key: String) = "exercise/${java.net.URLEncoder.encode(key, "UTF-8")}"
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

    val pillScreens = setOf(Routes.HOME, Routes.INSIGHTS, Routes.MUSIC, Routes.SETTINGS, Routes.TEMPO, Routes.SOCIAL, Routes.CALORIES, Routes.MOTIVATION)
    val showPill = currentRoute in pillScreens

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
        ) {
            composable(Routes.HOME) { HomeScreen(navController) }
            composable(Routes.TRAIN) { LogScreen(navController) }
            composable(Routes.INSIGHTS) { ProgressScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.MUSIC) { MusicScreen() }
            composable(Routes.TEMPO) { TempoScreen() }
            composable(Routes.SOCIAL) { SocialScreen() }
            composable(Routes.CALORIES) { CaloriesScreen() }
            composable(Routes.MOTIVATION) { MotivationScreen() }
            composable(
                Routes.WORKOUT_DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("sessionId") ?: return@composable
                WorkoutDetailScreen(sessionId = id, navController = navController)
            }
            composable(
                Routes.EXERCISE_HISTORY,
                arguments = listOf(navArgument("exerciseKey") { type = NavType.StringType })
            ) { entry ->
                val key = java.net.URLDecoder.decode(
                    entry.arguments?.getString("exerciseKey") ?: return@composable, "UTF-8"
                )
                com.redline.app.ui.progress.ExerciseHistoryScreen(exerciseKey = key)
            }
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
        PillItem(Routes.SOCIAL, "Social", Icons.Outlined.People, Icons.Filled.People),
        PillItem(Routes.INSIGHTS, "Stats", Icons.Outlined.Insights, Icons.Filled.Insights)
    )

    Row(
        modifier = Modifier
            .padding(bottom = 28.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(SurfaceVariant.copy(alpha = 0.92f))
            .border(1.dp, Outline, RoundedCornerShape(32.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (selected) Red500.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onNavigate(item.route) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(22.dp),
                        tint = if (selected) Red500 else OnSurfaceVariant
                    )
                    if (selected) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = Red500
                        )
                    }
                }
            }
        }
    }
}

package com.redline.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * The current week, Monday to Sunday, always fully on screen. Days with a
 * finished workout get a red dot; today is filled red; days still to come
 * are dimmed.
 */
@Composable
fun CalendarStrip(
    workoutDays: Set<LocalDate>,
    today: LocalDate = LocalDate.now()
) {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Row(modifier = Modifier.fillMaxWidth()) {
        (0L..6L).map { monday.plusDays(it) }.forEach { date ->
            DayCell(
                date = date,
                isToday = date == today,
                isFuture = date.isAfter(today),
                hasWorkout = date in workoutDays,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isFuture: Boolean,
    hasWorkout: Boolean,
    modifier: Modifier = Modifier
) {
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    val dim = if (isFuture) 0.45f else 1f
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = dayName,
            style = MaterialTheme.typography.labelMedium,
            color = (if (isToday) colors.primary else colors.onSurfaceVariant).copy(alpha = dim)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (isToday) colors.primary else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday || hasWorkout) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isToday -> colors.onPrimary
                    hasWorkout -> colors.onSurface
                    else -> colors.onSurfaceVariant.copy(alpha = dim)
                }
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(if (hasWorkout) colors.primary else Color.Transparent, CircleShape)
        )
    }
}

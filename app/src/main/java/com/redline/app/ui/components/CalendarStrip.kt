package com.redline.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.redline.app.ui.theme.OnSurfaceVariant
import com.redline.app.ui.theme.Outline
import com.redline.app.ui.theme.Red500
import com.redline.app.ui.theme.SurfaceVariant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarStrip(
    workoutDays: Set<LocalDate>,
    onDayClick: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val days = remember { (13 downTo 0).map { today.minusDays(it.toLong()) } }
    var selectedDate by remember { mutableStateOf(today) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        listState.scrollToItem(days.size - 1)
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days) { date ->
            val isToday = date == today
            val hasWorkout = date in workoutDays
            val isSelected = date == selectedDate

            DayCell(
                date = date,
                isToday = isToday,
                hasWorkout = hasWorkout,
                isSelected = isSelected,
                onClick = {
                    selectedDate = date
                    onDayClick(date)
                }
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    hasWorkout: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) SurfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
            .then(
                if (isSelected) Modifier.border(1.dp, Outline, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .size(width = 44.dp, height = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = dayName,
            style = MaterialTheme.typography.labelMedium,
            color = if (isToday) Red500 else OnSurfaceVariant
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.onSurface else OnSurfaceVariant
        )
        if (hasWorkout) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(Red500, CircleShape)
            )
        }
    }
}

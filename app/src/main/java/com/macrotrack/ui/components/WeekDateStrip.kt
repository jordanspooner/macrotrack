package com.macrotrack.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.macrotrack.ui.log.WeekDay
import com.macrotrack.ui.theme.macroCaloriesColor
import com.macrotrack.ui.theme.restingSurfaceColor
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WeekDateStrip(
    weekDays: List<WeekDay>,
    onDateSelected: (WeekDay) -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = weekDays.first().date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenCalendar() }
                .padding(vertical = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            weekDays.forEach { day ->
                DayItem(
                    day = day,
                    onClick = { onDateSelected(day) }
                )
            }
        }
    }
}

@Composable
private fun DayItem(
    day: WeekDay,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (day.isSelected) MaterialTheme.colorScheme.primaryContainer else restingSurfaceColor(),
        animationSpec = tween(durationMillis = 200),
    )
    val textColor = if (day.isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    val scale by animateFloatAsState(
        targetValue = if (day.isSelected) 1.08f else 1f,
        animationSpec = tween(durationMillis = 200),
    )

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
        shadowElevation = if (day.isSelected) 3.dp else 0.dp,
        tonalElevation = if (day.isSelected) 2.dp else 0.dp,
        modifier = Modifier
            .scale(scale)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = day.dayName,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = day.dayNumber.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                fontWeight = if (day.isToday) {
                    FontWeight.Bold
                } else if (day.isSelected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                }
            )
            // Today indicator: a small dot centered below the day number.
            if (day.isToday) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(macroCaloriesColor(), CircleShape)
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
            // Kcal indicator bar (at-a-glance progress; hidden when empty).
            if (day.kcalPercent > 0f) {
                MacroBar(
                    progress = day.kcalPercent,
                    color = macroCaloriesColor(),
                    modifier = Modifier
                        .width(20.dp)
                        .height(3.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }
        }
    }
}

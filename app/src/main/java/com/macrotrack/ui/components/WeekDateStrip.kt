package com.macrotrack.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.macrotrack.ui.log.WeekDay
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
            style = MaterialTheme.typography.titleSmall,
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
        targetValue = if (day.isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
    )
    val textColor = if (day.isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(
        color = containerColor,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
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
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
            )
            // Kcal indicator bar
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(4.dp)
            ) {
                // To be implemented using MacroBar or simple Box
            }
        }
    }
}

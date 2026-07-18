package com.macrotrack.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.macrotrack.ui.log.WeekDay
import com.macrotrack.ui.theme.MacroTrackPillShape
import com.macrotrack.ui.theme.MacroTrackShapes
import com.macrotrack.ui.theme.MotionTokens
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandPrimary
import com.macrotrack.ui.theme.macroCaloriesColor
import com.macrotrack.ui.theme.restingSurfaceColor
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WeekDateStrip(
    weekDays: List<WeekDay>,
    onDateSelected: (WeekDay) -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = restingSurfaceColor(),
        shape = MacroTrackShapes.large,
        modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCalendar() }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = weekDays.first().date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                weekDays.forEach { DayItem(it, onClick = { onDateSelected(it) }) }
            }
        }
    }
}

@Composable
private fun DayItem(day: WeekDay, onClick: () -> Unit) {
    val containerColor by animateColorAsState(
        targetValue = if (day.isSelected) brandPrimary().copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(MotionTokens.medium),
    )
    val textColor = when {
        day.isSelected -> brandPrimary()
        day.isToday -> brandPrimary()
        else -> MaterialTheme.colorScheme.onSurface
    }
    val scale by animateFloatAsState(
        targetValue = if (day.isSelected) 1.05f else 1f,
        animationSpec = tween(MotionTokens.medium),
    )
    Surface(
        color = containerColor,
        shape = MacroTrackPillShape,
        modifier = Modifier
            .scale(scale)
            .clip(MacroTrackPillShape)
            .clickable(onClick = onClick)
            .padding(Spacing.xs),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                day.dayName,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = if (day.isToday || day.isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                day.dayNumber.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                fontWeight = if (day.isToday || day.isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            if (day.kcalPercent > 0f) {
                MacroBar(
                    progress = day.kcalPercent,
                    color = macroCaloriesColor(),
                    modifier = Modifier
                        .width(24.dp)
                        .height(4.dp),
                )
            } else {
                Box(Modifier.size(width = 24.dp, height = 4.dp))
            }
        }
    }
}

package com.macrotrack.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macrotrack.ui.log.WeekDay
import com.macrotrack.ui.theme.MacroTrackShapes
import com.macrotrack.ui.theme.MotionTokens
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandPrimary
import com.macrotrack.ui.theme.macroCarbsColor
import com.macrotrack.ui.theme.macroFatColor
import com.macrotrack.ui.theme.macroProteinColor
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
            ) {
                weekDays.forEach { DayItem(it, onClick = { onDateSelected(it) }, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayItem(day: WeekDay, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor by animateColorAsState(
        targetValue = if (day.isSelected) brandPrimary().copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(MotionTokens.medium),
    )
    val textColor = when {
        day.isSelected -> brandPrimary()
        day.isToday -> brandPrimary()
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            day.dayName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (day.isToday || day.isSelected) FontWeight.Bold else FontWeight.Normal,
        )
        Surface(
            color = containerColor,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                day.dayNumber.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                fontWeight = if (day.isToday || day.isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        }
        MacroRatioBar(
            proteinGoal = day.proteinKcalGoal,
            carbsGoal = day.carbsKcalGoal,
            fatGoal = day.fatKcalGoal,
            proteinActual = day.proteinKcalActual,
            carbsActual = day.carbsKcalActual,
            fatActual = day.fatKcalActual,
        )
    }
}

@Composable
private fun MacroRatioBar(
    proteinGoal: Float,
    carbsGoal: Float,
    fatGoal: Float,
    proteinActual: Float,
    carbsActual: Float,
    fatActual: Float,
) {
    val totalGoal = (proteinGoal + carbsGoal + fatGoal).coerceAtLeast(1f)
    val proteinGoalWeight = proteinGoal / totalGoal
    val carbsGoalWeight = carbsGoal / totalGoal
    val fatGoalWeight = fatGoal / totalGoal

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
    ) {
        MacroRatioSegment(
            segmentWeight = proteinGoalWeight,
            trackColor = trackColor,
            goalWeight = proteinGoalWeight,
            actualShare = proteinActual,
            fillColor = macroProteinColor(),
        )
        MacroRatioSegment(
            segmentWeight = carbsGoalWeight,
            trackColor = trackColor,
            goalWeight = carbsGoalWeight,
            actualShare = carbsActual,
            fillColor = macroCarbsColor(),
        )
        MacroRatioSegment(
            segmentWeight = fatGoalWeight,
            trackColor = trackColor,
            goalWeight = fatGoalWeight,
            actualShare = fatActual,
            fillColor = macroFatColor(),
        )
    }
}

@Composable
private fun RowScope.MacroRatioSegment(
    segmentWeight: Float,
    trackColor: Color,
    goalWeight: Float,
    actualShare: Float,
    fillColor: Color,
) {
    if (segmentWeight <= 0f) return

    val fillFraction = (actualShare / goalWeight).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .weight(segmentWeight)
            .fillMaxHeight()
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fillFraction)
                .background(fillColor)
        )
    }
}

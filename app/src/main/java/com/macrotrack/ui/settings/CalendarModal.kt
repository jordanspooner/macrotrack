package com.macrotrack.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.macrotrack.ui.theme.MotionTokens
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandOnPrimary
import com.macrotrack.ui.theme.brandPrimary
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_OF_WEEK_HEADERS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarModal(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    var displayMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { displayMonth = displayMonth.minusMonths(1) },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous month",
                    )
                }
                Text(
                    modifier = Modifier.weight(1f),
                    text = displayMonth.format(
                        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                IconButton(
                    onClick = { displayMonth = displayMonth.plusMonths(1) },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next month",
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
            ) {
                DAY_OF_WEEK_HEADERS.forEach { label ->
                    Text(
                        modifier = Modifier.weight(1f),
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            AnimatedContent(
                targetState = displayMonth,
                transitionSpec = {
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = MotionTokens.medium,
                            easing = MotionTokens.fastEasing,
                        ),
                    ) togetherWith fadeOut(
                        animationSpec = tween(
                            durationMillis = MotionTokens.medium,
                            easing = MotionTokens.fastEasing,
                        ),
                    )
                },
                label = "monthGrid",
            ) { month ->
                val gridCells = buildMonthGrid(month)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth(),
                    userScrollEnabled = false,
                ) {
                    items(gridCells) { date ->
                        if (date == null) {
                            Box(modifier = Modifier.size(40.dp))
                        } else {
                            val isSelected = date == selectedDate
                            val isToday = date == today
                            val backgroundColor by animateColorAsState(
                                targetValue = if (isSelected) brandPrimary() else Color.Transparent,
                                animationSpec = tween(
                                    durationMillis = MotionTokens.medium,
                                    easing = MotionTokens.fastEasing,
                                ),
                                label = "cellBackground",
                            )
                            val textColor by animateColorAsState(
                                targetValue = when {
                                    isSelected -> brandOnPrimary()
                                    isToday -> brandPrimary()
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                animationSpec = tween(
                                    durationMillis = MotionTokens.medium,
                                    easing = MotionTokens.fastEasing,
                                ),
                                label = "cellText",
                            )
                            Surface(
                                onClick = {
                                    onDateSelected(date)
                                    onDismiss()
                                },
                                color = backgroundColor,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(2.dp)
                                    .then(
                                        if (isToday && !isSelected) {
                                            Modifier.border(2.dp, brandPrimary(), CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        color = textColor,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(
                    onClick = {
                        onDateSelected(LocalDate.now())
                        onDismiss()
                    },
                ) {
                    Text("Jump to today", color = brandPrimary())
                }
            }
        }
    }
}

private fun buildMonthGrid(yearMonth: YearMonth): List<LocalDate?> {
    val firstDay = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val startOffset = firstDay.dayOfWeek.value - 1 // Monday = 0, Sunday = 6
    val cells = mutableListOf<LocalDate?>()
    repeat(startOffset) { cells.add(null) }
    for (d in 1..daysInMonth) {
        cells.add(yearMonth.atDay(d))
    }
    while (cells.size % 7 != 0) {
        cells.add(null)
    }
    return cells
}

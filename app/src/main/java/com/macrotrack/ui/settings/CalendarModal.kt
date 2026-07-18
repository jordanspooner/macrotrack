package com.macrotrack.ui.settings

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
import androidx.compose.ui.unit.dp
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
    var clickConfirmDate: LocalDate? by remember { mutableStateOf(null) }

    val gridCells = buildMonthGrid(displayMonth)

    val weekStartColumn = clickConfirmDate?.dayOfWeek?.value?.minus(1)
        ?: if (selectedDate == clickConfirmDate) selectedDate.dayOfWeek.value - 1 else null
    val highlightedWeekRow: Int? = clickConfirmDate?.let { chosen ->
        val startOffset = displayMonth.atDay(1).dayOfWeek.value - 1
        val index = startOffset + chosen.dayOfMonth - 1
        index / 7
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                    .padding(vertical = 8.dp),
            ) {
                DAY_OF_WEEK_HEADERS.forEach { label ->
                    Text(
                        modifier = Modifier.weight(1f),
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = false,
            ) {
                items(gridCells) { date ->
                    val cellIndex = gridCells.indexOf(date)
                    val rowIndex = if (cellIndex >= 0) cellIndex / 7 else -1
                    val isWeekHighlighted = date != null &&
                        highlightedWeekRow != null &&
                        rowIndex == highlightedWeekRow

                    if (date == null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .then(
                                    if (isWeekHighlighted) {
                                        Modifier.padding(2.dp)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    } else {
                        val isSelected = date == selectedDate
                        val isToday = date == today
                        val backgroundColor = when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            isToday -> MaterialTheme.colorScheme.tertiaryContainer
                            isWeekHighlighted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            else -> Color.Transparent
                        }
                        Surface(
                            onClick = {
                                clickConfirmDate = date
                                onDateSelected(date)
                            },
                            color = backgroundColor,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(40.dp)
                                .padding(2.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(
                    onClick = {
                        onDateSelected(LocalDate.now())
                        onDismiss()
                    },
                ) {
                    Text("Today")
                }
                TextButton(
                    onClick = {
                        onDateSelected(LocalDate.now())
                        onDismiss()
                    },
                ) {
                    Text("This Week")
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

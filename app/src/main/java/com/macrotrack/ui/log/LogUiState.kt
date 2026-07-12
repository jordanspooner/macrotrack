package com.macrotrack.ui.log

import com.macrotrack.domain.model.DailySummary
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Section
import java.time.LocalDate

data class LogUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val weekDates: List<WeekDay> = emptyList(),
    val dailySummary: DailySummary? = null,
    val sections: List<SectionWithEntries> = emptyList(),
    val selectionMode: SelectionMode = SelectionMode.Off,
    val isLoading: Boolean = false,
)

data class WeekDay(
    val date: LocalDate,
    val dayName: String,         // "Mon", "Tue", etc.
    val dayNumber: Int,          // 7, 8, etc.
    val kcalPercent: Float,      // 0.0-1.0 for progress bar
    val isSelected: Boolean,
    val isToday: Boolean,
)

data class SectionWithEntries(
    val section: Section,
    val entries: List<LogEntry>,
    val totalMacros: Macros,
    val isExpanded: Boolean = true,
)

sealed class Action {
    object Copy : Action()
    object Move : Action()
}

sealed class SelectionMode {
    object Off : SelectionMode()
    data class Selecting(val selectedIds: Set<Long>) : SelectionMode()
    data class ChoosingDestination(
        val selectedIds: Set<Long>,
        val action: Action,
    ) : SelectionMode()
}

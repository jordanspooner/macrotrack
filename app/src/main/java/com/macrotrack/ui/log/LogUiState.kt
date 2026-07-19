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
    val dayName: String,
    val dayNumber: Int,
    val isSelected: Boolean,
    val isToday: Boolean,
    val proteinKcalGoal: Float = 0f,
    val carbsKcalGoal: Float = 0f,
    val fatKcalGoal: Float = 0f,
    val proteinKcalActual: Float = 0f,
    val carbsKcalActual: Float = 0f,
    val fatKcalActual: Float = 0f,
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

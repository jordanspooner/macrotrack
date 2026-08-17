package com.macrotrack.ui.log

import com.macrotrack.domain.model.DailySummary
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Section
import java.time.LocalDate

/** The Monday that starts the week containing [date]. */
internal fun mondayOf(date: LocalDate): LocalDate =
    date.minusDays(date.dayOfWeek.value.toLong() - 1)

data class LogUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val displayedWeekStart: LocalDate = mondayOf(LocalDate.now()),
    val prevDay: DayContent? = null,
    val currentDay: DayContent? = null,
    val nextDay: DayContent? = null,
    val prevWeek: List<WeekDay> = emptyList(),
    val currentWeek: List<WeekDay> = emptyList(),
    val nextWeek: List<WeekDay> = emptyList(),
    val selectionMode: SelectionMode = SelectionMode.Off,
    val isLoading: Boolean = false,
)

data class DayContent(
    val date: LocalDate,
    val summary: DailySummary,
    val sections: List<SectionWithEntries>,
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

    /**
     * Type-level compatibility for the pre-drag UI only; the ViewModel never
     * produces this action. Moves are driven by drag ([LogViewModel.moveDraggedEntries]).
     */
    object Move : Action()
}

sealed class SelectionMode {
    object Off : SelectionMode()

    /**
     * Active multi-select. [sourceDate] is the date at selection start;
     * [selectedEntries] is an ordered snapshot (source visual order) that every
     * downstream operation resolves from; [selectedIds] is derived for UI highlighting.
     */
    data class Selecting(
        val sourceDate: LocalDate,
        val selectedEntries: List<LogEntry>,
    ) : SelectionMode() {
        val selectedIds: Set<Long> get() = selectedEntries.mapTo(linkedSetOf()) { it.id }
    }

    data class ChoosingDestination(
        val sourceDate: LocalDate,
        val selectedEntries: List<LogEntry>,
        val action: Action,
    ) : SelectionMode() {
        val selectedIds: Set<Long> get() = selectedEntries.mapTo(linkedSetOf()) { it.id }
    }
}
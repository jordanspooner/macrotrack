package com.macrotrack.ui.log

import com.macrotrack.domain.model.DailySummary
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Section
import java.time.LocalDate

data class LogUiState(
    val selectedDate: LocalDate = LocalDate.now(),
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
    val proteinProgress: Float = 0f,
    val carbsProgress: Float = 0f,
    val fatProgress: Float = 0f,
    /** Kcal share of the daily goal (protein*4, carbs*4, fat*9), as a fraction of total goal kcal. */
    val proteinShare: Float = 0f,
    val carbsShare: Float = 0f,
    val fatShare: Float = 0f,
)

/**
 * Direct, uncapped per-macro progress (actual grams / daily goal grams).
 * May exceed 1f when a macro goal is exceeded; the perimeter ring shows a
 * full-segment overage state past 100%.
 */
internal fun macroGoalProgress(actualG: Float, goalG: Int): Float =
    if (goalG > 0) actualG / goalG else 0f

/**
 * Kcal-weighted share of each macro in the daily goal
 * (protein/carbs at 4 kcal/g, fat at 9 kcal/g). Each share is a fraction of
 * the total goal kcal, so segment lengths on the day perimeter are
 * proportional to the macros' energy contribution.
 */
internal data class MacroShares(
    val protein: Float,
    val carbs: Float,
    val fat: Float,
)

internal fun macroGoalShares(proteinG: Int, carbsG: Int, fatG: Int): MacroShares {
    val proteinKcal = proteinG * 4
    val carbsKcal = carbsG * 4
    val fatKcal = fatG * 9
    val total = proteinKcal + carbsKcal + fatKcal
    return if (total <= 0) {
        MacroShares(protein = 0f, carbs = 0f, fat = 0f)
    } else {
        MacroShares(
            protein = proteinKcal / total.toFloat(),
            carbs = carbsKcal / total.toFloat(),
            fat = fatKcal / total.toFloat(),
        )
    }
}

data class SectionWithEntries(
    val section: Section,
    val entries: List<LogEntry>,
    val totalMacros: Macros,
    val isExpanded: Boolean = true,
    /** Per-meal macro/kcal goal derived from the daily goals and section distribution; null when section goals are disabled. */
    val goalMacros: Macros? = null,
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

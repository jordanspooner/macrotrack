package com.macrotrack.domain.usecase.log

import com.macrotrack.data.local.db.dao.DailyMacroRow
import com.macrotrack.data.repository.LogRepository
import com.macrotrack.data.repository.SettingsRepository
import com.macrotrack.domain.model.DailyGoals
import com.macrotrack.domain.model.DailySummary
import com.macrotrack.domain.model.Macros
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class GetDailySummaryUseCase @Inject constructor(
    private val logRepository: LogRepository,
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(date: LocalDate): Flow<DailySummary> {
        return combine(
            logRepository.getMacrosByDateRange(date, date),
            settingsRepository.getDailyGoals(),
        ) { rows, goals ->
            val macros = rows.firstOrNull()?.toMacros() ?: Macros(0f, 0f, 0f, 0f)
            DailySummary(date = date, logged = macros, goals = goals)
        }
    }

    suspend fun invokeOnce(date: LocalDate): DailySummary {
        val rows = logRepository.getMacrosByDateRangeOnce(date, date)
        val goals = settingsRepository.getDailyGoals().first()
        val macros = rows.firstOrNull()?.toMacros() ?: Macros(0f, 0f, 0f, 0f)
        return DailySummary(date = date, logged = macros, goals = goals)
    }

    private fun DailyMacroRow.toMacros() = Macros(
        kcal = kcal,
        proteinG = protein,
        carbsG = carbs,
        fatG = fat,
    )
}
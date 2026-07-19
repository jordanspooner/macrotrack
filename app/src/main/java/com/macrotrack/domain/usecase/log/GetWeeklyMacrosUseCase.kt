package com.macrotrack.domain.usecase.log

import com.macrotrack.data.local.db.dao.DailyMacroRow
import com.macrotrack.data.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetWeeklyMacrosUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    operator fun invoke(weekStart: LocalDate, weekEnd: LocalDate): Flow<List<DailyMacroRow>> {
        return logRepository.getMacrosByDateRange(weekStart, weekEnd)
    }
}

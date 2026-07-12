package com.macrotrack.domain.usecase.log

import com.macrotrack.data.repository.LogRepository
import com.macrotrack.domain.model.LogEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetDailyLogUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    operator fun invoke(date: LocalDate): Flow<List<LogEntry>> {
        return logRepository.getLogEntriesByDate(date)
    }
}

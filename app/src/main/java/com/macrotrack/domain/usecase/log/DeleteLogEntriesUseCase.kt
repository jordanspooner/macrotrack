package com.macrotrack.domain.usecase.log

import com.macrotrack.data.repository.LogRepository
import com.macrotrack.domain.model.LogEntry
import javax.inject.Inject

class DeleteLogEntriesUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    suspend operator fun invoke(entries: List<LogEntry>) {
        logRepository.delete(entries)
    }
}

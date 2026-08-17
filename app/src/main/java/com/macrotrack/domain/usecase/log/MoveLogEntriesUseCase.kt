package com.macrotrack.domain.usecase.log

import com.macrotrack.data.repository.LogRepository
import com.macrotrack.domain.model.LogEntry
import java.time.LocalDate
import javax.inject.Inject

class MoveLogEntriesUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    suspend operator fun invoke(
        entries: List<LogEntry>,
        targetDate: LocalDate,
        targetSectionId: Long? = null
    ) {
        val movedEntries = entries.map { entry ->
            entry.copy(
                date = targetDate,
                sectionId = targetSectionId ?: entry.sectionId
            )
        }
        logRepository.updateAll(movedEntries)
    }
}

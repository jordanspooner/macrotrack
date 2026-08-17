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
        if (entries.isEmpty()) return
        val movedEntries = entries.filter { entry ->
            val sectionId = targetSectionId ?: entry.sectionId
            entry.date != targetDate || entry.sectionId != sectionId
        }
        if (movedEntries.isEmpty()) return // Moving within the same (date, section) is a no-op
        val transformed = movedEntries.map { entry ->
            entry.copy(
                date = targetDate,
                sectionId = targetSectionId ?: entry.sectionId
            )
        }
        logRepository.updateAllAtEnd(transformed)
    }
}
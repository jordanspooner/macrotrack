package com.macrotrack.domain.usecase.log

import com.macrotrack.data.repository.LogRepository
import com.macrotrack.domain.model.LogEntry
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class CopyLogEntriesUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    suspend operator fun invoke(
        entries: List<LogEntry>,
        targetDate: LocalDate,
        targetSectionId: Long? = null
    ) {
        val now = Instant.now()
        val copiedEntries = entries.map { entry ->
            entry.copy(
                id = 0, // Reset ID for insertion
                date = targetDate,
                sectionId = targetSectionId ?: entry.sectionId,
                createdAt = now
            )
        }
        logRepository.insertAll(copiedEntries)
    }
}

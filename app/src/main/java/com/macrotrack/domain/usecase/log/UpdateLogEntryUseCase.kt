package com.macrotrack.domain.usecase.log

import com.macrotrack.data.repository.LogRepository
import com.macrotrack.domain.model.LogEntry
import javax.inject.Inject

class UpdateLogEntryUseCase @Inject constructor(
    private val logRepository: LogRepository,
) {
    suspend operator fun invoke(
        entry: LogEntry,
        portionG: Float,
        portionLabel: String?,
    ) {
        val per100 = if (entry.portionG > 0f) entry.macros * (100f / entry.portionG) else entry.macros
        val newMacros = per100 * (portionG / 100f)
        val updated = entry.copy(
            portionG = portionG,
            portionLabel = portionLabel,
            macros = newMacros,
        )
        logRepository.update(updated)
    }
}

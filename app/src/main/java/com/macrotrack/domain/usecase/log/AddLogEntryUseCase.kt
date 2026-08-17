package com.macrotrack.domain.usecase.log

import com.macrotrack.data.repository.LogRepository
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.LogEntry
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class AddLogEntryUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    suspend operator fun invoke(
        food: FoodItem,
        portionG: Float,
        portionLabel: String?,
        date: LocalDate,
        sectionId: Long,
        sortOrder: Int
    ): Long {
        val ratio = portionG / 100f
        val macrosForPortion = food.macroPer100g * ratio

        // Ignore the caller-provided sortOrder (currently hardcoded to 0 by the
        // UI) and append after existing entries so new adds stay compatible
        // with copy/move appending and keep unique, stable sort orders.
        val nextSortOrder = logRepository.getLogEntriesByDateOnce(date)
            .asSequence()
            .filter { it.sectionId == sectionId }
            .maxOfOrNull { it.sortOrder }
            ?.let { it + 1 }
            ?: 0

        val entry = LogEntry(
            date = date,
            sectionId = sectionId,
            foodItemId = food.id.takeIf { it > 0 },
            name = food.name,
            brand = food.brand,
            portionG = portionG,
            portionLabel = portionLabel,
            macros = macrosForPortion,
            sortOrder = nextSortOrder,
            createdAt = Instant.now()
        )
        return logRepository.insert(entry)
    }
}

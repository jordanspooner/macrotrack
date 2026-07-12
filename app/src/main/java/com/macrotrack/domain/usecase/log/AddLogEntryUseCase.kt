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
        
        val entry = LogEntry(
            date = date,
            sectionId = sectionId,
            foodItemId = food.id.takeIf { it > 0 },
            name = food.name,
            brand = food.brand,
            portionG = portionG,
            portionLabel = portionLabel,
            macros = macrosForPortion,
            sortOrder = sortOrder,
            createdAt = Instant.now()
        )
        return logRepository.insert(entry)
    }
}

package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.domain.model.FoodItem
import javax.inject.Inject

class LookupBarcodeUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(ean: String): FoodItem? {
        if (ean.isBlank()) return null
        return foodRepository.getFoodByEan(ean)
    }
}

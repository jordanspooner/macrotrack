package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.UserFoodRepository
import com.macrotrack.domain.model.FoodItem
import javax.inject.Inject

/**
 * Looks up a food by its EAN/barcode across the prebuilt database first, then the
 * user-created foods. Returns null if the barcode is unknown.
 */
class LookupBarcodeUseCase @Inject constructor(
    private val foodRepository: FoodRepository,
    private val userFoodRepository: UserFoodRepository
) {
    suspend operator fun invoke(ean: String): FoodItem? {
        if (ean.isBlank()) return null
        return foodRepository.getFoodByEan(ean) ?: userFoodRepository.getByEan(ean)
    }
}

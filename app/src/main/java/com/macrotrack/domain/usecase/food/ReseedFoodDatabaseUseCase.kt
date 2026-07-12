package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
import javax.inject.Inject

/**
 * Clears the pre-built `food_items` table and re-imports it from the bundled
 * `food_database.db` asset. Lets you pick up a freshly rebuilt asset (after
 * dropping new USDA / OFF CSVs into `food-db-builder/data/` and running the
 * builder) without clearing app storage.
 *
 * Returns the number of foods re-inserted.
 */
class ReseedFoodDatabaseUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(): Int = foodRepository.reseedFromAsset()
}

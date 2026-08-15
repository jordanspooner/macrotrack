package com.macrotrack.domain.usecase.foodsource

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.FoodSourceRepository
import javax.inject.Inject

class DeleteFoodSourceUseCase @Inject constructor(
    private val foodRepository: FoodRepository,
    private val foodSourceRepository: FoodSourceRepository
) {
    suspend operator fun invoke(sourceId: String) {
        foodRepository.deleteByDataSource(sourceId)
        foodSourceRepository.delete(sourceId)
    }
}

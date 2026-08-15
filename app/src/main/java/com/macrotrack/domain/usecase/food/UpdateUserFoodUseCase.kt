package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.domain.model.FoodItem
import javax.inject.Inject

class UpdateUserFoodUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(food: FoodItem) {
        foodRepository.updateUserFood(food)
    }
}

package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.Source
import javax.inject.Inject

class AddUserFoodUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(food: FoodItem): FoodItem {
        return foodRepository.insertUserFood(
            food.copy(source = Source.USER, dataSourceId = "my-foods")
        )
    }
}

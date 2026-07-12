package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.UserFoodRepository
import com.macrotrack.domain.model.FoodItem
import javax.inject.Inject

/**
 * Persists a user-created food (from Quick Add or a confirmed label scan) to the
 * local `user_foods` table and returns it with its assigned id.
 */
class AddUserFoodUseCase @Inject constructor(
    private val userFoodRepository: UserFoodRepository
) {
    suspend operator fun invoke(food: FoodItem): FoodItem {
        return userFoodRepository.insert(food)
    }
}

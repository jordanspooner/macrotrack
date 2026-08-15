package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
import javax.inject.Inject

class DeleteUserFoodUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(id: Long) {
        foodRepository.deleteUserFood(id)
    }
}

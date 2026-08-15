package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.domain.model.FoodItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchUserFoodsUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    operator fun invoke(query: String): Flow<List<FoodItem>> {
        if (query.isBlank()) return foodRepository.getAllUserFoods()
        val ftsQuery = query.trim().lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
        return foodRepository.searchUserFoods(ftsQuery)
    }
}

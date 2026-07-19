package com.macrotrack.domain.usecase.foodsource

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.FoodSourceRepository
import com.macrotrack.domain.model.FoodSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetInstalledFoodSourcesUseCase @Inject constructor(
    private val foodSourceRepository: FoodSourceRepository,
    private val foodRepository: FoodRepository
) {
    operator fun invoke(): Flow<List<FoodSource>> {
        return foodSourceRepository.getInstalledSources().combine(
            foodRepository.countUserFoodsFlow()
        ) { sources, userCount ->
            sources.map { source ->
                if (source.id == "my-foods") source.copy(itemCount = userCount)
                else source
            }
        }
    }

    private fun FoodRepository.countUserFoodsFlow() = getAllUserFoods()
        .map { it.size }
}

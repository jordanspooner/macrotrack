package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.search.FoodSearchRanker
import com.macrotrack.domain.search.QueryNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

class SearchUserFoodsUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    private val ranker = FoodSearchRanker()

    /**
     * Searches user-created foods. A blank [query] returns every user food
     * (preserving the "empty search" all-foods behavior). Otherwise both the
     * exact/prefix FTS path and the fuzzy path are merged through
     * [FoodSearchRanker].
     */
    operator fun invoke(query: String): Flow<List<FoodItem>> {
        if (query.isBlank()) return foodRepository.getAllUserFoods()

        val ftsQuery = QueryNormalizer.ftsPrefixQuery(query) ?: return emptyFlow()
        return combine(
            foodRepository.searchUserFoods(ftsQuery),
            foodRepository.searchUserFoodsFuzzy(query)
        ) { candidates, fuzzy ->
            ranker.rank(query, candidates, fuzzy)
        }
    }
}
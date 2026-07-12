package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.LogRepository
import com.macrotrack.domain.model.FoodItem
import javax.inject.Inject

/**
 * Produces food recommendations for the "empty search" state, blending four
 * heuristics in priority order (per the product spec):
 *   1. Recency within the target section
 *   2. Frequency within the target section
 *   3. Recency overall
 *   4. Frequency overall
 *
 * Results are de-duplicated across the heuristics and resolved to [FoodItem]s.
 */
class GetRecommendationsUseCase @Inject constructor(
    private val logRepository: LogRepository,
    private val foodRepository: FoodRepository
) {
    suspend fun getRecommendations(sectionId: Long, limit: Int = 10): List<FoodItem> {
        val seen = LinkedHashSet<Long>()
        val ranked = mutableListOf<Long>()

        fun addAll(ids: List<Long>) {
            for (id in ids) if (seen.add(id)) ranked.add(id)
        }

        addAll(logRepository.getRecentFoodIds(sectionId, limit))
        addAll(logRepository.getFrequentFoodIds(sectionId, limit))
        addAll(logRepository.getRecentFoodIdsOverall(limit))
        addAll(logRepository.getFrequentFoodIdsOverall(limit))

        return ranked.take(limit).mapNotNull { foodRepository.getFoodById(it) }
    }

    suspend fun getRecent(sectionId: Long, limit: Int = 10): List<FoodItem> =
        fetch(logRepository.getRecentFoodIds(sectionId, limit))

    suspend fun getFrequent(sectionId: Long, limit: Int = 10): List<FoodItem> =
        fetch(logRepository.getFrequentFoodIds(sectionId, limit))

    private suspend fun fetch(ids: List<Long>): List<FoodItem> =
        ids.mapNotNull { foodRepository.getFoodById(it) }
}

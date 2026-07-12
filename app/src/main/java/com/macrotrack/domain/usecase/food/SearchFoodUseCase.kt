package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.LogRepository
import com.macrotrack.domain.model.FoodItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SearchFoodUseCase @Inject constructor(
    private val foodRepository: FoodRepository,
    private val logRepository: LogRepository
) {
    /**
     * Searches the food database with the given [query].
     *
     * Results are returned in FTS relevance order, then re-ranked so that foods
     * the user has logged before are surfaced ahead of never-logged foods
     * (preserving relevance within each group).
     */
    operator fun invoke(query: String): Flow<List<FoodItem>> {
        if (query.isBlank()) return emptyFlow()

        val ftsQuery = formatForFts(query)
        return foodRepository.searchFts(ftsQuery)
            .combine(logRepository.getLoggedFoodIds().toSetFlow()) { results, logged ->
                rank(results, logged)
            }
    }

    /**
     * Formats a free-text query for FTS prefix matching: each whitespace-separated
     * token becomes a prefix token (e.g. "chi bre" -> "chi* bre*").
     */
    private fun formatForFts(query: String): String {
        return query.trim().lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
    }

    private fun rank(results: List<FoodItem>, logged: Set<Long>): List<FoodItem> {
        // `results` is already ordered by FTS rank (best first). Partition so that
        // previously-logged foods come first while keeping relevance order inside
        // each partition.
        val (loggedItems, others) = results.partition { it.id in logged }
        return loggedItems + others
    }

    private fun Flow<List<Long>>.toSetFlow(): Flow<Set<Long>> =
        map { it.toSet() }
}

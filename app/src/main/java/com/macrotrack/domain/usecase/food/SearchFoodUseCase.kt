package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.LogRepository
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.FoodUsageStats
import com.macrotrack.domain.search.FoodSearchRanker
import com.macrotrack.domain.search.QueryNormalizer
import com.macrotrack.domain.search.UsageScoring
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class SearchFoodUseCase @Inject constructor(
    private val foodRepository: FoodRepository,
    private val logRepository: LogRepository
) {
    private val ranker = FoodSearchRanker()

    /**
     * Searches the food database with the given [query].
     *
     * Both the exact/prefix FTS path and the typo-tolerant fuzzy path are
     * queried and merged through [FoodSearchRanker], so fuzzy candidates can
     * never outrank valid exact/prefix/token matches. Within a tier, each
     * food's normalized log-usage score (scoped to [sectionId] when given)
     * only breaks close text matches. Usage aggregation is scoped to the
     * current search candidates, so it is bounded per query but stays a
     * [Flow] so log changes re-rank the current result set.
     */
    operator fun invoke(query: String): Flow<List<FoodItem>> = invoke(query, null)

    operator fun invoke(query: String, sectionId: Long?): Flow<List<FoodItem>> {
        if (query.isBlank()) return emptyFlow()

        val ftsQuery = QueryNormalizer.ftsPrefixQuery(query) ?: return emptyFlow()
        val section = sectionId ?: 0L
        return combine(
            foodRepository.searchFts(ftsQuery),
            foodRepository.searchFtsFuzzy(query)
        ) { candidates, fuzzy -> candidates to fuzzy }
            .flatMapLatest { (candidates, fuzzy) ->
                val candidateIds = (candidates + fuzzy).map { it.id }.distinct()
                val usage = if (candidateIds.isEmpty()) {
                    flowOf(emptyList<FoodUsageStats>())
                } else {
                    logRepository.getFoodUsageStats(section, candidateIds)
                }
                combine(
                    flowOf(candidates),
                    flowOf(fuzzy),
                    usage
                ) { exact, fuzzyCandidates, stats ->
                    ranker.rank(
                        query = query,
                        candidates = exact,
                        fuzzyCandidates = fuzzyCandidates,
                        usageScores = UsageScoring.scores(stats)
                    )
                }
            }
    }
}

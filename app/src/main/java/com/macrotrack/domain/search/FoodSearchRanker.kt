package com.macrotrack.domain.search

import com.macrotrack.domain.model.FoodItem
import kotlin.math.abs

/**
 * Pure, deterministic ranker for food search results.
 *
 * Ranking tiers, best first:
 * 0. exact normalized name match (query == full name)
 * 1. name prefix (query is a prefix of the full name)
 * 2. token/substring matches inside the name (partial multi-token coverage)
 * 3. fuzzy name matches (bounded Damerau-Levenshtein + trigram overlap)
 * 4. brand-only matches (name did not match)
 * 5. brand fuzzy matches
 *
 * Within a tier: more matched query tokens win, then more exact/prefix token
 * matches, then lower fuzzy distance, then higher trigram overlap, then
 * shorter names, then the optional [usageScores] boost (falling back to the
 * legacy all-or-nothing [loggedIds] flag), then ascending id (stable tie-break).
 *
 * Fuzzy matching only applies to query tokens of at least
 * [MIN_FUZZY_TOKEN_LENGTH] characters (3), so one- and two-character tokens
 * never fuzzy match. All inputs are normalized through [QueryNormalizer], so
 * accents, case and punctuation are ignored consistently.
 */
class FoodSearchRanker(
    private val normalizer: QueryNormalizer = QueryNormalizer,
) {

    companion object {
        /** Query tokens shorter than this never participate in fuzzy matching. */
        const val MIN_FUZZY_TOKEN_LENGTH = 3

        /** Hard cap on the number of final results returned after merging/scoring. */
        const val MAX_RESULTS = 50

        private const val TIER_EXACT = 0
        private const val TIER_PREFIX = 1
        private const val TIER_TOKEN = 2
        private const val TIER_NAME_FUZZY = 3
        private const val TIER_BRAND = 4
        private const val TIER_BRAND_FUZZY = 5
        private const val NO_MATCH_TIER = 6

        private val SCORE_COMPARATOR = compareBy<Scored> { it.tier }
            .thenByDescending { it.coverage }
            .thenByDescending { it.exactTokens }
            .thenByDescending { it.prefixTokens }
            .thenByDescending { it.substringTokens }
            .thenByDescending { it.fuzzyTokens }
            .thenBy { it.fuzzyDistance }
            .thenByDescending { it.fuzzyTrigrams }
            .thenBy { it.nameLength }
            .thenByDescending { it.usage }
            .thenBy { it.id }
    }

    /**
     * Ranks [candidates] (typically FTS results) for [query], merging in the
     * optionally separate [fuzzyCandidates] (deduplicated by id, FTS wins).
     * Items with no match are dropped and the final result set is capped at
     * [MAX_RESULTS].
     *
     * [loggedIds] is the legacy all-or-nothing boost: it lifts previously-logged
     * foods above unlogged ones when every other signal ties. [usageScores]
     * replaces it with a bounded, graded signal (e.g. from [UsageScoring]); when
     * an id is present in [usageScores] that value wins, otherwise [loggedIds]
     * is used. Pass neither to disable personalization.
     */
    fun rank(
        query: String,
        candidates: List<FoodItem>,
        fuzzyCandidates: List<FoodItem> = emptyList(),
        loggedIds: Set<Long> = emptySet(),
        usageScores: Map<Long, Double> = emptyMap(),
    ): List<FoodItem> {
        val tokens = normalizer.tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        val queryNorm = tokens.joinToString(" ")

        val merged = candidates + fuzzyCandidates.filter { fuzzy ->
            candidates.none { it.id == fuzzy.id }
        }
        if (merged.isEmpty()) return emptyList()

        val indexed = merged.map { food ->
            val nameTokens = normalizer.tokenize(food.name)
            val brandTokens = food.brand?.let(normalizer::tokenize).orEmpty()
            IndexedFood(food, nameTokens, brandTokens, nameTokens.joinToString(" "))
        }

        return indexed
            .map { score(it, tokens, queryNorm, loggedIds, usageScores) }
            .filter { it.tier < NO_MATCH_TIER }
            .sortedWith(SCORE_COMPARATOR)
            .take(MAX_RESULTS)
            .map { it.food }
    }

    private class IndexedFood(
        val food: FoodItem,
        val nameTokens: List<String>,
        val brandTokens: List<String>,
        val nameNorm: String,
    )

    private class Scored(
        val food: FoodItem,
        val tier: Int,
        val coverage: Int,
        val exactTokens: Int,
        val prefixTokens: Int,
        val substringTokens: Int,
        val fuzzyTokens: Int,
        val fuzzyDistance: Int,
        val fuzzyTrigrams: Int,
        val nameLength: Int,
        val usage: Double,
        val id: Long,
    )

    private fun score(
        indexed: IndexedFood,
        tokens: List<String>,
        queryNorm: String,
        loggedIds: Set<Long>,
        usageScores: Map<Long, Double>,
    ): Scored {
        val nameMatch = matchAll(tokens, indexed.nameTokens)
        val tier = when {
            indexed.nameNorm == queryNorm -> TIER_EXACT
            indexed.nameNorm.startsWith(queryNorm) -> TIER_PREFIX
            nameMatch.coverage > 0 -> TIER_TOKEN
            nameMatch.fuzzyTokens > 0 -> TIER_NAME_FUZZY
            else -> {
                val brandMatch = matchAll(tokens, indexed.brandTokens)
                when {
                    brandMatch.coverage > 0 -> TIER_BRAND
                    brandMatch.fuzzyTokens > 0 -> TIER_BRAND_FUZZY
                    else -> NO_MATCH_TIER
                }
            }
        }
        return Scored(
            food = indexed.food,
            tier = tier,
            coverage = nameMatch.coverage,
            exactTokens = nameMatch.exactTokens,
            prefixTokens = nameMatch.prefixTokens,
            substringTokens = nameMatch.substringTokens,
            fuzzyTokens = nameMatch.fuzzyTokens,
            fuzzyDistance = nameMatch.minDistance,
            fuzzyTrigrams = nameMatch.maxTrigrams,
            nameLength = indexed.nameNorm.length,
            usage = usageScores[indexed.food.id]
                ?: if (indexed.food.id in loggedIds) 1.0 else 0.0,
            id = indexed.food.id,
        )
    }

    private class MatchResult {
        var coverage = 0
        var exactTokens = 0
        var prefixTokens = 0
        var substringTokens = 0
        var fuzzyTokens = 0
        var minDistance = Int.MAX_VALUE
        var maxTrigrams = 0
    }

    private fun matchAll(queryTokens: List<String>, candidateTokens: List<String>): MatchResult {
        val result = MatchResult()
        for (queryToken in queryTokens) {
            var best: MatchKind? = null
            var bestDistance = Int.MAX_VALUE
            var bestTrigrams = 0
            for (candidateToken in candidateTokens) {
                if (candidateToken == queryToken) {
                    best = MatchKind.EXACT
                    break
                }
                if (candidateToken.startsWith(queryToken)) {
                    if (best == null || best > MatchKind.PREFIX) best = MatchKind.PREFIX
                    continue
                }
                if (candidateToken.contains(queryToken)) {
                    if (best == null || best > MatchKind.SUBSTRING) best = MatchKind.SUBSTRING
                    continue
                }
                val fuzzy = fuzzyMatch(queryToken, candidateToken)
                if (fuzzy != null && fuzzy.distance < bestDistance) {
                    best = MatchKind.FUZZY
                    bestDistance = fuzzy.distance
                    bestTrigrams = fuzzy.trigramOverlap
                }
            }
            when (best) {
                MatchKind.EXACT -> {
                    result.coverage++
                    result.exactTokens++
                }
                MatchKind.PREFIX -> {
                    result.coverage++
                    result.prefixTokens++
                }
                MatchKind.SUBSTRING -> {
                    result.coverage++
                    result.substringTokens++
                }
                MatchKind.FUZZY -> {
                    result.fuzzyTokens++
                    result.minDistance = minOf(result.minDistance, bestDistance)
                    result.maxTrigrams = maxOf(result.maxTrigrams, bestTrigrams)
                }
                null -> Unit
            }
        }
        return result
    }

    private class FuzzyMatch(val distance: Int, val trigramOverlap: Int)

    private fun fuzzyMatch(queryToken: String, candidateToken: String): FuzzyMatch? {
        if (queryToken.length < MIN_FUZZY_TOKEN_LENGTH) return null
        if (candidateToken == queryToken) return null
        val maxDistance = if (queryToken.length == MIN_FUZZY_TOKEN_LENGTH) 1 else 2
        if (abs(queryToken.length - candidateToken.length) > maxDistance) return null
        val distance = boundedDamerauLevenshtein(queryToken, candidateToken, maxDistance)
        if (distance > maxDistance) return null
        val trigramOverlap = if (queryToken.length >= 4) {
            val overlap = trigramSet(queryToken).intersect(trigramSet(candidateToken)).size
            if (overlap < 1) return null
            overlap
        } else {
            0
        }
        return FuzzyMatch(distance, trigramOverlap)
    }

    private fun trigramSet(s: String): Set<String> {
        if (s.length < 3) return emptySet()
        return buildSet {
            for (i in 0..s.length - 3) add(s.substring(i, i + 3))
        }
    }

    private fun boundedDamerauLevenshtein(a: String, b: String, max: Int): Int {
        val n = a.length
        val m = b.length
        if (abs(n - m) > max) return max + 1
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j
        for (i in 1..n) {
            var rowMin = Int.MAX_VALUE
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    dp[i][j] = minOf(dp[i][j], dp[i - 2][j - 2] + 1)
                }
                rowMin = minOf(rowMin, dp[i][j])
            }
            if (rowMin > max) return max + 1
        }
        return dp[n][m]
    }

    private enum class MatchKind { EXACT, PREFIX, SUBSTRING, FUZZY }
}
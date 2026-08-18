package com.macrotrack.domain.search

import com.macrotrack.domain.model.FoodUsageStats
import kotlin.math.exp

/**
 * Deterministic, normalized usage scoring for the search ranker.
 *
 * Each food's history is reduced to a single score in `[0, 1]` that blends
 * target-section frequency and recency with overall frequency and recency.
 * The score is deliberately bounded and used by [FoodSearchRanker] only as a
 * late tie-break within a tier, so history can break close text matches but can
 * never lift a weak text match above a strong exact/prefix/token/fuzzy one.
 *
 * Frequency components are normalized against the busiest food in the same
 * candidate batch; recency components use exponential decay with a
 * [HALF_LIFE_MILLIS] half life. The result is pure and deterministic for a
 * fixed set of inputs, so it is safe to unit test.
 */
object UsageScoring {

    /** Half life for the recency decay: a log entry this old is worth ~50%. */
    const val HALF_LIFE_MILLIS = 30L * 24 * 60 * 60 * 1000

    const val WEIGHT_SECTION_FREQUENCY = 0.4
    const val WEIGHT_SECTION_RECENCY = 0.3
    const val WEIGHT_OVERALL_FREQUENCY = 0.2
    const val WEIGHT_OVERALL_RECENCY = 0.1

    /**
     * Computes a usage score for every food in [stats]. The frequency
     * components are normalized against the maximum counts seen in the batch.
     */
    fun scores(
        stats: List<FoodUsageStats>,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Map<Long, Double> {
        if (stats.isEmpty()) return emptyMap()
        val maxSectionCount = (stats.maxOfOrNull { it.sectionCount } ?: 0).coerceAtLeast(0)
        val maxOverallCount = (stats.maxOfOrNull { it.overallCount } ?: 0).coerceAtLeast(0)
        return stats.associate { food ->
            food.foodItemId to score(food, maxSectionCount, maxOverallCount, nowEpochMillis)
        }
    }

    /** Score for a single food, given the batch maxima for the frequency terms. */
    fun score(
        stats: FoodUsageStats,
        maxSectionCount: Int,
        maxOverallCount: Int,
        nowEpochMillis: Long,
    ): Double {
        val sectionFrequency = if (maxSectionCount > 0) {
            stats.sectionCount.toDouble() / maxSectionCount
        } else {
            0.0
        }
        val overallFrequency = if (maxOverallCount > 0) {
            stats.overallCount.toDouble() / maxOverallCount
        } else {
            0.0
        }
        return (WEIGHT_SECTION_FREQUENCY * sectionFrequency
            + WEIGHT_SECTION_RECENCY * recency(stats.sectionRecentCreatedAt, nowEpochMillis)
            + WEIGHT_OVERALL_FREQUENCY * overallFrequency
            + WEIGHT_OVERALL_RECENCY * recency(stats.overallRecentCreatedAt, nowEpochMillis))
    }

    private fun recency(createdAt: Long?, nowEpochMillis: Long): Double {
        if (createdAt == null) return 0.0
        val elapsed = (nowEpochMillis - createdAt).coerceAtLeast(0)
        return exp(-elapsed.toDouble() / HALF_LIFE_MILLIS)
    }
}
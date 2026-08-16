package com.macrotrack.domain.model

/**
 * Per-food usage statistics derived from the log history, used as a bounded
 * secondary signal when ranking search results. The section-scoped fields are
 * relative to the section the user is currently adding food to; the overall
 * fields span the entire log.
 */
data class FoodUsageStats(
    val foodItemId: Long,
    val overallCount: Int,
    val overallRecentCreatedAt: Long?,
    val sectionCount: Int,
    val sectionRecentCreatedAt: Long?,
)
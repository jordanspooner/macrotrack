package com.macrotrack.domain.search

import com.google.common.truth.Truth.assertThat
import com.macrotrack.domain.model.FoodUsageStats
import org.junit.Test

class UsageScoringTest {

    private val NOW = 1_000_000_000_000L

    private fun stats(
        id: Long,
        overallCount: Int = 0,
        overallRecent: Long? = null,
        sectionCount: Int = 0,
        sectionRecent: Long? = null,
    ) = FoodUsageStats(
        foodItemId = id,
        overallCount = overallCount,
        overallRecentCreatedAt = overallRecent,
        sectionCount = sectionCount,
        sectionRecentCreatedAt = sectionRecent,
    )

    @Test
    fun `empty stats yield no scores`() {
        assertThat(UsageScoring.scores(emptyList(), NOW)).isEmpty()
    }

    @Test
    fun `scores are normalized to zero when there is no history`() {
        val map = UsageScoring.scores(listOf(stats(1), stats(2)), NOW)
        assertThat(map[1]!!).isEqualTo(0.0)
        assertThat(map[2]!!).isEqualTo(0.0)
    }

    @Test
    fun `more section usage scores higher than none`() {
        val used = stats(1, sectionCount = 10, overallCount = 10)
        val unused = stats(2)
        val map = UsageScoring.scores(listOf(used, unused), NOW)
        assertThat(map[1]!!).isGreaterThan(map[2]!!)
    }

    @Test
    fun `section frequency is normalized against the batch maximum`() {
        val busiest = stats(1, sectionCount = 10, overallCount = 10)
        val half = stats(2, sectionCount = 5, overallCount = 5)
        val map = UsageScoring.scores(listOf(busiest, half), NOW)
        assertThat(map[1]!!).isGreaterThan(map[2]!!)
    }

    @Test
    fun `recent usage scores higher than stale usage`() {
        val fresh = stats(1, sectionCount = 1, sectionRecent = NOW - 60_000, overallCount = 1)
        val stale = stats(2, sectionCount = 1, sectionRecent = NOW - 120 * UsageScoring.HALF_LIFE_MILLIS, overallCount = 1)
        val map = UsageScoring.scores(listOf(fresh, stale), NOW)
        assertThat(map[1]!!).isGreaterThan(map[2]!!)
    }

    @Test
    fun `overall-only history still contributes a bounded signal`() {
        val overallOnly = stats(1, overallCount = 10, overallRecent = NOW - 60_000)
        val none = stats(2)
        val map = UsageScoring.scores(listOf(overallOnly, none), NOW)
        assertThat(map[1]!!).isGreaterThan(0.0)
        assertThat(map[1]!!).isGreaterThan(map[2]!!)
    }

    @Test
    fun `scores never leave the unit interval`() {
        val map = UsageScoring.scores(
            listOf(
                stats(1, overallCount = 100, overallRecent = NOW, sectionCount = 100, sectionRecent = NOW),
                stats(2, overallCount = 3, overallRecent = NOW - 60_000, sectionCount = 2, sectionRecent = NOW - 60_000),
            ),
            NOW,
        )
        map.values.forEach { score ->
            assertThat(score).isAtLeast(0.0)
            assertThat(score).isAtMost(1.0)
        }
    }

    @Test
    fun `scoring is deterministic for fixed inputs`() {
        val input = listOf(
            stats(1, overallCount = 7, overallRecent = NOW - 1_000, sectionCount = 4, sectionRecent = NOW - 2_000),
            stats(2, overallCount = 2, overallRecent = NOW - 50_000, sectionCount = 1, sectionRecent = NOW - 60_000),
        )
        assertThat(UsageScoring.scores(input, NOW)).isEqualTo(UsageScoring.scores(input, NOW))
    }
}
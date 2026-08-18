package com.macrotrack.domain.model

/**
 * The macro a per-section percentage applies to.
 */
enum class MacroType { PROTEIN, CARBS, FAT }

/**
 * Per-section distribution of the daily macro goals, keyed by section id.
 * Each inner map holds a [MacroType] to percentage (0..100) of the daily goal
 * that should be consumed by that section; for a given macro the percentages
 * across all sections should sum to 100.
 */
data class SectionGoalPercentages(
    val percentages: Map<Long, Map<MacroType, Float>> = emptyMap(),
) {
    /**
     * Resolves the effective percentages for [sectionIds], falling back to an
     * even split whenever the stored distribution is empty or does not exactly
     * cover the current sections (e.g. a section was added/removed since the
     * distribution was saved). Missing or non-finite macro values within a
     * stored section fall back to the even share.
     */
    fun resolve(sectionIds: Collection<Long>): Map<Long, Map<MacroType, Float>> {
        if (sectionIds.isEmpty()) return emptyMap()
        val ids = sectionIds.toSet()
        val coversCurrent = percentages.isNotEmpty() && percentages.keys == ids
        val base = if (coversCurrent) percentages else evenSplit(sectionIds)
        val share = 100f / sectionIds.size
        return sectionIds.associateWith { id ->
            val stored = base[id].orEmpty()
            MacroType.entries.associateWith { macro ->
                stored[macro]?.takeIf { it.isFinite() && it >= 0f } ?: share
            }
        }
    }

    companion object {
        /** Equal percentage for every section, for each macro. */
        fun evenSplit(sectionIds: Collection<Long>): Map<Long, Map<MacroType, Float>> {
            if (sectionIds.isEmpty()) return emptyMap()
            val share = 100f / sectionIds.size
            return sectionIds.associateWith {
                mapOf(
                    MacroType.PROTEIN to share,
                    MacroType.CARBS to share,
                    MacroType.FAT to share,
                )
            }
        }
    }
}

/**
 * Whether per-section goals are enabled and, if so, how the daily goals are
 * distributed across sections.
 */
data class SectionGoals(
    val enabled: Boolean = false,
    val percentages: SectionGoalPercentages = SectionGoalPercentages(),
) {
    /**
     * Goal macros for [sectionId] derived from [dailyGoals] scaled by this
     * section's percentage of each macro, with kcal computed as P*4 + C*4 + F*9.
     * Returns null when section goals are disabled. Falls back to an even
     * split when the distribution is missing or stale for [sectionIds].
     */
    fun macroGoalFor(
        dailyGoals: DailyGoals,
        sectionId: Long,
        sectionIds: Collection<Long>,
    ): Macros? {
        if (!enabled) return null
        val resolved = this.percentages.resolve(sectionIds)[sectionId] ?: return null
        val proteinG = dailyGoals.proteinG * (resolved[MacroType.PROTEIN] ?: 0f) / 100f
        val carbsG = dailyGoals.carbsG * (resolved[MacroType.CARBS] ?: 0f) / 100f
        val fatG = dailyGoals.fatG * (resolved[MacroType.FAT] ?: 0f) / 100f
        return Macros(
            kcal = proteinG * 4 + carbsG * 4 + fatG * 9,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
        )
    }
}
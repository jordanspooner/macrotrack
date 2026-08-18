package com.macrotrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SectionGoalSettingsTest {

    private val dailyGoals = DailyGoals(proteinG = 150, carbsG = 250, fatG = 65)

    @Test
    fun `disabled returns no per-meal goal`() {
        val sectionGoals = SectionGoals(enabled = false)
        assertNull(sectionGoals.macroGoalFor(dailyGoals, sectionId = 1L, sectionIds = listOf(1L)))
    }

    @Test
    fun `missing distribution falls back to even split`() {
        val sectionGoals = SectionGoals(
            enabled = true,
            percentages = SectionGoalPercentages(percentages = emptyMap()),
        )
        val goals = sectionGoals.macroGoalFor(dailyGoals, sectionId = 1L, sectionIds = listOf(1L, 2L))!!

        assertEquals(75f, goals.proteinG, 0.01f) // 150 * 50%
        assertEquals(125f, goals.carbsG, 0.01f) // 250 * 50%
        assertEquals(32.5f, goals.fatG, 0.01f) // 65 * 50%
        assertEquals(1092.5f, goals.kcal, 0.01f) // 75*4 + 125*4 + 32.5*9
    }

    @Test
    fun `stale distribution falls back to even split`() {
        val sectionGoals = SectionGoals(
            enabled = true,
            percentages = SectionGoalPercentages(
                percentages = mapOf(
                    1L to mapOf(MacroType.PROTEIN to 100f, MacroType.CARBS to 100f, MacroType.FAT to 100f),
                    2L to mapOf(MacroType.PROTEIN to 0f, MacroType.CARBS to 0f, MacroType.FAT to 0f),
                ),
            ),
        )
        // Section 2 replaced by section 3 in the current sections → distribution stale.
        val goals = sectionGoals.macroGoalFor(dailyGoals, sectionId = 1L, sectionIds = listOf(1L, 3L))!!
        assertEquals(75f, goals.proteinG, 0.01f)
        assertEquals(125f, goals.carbsG, 0.01f)
    }

    @Test
    fun `distributed grams and kcal match percentages`() {
        val sectionGoals = SectionGoals(
            enabled = true,
            percentages = SectionGoalPercentages(
                percentages = mapOf(
                    1L to mapOf(MacroType.PROTEIN to 40f, MacroType.CARBS to 30f, MacroType.FAT to 20f),
                    2L to mapOf(MacroType.PROTEIN to 60f, MacroType.CARBS to 70f, MacroType.FAT to 80f),
                ),
            ),
        )
        val goals = sectionGoals.macroGoalFor(dailyGoals, sectionId = 1L, sectionIds = listOf(1L, 2L))!!

        assertEquals(60f, goals.proteinG, 0.01f) // 150 * 40%
        assertEquals(75f, goals.carbsG, 0.01f) // 250 * 30%
        assertEquals(13f, goals.fatG, 0.01f) // 65 * 20%
        assertEquals(60 * 4 + 75 * 4 + 13 * 9f, goals.kcal, 0.01f)
    }

    @Test
    fun `incomplete macros within a stored section fall back to even share`() {
        val sectionGoals = SectionGoals(
            enabled = true,
            percentages = SectionGoalPercentages(
                percentages = mapOf(
                    1L to mapOf(MacroType.PROTEIN to 40f),
                    2L to mapOf(MacroType.PROTEIN to 60f),
                ),
            ),
        )
        val goals = sectionGoals.macroGoalFor(dailyGoals, sectionId = 1L, sectionIds = listOf(1L, 2L))!!

        assertEquals(60f, goals.proteinG, 0.01f) // stored
        assertEquals(125f, goals.carbsG, 0.01f) // missing → even share
        assertEquals(32.5f, goals.fatG, 0.01f) // missing → even share
    }

    @Test
    fun `even split shares each macro equally`() {
        val split = SectionGoalPercentages.evenSplit(listOf(1L, 2L, 3L))
        assertEquals(3, split.size)
        for ((_, macros) in split) {
            assertEquals(100f / 3f, macros[MacroType.PROTEIN]!!, 0.001f)
            assertEquals(100f / 3f, macros[MacroType.CARBS]!!, 0.001f)
            assertEquals(100f / 3f, macros[MacroType.FAT]!!, 0.001f)
        }
    }

    @Test
    fun `resolve handles empty sections`() {
        assertEquals(emptyMap<Long, Map<MacroType, Float>>(), SectionGoalPercentages().resolve(emptyList()))
    }
}
package com.macrotrack.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionDistributionTest {

    @Test
    fun `init distribution splits evenly across sections`() {
        val sections = listOf(
            DraftSection(id = 1, name = "A", timeOfDay = java.time.LocalTime.of(8, 0), sortOrder = 0),
            DraftSection(id = 2, name = "B", timeOfDay = java.time.LocalTime.of(12, 0), sortOrder = 1),
            DraftSection(id = 3, name = "C", timeOfDay = java.time.LocalTime.of(18, 0), sortOrder = 2),
        )
        val dist = SettingsViewModel.initDistribution(sections)
        assertEquals(3, dist.size)
        for (sectionId in dist.keys) {
            val macros = dist[sectionId]!!
            val total = macros.values.sum()
            assertEquals(100f, total, 0.01f)
        }
    }

    @Test
    fun `serialize then parse round-trips correctly`() {
        val original = mapOf(
            1L to mapOf(MacroType.PROTEIN to 40f, MacroType.CARBS to 30f, MacroType.FAT to 30f),
            2L to mapOf(MacroType.PROTEIN to 60f, MacroType.CARBS to 20f, MacroType.FAT to 20f),
        )
        val json = SettingsViewModel.serializeDistribution(original)
        val parsed = SettingsViewModel.parseDistribution(json)
        assertEquals(original, parsed)
    }

    @Test
    fun `parse handles empty json`() {
        assertTrue(SettingsViewModel.parseDistribution("").isEmpty())
        assertTrue(SettingsViewModel.parseDistribution("{}").isEmpty())
    }
}
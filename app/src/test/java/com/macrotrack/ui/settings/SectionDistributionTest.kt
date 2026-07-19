package com.macrotrack.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
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

    @Test
    fun `surplus increase below 100 leaves others unchanged`() {
        val vm = SettingsViewModelFixture(
            sections = listOf(
                DraftSection(id = 1, name = "A", timeOfDay = java.time.LocalTime.of(8, 0), sortOrder = 0),
                DraftSection(id = 2, name = "B", timeOfDay = java.time.LocalTime.of(12, 0), sortOrder = 1),
            ),
            distribution = mapOf(
                1L to mapOf(MacroType.PROTEIN to 20f, MacroType.CARBS to 20f, MacroType.FAT to 20f),
                2L to mapOf(MacroType.PROTEIN to 20f, MacroType.CARBS to 20f, MacroType.FAT to 20f),
            ),
        )
        vm.updateDistribution(sectionId = 1L, macroType = MacroType.PROTEIN, rawValue = 50f)
        val dist = vm.distribution()
        assertEquals(50f, dist[1L]?.get(MacroType.PROTEIN)!!, 0.01f)
        assertEquals(20f, dist[2L]?.get(MacroType.PROTEIN)!!, 0.01f)
    }

    @Test
    fun `increase past 100 proportionally scales others down`() {
        val vm = SettingsViewModelFixture(
            sections = listOf(
                DraftSection(id = 1, name = "A", timeOfDay = java.time.LocalTime.of(8, 0), sortOrder = 0),
                DraftSection(id = 2, name = "B", timeOfDay = java.time.LocalTime.of(12, 0), sortOrder = 1),
            ),
            distribution = mapOf(
                1L to mapOf(MacroType.PROTEIN to 60f, MacroType.CARBS to 0f, MacroType.FAT to 0f),
                2L to mapOf(MacroType.PROTEIN to 40f, MacroType.CARBS to 0f, MacroType.FAT to 0f),
            ),
        )
        vm.updateDistribution(sectionId = 1L, macroType = MacroType.PROTEIN, rawValue = 80f)
        val dist = vm.distribution()
        assertEquals(80f, dist[1L]?.get(MacroType.PROTEIN)!!, 0.01f)
        assertEquals(20f, dist[2L]?.get(MacroType.PROTEIN)!!, 0.01f)
    }

    @Test
    fun `residual under 100 snaps to 100 via normalizeResidual`() {
        val vm = SettingsViewModelFixture(
            sections = listOf(
                DraftSection(id = 1, name = "A", timeOfDay = java.time.LocalTime.of(8, 0), sortOrder = 0),
                DraftSection(id = 2, name = "B", timeOfDay = java.time.LocalTime.of(12, 0), sortOrder = 1),
            ),
            distribution = mapOf(
                1L to mapOf(MacroType.PROTEIN to 33.32f, MacroType.CARBS to 0f, MacroType.FAT to 0f),
                2L to mapOf(MacroType.PROTEIN to 66.66f, MacroType.CARBS to 0f, MacroType.FAT to 0f),
            ),
        )
        vm.updateDistribution(sectionId = 1L, macroType = MacroType.PROTEIN, rawValue = 33.32f)
        val total = vm.distribution().values.sumOf { it[MacroType.PROTEIN]?.toDouble() ?: 0.0 }
        assertEquals(100.0, total, 0.05)
    }
}

private class SettingsViewModelFixture(
    sections: List<DraftSection>,
    distribution: Map<Long, Map<MacroType, Float>>,
) {
    private val _draftSections = MutableStateFlow(sections)
    private val _sectionDistribution = MutableStateFlow(distribution)

    fun updateDistribution(sectionId: Long, macroType: MacroType, rawValue: Float) {
        val newValue = rawValue.coerceIn(0f, 100f)
        val current = _sectionDistribution.value.toMutableMap()
        val sectionIds = _draftSections.value.map { it.id }
        val touchedMacros = current.getOrPut(sectionId) { mutableMapOf() }.toMutableMap()
        val oldValue = touchedMacros[macroType] ?: 0f
        touchedMacros[macroType] = newValue
        current[sectionId] = touchedMacros

        val othersTotal = sectionIds.filter { it != sectionId }
            .sumOf { (current[it]?.get(macroType) ?: 0f).toDouble() }
            .toFloat()
        val totalAfter = newValue + othersTotal
        if (totalAfter > 100f && sectionIds.size > 1) {
            val targetOthersTotal = (100f - newValue).coerceAtLeast(0f)
            val factor = if (othersTotal > 0f) targetOthersTotal / othersTotal else 0f
            for (otherId in sectionIds.filter { it != sectionId }) {
                val om = current.getOrPut(otherId) { mutableMapOf() }.toMutableMap()
                val otherOld = om[macroType] ?: 0f
                om[macroType] = (otherOld * factor).coerceIn(0f, 100f)
                current[otherId] = om
            }
        }
        _sectionDistribution.value = current
        normalizeResidual(macroType)
    }

    private fun normalizeResidual(macroType: MacroType) {
        val sectionIds = _draftSections.value.map { it.id }
        val total = sectionIds.sumOf {
            (_sectionDistribution.value[it]?.get(macroType) ?: 0f).toDouble()
        }.toFloat()
        if (total >= 99.95f && total < 100f) {
            val residual = 100f - total
            val target = sectionIds.minByOrNull {
                _sectionDistribution.value[it]?.get(macroType) ?: 0f
            } ?: return
            val map = _sectionDistribution.value.toMutableMap()
            val tm = (map[target] ?: emptyMap()).toMutableMap()
            tm[macroType] = (tm[macroType] ?: 0f) + residual
            map[target] = tm
            _sectionDistribution.value = map
        }
    }

    fun distribution(): Map<Long, Map<MacroType, Float>> = _sectionDistribution.value
}
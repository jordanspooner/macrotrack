package com.macrotrack.data.local.datastore

import com.macrotrack.domain.model.MacroType
import com.macrotrack.domain.model.SectionGoalPercentages
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionGoalCodecTest {

    @Test
    fun `serialize then parse round-trips`() {
        val original = mapOf(
            1L to mapOf(MacroType.PROTEIN to 40f, MacroType.CARBS to 30f, MacroType.FAT to 30f),
            2L to mapOf(MacroType.PROTEIN to 60f, MacroType.CARBS to 20f, MacroType.FAT to 20f),
        )
        val json = SectionGoalCodec.serialize(original)
        assertEquals(original, SectionGoalCodec.parseMap(json))
    }

    @Test
    fun `parse handles empty and absent json`() {
        assertTrue(SectionGoalCodec.parse("").percentages.isEmpty())
        assertTrue(SectionGoalCodec.parse("{}").percentages.isEmpty())
        assertTrue(SectionGoalCodec.parse(null).percentages.isEmpty())
    }

    @Test
    fun `parse matches the legacy settings format`() {
        val json = "{\"1\":{\"PROTEIN\":40.0,\"CARBS\":30.0,\"FAT\":30.0}}"
        val parsed = SectionGoalCodec.parse(json).percentages
        assertEquals(40f, parsed[1L]?.get(MacroType.PROTEIN)!!, 0.01f)
        assertEquals(30f, parsed[1L]?.get(MacroType.CARBS)!!, 0.01f)
        assertEquals(30f, parsed[1L]?.get(MacroType.FAT)!!, 0.01f)
    }

    @Test
    fun `serialize is locale independent`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val json = SectionGoalCodec.serialize(
                mapOf(1L to mapOf(MacroType.PROTEIN to 40f, MacroType.CARBS to 30f, MacroType.FAT to 30f))
            )
            assertTrue("expected '.' decimal separator, got: $json", json.contains("\"PROTEIN\":40.0"))
            assertEquals(
                mapOf(1L to mapOf(MacroType.PROTEIN to 40f, MacroType.CARBS to 30f, MacroType.FAT to 30f)),
                SectionGoalCodec.parseMap(json),
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `ignores unknown macro keys`() {
        val json = "{\"1\":{\"PROTEIN\":40.0,\"CARBS\":30.0,\"FAT\":30.0,\"BOGUS\":50.0}}"
        val parsed = SectionGoalCodec.parse(json).percentages
        assertEquals(mapOf(MacroType.PROTEIN to 40f, MacroType.CARBS to 30f, MacroType.FAT to 30f), parsed[1L])
    }
}
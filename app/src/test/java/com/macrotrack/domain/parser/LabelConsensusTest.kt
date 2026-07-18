package com.macrotrack.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelConsensusTest {

    private fun label(
        kcal: Float? = null,
        protein: Float? = null,
        carbs: Float? = null,
        fat: Float? = null,
        serving: Float? = null,
        servingLabel: String? = null
    ) = ParsedNutritionLabel(
        per100 = MacroValues(kcal, fat, carbs, protein),
        perServing = null,
        servingSizeG = serving,
        servingLabel = servingLabel
    )

    @Test
    fun `all four fields lock together once each has MIN_READS agreeing reads and combo is consistent`() {
        var c = LabelConsensus()
        // Not enough reads yet — should not lock.
        repeat(ConsensusField.MIN_READS - 1) { c = c.accept(label(kcal = 200f, protein = 15f, carbs = 20f, fat = 5f)) }
        assertFalse(c.kcal.confirmed)
        // On the MIN_READS-th read all four fields reach the threshold and the
        // combo is Atwater-consistent (15*4 + 20*4 + 5*9 = 185 ≈ 200), so the
        // whole group locks.
        c = c.accept(label(kcal = 200f, protein = 15f, carbs = 20f, fat = 5f))
        assertTrue(c.kcal.confirmed)
        assertTrue(c.protein.confirmed && c.carbs.confirmed && c.fat.confirmed)
    }

    @Test
    fun `core four lock together when all observed fields are confident and combo is consistent`() {
        var c = LabelConsensus()
        // Macros with partial evidence (1-2 reads, below MIN_READS) block the lock.
        c = c.accept(label(kcal = 200f, protein = 15f))
        c = c.accept(label(kcal = 200f, carbs = 20f))
        assertFalse(c.kcal.confirmed) // protein/carbs/fat not all confident yet
        // Once all observed fields reach MIN_READS and combo is consistent, everything locks.
        repeat(ConsensusField.MIN_READS) {
            c = c.accept(label(kcal = 200f, protein = 15f, carbs = 20f, fat = 5f))
        }
        assertTrue(c.kcal.confirmed)
        assertTrue(c.protein.confirmed && c.carbs.confirmed && c.fat.confirmed)
    }

    @Test
    fun `nil macros do not block lock when genuinely absent`() {
        var c = LabelConsensus()
        // Consistent scans with no macros — nil fields have readCount 0, so they
        // don't block the lock.
        repeat(ConsensusField.MIN_READS) { c = c.accept(label(kcal = 200f)) }
        assertTrue(c.kcal.confirmed)
        // Nil macros stay unconfirmed (locked to null).
        assertFalse(c.protein.confirmed)
        assertFalse(c.carbs.confirmed)
        assertFalse(c.fat.confirmed)
    }

    @Test
    fun `field locks to the most common value`() {
        var c = LabelConsensus()
        repeat(ConsensusField.MIN_READS) { c = c.accept(label(kcal = 200f)) }
        c = c.accept(label(kcal = 201f))
        c = c.accept(label(kcal = 201f))
        assertEquals(200f, c.kcal.value)
    }

    @Test
    fun `field does not lock when there is no majority`() {
        var c = LabelConsensus()
        // Three distinct values so no single value can reach >half
        val values = listOf(200f, 300f, 400f)
        repeat(ConsensusField.MIN_READS * 3) {
            c = c.accept(label(kcal = values[it % 3]))
        }
        assertFalse(c.kcal.confirmed)
    }

    @Test
    fun `null readings are ignored`() {
        var c = LabelConsensus()
        repeat(ConsensusField.MIN_READS) { c = c.accept(label(kcal = null)) }
        assertEquals(0, c.kcal.readCount)
    }

    @Test
    fun `implausible readings are discarded`() {
        var c = LabelConsensus()
        repeat(ConsensusField.MIN_READS) { c = c.accept(label(kcal = 5000f)) }
        assertEquals(0, c.kcal.readCount)
    }

    @Test
    fun `plausibility check uses 0 to 1000 for kcal`() {
        var c = LabelConsensus()
        c = c.accept(label(kcal = -1f))
        assertEquals(0, c.kcal.readCount)
        c = c.accept(label(kcal = 1001f))
        assertEquals(0, c.kcal.readCount)
        c = c.accept(label(kcal = 500f))
        assertEquals(1, c.kcal.readCount)
    }

    @Test
    fun `macros cannot exceed 100`() {
        var c = LabelConsensus()
        c = c.accept(label(fat = 101f))
        assertEquals(0, c.fat.readCount)
        c = c.accept(label(fat = 50f))
        assertEquals(1, c.fat.readCount)
    }

    @Test
    fun `consistency check - macros must add up to kcal`() {
        var c = LabelConsensus()
        // kcal=100, protein=25, carbs=0, fat=0 => computed=100, consistent
        repeat(ConsensusField.MIN_READS) {
            c = c.accept(label(kcal = 100f, protein = 25f, carbs = 0f, fat = 0f))
        }
        assertTrue(c.kcal.confirmed)

        // Now start with inconsistent values
        c = LabelConsensus()
        repeat(ConsensusField.MIN_READS) {
            c = c.accept(label(kcal = 100f, protein = 50f, carbs = 50f, fat = 50f))
        }
        assertFalse(c.kcal.confirmed)
    }

    @Test
    fun `lock persists even after inconsistent readings`() {
        var c = LabelConsensus()
        repeat(ConsensusField.MIN_READS) {
            c = c.accept(label(kcal = 250f, protein = 12f, carbs = 30f, fat = 8f))
        }
        assertTrue(c.kcal.confirmed)
        val locked = c.kcal.value
        // Consistent reading that is different
        c = c.accept(label(kcal = 300f, protein = 12f, carbs = 30f, fat = 8f))
        assertEquals(locked, c.kcal.value)
    }

    @Test
    fun `toParsedLabel preserves consensus values including serving label`() {
        var c = LabelConsensus()
        repeat(ConsensusField.MIN_READS) {
            c = c.accept(label(kcal = 250f, protein = 12f, carbs = 30f, fat = 8f, serving = 150f, servingLabel = "1 portion"))
        }
        val parsed = c.toParsedLabel()
        assertEquals(250f, parsed.per100?.kcal)
        assertEquals(12f, parsed.per100?.protein)
        assertEquals(30f, parsed.per100?.carbs)
        assertEquals(8f, parsed.per100?.fat)
        assertEquals(150f, parsed.servingSizeG)
        assertEquals("1 portion", parsed.servingLabel)
    }

    @Test
    fun `progress ramps up over MIN_READS`() {
        var c = LabelConsensus()
        assertEquals(0f, c.kcal.progress, 0.01f)
        c = c.accept(label(kcal = 200f))
        assertEquals(1f / ConsensusField.MIN_READS, c.kcal.progress, 0.01f)
        repeat(ConsensusField.MIN_READS - 1) { c = c.accept(label(kcal = 200f)) }
        assertTrue(c.kcal.progress >= 1f)
    }

    @Test
    fun `sum of macros check respects absolute tolerance for near-zero labels`() {
        var c = LabelConsensus()
        // kcal=1, protein=0, carbs=0, fat=0 => computed=0, diff=1 < 20 => consistent
        repeat(ConsensusField.MIN_READS) {
            c = c.accept(label(kcal = 1f, protein = 0f, carbs = 0f, fat = 0f))
        }
        assertTrue(c.kcal.confirmed)
    }

    @Test
    fun `accept from per-serving with serving size resolves to per-100g`() {
        // Per-serving (50g): kcal=100, fat=5, carbs=10, protein=2.5
        // Resolves to per-100g: kcal=200, fat=10, carbs=20, protein=5
        // Consistency: 4*5+4*20+9*10 = 20+80+90 = 190 ≈ 200 ✓
        val perServ = ParsedNutritionLabel(
            per100 = null,
            perServing = MacroValues(kcal = 100f, fat = 5f, carbs = 10f, protein = 2.5f),
            servingSizeG = 50f,
            servingLabel = null
        )
        var c = LabelConsensus()
        repeat(ConsensusField.MIN_READS) { c = c.accept(perServ) }
        assertTrue(c.kcal.confirmed)
        assertEquals(200f, c.kcal.value!!, 0.01f)
        assertEquals(10f, c.fat.value!!, 0.01f)
        assertEquals(20f, c.carbs.value!!, 0.01f)
        assertEquals(5f, c.protein.value!!, 0.01f)
    }

    @Test
    fun `cross-validation picks per-100g when both columns present`() {
        val both = ParsedNutritionLabel(
            per100 = MacroValues(kcal = 200f, fat = 10f, carbs = 20f, protein = 5f),
            perServing = MacroValues(kcal = 100f, fat = 5f, carbs = 10f, protein = 2.5f),
            servingSizeG = 50f,
            servingLabel = null
        )
        var c = LabelConsensus()
        repeat(ConsensusField.MIN_READS) { c = c.accept(both) }
        assertEquals(200f, c.kcal.value!!, 0.01f)
        assertEquals(10f, c.fat.value!!, 0.01f)
    }

    @Test
    fun `cross-validation uses per-serving when per-100g is null`() {
        val noPer100 = ParsedNutritionLabel(
            per100 = null,
            perServing = MacroValues(kcal = 100f, fat = 5f, carbs = 10f, protein = 2.5f),
            servingSizeG = 50f,
            servingLabel = null
        )
        var c = LabelConsensus()
        repeat(ConsensusField.MIN_READS) { c = c.accept(noPer100) }
        assertEquals(200f, c.kcal.value!!, 0.01f)
        assertEquals(10f, c.fat.value!!, 0.01f)
    }

    @Test
    fun `serving confusion detected when history has two values matching serving ratio`() {
        // Simulate: OCR sometimes reads fat=75 (per-100g), sometimes fat=15 (per-serving, 20g serving)
        // 75/15 = 5, and 100/20 = 5 -> ratio matches -> should resolve to 75
        var c = LabelConsensus()
        // Alternate between per-100g and per-serving values
        repeat(5) {
            c = c.accept(ParsedNutritionLabel(
                per100 = MacroValues(kcal = null, fat = 75f, carbs = null, protein = null),
                perServing = null,
                servingSizeG = 20f,
                servingLabel = null
            ))
        }
        repeat(5) {
            c = c.accept(ParsedNutritionLabel(
                per100 = null,
                perServing = MacroValues(kcal = null, fat = 15f, carbs = null, protein = null),
                servingSizeG = 20f,
                servingLabel = null
            ))
        }
        // Should resolve: per-100g=75 is the correct value, per-serving=15 should be discarded
        // After resolution, all remaining values should be 75
        assertEquals(75f, c.fat.value!!, 0.01f)
    }
}

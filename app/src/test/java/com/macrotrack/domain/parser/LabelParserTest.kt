package com.macrotrack.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelParserTest {

    private val parser = LabelParser()

    @Test
    fun `parses a typical uk per-100g label`() {
        val text = """
            Sainsbury's Orange Juice
            Typical values per 100g
            Energy 197 kJ / 47 kcal
            Fat 0.1 g
            Carbohydrate 9.5 g
            of which sugars 8.0 g
            Protein 0.8 g
            Salt 0.0 g
            Serving size: 150 g
        """.trimIndent()

        val result = parser.parse(text)
        assertEquals(47f, result.per100?.kcal!!, 0.01f)
        assertEquals(0.1f, result.per100?.fat!!, 0.01f)
        assertEquals(9.5f, result.per100?.carbs!!, 0.01f)
        assertEquals(0.8f, result.per100?.protein!!, 0.01f)
        assertEquals(150f, result.servingSizeG!!, 0.01f)
    }

    @Test
    fun `returns null kcal when missing`() {
        val result = parser.parse("per 100g\nFat 5 g\nCarbohydrate 70 g\nProtein 10 g")
        assertNull(result.per100?.kcal)
    }

    @Test
    fun `handles comma decimal separator`() {
        val result = parser.parse("per 100g\nEnergy 47,5 kcal\nFat 1,2 g\nCarbohydrate 9,0 g\nProtein 0,8 g")
        assertEquals(47.5f, result.per100?.kcal!!, 0.01f)
        assertEquals(1.2f, result.per100?.fat!!, 0.01f)
    }

    @Test
    fun `detects per 100g from 100g token`() {
        val result = parser.parse("Nutrition 100g\nEnergy 200 kcal\nFat 10 g\nCarbohydrate 20 g\nProtein 5 g")
        assertNotNull(result.per100)
    }

    @Test
    fun `scales a per-serving table up to per 100g`() {
        val text = """
            Per serving
            Energy 100 kcal
            Fat 5 g
            Carbohydrate 10 g
            Protein 4 g
            Serving size: 50 g
        """.trimIndent()
        val result = parser.parse(text)
        // Per-serving values should be scaled to per-100g via cross-validation
        assertNotNull(result.perServing)
        assertEquals(50f, result.servingSizeG!!, 0.01f)
    }

    @Test
    fun `converts energy given only in kJ`() {
        val result = parser.parse("per 100g\nEnergy 837 kJ\nFat 5 g\nCarbohydrate 10 g\nProtein 4 g")
        assertEquals(200f, result.per100?.kcal!!, 0.5f)
    }

    @Test
    fun `reads macro value placed on the following line`() {
        val text = """
            per 100g
            Energy 200 kcal
            Fat
            12.0 g
            Carbohydrate
            34.0 g
            Protein
            8.0 g
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals(12f, result.per100?.fat!!, 0.01f)
        assertEquals(34f, result.per100?.carbs!!, 0.01f)
        assertEquals(8f, result.per100?.protein!!, 0.01f)
    }

    @Test
    fun `parses a variety of serving-size phrasings`() {
        val result = parser.parse("per 100g\nEnergy 200 kcal\nFat 10 g\n1 portion (50g)")
        assertEquals(50f, result.servingSizeG!!, 0.01f)
    }

    @Test
    fun `ignores saturated fat when extracting total fat`() {
        val text = """
            per 100g
            Energy 200 kcal
            Fat 12.0 g
            of which saturates 2.0 g
            Carbohydrate 10 g
            Protein 4 g
        """.trimIndent()
        val result = parser.parse(text)
        assertEquals(12f, result.per100?.fat!!, 0.01f)
    }
}

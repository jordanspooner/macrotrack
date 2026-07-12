package com.macrotrack.dbbuilder.cleaner

import com.macrotrack.dbbuilder.parser.ParsedFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataCleanerTest {

    private val cleaner = DataCleaner()

    private fun food(
        ean: String? = null,
        brand: String? = null,
        name: String = "Food",
        kcal: Float = 125f,
        protein: Float = 10f,
        carbs: Float = 10f,
        fat: Float = 5f
    ) = ParsedFood(
        source = if (ean == null) "USDA" else "OPEN_FOOD_FACTS",
        sourceId = ean ?: name,
        ean = ean,
        brand = brand,
        name = name,
        defaultPortionG = 100f,
        defaultPortionLabel = "100g",
        kcalPer100g = kcal,
        proteinPer100g = protein,
        carbsPer100g = carbs,
        fatPer100g = fat
    )

    @Test
    fun `keeps valid foods`() {
        val result = cleaner.clean(listOf(food()))
        assertEquals(1, result.size)
    }

    @Test
    fun `drops foods with macro mass over 100g`() {
        val result = cleaner.clean(listOf(food(protein = 60f, carbs = 60f, fat = 60f)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `drops foods with inconsistent kcal`() {
        // 10*4 + 10*4 + 5*9 = 125, but stated 1000 kcal -> >15% off
        val result = cleaner.clean(listOf(food(kcal = 1000f)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `normalises name casing`() {
        val result = cleaner.clean(listOf(food(name = "cheddar cheese")))
        assertEquals("Cheddar cheese", result.first().name)
    }

    @Test
    fun `deduplicates by ean keeping more complete record`() {
        val basic = food(ean = "123", brand = null)
        val rich = food(ean = "123", brand = "Brand X")
        val result = cleaner.clean(listOf(basic, rich))
        assertEquals(1, result.size)
        assertEquals("Brand X", result.first().brand)
    }

    @Test
    fun `deduplicates usda by name and brand`() {
        val a = food(name = "Oats", brand = "Tesco")
        val b = food(name = "oats", brand = "tesco")
        val result = cleaner.clean(listOf(a, b))
        assertEquals(1, result.size)
    }
}

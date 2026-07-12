package com.macrotrack.dbbuilder.cleaner

import com.macrotrack.dbbuilder.parser.ParsedFood
import java.util.Locale

/**
 * Validates, normalises and de-duplicates parsed food records before they are
 * written to the pre-built database.
 */
class DataCleaner {

    fun clean(foods: List<ParsedFood>): List<ParsedFood> {
        println("Cleaning data (starting with ${foods.size} records)...")

        val validFoods = foods.filter { isValid(it) }
        println("Valid foods: ${validFoods.size}")

        val normalizedFoods = validFoods.map { normalize(it) }

        val deduplicatedFoods = deduplicate(normalizedFoods)
        println("Cleaned data: ${deduplicatedFoods.size} records.")

        return deduplicatedFoods
    }

    /**
     * Keeps only foods with valid, internally-consistent nutrition data.
     */
    private fun isValid(food: ParsedFood): Boolean {
        if (food.kcalPer100g <= 0) return false
        if (listOf(food.proteinPer100g, food.carbsPer100g, food.fatPer100g).any { it < 0 }) return false

        // Macronutrient mass cannot exceed 100g per 100g (allow rounding slack).
        if (food.proteinPer100g + food.carbsPer100g + food.fatPer100g > 105f) return false

        // Pure fat is ~9 kcal/g, so 100g of food tops out near 900 kcal.
        if (food.kcalPer100g > 950f) return false

        // Sanity: stated kcal should roughly match P*4 + C*4 + F*9 (15% slack).
        val computed = food.proteinPer100g * 4 + food.carbsPer100g * 4 + food.fatPer100g * 9
        val diff = kotlin.math.abs(food.kcalPer100g - computed)
        if (computed > 0 && (diff / food.kcalPer100g) > 0.15f) return false

        return true
    }

    private fun normalize(food: ParsedFood): ParsedFood {
        return food.copy(
            name = food.name.trim().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            },
            brand = food.brand?.trim()?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * De-duplicates records. Foods carrying an EAN are merged by EAN, preferring
     * the most complete record (brand present, serving size present). USDA foods
     * (no EAN) are de-duplicated by a normalised name+brand key.
     */
    private fun deduplicate(foods: List<ParsedFood>): List<ParsedFood> {
        val byEan = mutableMapOf<String, ParsedFood>()
        val byName = mutableMapOf<String, ParsedFood>()
        val result = mutableListOf<ParsedFood>()

        for (food in foods) {
            if (!food.ean.isNullOrBlank()) {
                val existing = byEan[food.ean]
                if (existing == null || isMoreComplete(food, existing)) {
                    byEan[food.ean] = food
                }
            } else {
                val key = nameKey(food)
                val existing = byName[key]
                if (existing == null) {
                    byName[key] = food
                }
            }
        }

        result.addAll(byEan.values)
        result.addAll(byName.values)
        return result
    }

    private fun nameKey(food: ParsedFood): String {
        val brand = food.brand?.lowercase(Locale.getDefault()).orEmpty()
        return "${brand}::${food.name.lowercase(Locale.getDefault())}"
    }

    /**
     * True when [candidate] is a strictly better record than [current].
     */
    private fun isMoreComplete(candidate: ParsedFood, current: ParsedFood): Boolean {
        val scoreC = completenessScore(candidate)
        val scoreE = completenessScore(current)
        return scoreC > scoreE
    }

    private fun completenessScore(food: ParsedFood): Int {
        var score = 0
        if (!food.brand.isNullOrBlank()) score += 2
        if (food.defaultPortionG != null && food.defaultPortionG > 0f) score += 1
        if (!food.defaultPortionLabel.isNullOrBlank()) score += 1
        return score
    }
}

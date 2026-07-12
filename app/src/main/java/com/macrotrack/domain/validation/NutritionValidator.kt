package com.macrotrack.domain.validation

import com.macrotrack.domain.model.Macros
import kotlin.math.abs

object NutritionValidator {

    /**
     * Checks if the given macros are consistent, meaning the kcal is roughly
     * equal to protein * 4 + carbs * 4 + fat * 9.
     * Allows a 15% tolerance due to rounding and varying calculation methods.
     */
    fun areMacrosConsistent(macros: Macros, tolerance: Float = 0.15f): Boolean {
        if (macros.kcal <= 0) return false
        val computed = macros.computedKcal
        val diff = abs(macros.kcal - computed)
        return (diff / macros.kcal) <= tolerance
    }

    /**
     * Validates that per 100g values do not exceed 100g total for macronutrients.
     */
    fun isValidPer100g(macros: Macros): Boolean {
        return (macros.proteinG + macros.carbsG + macros.fatG) <= 100f
    }
}

package com.macrotrack.domain.model

data class Macros(
    val kcal: Float,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
) {
    /** Computed kcal from macros: P*4 + C*4 + F*9 */
    val computedKcal: Float get() = proteinG * 4 + carbsG * 4 + fatG * 9

    /** Scale macros by a multiplier (e.g., for portion adjustment) */
    operator fun times(factor: Float) = Macros(
        kcal = kcal * factor,
        proteinG = proteinG * factor,
        carbsG = carbsG * factor,
        fatG = fatG * factor,
    )

    /** Sum two Macros (e.g., aggregating section totals) */
    operator fun plus(other: Macros) = Macros(
        kcal = kcal + other.kcal,
        proteinG = proteinG + other.proteinG,
        carbsG = carbsG + other.carbsG,
        fatG = fatG + other.fatG,
    )
}

package com.macrotrack.domain.model

data class DailyGoals(
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
) {
    val kcal: Int get() = proteinG * 4 + carbsG * 4 + fatG * 9
    val proteinPercent: Float get() = if (kcal > 0) (proteinG * 4f) / kcal * 100 else 0f
    val carbsPercent: Float get() = if (kcal > 0) (carbsG * 4f) / kcal * 100 else 0f
    val fatPercent: Float get() = if (kcal > 0) (fatG * 9f) / kcal * 100 else 0f
}

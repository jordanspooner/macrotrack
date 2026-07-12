package com.macrotrack.domain.model

import java.time.LocalDate

data class DailySummary(
    val date: LocalDate,
    val logged: Macros,
    val goals: DailyGoals,
) {
    val kcalPercent: Float get() = if (goals.kcal > 0) logged.kcal / goals.kcal else 0f
    val proteinPercent: Float get() = if (goals.proteinG > 0) logged.proteinG / goals.proteinG else 0f
    val carbsPercent: Float get() = if (goals.carbsG > 0) logged.carbsG / goals.carbsG else 0f
    val fatPercent: Float get() = if (goals.fatG > 0) logged.fatG / goals.fatG else 0f
}

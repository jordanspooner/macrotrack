package com.macrotrack.domain.model

import java.time.Instant
import java.time.LocalDate

data class LogEntry(
    val id: Long = 0,
    val date: LocalDate,
    val sectionId: Long,
    val foodItemId: Long? = null,
    val name: String,
    val brand: String? = null,
    val portionG: Float,
    val portionLabel: String? = null,
    val macros: Macros,             // Pre-computed for this portion
    val sortOrder: Int,
    val createdAt: Instant,
)

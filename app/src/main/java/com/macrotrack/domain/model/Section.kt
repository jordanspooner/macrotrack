package com.macrotrack.domain.model

import java.time.LocalTime

data class Section(
    val id: Long = 0,
    val name: String,
    val timeOfDay: LocalTime,       // For default section selection
    val sortOrder: Int,
)

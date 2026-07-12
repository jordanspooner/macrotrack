package com.macrotrack.domain.model

enum class Source { USDA, OPEN_FOOD_FACTS, USER }

data class FoodItem(
    val id: Long = 0,
    val source: Source,             // USDA, OPEN_FOOD_FACTS, USER
    val sourceId: String? = null,
    val ean: String? = null,
    val brand: String? = null,
    val name: String,
    val defaultPortionG: Float? = null,
    val defaultPortionLabel: String? = null,
    val macroPer100g: Macros,       // Always per 100g
)

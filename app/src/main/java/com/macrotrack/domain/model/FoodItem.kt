package com.macrotrack.domain.model

enum class Source { USDA, OPEN_FOOD_FACTS, SUPERMARKET, RESTAURANT, USER }

data class FoodItem(
    val id: Long = 0,
    val source: Source,
    val sourceId: String? = null,
    val dataSourceId: String? = null,
    val ean: String? = null,
    val brand: String? = null,
    val name: String,
    val defaultPortionG: Float? = null,
    val defaultPortionLabel: String? = null,
    val macroPer100g: Macros,
)

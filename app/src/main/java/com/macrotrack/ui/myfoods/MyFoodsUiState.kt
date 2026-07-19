package com.macrotrack.ui.myfoods

import com.macrotrack.domain.model.FoodItem

data class MyFoodsUiState(
    val query: String = "",
    val foods: List<FoodItem> = emptyList(),
)

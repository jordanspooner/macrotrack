package com.macrotrack.ui.foodsources

import com.macrotrack.domain.model.FoodSource

data class FoodSourcesUiState(
    val sources: List<FoodSource> = emptyList(),
    val isLoadingCatalog: Boolean = false,
    val catalogError: String? = null,
    val downloadProgress: Map<String, Float> = emptyMap(),
    val downloadError: String? = null,
)

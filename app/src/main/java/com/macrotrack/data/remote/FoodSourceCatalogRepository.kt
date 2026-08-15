package com.macrotrack.data.remote

import com.macrotrack.domain.model.FoodSource

interface FoodSourceCatalogRepository {
    suspend fun fetchCatalog(): Result<List<FoodSource>>
}

package com.macrotrack.data.repository

import com.macrotrack.domain.model.FoodItem
import kotlinx.coroutines.flow.Flow

interface FoodRepository {
    fun searchFts(query: String): Flow<List<FoodItem>>
    suspend fun getFoodByEan(ean: String): FoodItem?
    suspend fun getFoodById(id: Long): FoodItem?
    suspend fun reseedFromAsset(): Int
}

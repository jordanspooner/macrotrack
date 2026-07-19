package com.macrotrack.data.repository

import com.macrotrack.domain.model.FoodItem
import kotlinx.coroutines.flow.Flow

interface FoodRepository {
    fun searchFts(query: String): Flow<List<FoodItem>>
    fun getAllUserFoods(): Flow<List<FoodItem>>
    fun searchUserFoods(query: String): Flow<List<FoodItem>>
    suspend fun getFoodByEan(ean: String): FoodItem?
    suspend fun getFoodById(id: Long): FoodItem?
    suspend fun insertUserFood(food: FoodItem): FoodItem
    suspend fun insertAll(foods: List<FoodItem>)
    suspend fun deleteByDataSource(sourceId: String)
    suspend fun countByDataSource(sourceId: String): Int
    suspend fun countUserFoods(): Int
    suspend fun updateUserFood(food: FoodItem)
    suspend fun deleteUserFood(id: Long)
    suspend fun count(): Int
    suspend fun clearAll()
}

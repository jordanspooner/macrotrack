package com.macrotrack.data.repository

import com.macrotrack.domain.model.FoodItem
import kotlinx.coroutines.flow.Flow

interface FoodRepository {
    /**
     * FTS5 text search. [query] is the FTS5 MATCH string (e.g. `"chi"* "bre"*`),
     * passed through as a bound parameter. Returns up to 200 candidates in
     * BM25 order (name weighted above brand); the application ranker caps the
     * final result set.
     */
    fun searchFts(query: String): Flow<List<FoodItem>>

    /**
     * FTS5 trigram (fuzzy) search. [query] is raw free text; it is normalized
     * and quoted internally. Returns up to 200 candidates in BM25 order.
     */
    fun searchFtsFuzzy(query: String): Flow<List<FoodItem>>

    fun getAllUserFoods(): Flow<List<FoodItem>>

    /** FTS5 text search restricted to user foods, same contract as [searchFts]. */
    fun searchUserFoods(query: String): Flow<List<FoodItem>>

    /** FTS5 trigram (fuzzy) search restricted to user foods, same contract as [searchFtsFuzzy]. */
    fun searchUserFoodsFuzzy(query: String): Flow<List<FoodItem>>

    suspend fun getFoodByEan(ean: String): FoodItem?
    suspend fun getFoodById(id: Long): FoodItem?
    suspend fun insertUserFood(food: FoodItem): FoodItem
    suspend fun insertAll(foods: List<FoodItem>)
    suspend fun deleteByDataSource(sourceId: String)
    suspend fun countByDataSource(sourceId: String): Int
    suspend fun countUserFoods(): Int
    fun observeCount(): Flow<Int>
    suspend fun updateUserFood(food: FoodItem)
    suspend fun deleteUserFood(id: Long)
    suspend fun count(): Int
    suspend fun clearAll()
}

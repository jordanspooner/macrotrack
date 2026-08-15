package com.macrotrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.macrotrack.data.local.db.entity.FoodItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {
    @Query("""
        SELECT food_items.* FROM food_items
        JOIN food_items_fts ON food_items.id = food_items_fts.rowid
        WHERE food_items_fts MATCH :query
        ORDER BY matchinfo(food_items_fts)
        LIMIT 50
    """)
    fun searchFoods(query: String): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE source = 'USER'")
    fun getAllUserFoods(): Flow<List<FoodItemEntity>>

    @Query("""
        SELECT food_items.* FROM food_items
        JOIN food_items_fts ON food_items.id = food_items_fts.rowid
        WHERE food_items_fts MATCH :query AND food_items.source = 'USER'
        ORDER BY matchinfo(food_items_fts)
    """)
    fun searchUserFoods(query: String): Flow<List<FoodItemEntity>>

    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM food_items WHERE source = 'USER'")
    suspend fun countUserFoods(): Int

    @Query("SELECT COUNT(*) FROM food_items WHERE dataSourceId = :sourceId AND source != 'USER'")
    suspend fun countByDataSource(sourceId: String): Int

    @Query("DELETE FROM food_items WHERE dataSourceId = :sourceId")
    suspend fun deleteByDataSource(sourceId: String)

    @Query("DELETE FROM food_items")
    suspend fun clearAll()

    @Query("SELECT * FROM food_items WHERE ean = :ean LIMIT 1")
    suspend fun getFoodByEan(ean: String): FoodItemEntity?

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getFoodById(id: Long): FoodItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodItemEntity): Long

    @Update
    suspend fun update(food: FoodItemEntity)

    @Query("DELETE FROM food_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}

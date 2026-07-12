package com.macrotrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun count(): Int

    /**
     * Removes every pre-built food row. Room's @Fts4 external-content triggers
     * keep `food_items_fts` in sync, so the search index is cleared too.
     */
    @Query("DELETE FROM food_items")
    suspend fun clearAll()

    @Query("SELECT * FROM food_items WHERE ean = :ean LIMIT 1")
    suspend fun getFoodByEan(ean: String): FoodItemEntity?

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getFoodById(id: Long): FoodItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodItemEntity>)
}

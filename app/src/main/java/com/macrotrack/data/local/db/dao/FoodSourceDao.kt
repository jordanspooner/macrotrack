package com.macrotrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.macrotrack.data.local.db.entity.FoodSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodSourceDao {
    @Query("SELECT * FROM food_sources ORDER BY isUserSource DESC, name ASC")
    fun getAll(): Flow<List<FoodSourceEntity>>

    @Query("SELECT * FROM food_sources WHERE id = :id")
    suspend fun getById(id: String): FoodSourceEntity?

    @Query("SELECT * FROM food_sources WHERE isUserSource = 0 ORDER BY name ASC")
    fun getNonUserSources(): Flow<List<FoodSourceEntity>>

    @Query("SELECT COUNT(*) FROM food_sources WHERE isUserSource = 0")
    suspend fun countNonUserSources(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: FoodSourceEntity)

    @Query("DELETE FROM food_sources WHERE id = :id")
    suspend fun delete(id: String)
}

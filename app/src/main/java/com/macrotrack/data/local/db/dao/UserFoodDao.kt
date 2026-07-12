package com.macrotrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.macrotrack.data.local.db.entity.UserFoodEntity

@Dao
interface UserFoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserFood(userFood: UserFoodEntity): Long

    @Query("SELECT * FROM user_foods WHERE id = :id")
    suspend fun getUserFoodById(id: Long): UserFoodEntity?

    @Query("SELECT * FROM user_foods WHERE ean = :ean LIMIT 1")
    suspend fun getUserFoodByEan(ean: String): UserFoodEntity?
}

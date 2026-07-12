package com.macrotrack.data.repository

import com.macrotrack.domain.model.FoodItem
import kotlinx.coroutines.flow.Flow

interface UserFoodRepository {
    /** Inserts (or replaces) a user-created food and returns it with its new id. */
    suspend fun insert(food: FoodItem): FoodItem

    /** Looks up a user food by EAN/barcode, if present. */
    suspend fun getByEan(ean: String): FoodItem?

    /** Looks up a user food by its row id. */
    suspend fun getById(id: Long): FoodItem?
}

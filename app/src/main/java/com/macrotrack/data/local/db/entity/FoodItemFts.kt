package com.macrotrack.data.local.db.entity

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "food_items_fts")
@Fts4(contentEntity = FoodItemEntity::class)
data class FoodItemFts(
    val name: String,
    val brand: String?
)

package com.macrotrack.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_foods")
data class UserFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ean: String? = null,
    val brand: String? = null,
    val name: String,
    val defaultPortionG: Float? = null,
    val defaultPortionLabel: String? = null,
    val kcalPer100g: Float,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
)

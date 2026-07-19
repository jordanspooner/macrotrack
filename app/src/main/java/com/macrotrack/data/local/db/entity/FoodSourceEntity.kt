package com.macrotrack.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_sources")
data class FoodSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val version: String? = null,
    val publisher: String? = null,
    val itemCount: Int = 0,
    val installedAt: Long,
    val isUserSource: Boolean = false,
)

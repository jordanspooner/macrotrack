package com.macrotrack.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.macrotrack.data.local.db.dao.FoodItemDao
import com.macrotrack.data.local.db.dao.LogEntryDao
import com.macrotrack.data.local.db.dao.SectionDao
import com.macrotrack.data.local.db.dao.UserFoodDao
import com.macrotrack.data.local.db.entity.FoodItemEntity
import com.macrotrack.data.local.db.entity.FoodItemFts
import com.macrotrack.data.local.db.entity.LogEntryEntity
import com.macrotrack.data.local.db.entity.SectionEntity
import com.macrotrack.data.local.db.entity.UserFoodEntity

@Database(
    entities = [
        FoodItemEntity::class,
        FoodItemFts::class,
        LogEntryEntity::class,
        SectionEntity::class,
        UserFoodEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MacroTrackDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun sectionDao(): SectionDao
    abstract fun userFoodDao(): UserFoodDao
}

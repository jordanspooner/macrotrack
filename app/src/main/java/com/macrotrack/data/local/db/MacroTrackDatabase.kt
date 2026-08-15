package com.macrotrack.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.macrotrack.data.local.db.dao.FoodItemDao
import com.macrotrack.data.local.db.dao.FoodSourceDao
import com.macrotrack.data.local.db.dao.LogEntryDao
import com.macrotrack.data.local.db.dao.SectionDao
import com.macrotrack.data.local.db.entity.FoodItemEntity
import com.macrotrack.data.local.db.entity.FoodItemFts
import com.macrotrack.data.local.db.entity.FoodSourceEntity
import com.macrotrack.data.local.db.entity.LogEntryEntity
import com.macrotrack.data.local.db.entity.SectionEntity

@Database(
    entities = [
        FoodItemEntity::class,
        FoodItemFts::class,
        FoodSourceEntity::class,
        LogEntryEntity::class,
        SectionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MacroTrackDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao
    abstract fun foodSourceDao(): FoodSourceDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun sectionDao(): SectionDao
}

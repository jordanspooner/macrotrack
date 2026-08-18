package com.macrotrack.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.macrotrack.data.local.db.dao.FoodItemDao
import com.macrotrack.data.local.db.dao.FoodSourceDao
import com.macrotrack.data.local.db.dao.LogEntryDao
import com.macrotrack.data.local.db.dao.SectionDao
import com.macrotrack.data.local.db.entity.FoodItemEntity
import com.macrotrack.data.local.db.entity.FoodSourceEntity
import com.macrotrack.data.local.db.entity.LogEntryEntity
import com.macrotrack.data.local.db.entity.SectionEntity

@Database(
    entities = [
        FoodItemEntity::class,
        FoodSourceEntity::class,
        LogEntryEntity::class,
        SectionEntity::class
    ],
    // Version 4: the Room-managed FTS4 index (food_items_fts) was replaced by
    // manually managed FTS5 external-content tables (see SearchIndexManager).
    // The database is rebuilt deterministically via fallbackToDestructiveMigration.
    version = 4,
    exportSchema = false
)
abstract class MacroTrackDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao
    abstract fun foodSourceDao(): FoodSourceDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun sectionDao(): SectionDao
}

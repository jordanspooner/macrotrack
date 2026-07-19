package com.macrotrack.di

import android.content.Context
import androidx.room.Room
import com.macrotrack.data.local.db.MacroTrackDatabase
import com.macrotrack.data.local.db.dao.FoodItemDao
import com.macrotrack.data.local.db.dao.LogEntryDao
import com.macrotrack.data.local.db.dao.SectionDao
import com.macrotrack.data.local.db.dao.UserFoodDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMacroTrackDatabase(
        @ApplicationContext context: Context
    ): MacroTrackDatabase {
        return Room.databaseBuilder(
            context,
            MacroTrackDatabase::class.java,
            "macro_track.db"
        )
        // The pre-built food data is loaded once on first launch by
        // FoodDatabaseSeeder (seeds from the bundled databases/food_database.db
        // asset into the Room `food_items` table; FTS4 triggers keep the search
        // index in sync). A plain `createFromAsset` is intentionally avoided so
        // the bundled file does not need to carry Room's schema identity hash.
        // .createFromAsset("databases/food_database.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }

    @Provides
    fun provideFoodItemDao(database: MacroTrackDatabase): FoodItemDao = database.foodItemDao()

    @Provides
    fun provideLogEntryDao(database: MacroTrackDatabase): LogEntryDao = database.logEntryDao()

    @Provides
    fun provideSectionDao(database: MacroTrackDatabase): SectionDao = database.sectionDao()

    @Provides
    fun provideUserFoodDao(database: MacroTrackDatabase): UserFoodDao = database.userFoodDao()
}

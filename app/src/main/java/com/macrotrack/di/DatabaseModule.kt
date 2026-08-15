package com.macrotrack.di

import android.content.Context
import androidx.room.Room
import com.macrotrack.data.local.db.MacroTrackDatabase
import com.macrotrack.data.local.db.dao.FoodItemDao
import com.macrotrack.data.local.db.dao.FoodSourceDao
import com.macrotrack.data.local.db.dao.LogEntryDao
import com.macrotrack.data.local.db.dao.SectionDao
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
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }

    @Provides
    fun provideFoodItemDao(database: MacroTrackDatabase): FoodItemDao = database.foodItemDao()

    @Provides
    fun provideFoodSourceDao(database: MacroTrackDatabase): FoodSourceDao = database.foodSourceDao()

    @Provides
    fun provideLogEntryDao(database: MacroTrackDatabase): LogEntryDao = database.logEntryDao()

    @Provides
    fun provideSectionDao(database: MacroTrackDatabase): SectionDao = database.sectionDao()
}

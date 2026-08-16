package com.macrotrack.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.macrotrack.data.local.db.MacroTrackDatabase
import com.macrotrack.data.local.db.SearchIndexManager
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
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMacroTrackDatabase(
        @ApplicationContext context: Context,
        searchIndexManager: SearchIndexManager
    ): MacroTrackDatabase {
        return Room.databaseBuilder(
            context,
            MacroTrackDatabase::class.java,
            "macro_track.db"
        )
        // Bundled SQLite guarantees FTS5 with the trigram tokenizer on every
        // API level (the framework driver only has trigram from API 33+).
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .addCallback(object : RoomDatabase.Callback() {
            // Room 2.8 calls the SQLiteConnection overloads when a real SQLite
            // driver (BundledSQLiteDriver, framework driver) opens the
            // database; the SupportSQLiteDatabase overloads are the legacy
            // path used by the support-driver wrapper.
            override fun onCreate(db: SQLiteConnection) {
                searchIndexManager.createIndexes(db)
            }

            override fun onOpen(db: SQLiteConnection) {
                searchIndexManager.ensureIndexes(db)
            }

            override fun onCreate(db: SupportSQLiteDatabase) {
                searchIndexManager.createIndexes(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                searchIndexManager.ensureIndexes(db)
            }
        })
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

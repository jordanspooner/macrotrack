package com.macrotrack.data.local.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.macrotrack.data.local.db.dao.FoodItemDao
import com.macrotrack.data.local.db.entity.FoodItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end test of the FTS5 search index against the real Room database with
 * the bundled SQLite driver (the same configuration as DatabaseModule).
 * Verifies the DAO queries, BM25 ordering, trigram fuzzy matching, trigger
 * synchronization and the candidate window.
 */
@RunWith(AndroidJUnit4::class)
class FoodSearchIndexInstrumentedTest {

    private lateinit var database: com.macrotrack.data.local.db.MacroTrackDatabase
    private lateinit var dao: FoodItemDao
    private lateinit var indexManager: SearchIndexManager
    private lateinit var dbPath: java.io.File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dbPath = context.getDatabasePath("macrotrack_search_test.db")
        dbPath.delete()
        File(dbPath.parentFile, dbPath.name + "-wal").delete()
        File(dbPath.parentFile, dbPath.name + "-shm").delete()

        indexManager = SearchIndexManager()
        database = Room.databaseBuilder(context, com.macrotrack.data.local.db.MacroTrackDatabase::class.java, dbPath.name)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(object : RoomDatabase.Callback() {
                // With BundledSQLiteDriver Room 2.8 invokes the
                // SQLiteConnection callback overloads; without them the
                // SupportSQLiteDatabase overloads are dead code and the FTS
                // indexes are never created.
                override fun onCreate(db: SQLiteConnection) = indexManager.createIndexes(db)
                override fun onOpen(db: SQLiteConnection) = indexManager.ensureIndexes(db)
                override fun onCreate(db: SupportSQLiteDatabase) = indexManager.createIndexes(db)
                override fun onOpen(db: SupportSQLiteDatabase) = indexManager.ensureIndexes(db)
            })
            .build()
        dao = database.foodItemDao()
    }

    @After
    fun tearDown() {
        database.close()
        dbPath.delete()
        File(dbPath.parentFile, dbPath.name + "-wal").delete()
        File(dbPath.parentFile, dbPath.name + "-shm").delete()
    }

    private fun food(name: String, brand: String? = null) = FoodItemEntity(
        source = "OPEN_FOOD_FACTS",
        dataSourceId = "test-source",
        brand = brand,
        name = name,
        kcalPer100g = 100f,
        proteinPer100g = 10f,
        carbsPer100g = 10f,
        fatPer100g = 5f,
    )

    @Test
    fun freshDatabaseCreatesBothIndexes() = runBlocking {
        assertTrue(dao.searchFoods("chicken").first().isEmpty())
        assertTrue(dao.searchFoodsFuzzy("chicken").first().isEmpty())
        assertTrue(indexManager.isTrigramIndexActive)
    }

    /**
     * Regression test for the InvalidationTracker crash: the FTS5 tables are
     * manually managed and are not Room schema tables, so @Query Flow methods
     * observing them used to throw "There is no table with name
     * food_items_fts" when the Flow started. The @RawQuery search methods,
     * observed on the food_items entity only, must start and return results.
     */
    @Test
    fun rawQuerySearchStartsAndReturnsResultsWithoutInvalidationException() = runBlocking {
        val catalogId = dao.insert(food("Chicken breast", "Tesco"))
        val userId = dao.insert(food("Chicken breast").copy(source = "USER"))

        val fts = dao.searchFoodsRaw(
            SimpleSQLiteQuery(FoodItemDao.SEARCH_FTS_SQL, arrayOf("chicken", "chicken"))
        ).first()
        assertTrue(fts.any { it.id == catalogId })
        assertTrue(fts.any { it.id == userId })

        val fuzzy = dao.searchFoodsFuzzyRaw(
            SimpleSQLiteQuery(FoodItemDao.SEARCH_TRIGRAM_SQL, arrayOf("\"chicken\"", "\"chicken\""))
        ).first()
        assertTrue(fuzzy.any { it.id == catalogId })
        assertTrue(fuzzy.any { it.id == userId })

        val userFts = dao.searchUserFoodsRaw(
            SimpleSQLiteQuery(FoodItemDao.SEARCH_USER_FTS_SQL, arrayOf("chicken", "chicken"))
        ).first()
        assertTrue(userFts.map { it.id } == listOf(userId))

        val userFuzzy = dao.searchUserFoodsFuzzyRaw(
            SimpleSQLiteQuery(FoodItemDao.SEARCH_USER_TRIGRAM_SQL, arrayOf("\"chicken\"", "\"chicken\""))
        ).first()
        assertTrue(userFuzzy.map { it.id } == listOf(userId))
    }

    @Test
    fun bm25OrdersNameMatchesAboveBrandOnlyMatches() = runBlocking {
        val nameMatch = dao.insert(food("Dairy Milk", "Cadbury"))
        val brandMatch = dao.insert(food("Crunchy Bar", "Dairy Queen"))
        assertEquals(listOf(nameMatch, brandMatch), dao.searchFoods("dairy").first().map { it.id })
        assertEquals(listOf(nameMatch, brandMatch), dao.searchFoodsFuzzy("dairy").first().map { it.id })
    }

    @Test
    fun fuzzySearchMatchesTypos() = runBlocking {
        val id = dao.insert(food("Chicken breast", "Tesco"))
        val results = dao.searchFoodsFuzzy("chickn").first()
        assertTrue(results.any { it.id == id })
    }

    @Test
    fun prefixAndUnicodeQueriesWork() = runBlocking {
        val id = dao.insert(food("Café au Lait"))
        assertTrue(dao.searchFoods("\"café\"*").first().any { it.id == id })
        assertTrue(dao.searchFoods("cafe").first().any { it.id == id })
    }

    @Test
    fun updatesResyncAndDeletesRemoveFromIndex() = runBlocking {
        val id = dao.insert(food("Chicken breast"))
        dao.update(food("Turkey thigh").copy(id = id))
        assertFalse(dao.searchFoods("chicken").first().any { it.id == id })
        assertTrue(dao.searchFoods("turkey").first().any { it.id == id })
        assertTrue(dao.searchFoodsFuzzy("turk").first().any { it.id == id })

        dao.deleteById(id)
        assertFalse(dao.searchFoods("turkey").first().any { it.id == id })
        assertFalse(dao.searchFoodsFuzzy("turk").first().any { it.id == id })
    }

    @Test
    fun bulkInstallIsIndexedAndWindowIsCapped() = runBlocking {
        val foods = (0 until 201).map { food("Bulk Food $it") }
        dao.insertAll(foods)
        assertEquals(200, dao.searchFoods("bulk").first().size)
        assertEquals(200, dao.searchFoodsFuzzy("bulk").first().size)
    }

    @Test
    fun sourceReplacementResyncsIndex() = runBlocking {
        val old = dao.insert(food("Chicken breast"))
        dao.deleteByDataSource("test-source")
        assertTrue(dao.searchFoods("chicken").first().isEmpty())
        val fresh = dao.insert(food("Chicken breast"))
        assertTrue(dao.searchFoods("chicken").first().any { it.id == fresh })
        assertFalse(dao.searchFoods("chicken").first().any { it.id == old })
    }

    @Test
    fun rebuildRepairsDroppedIndex() = runBlocking {
        val id = dao.insert(food("Chicken breast"))
        val supportDb = database.openHelper.writableDatabase
        supportDb.execSQL("DROP TABLE ${SearchIndexManager.FTS_TABLE}")
        supportDb.execSQL("DROP TABLE ${SearchIndexManager.TRIGRAM_TABLE}")
        indexManager.createIndexes(supportDb)
        assertTrue(dao.searchFoods("chicken").first().isEmpty())
        indexManager.rebuild(supportDb)
        assertTrue(dao.searchFoods("chicken").first().any { it.id == id })
        assertTrue(dao.searchFoodsFuzzy("chicken").first().any { it.id == id })
    }
}
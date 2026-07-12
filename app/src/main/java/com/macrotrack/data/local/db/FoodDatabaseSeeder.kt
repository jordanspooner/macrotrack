package com.macrotrack.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.macrotrack.data.local.db.dao.FoodItemDao
import com.macrotrack.data.local.db.entity.FoodItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the Room `food_items` table from the pre-built read-only database bundled
 * as an asset (`databases/food_database.db`).
 *
 * The bundled DB is a plain SQLite file (no Room schema) produced by the
 * `food-db-builder` module. On first launch (when `food_items` is empty) we copy
 * it out of the APK and bulk-insert its rows; Room's FTS4 content-sync triggers
 * then populate the search index automatically.
 */
@Singleton
class FoodDatabaseSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foodItemDao: FoodItemDao
) {
    private val assetPath = "databases/food_database.db"

    suspend fun seedIfEmpty() {
        if (foodItemDao.count() > 0) return
        withContext(Dispatchers.IO) {
            runCatching { doSeed() }
        }
    }

    /**
     * Wipes the current `food_items` table and re-imports from the bundled asset.
     *
     * Useful while iterating on the data (e.g. after dropping fresh USDA / OFF
     * CSVs into `food-db-builder/data/` and rebuilding the asset) without having
     * to clear app storage. Returns the number of foods re-inserted.
     */
    suspend fun reseed(): Int {
        foodItemDao.clearAll()
        var inserted = 0
        withContext(Dispatchers.IO) {
            runCatching { inserted = doSeed() }
        }
        return inserted
    }

    private suspend fun doSeed(): Int {
        val assetFile = copyAssetToCache() ?: return 0
        try {
            val db = SQLiteDatabase.openDatabase(
                assetFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            try {
                if (!db.tableExists("food_items")) return 0
                val foods = mutableListOf<FoodItemEntity>()
                db.query("food_items", null, null, null, null, null, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        foods.add(cursor.toFoodItemEntity())
                    }
                }
                if (foods.isNotEmpty()) {
                    foodItemDao.insertAll(foods)
                    println("FoodDatabaseSeeder: seeded ${foods.size} foods.")
                    return foods.size
                }
            } finally {
                db.close()
            }
        } finally {
            assetFile.delete()
        }
        return 0
    }

    private fun copyAssetToCache(): File? {
        return try {
            context.assets.open(assetPath).use { input ->
                val out = File(context.cacheDir, "food_database_seed.db")
                FileOutputStream(out).use { output -> input.copyTo(output) }
                out
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun SQLiteDatabase.tableExists(name: String): Boolean {
        rawQuery(
            "SELECT name FROM sqlite_master WHERE type IN ('table','view') AND name = ?",
            arrayOf(name)
        ).use { c -> return c.moveToFirst() }
    }

    private fun android.database.Cursor.toFoodItemEntity(): FoodItemEntity {
        fun string(col: String): String? {
            val i = getColumnIndex(col)
            return if (i < 0 || isNull(i)) null else getString(i)
        }
        fun float(col: String): Float {
            val i = getColumnIndex(col)
            return if (i < 0 || isNull(i)) 0f else getFloat(i)
        }
        fun floatOrNull(col: String): Float? {
            val i = getColumnIndex(col)
            return if (i < 0 || isNull(i)) null else getFloat(i)
        }
        return FoodItemEntity(
            id = 0, // let Room auto-generate; FTS trigger keeps docid in sync
            source = string("source") ?: "UNKNOWN",
            sourceId = string("sourceId"),
            ean = string("ean"),
            brand = string("brand"),
            name = string("name") ?: "",
            defaultPortionG = floatOrNull("defaultPortionG"),
            defaultPortionLabel = string("defaultPortionLabel"),
            kcalPer100g = float("kcalPer100g"),
            proteinPer100g = float("proteinPer100g"),
            carbsPer100g = float("carbsPer100g"),
            fatPer100g = float("fatPer100g")
        )
    }
}

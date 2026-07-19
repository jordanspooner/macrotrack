package com.macrotrack.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.macrotrack.data.local.db.dao.FoodItemDao
import com.macrotrack.data.local.db.entity.FoodItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodSourceInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foodItemDao: FoodItemDao
) {
    suspend fun install(dbFile: File, dataSourceId: String, sourceType: String): Int = withContext(Dispatchers.IO) {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            if (!tableExists(db, "food_items")) return@withContext 0
            val foods = mutableListOf<FoodItemEntity>()
            db.query("food_items", null, null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    foods.add(cursor.toFoodItemEntity(dataSourceId, sourceType))
                }
            }
            if (foods.isEmpty()) return@withContext 0
            foodItemDao.insertAll(foods)
            foods.size
        } finally {
            db?.close()
            if (dbFile.exists()) dbFile.delete()
        }
    }

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean {
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type IN ('table','view') AND name = ?",
            arrayOf(name)
        ).use { c -> return c.moveToFirst() }
    }

    private fun android.database.Cursor.toFoodItemEntity(dataSourceId: String, sourceType: String): FoodItemEntity {
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
            id = 0,
            source = sourceType,
            sourceId = string("sourceId"),
            dataSourceId = dataSourceId,
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

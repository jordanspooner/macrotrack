package com.macrotrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.macrotrack.data.local.db.entity.LogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries WHERE date = :date ORDER BY sortOrder ASC")
    fun getLogEntriesByDate(date: String): Flow<List<LogEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogEntry(entry: LogEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<LogEntryEntity>)

    @Update
    suspend fun updateLogEntry(entry: LogEntryEntity)

    @Update
    suspend fun updateAll(entries: List<LogEntryEntity>)

    @Delete
    suspend fun deleteLogEntries(entries: List<LogEntryEntity>)

    @Query("SELECT foodItemId FROM log_entries WHERE sectionId = :sectionId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentFoodIds(sectionId: Long, limit: Int): List<Long>

    @Query("SELECT foodItemId FROM log_entries WHERE sectionId = :sectionId GROUP BY foodItemId ORDER BY COUNT(*) DESC LIMIT :limit")
    suspend fun getFrequentFoodIds(sectionId: Long, limit: Int): List<Long>

    @Query("SELECT foodItemId FROM log_entries WHERE foodItemId IS NOT NULL ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentFoodIdsOverall(limit: Int): List<Long>

    @Query("SELECT foodItemId FROM log_entries WHERE foodItemId IS NOT NULL GROUP BY foodItemId ORDER BY COUNT(*) DESC LIMIT :limit")
    suspend fun getFrequentFoodIdsOverall(limit: Int): List<Long>

    @Query("SELECT DISTINCT foodItemId FROM log_entries WHERE foodItemId IS NOT NULL")
    fun getLoggedFoodIds(): Flow<List<Long>>

    @Query("SELECT date, SUM(kcal) AS kcal, SUM(protein) AS protein, SUM(carbs) AS carbs, SUM(fat) AS fat FROM log_entries WHERE date BETWEEN :from AND :to GROUP BY date")
    fun getMacrosByDateRange(from: String, to: String): Flow<List<DailyMacroRow>>
}

data class DailyMacroRow(
    val date: String,
    val kcal: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
)

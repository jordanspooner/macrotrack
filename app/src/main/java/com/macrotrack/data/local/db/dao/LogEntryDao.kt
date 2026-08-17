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
    @Query("SELECT * FROM log_entries WHERE date = :date ORDER BY sortOrder ASC, id ASC")
    fun getLogEntriesByDate(date: String): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE date = :date ORDER BY sortOrder ASC, id ASC")
    suspend fun getLogEntriesByDateOnce(date: String): List<LogEntryEntity>

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

    /**
     * Per-food usage statistics: overall count / most-recent [createdAt] plus
     * the same figures scoped to [sectionId]. Only rows with a [foodItemId]
     * that is among [candidateIds] are considered, so the aggregation is
     * bounded to the current search candidates instead of the whole log.
     * Read-only aggregation — no schema changes.
     */
    @Query(
        """
        SELECT foodItemId,
               COUNT(*) AS overallCount,
               MAX(createdAt) AS overallRecentCreatedAt,
               COALESCE(SUM(CASE WHEN sectionId = :sectionId THEN 1 ELSE 0 END), 0) AS sectionCount,
               MAX(CASE WHEN sectionId = :sectionId THEN createdAt END) AS sectionRecentCreatedAt
        FROM log_entries
        WHERE foodItemId IS NOT NULL AND foodItemId IN (:candidateIds)
        GROUP BY foodItemId
        """
    )
    fun getFoodUsageStats(sectionId: Long, candidateIds: List<Long>): Flow<List<FoodUsageStatsRow>>

    @Query("SELECT date, SUM(kcal) AS kcal, SUM(protein) AS protein, SUM(carbs) AS carbs, SUM(fat) AS fat FROM log_entries WHERE date BETWEEN :from AND :to GROUP BY date")
    fun getMacrosByDateRange(from: String, to: String): Flow<List<DailyMacroRow>>
}

data class FoodUsageStatsRow(
    val foodItemId: Long,
    val overallCount: Int,
    val overallRecentCreatedAt: Long?,
    val sectionCount: Int,
    val sectionRecentCreatedAt: Long?,
)

data class DailyMacroRow(
    val date: String,
    val kcal: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
)

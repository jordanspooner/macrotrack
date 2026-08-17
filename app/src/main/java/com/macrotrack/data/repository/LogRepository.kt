package com.macrotrack.data.repository

import com.macrotrack.data.local.db.dao.DailyMacroRow
import com.macrotrack.domain.model.FoodUsageStats
import com.macrotrack.domain.model.LogEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface LogRepository {
    fun getLogEntriesByDate(date: LocalDate): Flow<List<LogEntry>>
    suspend fun getLogEntriesByDateOnce(date: LocalDate): List<LogEntry>
    fun getMacrosByDateRange(from: LocalDate, to: LocalDate): Flow<List<DailyMacroRow>>
    suspend fun insert(entry: LogEntry): Long
    suspend fun insertAll(entries: List<LogEntry>)
    suspend fun update(entry: LogEntry)
    suspend fun updateAll(entries: List<LogEntry>)
    suspend fun delete(entries: List<LogEntry>)

    /**
     * Inserts [entries] in a single transaction, assigning each a fresh
     * append sort order after the entries already stored for its
     * (date, sectionId) group. All [entries] must share the same date.
     */
    suspend fun insertAllAtEnd(entries: List<LogEntry>)

    /**
     * Updates [entries] in a single transaction, assigning each a fresh
     * append sort order after the entries already stored for its
     * (date, sectionId) group. All [entries] must share the same date.
     */
    suspend fun updateAllAtEnd(entries: List<LogEntry>)
    suspend fun getRecentFoodIds(sectionId: Long, limit: Int): List<Long>
    suspend fun getFrequentFoodIds(sectionId: Long, limit: Int): List<Long>
    suspend fun getRecentFoodIdsOverall(limit: Int): List<Long>
    suspend fun getFrequentFoodIdsOverall(limit: Int): List<Long>
    fun getLoggedFoodIds(): Flow<List<Long>>

    /**
     * Per-food usage statistics scoped to the given [candidateIds] (typically
     * the current search candidates), relative to [sectionId]. Stays a [Flow]
     * so log changes re-rank the current query.
     */
    fun getFoodUsageStats(sectionId: Long, candidateIds: List<Long>): Flow<List<FoodUsageStats>>
}

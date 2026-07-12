package com.macrotrack.data.repository

import com.macrotrack.domain.model.LogEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface LogRepository {
    fun getLogEntriesByDate(date: LocalDate): Flow<List<LogEntry>>
    suspend fun insert(entry: LogEntry): Long
    suspend fun insertAll(entries: List<LogEntry>)
    suspend fun update(entry: LogEntry)
    suspend fun updateAll(entries: List<LogEntry>)
    suspend fun delete(entries: List<LogEntry>)
    suspend fun getRecentFoodIds(sectionId: Long, limit: Int): List<Long>
    suspend fun getFrequentFoodIds(sectionId: Long, limit: Int): List<Long>
    suspend fun getRecentFoodIdsOverall(limit: Int): List<Long>
    suspend fun getFrequentFoodIdsOverall(limit: Int): List<Long>
    fun getLoggedFoodIds(): Flow<List<Long>>
}

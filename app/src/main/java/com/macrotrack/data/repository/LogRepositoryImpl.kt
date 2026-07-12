package com.macrotrack.data.repository

import com.macrotrack.data.local.db.dao.LogEntryDao
import com.macrotrack.data.mapper.toDomain
import com.macrotrack.data.mapper.toEntity
import com.macrotrack.domain.model.LogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class LogRepositoryImpl @Inject constructor(
    private val logEntryDao: LogEntryDao
) : LogRepository {
    override fun getLogEntriesByDate(date: LocalDate): Flow<List<LogEntry>> {
        return logEntryDao.getLogEntriesByDate(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun insert(entry: LogEntry): Long {
        return logEntryDao.insertLogEntry(entry.toEntity())
    }

    override suspend fun insertAll(entries: List<LogEntry>) {
        logEntryDao.insertAll(entries.map { it.toEntity() })
    }

    override suspend fun update(entry: LogEntry) {
        logEntryDao.updateLogEntry(entry.toEntity())
    }

    override suspend fun updateAll(entries: List<LogEntry>) {
        logEntryDao.updateAll(entries.map { it.toEntity() })
    }

    override suspend fun delete(entries: List<LogEntry>) {
        logEntryDao.deleteLogEntries(entries.map { it.toEntity() })
    }

    override suspend fun getRecentFoodIds(sectionId: Long, limit: Int): List<Long> {
        return logEntryDao.getRecentFoodIds(sectionId, limit)
    }

    override suspend fun getFrequentFoodIds(sectionId: Long, limit: Int): List<Long> {
        return logEntryDao.getFrequentFoodIds(sectionId, limit)
    }

    override suspend fun getRecentFoodIdsOverall(limit: Int): List<Long> {
        return logEntryDao.getRecentFoodIdsOverall(limit)
    }

    override suspend fun getFrequentFoodIdsOverall(limit: Int): List<Long> {
        return logEntryDao.getFrequentFoodIdsOverall(limit)
    }

    override fun getLoggedFoodIds(): Flow<List<Long>> {
        return logEntryDao.getLoggedFoodIds()
    }
}

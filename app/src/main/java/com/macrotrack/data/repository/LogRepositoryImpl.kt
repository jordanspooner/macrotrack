package com.macrotrack.data.repository

import androidx.room.Transactor
import androidx.room.useWriterConnection
import com.macrotrack.data.local.db.MacroTrackDatabase
import com.macrotrack.data.local.db.dao.DailyMacroRow
import com.macrotrack.data.local.db.dao.LogEntryDao
import com.macrotrack.data.mapper.toDomain
import com.macrotrack.data.mapper.toEntity
import com.macrotrack.domain.model.FoodUsageStats
import com.macrotrack.domain.model.LogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class LogRepositoryImpl @Inject constructor(
    private val macroTrackDatabase: MacroTrackDatabase,
    private val logEntryDao: LogEntryDao
) : LogRepository {
    override fun getLogEntriesByDate(date: LocalDate): Flow<List<LogEntry>> {
        return logEntryDao.getLogEntriesByDate(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getLogEntriesByDateOnce(date: LocalDate): List<LogEntry> {
        return logEntryDao.getLogEntriesByDateOnce(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .map { it.toDomain() }
    }

    override fun getMacrosByDateRange(from: LocalDate, to: LocalDate): Flow<List<DailyMacroRow>> {
        return logEntryDao.getMacrosByDateRange(
            from = from.format(DateTimeFormatter.ISO_LOCAL_DATE),
            to = to.format(DateTimeFormatter.ISO_LOCAL_DATE)
        )
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

    override suspend fun insertAllAtEnd(entries: List<LogEntry>) {
        if (entries.isEmpty()) return
        macroTrackDatabase.useWriterConnection { connection ->
            connection.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                insertAllAtEndInTransaction(entries)
            }
        }
    }

    override suspend fun updateAllAtEnd(entries: List<LogEntry>) {
        if (entries.isEmpty()) return
        macroTrackDatabase.useWriterConnection { connection ->
            connection.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                updateAllAtEndInTransaction(entries)
            }
        }
    }

    /** Transaction body for [insertAllAtEnd]; internal for unit testing without Room. */
    internal suspend fun insertAllAtEndInTransaction(entries: List<LogEntry>) {
        val existing = logEntryDao.getLogEntriesByDateOnce(entries.first().date.toIsoDate())
        val appended = LogEntryOrdering.appendSortOrders(entries.map { it.toEntity() }, existing)
        logEntryDao.insertAll(appended)
    }

    /** Transaction body for [updateAllAtEnd]; internal for unit testing without Room. */
    internal suspend fun updateAllAtEndInTransaction(entries: List<LogEntry>) {
        val existing = logEntryDao.getLogEntriesByDateOnce(entries.first().date.toIsoDate())
        val appended = LogEntryOrdering.appendSortOrders(entries.map { it.toEntity() }, existing)
        logEntryDao.updateAll(appended)
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

    override fun getFoodUsageStats(sectionId: Long, candidateIds: List<Long>): Flow<List<FoodUsageStats>> {
        return logEntryDao.getFoodUsageStats(sectionId, candidateIds)
            .map { rows -> rows.map { it.toDomain() } }
    }
}

private fun LocalDate.toIsoDate(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)

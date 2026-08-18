package com.macrotrack.data.repository

import com.macrotrack.data.local.db.MacroTrackDatabase
import com.macrotrack.data.local.db.dao.LogEntryDao
import com.macrotrack.data.local.db.entity.LogEntryEntity
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Macros
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LogRepositoryImplTest {

    private val tuesday = LocalDate.of(2026, 8, 11)

    private fun domainEntry(date: LocalDate, sectionId: Long, sortOrder: Int, id: Long = 0) = LogEntry(
        id = id,
        date = date,
        sectionId = sectionId,
        foodItemId = 7L,
        name = "Oatmeal",
        brand = "Quaker",
        portionG = 200f,
        portionLabel = "2 servings",
        macros = Macros(300f, 12f, 40f, 6f),
        sortOrder = sortOrder,
        createdAt = Instant.ofEpochMilli(1000),
    )

    private fun entity(id: Long, date: String, sectionId: Long, sortOrder: Int) = LogEntryEntity(
        id = id,
        date = date,
        sectionId = sectionId,
        foodItemId = 7L,
        name = "Oatmeal",
        brand = "Quaker",
        portionG = 200f,
        portionLabel = "2 servings",
        kcal = 300f,
        protein = 12f,
        carbs = 40f,
        fat = 6f,
        sortOrder = sortOrder,
        createdAt = 1000L,
    )

    @Test
    fun `insertAllAtEndInTransaction reads target date once and appends after existing max per section`() = runTest {
        val dao = mockk<LogEntryDao>(relaxed = true)
        coEvery { dao.getLogEntriesByDateOnce("2026-08-11") } returns listOf(
            entity(1, "2026-08-11", sectionId = 1, sortOrder = 0),
            entity(2, "2026-08-11", sectionId = 2, sortOrder = 4),
        )
        val captured = slot<List<LogEntryEntity>>()
        coEvery { dao.insertAll(capture(captured)) } returns Unit
        val repo = LogRepositoryImpl(mockk<MacroTrackDatabase>(relaxed = true), dao)

        repo.insertAllAtEndInTransaction(
            listOf(
                domainEntry(tuesday, sectionId = 1, sortOrder = 99),
                domainEntry(tuesday, sectionId = 2, sortOrder = 98),
                domainEntry(tuesday, sectionId = 1, sortOrder = 97),
            )
        )

        coVerify(exactly = 1) { dao.getLogEntriesByDateOnce("2026-08-11") }
        coVerify(exactly = 1) { dao.insertAll(any()) }
        assertEquals(listOf(1, 5, 2), captured.captured.map { it.sortOrder })
        assertEquals(listOf("2026-08-11"), captured.captured.map { it.date }.distinct())
    }

    @Test
    fun `insertAllAtEndInTransaction keeps entry identity when mapping to entities`() = runTest {
        val dao = mockk<LogEntryDao>(relaxed = true)
        coEvery { dao.getLogEntriesByDateOnce(any()) } returns emptyList()
        val captured = slot<List<LogEntryEntity>>()
        coEvery { dao.insertAll(capture(captured)) } returns Unit
        val repo = LogRepositoryImpl(mockk<MacroTrackDatabase>(relaxed = true), dao)

        repo.insertAllAtEndInTransaction(listOf(domainEntry(tuesday, sectionId = 1, sortOrder = 3)))

        val inserted = captured.captured.single()
        assertEquals(0L, inserted.id)
        assertEquals(1L, inserted.sectionId)
        assertEquals(7L, inserted.foodItemId)
        assertEquals("Oatmeal", inserted.name)
        assertEquals("Quaker", inserted.brand)
        assertEquals(200f, inserted.portionG, 0f)
        assertEquals("2 servings", inserted.portionLabel)
        assertEquals(300f, inserted.kcal, 0f)
        assertEquals(12f, inserted.protein, 0f)
        assertEquals(40f, inserted.carbs, 0f)
        assertEquals(6f, inserted.fat, 0f)
        assertEquals(1000L, inserted.createdAt)
        assertEquals(0, inserted.sortOrder)
    }

    @Test
    fun `updateAllAtEndInTransaction reads target date once and appends after existing max`() = runTest {
        val dao = mockk<LogEntryDao>(relaxed = true)
        coEvery { dao.getLogEntriesByDateOnce("2026-08-11") } returns listOf(
            entity(1, "2026-08-11", sectionId = 1, sortOrder = 5),
        )
        val captured = slot<List<LogEntryEntity>>()
        coEvery { dao.updateAll(capture(captured)) } returns Unit
        val repo = LogRepositoryImpl(mockk<MacroTrackDatabase>(relaxed = true), dao)

        repo.updateAllAtEndInTransaction(
            listOf(domainEntry(tuesday, sectionId = 1, sortOrder = 9, id = 10))
        )

        coVerify(exactly = 1) { dao.getLogEntriesByDateOnce("2026-08-11") }
        coVerify(exactly = 1) { dao.updateAll(any()) }
        val updated = captured.captured.single()
        assertEquals(6, updated.sortOrder)
        assertEquals(10L, updated.id)
        assertEquals(1000L, updated.createdAt)
    }

    @Test
    fun `updateAllAtEndInTransaction appends per section with unique sort orders`() = runTest {
        val dao = mockk<LogEntryDao>(relaxed = true)
        coEvery { dao.getLogEntriesByDateOnce(any()) } returns emptyList()
        val captured = slot<List<LogEntryEntity>>()
        coEvery { dao.updateAll(capture(captured)) } returns Unit
        val repo = LogRepositoryImpl(mockk<MacroTrackDatabase>(relaxed = true), dao)

        repo.updateAllAtEndInTransaction(
            listOf(
                domainEntry(tuesday, sectionId = 1, sortOrder = 0),
                domainEntry(tuesday, sectionId = 2, sortOrder = 1),
                domainEntry(tuesday, sectionId = 1, sortOrder = 2),
            )
        )

        assertEquals(listOf(0, 0, 1), captured.captured.map { it.sortOrder })
        assertEquals(listOf(1L, 2L, 1L), captured.captured.map { it.sectionId })
    }

    @Test
    fun `public insertAllAtEnd with empty input makes no database calls`() = runTest {
        val dao = mockk<LogEntryDao>(relaxed = true)
        val repo = LogRepositoryImpl(mockk<MacroTrackDatabase>(relaxed = true), dao)

        repo.insertAllAtEnd(emptyList())
        repo.updateAllAtEnd(emptyList())

        coVerify(exactly = 0) { dao.getLogEntriesByDateOnce(any()) }
        coVerify(exactly = 0) { dao.insertAll(any()) }
        coVerify(exactly = 0) { dao.updateAll(any()) }
    }
}
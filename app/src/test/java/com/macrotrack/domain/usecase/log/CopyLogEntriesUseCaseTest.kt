package com.macrotrack.domain.usecase.log

import com.macrotrack.data.repository.LogRepository
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Macros
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CopyLogEntriesUseCaseTest {

    private val logRepository = mockk<LogRepository>(relaxed = true)
    private val useCase = CopyLogEntriesUseCase(logRepository)

    private val monday = LocalDate.of(2026, 8, 10)
    private val tuesday = LocalDate.of(2026, 8, 11)

    private fun entry(
        id: Long,
        date: LocalDate,
        sectionId: Long,
        sortOrder: Int = 0,
        createdAt: Instant = Instant.ofEpochMilli(1000)
    ) = LogEntry(
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
        createdAt = createdAt,
    )

    private fun expectInsertAllAtEnd(): CapturingSlot<List<LogEntry>> {
        val captured = slot<List<LogEntry>>()
        coEvery { logRepository.insertAllAtEnd(capture(captured)) } returns Unit
        return captured
    }

    @Test
    fun `copy resets id changes date and preserves source section when targetSectionId is null`() = runTest {
        val before = Instant.now()
        val source = entry(10, monday, sectionId = 2, sortOrder = 3)
        val captured = expectInsertAllAtEnd()

        useCase(listOf(source), tuesday)

        val copied = captured.captured.single()
        assertEquals(0L, copied.id)
        assertEquals(tuesday, copied.date)
        assertEquals(2L, copied.sectionId)
        assertTrue("copy createdAt should be now", copied.createdAt.isAfter(before))
        assertTrue("copy createdAt should be now", copied.createdAt <= Instant.now())
    }

    @Test
    fun `copy preserves macros portion and food identity`() = runTest {
        val source = entry(10, monday, sectionId = 2, sortOrder = 3)
        val captured = expectInsertAllAtEnd()

        useCase(listOf(source), tuesday)

        val copied = captured.captured.single()
        assertEquals(7L, copied.foodItemId)
        assertEquals("Oatmeal", copied.name)
        assertEquals("Quaker", copied.brand)
        assertEquals(200f, copied.portionG, 0f)
        assertEquals("2 servings", copied.portionLabel)
        assertEquals(Macros(300f, 12f, 40f, 6f), copied.macros)
    }

    @Test
    fun `targetSectionId overrides the source section`() = runTest {
        val source = entry(10, monday, sectionId = 2, sortOrder = 3)
        val captured = expectInsertAllAtEnd()

        useCase(listOf(source), tuesday, targetSectionId = 5)

        assertEquals(5L, captured.captured.single().sectionId)
    }

    @Test
    fun `duplicating to the same date resets id and keeps date and section`() = runTest {
        val source = entry(10, monday, sectionId = 1, sortOrder = 5)
        val captured = expectInsertAllAtEnd()

        useCase(listOf(source), monday)

        val copied = captured.captured.single()
        assertEquals(0L, copied.id)
        assertEquals(monday, copied.date)
        assertEquals(1L, copied.sectionId)
        assertEquals(5, copied.sortOrder)
    }

    @Test
    fun `incoming entries keep source visual order and are passed in one batch`() = runTest {
        val captured = expectInsertAllAtEnd()
        val incoming = listOf(
            entry(10, monday, sectionId = 1, sortOrder = 0),
            entry(11, monday, sectionId = 2, sortOrder = 1),
            entry(12, monday, sectionId = 1, sortOrder = 2),
        )

        useCase(incoming, tuesday)

        val copied = captured.captured
        assertEquals(listOf(0L, 0L, 0L), copied.map { it.id })
        assertEquals(listOf(0, 1, 2), copied.map { it.sortOrder })
        assertEquals(listOf(1L, 2L, 1L), copied.map { it.sectionId })
    }

    @Test
    fun `empty entries perform no repository writes`() = runTest {
        useCase(emptyList(), tuesday)

        coVerify(exactly = 0) { logRepository.insertAllAtEnd(any()) }
    }

    @Test
    fun `batch is inserted at end in a single call`() = runTest {
        val captured = expectInsertAllAtEnd()

        useCase(listOf(entry(10, monday, sectionId = 1)), tuesday)

        coVerify(exactly = 1) { logRepository.insertAllAtEnd(any()) }
        assertEquals(tuesday, captured.captured.single().date)
    }
}
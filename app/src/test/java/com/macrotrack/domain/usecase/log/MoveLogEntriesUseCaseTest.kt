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
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MoveLogEntriesUseCaseTest {

    private val logRepository = mockk<LogRepository>(relaxed = true)
    private val useCase = MoveLogEntriesUseCase(logRepository)

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

    private fun expectUpdateAllAtEnd(): CapturingSlot<List<LogEntry>> {
        val captured = slot<List<LogEntry>>()
        coEvery { logRepository.updateAllAtEnd(capture(captured)) } returns Unit
        return captured
    }

    @Test
    fun `move preserves id and createdAt and changes date and section`() = runTest {
        val createdAt = Instant.ofEpochMilli(123456)
        val source = entry(10, monday, sectionId = 2, sortOrder = 3, createdAt = createdAt)
        val captured = expectUpdateAllAtEnd()

        useCase(listOf(source), tuesday, targetSectionId = 4)

        val moved = captured.captured.single()
        assertEquals(10L, moved.id)
        assertEquals(createdAt, moved.createdAt)
        assertEquals(tuesday, moved.date)
        assertEquals(4L, moved.sectionId)
        assertEquals(7L, moved.foodItemId)
        assertEquals(200f, moved.portionG, 0f)
        assertEquals(Macros(300f, 12f, 40f, 6f), moved.macros)
    }

    @Test
    fun `move preserves source section when targetSectionId is null`() = runTest {
        val source = entry(10, monday, sectionId = 2, sortOrder = 3)
        val captured = expectUpdateAllAtEnd()

        useCase(listOf(source), tuesday)

        assertEquals(2L, captured.captured.single().sectionId)
    }

    @Test
    fun `moving within the same date and section is a no-op`() = runTest {
        val source = entry(10, monday, sectionId = 2, sortOrder = 3)

        useCase(listOf(source), monday)

        coVerify(exactly = 0) { logRepository.updateAllAtEnd(any()) }
    }

    @Test
    fun `moving to the same section on the same date via explicit targetSectionId is a no-op`() = runTest {
        val source = entry(10, monday, sectionId = 2, sortOrder = 3)

        useCase(listOf(source), monday, targetSectionId = 2)

        coVerify(exactly = 0) { logRepository.updateAllAtEnd(any()) }
    }

    @Test
    fun `moving to a different section on the same date keeps identity and source order`() = runTest {
        val source = entry(10, monday, sectionId = 2, sortOrder = 3)
        val captured = expectUpdateAllAtEnd()

        useCase(listOf(source), monday, targetSectionId = 1)

        val moved = captured.captured.single()
        assertEquals(1L, moved.sectionId)
        assertEquals(10L, moved.id)
        assertEquals(3, moved.sortOrder)
        assertEquals(monday, moved.date)
    }

    @Test
    fun `moving to another date keeps source order for repository append`() = runTest {
        val source = entry(10, monday, sectionId = 1, sortOrder = 9)
        val captured = expectUpdateAllAtEnd()

        useCase(listOf(source), tuesday)

        val moved = captured.captured.single()
        assertEquals(tuesday, moved.date)
        assertEquals(9, moved.sortOrder)
    }

    @Test
    fun `mixed selection only updates the entries that actually move`() = runTest {
        val stays = entry(10, monday, sectionId = 2, sortOrder = 0)
        val moves = entry(11, monday, sectionId = 1, sortOrder = 1)
        val captured = expectUpdateAllAtEnd()

        useCase(listOf(stays, moves), monday, targetSectionId = 2)

        val updated = captured.captured
        assertEquals(listOf(11L), updated.map { it.id })
        assertEquals(2L, updated.single().sectionId)
        assertEquals(monday, updated.single().date)
    }

    @Test
    fun `empty entries perform no repository writes`() = runTest {
        useCase(emptyList(), tuesday)

        coVerify(exactly = 0) { logRepository.updateAllAtEnd(any()) }
    }
}
package com.macrotrack.ui.log

import com.macrotrack.data.local.db.dao.DailyMacroRow
import com.macrotrack.data.repository.LogRepository
import com.macrotrack.data.repository.SectionRepository
import com.macrotrack.data.repository.SettingsRepository
import com.macrotrack.domain.model.DailyGoals
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Section
import com.macrotrack.domain.usecase.log.CopyLogEntriesUseCase
import com.macrotrack.domain.usecase.log.DeleteLogEntriesUseCase
import com.macrotrack.domain.usecase.log.GetDailyLogUseCase
import com.macrotrack.domain.usecase.log.GetWeeklyMacrosUseCase
import com.macrotrack.domain.usecase.log.MoveLogEntriesUseCase
import com.macrotrack.domain.usecase.settings.GetSectionsUseCase
import com.macrotrack.domain.usecase.settings.GetSectionGoalsUseCase
import com.macrotrack.domain.usecase.settings.GetSettingsUseCase
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModelSelectionTest {

    private val dispatcher = StandardTestDispatcher()

    private val logRepository = mockk<LogRepository>(relaxed = true)
    private val sectionRepository = mockk<SectionRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    private val entriesFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    private val macrosFlow = MutableStateFlow<List<DailyMacroRow>>(emptyList())

    private val copyLogEntriesUseCase = CopyLogEntriesUseCase(logRepository)
    private val moveLogEntriesUseCase = MoveLogEntriesUseCase(logRepository)
    private val deleteLogEntriesUseCase = DeleteLogEntriesUseCase(logRepository)

    private lateinit var viewModel: LogViewModel

    private val day = LocalDate.of(2026, 8, 10)
    private val otherDay = LocalDate.of(2026, 8, 11)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { logRepository.getLogEntriesByDate(any()) } returns entriesFlow
        every { logRepository.getMacrosByDateRange(any(), any()) } returns macrosFlow
        every { sectionRepository.getAllSections() } returns MutableStateFlow(
            listOf(Section(id = 1, name = "Breakfast", timeOfDay = LocalTime.of(8, 0)))
        )
        every { settingsRepository.getDailyGoals() } returns MutableStateFlow(
            DailyGoals(proteinG = 150, carbsG = 250, fatG = 70)
        )
        every { settingsRepository.getSectionGoalsEnabled() } returns MutableStateFlow(false)
        every { settingsRepository.getSectionGoalDistribution() } returns MutableStateFlow(null)
        viewModel = LogViewModel(
            getDailyLogUseCase = GetDailyLogUseCase(logRepository),
            getSectionsUseCase = GetSectionsUseCase(sectionRepository),
            getSettingsUseCase = GetSettingsUseCase(settingsRepository),
            getSectionGoalsUseCase = GetSectionGoalsUseCase(settingsRepository),
            deleteLogEntriesUseCase = deleteLogEntriesUseCase,
            copyLogEntriesUseCase = copyLogEntriesUseCase,
            moveLogEntriesUseCase = moveLogEntriesUseCase,
            getWeeklyMacrosUseCase = GetWeeklyMacrosUseCase(logRepository),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.collectUiState() {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private fun entry(
        id: Long,
        sortOrder: Int = 0,
        sectionId: Long = 1,
        date: LocalDate = day,
    ) = LogEntry(
        id = id,
        date = date,
        sectionId = sectionId,
        foodItemId = 7L,
        name = "Oatmeal $id",
        brand = "Quaker",
        portionG = 200f,
        portionLabel = "2 servings",
        macros = Macros(300f, 12f, 40f, 6f),
        sortOrder = sortOrder,
        createdAt = Instant.ofEpochMilli(1000 + id),
    )

    private fun TestScope.selectEntry(entryId: Long) {
        collectUiState()
        viewModel.onDateSelected(day)
        entriesFlow.value = listOf(entry(entryId))
        advanceUntilIdle()
        viewModel.toggleSelectionMode(entryId)
        advanceUntilIdle()
    }

    private fun captureInsertAll(): CapturingSlot<List<LogEntry>> {
        val captured = slot<List<LogEntry>>()
        coEvery { logRepository.insertAllAtEnd(capture(captured)) } returns Unit
        return captured
    }

    private fun captureUpdateAll(): CapturingSlot<List<LogEntry>> {
        val captured = slot<List<LogEntry>>()
        coEvery { logRepository.updateAllAtEnd(capture(captured)) } returns Unit
        return captured
    }

    @Test
    fun `selecting the first entry keeps the source date and snapshot`() = runTest(dispatcher) {
        selectEntry(1)

        val mode = viewModel.uiState.value.selectionMode as SelectionMode.Selecting
        assertEquals(day, mode.sourceDate)
        assertEquals(listOf(1L), mode.selectedEntries.map { it.id })
        assertEquals(setOf(1L), mode.selectedIds)
    }

    @Test
    fun `toggling entries preserves source visual order`() = runTest(dispatcher) {
        collectUiState()
        viewModel.onDateSelected(day)
        entriesFlow.value = listOf(entry(1, sortOrder = 1), entry(2, sortOrder = 0))
        advanceUntilIdle()

        viewModel.toggleSelectionMode(2)
        viewModel.toggleSelectionMode(1)
        advanceUntilIdle()

        val mode = viewModel.uiState.value.selectionMode as SelectionMode.Selecting
        assertEquals(listOf(2L, 1L), mode.selectedEntries.map { it.id })
        assertEquals(setOf(1L, 2L), mode.selectedIds)
    }

    @Test
    fun `removing one of several keeps the snapshot in source order`() = runTest(dispatcher) {
        collectUiState()
        viewModel.onDateSelected(day)
        entriesFlow.value = listOf(entry(1, sortOrder = 1), entry(2, sortOrder = 0))
        advanceUntilIdle()

        viewModel.toggleSelectionMode(2)
        viewModel.toggleSelectionMode(1)
        viewModel.toggleSelectionMode(2)
        advanceUntilIdle()

        val mode = viewModel.uiState.value.selectionMode as SelectionMode.Selecting
        assertEquals(listOf(1L), mode.selectedEntries.map { it.id })
    }

    @Test
    fun `toggleSelection starts selection with the given entry and source date`() = runTest(dispatcher) {
        collectUiState()
        viewModel.onDateSelected(day)
        advanceUntilIdle()

        viewModel.toggleSelection(entry(1, sortOrder = 2), day)
        advanceUntilIdle()

        val mode = viewModel.uiState.value.selectionMode as SelectionMode.Selecting
        assertEquals(day, mode.sourceDate)
        assertEquals(listOf(1L), mode.selectedEntries.map { it.id })
        assertEquals(setOf(1L), mode.selectedIds)
    }

    @Test
    fun `toggleSelection preserves source order when toggling multiple entries`() = runTest(dispatcher) {
        collectUiState()
        viewModel.toggleSelection(entry(1, sortOrder = 1), day)
        viewModel.toggleSelection(entry(2, sortOrder = 0), day)
        advanceUntilIdle()

        val mode = viewModel.uiState.value.selectionMode as SelectionMode.Selecting
        assertEquals(listOf(2L, 1L), mode.selectedEntries.map { it.id })
    }

    @Test
    fun `toggleSelection removes an entry when toggled again and turns off when empty`() = runTest(dispatcher) {
        collectUiState()
        viewModel.toggleSelection(entry(1), day)
        viewModel.toggleSelection(entry(1), day)
        advanceUntilIdle()

        assertEquals(SelectionMode.Off, viewModel.uiState.value.selectionMode)
    }

    @Test
    fun `toggleSelection with a different source date starts a fresh selection`() = runTest(dispatcher) {
        collectUiState()
        viewModel.toggleSelection(entry(1), day)
        viewModel.toggleSelection(entry(2, sortOrder = 5), otherDay)
        advanceUntilIdle()

        val mode = viewModel.uiState.value.selectionMode as SelectionMode.Selecting
        assertEquals(otherDay, mode.sourceDate)
        assertEquals(listOf(2L), mode.selectedEntries.map { it.id })
    }

    @Test
    fun `toggleSelection works without the entry being present in the current day`() = runTest(dispatcher) {
        collectUiState()
        viewModel.onDateSelected(day)
        advanceUntilIdle()
        val orphan = entry(9, sortOrder = 3)

        viewModel.toggleSelectionMode(9)
        viewModel.toggleSelection(orphan, day)
        advanceUntilIdle()

        val mode = viewModel.uiState.value.selectionMode as SelectionMode.Selecting
        assertEquals(day, mode.sourceDate)
        assertEquals(listOf(9L), mode.selectedEntries.map { it.id })
    }

    @Test
    fun `removing the last selected entry turns selection off`() = runTest(dispatcher) {
        selectEntry(1)
        viewModel.toggleSelectionMode(1)
        advanceUntilIdle()

        assertEquals(SelectionMode.Off, viewModel.uiState.value.selectionMode)
    }

    @Test
    fun `normal date navigation exits selection`() = runTest(dispatcher) {
        selectEntry(1)

        viewModel.onDateSelected(otherDay)
        advanceUntilIdle()

        assertEquals(SelectionMode.Off, viewModel.uiState.value.selectionMode)
        assertEquals(otherDay, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `duplicateSelectedEntries copies onto the source date and exits selection`() = runTest(dispatcher) {
        selectEntry(1)
        val captured = captureInsertAll()

        viewModel.duplicateSelectedEntries()
        advanceUntilIdle()

        val copied = captured.captured.single()
        assertEquals(day, copied.date)
        assertEquals(1L, copied.sectionId)
        assertEquals(0L, copied.id)
        val mode = viewModel.uiState.value.selectionMode as SelectionMode.Selecting
        assertEquals(day, mode.sourceDate)
        assertEquals(listOf(1L), mode.selectedEntries.map { it.id })
    }

    @Test
    fun `copySelectedEntries enters a Copy destination retaining source date and snapshot`() = runTest(dispatcher) {
        selectEntry(1)

        viewModel.copySelectedEntries()
        advanceUntilIdle()

        val mode = viewModel.uiState.value.selectionMode as SelectionMode.ChoosingDestination
        assertEquals(day, mode.sourceDate)
        assertEquals(listOf(1L), mode.selectedEntries.map { it.id })
        assertEquals(Action.Copy, mode.action)
    }

    @Test
    fun `moveSelectedEntries compatibility shim enters a Copy destination`() = runTest(dispatcher) {
        selectEntry(1)

        viewModel.moveSelectedEntries()
        advanceUntilIdle()

        val mode = viewModel.uiState.value.selectionMode as SelectionMode.ChoosingDestination
        assertEquals(Action.Copy, mode.action)
        assertEquals(listOf(1L), mode.selectedEntries.map { it.id })
    }

    @Test
    fun `confirmCopyMove resolves from the snapshot even when the current day changes`() = runTest(dispatcher) {
        selectEntry(1)
        viewModel.copySelectedEntries()
        entriesFlow.value = emptyList()
        advanceUntilIdle()
        val captured = captureInsertAll()

        viewModel.confirmCopyMove(otherDay)
        advanceUntilIdle()

        val copied = captured.captured.single()
        assertEquals(otherDay, copied.date)
        assertEquals("Oatmeal 1", copied.name)
        val mode = viewModel.uiState.value.selectionMode as SelectionMode.Selecting
        assertEquals(day, mode.sourceDate)
        assertEquals(listOf(1L), mode.selectedEntries.map { it.id })
    }

    @Test
    fun `cancelChoosingDestination restores the Selecting state from the snapshot`() = runTest(dispatcher) {
        selectEntry(1)
        viewModel.copySelectedEntries()

        viewModel.cancelChoosingDestination()
        advanceUntilIdle()

        val mode = viewModel.uiState.value.selectionMode as SelectionMode.Selecting
        assertEquals(day, mode.sourceDate)
        assertEquals(listOf(1L), mode.selectedEntries.map { it.id })
    }

    @Test
    fun `deleteSelectedEntries deletes the snapshot and exits selection`() = runTest(dispatcher) {
        selectEntry(1)
        entriesFlow.value = emptyList()
        advanceUntilIdle()

        viewModel.deleteSelectedEntries()
        advanceUntilIdle()

        coVerify(exactly = 1) { logRepository.delete(listOf(entry(1))) }
        assertEquals(SelectionMode.Off, viewModel.uiState.value.selectionMode)
    }

    @Test
    fun `moveDraggedEntries moves to the target section and exits selection`() = runTest(dispatcher) {
        selectEntry(1)
        val captured = captureUpdateAll()

        viewModel.moveDraggedEntries(listOf(entry(1)), otherDay, targetSectionId = 2)
        advanceUntilIdle()

        val moved = captured.captured.single()
        assertEquals(otherDay, moved.date)
        assertEquals(2L, moved.sectionId)
        assertEquals(1L, moved.id)
        assertEquals(SelectionMode.Off, viewModel.uiState.value.selectionMode)
    }

    @Test
    fun `moveDraggedEntries no-ops and keeps selection when nothing changes`() = runTest(dispatcher) {
        selectEntry(1)

        viewModel.moveDraggedEntries(listOf(entry(1)), day, targetSectionId = 1)
        advanceUntilIdle()

        coVerify(exactly = 0) { logRepository.updateAllAtEnd(any()) }
        assertTrue(viewModel.uiState.value.selectionMode is SelectionMode.Selecting)
    }

    @Test
    fun `moveDraggedEntries ignores empty input`() = runTest(dispatcher) {
        viewModel.moveDraggedEntries(emptyList(), otherDay, 2)
        advanceUntilIdle()

        coVerify(exactly = 0) { logRepository.updateAllAtEnd(any()) }
        assertEquals(SelectionMode.Off, viewModel.uiState.value.selectionMode)
    }

    @Test
    fun `moveDraggedEntries with no target section preserves each entry section like a date bar drop`() = runTest(dispatcher) {
        val captured = captureUpdateAll()
        val breakfast = entry(1, sortOrder = 0, sectionId = 1)
        val dinner = entry(2, sortOrder = 1, sectionId = 2)

        viewModel.moveDraggedEntries(listOf(breakfast, dinner), otherDay)
        advanceUntilIdle()

        val moved = captured.captured
        assertEquals(listOf(1L, 2L), moved.map { it.id })
        assertEquals(otherDay, moved[0].date)
        assertEquals(1L, moved[0].sectionId)
        assertEquals(otherDay, moved[1].date)
        assertEquals(2L, moved[1].sectionId)
        assertEquals(SelectionMode.Off, viewModel.uiState.value.selectionMode)
    }

    @Test
    fun `moveDraggedEntries with no target section to the source date is a no-op keeping selection`() = runTest(dispatcher) {
        selectEntry(1)

        viewModel.moveDraggedEntries(listOf(entry(1)), day)
        advanceUntilIdle()

        coVerify(exactly = 0) { logRepository.updateAllAtEnd(any()) }
        assertTrue(viewModel.uiState.value.selectionMode is SelectionMode.Selecting)
    }

    @Test
    fun `navigateDuringDrag changes the date without clearing selection`() = runTest(dispatcher) {
        selectEntry(1)

        viewModel.navigateDuringDrag(otherDay)
        advanceUntilIdle()

        assertEquals(otherDay, viewModel.uiState.value.selectedDate)
        assertTrue(viewModel.uiState.value.selectionMode is SelectionMode.Selecting)
    }

    @Test
    fun `navigateWeekDuringDrag advances the week anchor without clearing selection`() = runTest(dispatcher) {
        selectEntry(1)

        viewModel.navigateWeekDuringDrag(2)
        advanceUntilIdle()

        assertEquals(day, viewModel.uiState.value.selectedDate)
        assertEquals(mondayOf(day).plusWeeks(2), viewModel.uiState.value.displayedWeekStart)
        assertTrue(viewModel.uiState.value.selectionMode is SelectionMode.Selecting)
    }
}
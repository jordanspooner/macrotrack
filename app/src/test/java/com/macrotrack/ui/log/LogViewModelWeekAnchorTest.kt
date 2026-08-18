package com.macrotrack.ui.log

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
import com.macrotrack.domain.usecase.settings.GetSettingsUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModelWeekAnchorTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private val dailyLog = mockk<GetDailyLogUseCase>()
    private val sections = mockk<GetSectionsUseCase>()
    private val settings = mockk<GetSettingsUseCase>()
    private val weeklyMacros = mockk<GetWeeklyMacrosUseCase>()
    private val deleteEntries = mockk<DeleteLogEntriesUseCase>(relaxed = true)
    private val copyEntries = mockk<CopyLogEntriesUseCase>(relaxed = true)
    private val moveEntries = mockk<MoveLogEntriesUseCase>(relaxed = true)

    private val monday = LocalDate.of(2026, 8, 10)
    private val sunday = LocalDate.of(2026, 8, 16)
    private val nextMonday = LocalDate.of(2026, 8, 17)

    private lateinit var viewModel: LogViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { dailyLog(any()) } returns flowOf(emptyList())
        every { sections() } returns flowOf(
            listOf(Section(id = 1L, name = "Dinner", timeOfDay = LocalTime.of(18, 0)))
        )
        every { settings() } returns flowOf(DailyGoals(proteinG = 150, carbsG = 250, fatG = 65))
        every { weeklyMacros(any(), any()) } returns flowOf(emptyList())
        viewModel = LogViewModel(
            getDailyLogUseCase = dailyLog,
            getSectionsUseCase = sections,
            getSettingsUseCase = settings,
            deleteLogEntriesUseCase = deleteEntries,
            copyLogEntriesUseCase = copyEntries,
            moveLogEntriesUseCase = moveEntries,
            getWeeklyMacrosUseCase = weeklyMacros,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.collectUiState() {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private fun entry(id: Long) = LogEntry(
        id = id,
        date = monday,
        sectionId = 1L,
        foodItemId = 7L,
        name = "Oatmeal $id",
        brand = null,
        portionG = 200f,
        portionLabel = null,
        macros = Macros(300f, 12f, 40f, 6f),
        sortOrder = 0,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun `displayedWeekStart defaults to the monday of the initial selected date`() = runTest(dispatcher) {
        collectUiState()
        advanceUntilIdle()

        assertEquals(LocalDate.now(), viewModel.uiState.value.selectedDate)
        assertEquals(mondayOf(LocalDate.now()), viewModel.uiState.value.displayedWeekStart)
    }

    @Test
    fun `advanceDayDuringDrag accumulates repeated deltas on the live date`() = runTest(dispatcher) {
        collectUiState()
        viewModel.onDateSelected(monday)
        advanceUntilIdle()

        viewModel.advanceDayDuringDrag(1)
        viewModel.advanceDayDuringDrag(1)
        viewModel.advanceDayDuringDrag(1)
        advanceUntilIdle()

        assertEquals(monday.plusDays(3), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `advanceDayDuringDrag keeps the week anchor while staying in the week`() = runTest(dispatcher) {
        collectUiState()
        viewModel.onDateSelected(monday)
        advanceUntilIdle()

        viewModel.advanceDayDuringDrag(1)
        viewModel.advanceDayDuringDrag(1)
        advanceUntilIdle()

        assertEquals(monday, viewModel.uiState.value.displayedWeekStart)
        assertEquals(monday.plusDays(2), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `advanceDayDuringDrag realigns the week anchor when crossing into a new week`() = runTest(dispatcher) {
        collectUiState()
        viewModel.onDateSelected(sunday)
        advanceUntilIdle()
        assertEquals(monday, viewModel.uiState.value.displayedWeekStart)

        viewModel.advanceDayDuringDrag(1)
        advanceUntilIdle()

        assertEquals(nextMonday, viewModel.uiState.value.selectedDate)
        assertEquals(nextMonday, viewModel.uiState.value.displayedWeekStart)
    }

    @Test
    fun `navigateWeekDuringDrag changes only the week anchor and keeps the selected date`() = runTest(dispatcher) {
        collectUiState()
        viewModel.onDateSelected(monday.plusDays(2))
        advanceUntilIdle()

        viewModel.navigateWeekDuringDrag(1)
        advanceUntilIdle()

        assertEquals(monday.plusDays(2), viewModel.uiState.value.selectedDate)
        assertEquals(nextMonday, viewModel.uiState.value.displayedWeekStart)
        viewModel.navigateWeekDuringDrag(-2)
        advanceUntilIdle()
        assertEquals(monday.minusWeeks(1), viewModel.uiState.value.displayedWeekStart)
    }

    @Test
    fun `selection survives day drag and week drag`() = runTest(dispatcher) {
        collectUiState()
        viewModel.onDateSelected(monday)
        advanceUntilIdle()
        viewModel.toggleSelection(entry(1), monday)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.selectionMode is SelectionMode.Selecting)

        viewModel.advanceDayDuringDrag(2)
        viewModel.navigateWeekDuringDrag(1)
        advanceUntilIdle()

        val mode = viewModel.uiState.value.selectionMode as SelectionMode.Selecting
        assertEquals(monday, mode.sourceDate)
        assertEquals(listOf(1L), mode.selectedEntries.map { it.id })
    }

    @Test
    fun `onDateSelected aligns the displayed week to the selected date monday`() = runTest(dispatcher) {
        collectUiState()
        viewModel.onDateSelected(monday.plusDays(5))
        advanceUntilIdle()

        assertEquals(monday.plusDays(5), viewModel.uiState.value.selectedDate)
        assertEquals(monday, viewModel.uiState.value.displayedWeekStart)

        viewModel.onDateSelected(nextMonday.plusDays(3))
        advanceUntilIdle()
        assertEquals(nextMonday, viewModel.uiState.value.displayedWeekStart)
    }

    @Test
    fun `week day is selected only when the date matches the selected date in the displayed week`() = runTest(dispatcher) {
        collectUiState()
        viewModel.onDateSelected(monday.plusDays(2))
        advanceUntilIdle()

        val currentWeek = viewModel.uiState.value.currentWeek
        assertEquals(1, currentWeek.count { it.isSelected })
        assertEquals(monday.plusDays(2), currentWeek.first { it.isSelected }.date)
        assertTrue(viewModel.uiState.value.prevWeek.none { it.isSelected })
        assertTrue(viewModel.uiState.value.nextWeek.none { it.isSelected })
    }

    @Test
    fun `weekly macro query is anchored to the displayed week start`() = runTest(dispatcher) {
        collectUiState()
        viewModel.onDateSelected(monday.plusDays(2))
        advanceUntilIdle()

        viewModel.navigateWeekDuringDrag(1)
        advanceUntilIdle()

        verify { weeklyMacros(monday, nextMonday.plusDays(13)) }
    }
}
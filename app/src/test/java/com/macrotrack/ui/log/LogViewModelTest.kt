package com.macrotrack.ui.log

import com.macrotrack.domain.model.DailyGoals
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
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LogViewModelTest {

    private val mainDispatcher: TestDispatcher = StandardTestDispatcher()
    private val dailyLog = mockk<GetDailyLogUseCase>()
    private val sections = mockk<GetSectionsUseCase>()
    private val settings = mockk<GetSettingsUseCase>()
    private val weeklyMacros = mockk<GetWeeklyMacrosUseCase>()
    private val deleteEntries = mockk<DeleteLogEntriesUseCase>(relaxed = true)
    private val copyEntries = mockk<CopyLogEntriesUseCase>(relaxed = true)
    private val moveEntries = mockk<MoveLogEntriesUseCase>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { dailyLog(any()) } returns flowOf(emptyList())
        every { sections() } returns flowOf(
            listOf(Section(id = 1L, name = "Dinner", timeOfDay = LocalTime.of(18, 0)))
        )
        every { settings() } returns flowOf(DailyGoals(proteinG = 150, carbsG = 250, fatG = 65))
        every { weeklyMacros(any(), any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selected date updates before its daily query completes`() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        val delayedDate = today.plusDays(2)
        val delayedDailyLog = MutableSharedFlow<List<com.macrotrack.domain.model.LogEntry>>()
        every { dailyLog(any()) } answers {
            if (firstArg<LocalDate>() == delayedDate) delayedDailyLog else flowOf(emptyList())
        }

        val viewModel = LogViewModel(
            getDailyLogUseCase = dailyLog,
            getSectionsUseCase = sections,
            getSettingsUseCase = settings,
            deleteLogEntriesUseCase = deleteEntries,
            copyLogEntriesUseCase = copyEntries,
            moveLogEntriesUseCase = moveEntries,
            getWeeklyMacrosUseCase = weeklyMacros,
        )
        val states = mutableListOf<LogUiState>()
        val collectJob = launch { viewModel.uiState.collect { states += it } }

        advanceUntilIdle()
        assertTrue(states.any { it.selectedDate == today && !it.isLoading })

        viewModel.onDateSelected(delayedDate)
        runCurrent()

        assertTrue(states.any { it.selectedDate == delayedDate })
        assertNull(states.last().currentDay)

        delayedDailyLog.emit(emptyList())
        advanceUntilIdle()
        assertTrue(states.last { it.selectedDate == delayedDate }.currentDay != null)

        collectJob.cancel()
    }
}

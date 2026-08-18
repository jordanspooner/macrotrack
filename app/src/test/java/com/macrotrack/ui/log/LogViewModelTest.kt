package com.macrotrack.ui.log

import com.macrotrack.domain.model.DailyGoals
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.MacroType
import com.macrotrack.domain.model.Section
import com.macrotrack.domain.model.SectionGoalPercentages
import com.macrotrack.domain.model.SectionGoals
import com.macrotrack.domain.usecase.log.CopyLogEntriesUseCase
import com.macrotrack.domain.usecase.log.DeleteLogEntriesUseCase
import com.macrotrack.domain.usecase.log.GetDailyLogUseCase
import com.macrotrack.domain.usecase.log.GetWeeklyMacrosUseCase
import com.macrotrack.domain.usecase.log.MoveLogEntriesUseCase
import com.macrotrack.domain.usecase.settings.GetSectionsUseCase
import com.macrotrack.domain.usecase.settings.GetSectionGoalsUseCase
import com.macrotrack.domain.usecase.settings.GetSettingsUseCase
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
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
import org.junit.Assert.assertEquals
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
    private val sectionGoals = mockk<GetSectionGoalsUseCase>()
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
        every { sectionGoals() } returns flowOf(SectionGoals(enabled = false))
        every { weeklyMacros(any(), any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = LogViewModel(
        getDailyLogUseCase = dailyLog,
        getSectionsUseCase = sections,
        getSettingsUseCase = settings,
        getSectionGoalsUseCase = sectionGoals,
        deleteLogEntriesUseCase = deleteEntries,
        copyLogEntriesUseCase = copyEntries,
        moveLogEntriesUseCase = moveEntries,
        getWeeklyMacrosUseCase = weeklyMacros,
    )

    @Test
    fun `selected date updates before its daily query completes`() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        val delayedDate = today.plusDays(2)
        val delayedDailyLog = MutableSharedFlow<List<com.macrotrack.domain.model.LogEntry>>()
        every { dailyLog(any()) } answers {
            if (firstArg<LocalDate>() == delayedDate) delayedDailyLog else flowOf(emptyList())
        }

        val viewModel = buildViewModel()
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

    @Test
    fun `week day receives uncapped per-macro progress`() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        val todayStr = today.toString()
        val macros = com.macrotrack.data.local.db.dao.DailyMacroRow(
            date = todayStr,
            kcal = 400f,
            protein = 200f,
            carbs = 250f,
            fat = 130f,
        )
        every { weeklyMacros(any(), any()) } returns flowOf(listOf(macros))

        val viewModel = buildViewModel()
        val states = mutableListOf<LogUiState>()
        val collectJob = launch { viewModel.uiState.collect { states += it } }
        advanceUntilIdle()

        val todayDay = states.last().currentWeek.firstOrNull { it.date == today }
        check(todayDay != null) { "current week should contain today" }
        assertEquals(200f / 150f, todayDay.proteinProgress, 0.0001f)
        assertEquals(250f / 250f, todayDay.carbsProgress, 0.0001f)
        assertEquals(130f / 65f, todayDay.fatProgress, 0.0001f)

        collectJob.cancel()
    }

    @Test
    fun `week day receives kcal-weighted macro shares`() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        every { weeklyMacros(any(), any()) } returns flowOf(emptyList())

        val viewModel = buildViewModel()
        val states = mutableListOf<LogUiState>()
        val collectJob = launch { viewModel.uiState.collect { states += it } }
        advanceUntilIdle()

        // Daily goals 150/250/65 → kcal shares 600/2185, 1000/2185, 585/2185.
        val todayDay = states.last().currentWeek.first { it.date == today }
        assertEquals(600f / 2185f, todayDay.proteinShare, 0.0001f)
        assertEquals(1000f / 2185f, todayDay.carbsShare, 0.0001f)
        assertEquals(585f / 2185f, todayDay.fatShare, 0.0001f)
        assertEquals(
            1f,
            todayDay.proteinShare + todayDay.carbsShare + todayDay.fatShare,
            0.0001f,
        )

        collectJob.cancel()
    }

    @Test
    fun `disabled section goals expose no per-meal goals`() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        every { sectionGoals() } returns flowOf(SectionGoals(enabled = false))

        val viewModel = buildViewModel()
        val states = mutableListOf<LogUiState>()
        val collectJob = launch { viewModel.uiState.collect { states += it } }
        advanceUntilIdle()

        val section = states.last().currentDay!!.sections.single()
        assertEquals("Dinner", section.section.name)
        assertNull(section.goalMacros)

        collectJob.cancel()
    }

    @Test
    fun `enabled section goals propagate distributed goal macros`() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        every { dailyLog(any()) } returns flowOf(
            listOf(
                LogEntry(
                    date = today,
                    sectionId = 1L,
                    name = "Chicken",
                    portionG = 100f,
                    macros = Macros(kcal = 400f, proteinG = 30f, carbsG = 5f, fatG = 20f),
                    sortOrder = 0,
                    createdAt = Instant.now(),
                )
            )
        )
        every { sectionGoals() } returns flowOf(
            SectionGoals(
                enabled = true,
                percentages = SectionGoalPercentages(
                    percentages = mapOf(
                        1L to mapOf(
                            MacroType.PROTEIN to 100f,
                            MacroType.CARBS to 100f,
                            MacroType.FAT to 100f,
                        )
                    )
                ),
            )
        )

        val viewModel = buildViewModel()
        val states = mutableListOf<LogUiState>()
        val collectJob = launch { viewModel.uiState.collect { states += it } }
        advanceUntilIdle()

        val section = states.last().currentDay!!.sections.single()
        assertEquals(1, section.entries.size)
        // Daily goals 150/250/65 at 100% → section goal equals the daily goal.
        val goal = checkNotNull(section.goalMacros)
        assertEquals(150f, goal.proteinG, 0.01f)
        assertEquals(250f, goal.carbsG, 0.01f)
        assertEquals(65f, goal.fatG, 0.01f)
        assertEquals(150 * 4 + 250 * 4 + 65 * 9f, goal.kcal, 0.01f)

        collectJob.cancel()
    }

    @Test
    fun `stale section distribution falls back to even split goals`() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        every { sections() } returns flowOf(
            listOf(
                Section(id = 1L, name = "Dinner", timeOfDay = LocalTime.of(18, 0)),
                Section(id = 2L, name = "Lunch", timeOfDay = LocalTime.of(12, 0)),
            )
        )
        every { sectionGoals() } returns flowOf(
            SectionGoals(
                enabled = true,
                percentages = SectionGoalPercentages(
                    percentages = mapOf(
                        // Distribution was saved for old sections (1 and 99); 99 is gone.
                        1L to mapOf(MacroType.PROTEIN to 100f, MacroType.CARBS to 100f, MacroType.FAT to 100f),
                        99L to mapOf(MacroType.PROTEIN to 0f, MacroType.CARBS to 0f, MacroType.FAT to 0f),
                    )
                ),
            )
        )

        val viewModel = buildViewModel()
        val states = mutableListOf<LogUiState>()
        val collectJob = launch { viewModel.uiState.collect { states += it } }
        advanceUntilIdle()

        val sections = states.last().currentDay!!.sections
        // Stale → even split, so each section targets 50% of the daily goals.
        val dinnerGoal = checkNotNull(sections.first { it.section.id == 1L }.goalMacros)
        val lunchGoal = checkNotNull(sections.first { it.section.id == 2L }.goalMacros)
        assertEquals(75f, dinnerGoal.proteinG, 0.01f)
        assertEquals(125f, dinnerGoal.carbsG, 0.01f)
        assertEquals(32.5f, dinnerGoal.fatG, 0.01f)
        assertEquals(lunchGoal, dinnerGoal)

        collectJob.cancel()
    }
}

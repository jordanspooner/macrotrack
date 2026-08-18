package com.macrotrack.domain.usecase.log

import com.google.common.truth.Truth.assertThat
import com.macrotrack.data.local.db.dao.DailyMacroRow
import com.macrotrack.data.repository.LogRepository
import com.macrotrack.data.repository.SettingsRepository
import com.macrotrack.domain.model.DailyGoals
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetDailySummaryUseCaseTest {

    private val logRepository = mockk<LogRepository>()
    private val settingsRepository = mockk<SettingsRepository>()

    @Test
    fun `empty aggregate result maps to zero macros`() = runTest {
        val date = LocalDate.of(2026, 8, 15)
        val goals = DailyGoals(proteinG = 150, carbsG = 250, fatG = 65)
        every { logRepository.getMacrosByDateRange(date, date) } returns flowOf(emptyList())
        every { settingsRepository.getDailyGoals() } returns flowOf(goals)

        val summary = GetDailySummaryUseCase(logRepository, settingsRepository)(date).first()

        assertThat(summary.date).isEqualTo(date)
        assertThat(summary.goals).isEqualTo(goals)
        assertThat(summary.logged.kcal).isEqualTo(0f)
        assertThat(summary.logged.proteinG).isEqualTo(0f)
        assertThat(summary.logged.carbsG).isEqualTo(0f)
        assertThat(summary.logged.fatG).isEqualTo(0f)
    }

    @Test
    fun `populated aggregate result maps row into summary`() = runTest {
        val date = LocalDate.of(2026, 8, 15)
        val goals = DailyGoals(proteinG = 150, carbsG = 250, fatG = 65)
        every { logRepository.getMacrosByDateRange(date, date) } returns flowOf(
            listOf(
                DailyMacroRow(
                    date = date.toString(),
                    kcal = 2100f,
                    protein = 120f,
                    carbs = 180f,
                    fat = 70f,
                )
            )
        )
        every { settingsRepository.getDailyGoals() } returns flowOf(goals)

        val summary = GetDailySummaryUseCase(logRepository, settingsRepository)(date).first()

        assertThat(summary.date).isEqualTo(date)
        assertThat(summary.goals).isEqualTo(goals)
        assertThat(summary.logged.kcal).isEqualTo(2100f)
        assertThat(summary.logged.proteinG).isEqualTo(120f)
        assertThat(summary.logged.carbsG).isEqualTo(180f)
        assertThat(summary.logged.fatG).isEqualTo(70f)
    }
}
package com.macrotrack.domain.usecase.settings

import com.macrotrack.data.repository.SettingsRepository
import com.macrotrack.domain.model.DailyGoals
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateDailyGoalsUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val useCase = UpdateDailyGoalsUseCase(settingsRepository)

    @Test
    fun `persists goals to repository`() = runTest {
        val goals = DailyGoals(proteinG = 120, carbsG = 200, fatG = 50)
        useCase(goals)
        coVerify(exactly = 1) { settingsRepository.updateDailyGoals(goals) }
    }

    @Test
    fun `kcal is computed from macros`() = runTest {
        val goals = DailyGoals(proteinG = 100, carbsG = 100, fatG = 100)
        // 100*4 + 100*4 + 100*9 = 1700
        assertEquals(1700, goals.kcal)
    }
}
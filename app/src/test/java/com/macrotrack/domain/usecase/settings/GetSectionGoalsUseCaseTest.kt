package com.macrotrack.domain.usecase.settings

import com.macrotrack.data.repository.SettingsRepository
import com.macrotrack.domain.model.MacroType
import com.macrotrack.domain.model.SectionGoals
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetSectionGoalsUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val useCase = GetSectionGoalsUseCase(settingsRepository)

    @Test
    fun `disabled flag surfaces as disabled section goals`() = runTest {
        every { settingsRepository.getSectionGoalsEnabled() } returns flowOf(false)
        every { settingsRepository.getSectionGoalDistribution() } returns flowOf(null)

        val result: SectionGoals = useCase().first()
        assertFalse(result.enabled)
        assertTrue(result.percentages.percentages.isEmpty())
    }

    @Test
    fun `enabled flag parses the stored distribution`() = runTest {
        every { settingsRepository.getSectionGoalsEnabled() } returns flowOf(true)
        every { settingsRepository.getSectionGoalDistribution() } returns flowOf(
            "{\"1\":{\"PROTEIN\":40.0,\"CARBS\":30.0,\"FAT\":30.0}}"
        )

        val result: SectionGoals = useCase().first()
        assertTrue(result.enabled)
        assertEquals(40f, result.percentages.percentages[1L]?.get(MacroType.PROTEIN)!!, 0.01f)
        assertEquals(30f, result.percentages.percentages[1L]?.get(MacroType.FAT)!!, 0.01f)
    }
}
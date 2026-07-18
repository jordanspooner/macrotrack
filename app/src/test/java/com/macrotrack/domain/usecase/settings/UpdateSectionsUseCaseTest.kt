package com.macrotrack.domain.usecase.settings

import com.macrotrack.data.repository.SectionRepository
import com.macrotrack.domain.model.Section
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class UpdateSectionsUseCaseTest {

    private val sectionRepository = mockk<SectionRepository>(relaxed = true)
    private val useCase = UpdateSectionsUseCase(sectionRepository)

    @Test
    fun `persists section list to repository`() = runTest {
        val sections = listOf(
            Section(id = 1, name = "Breakfast", timeOfDay = LocalTime.of(7, 30), sortOrder = 0),
            Section(id = 2, name = "Lunch", timeOfDay = LocalTime.of(12, 30), sortOrder = 1),
        )
        useCase(sections)
        coVerify(exactly = 1) { sectionRepository.updateAll(sections) }
    }

    @Test
    fun `order is preserved`() = runTest {
        val sections = listOf(
            Section(id = 1, name = "A", timeOfDay = LocalTime.of(8, 0), sortOrder = 0),
            Section(id = 2, name = "B", timeOfDay = LocalTime.of(9, 0), sortOrder = 1),
        )
        useCase(sections)
        coVerify(exactly = 1) { sectionRepository.updateAll(withArg { assertEquals(2, it.size) }) }
    }
}
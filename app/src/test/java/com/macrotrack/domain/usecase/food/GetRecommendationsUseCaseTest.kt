package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.LogRepository
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Source
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetRecommendationsUseCaseTest {

    private val logRepository = mockk<LogRepository>()
    private val foodRepository = mockk<FoodRepository>()

    private fun food(id: Long) = FoodItem(
        id = id, source = Source.OPEN_FOOD_FACTS, name = "Food $id",
        macroPer100g = Macros(100f, 10f, 10f, 5f)
    )

    private fun setup(
        recentSection: List<Long> = emptyList(),
        frequentSection: List<Long> = emptyList(),
        recentOverall: List<Long> = emptyList(),
        frequentOverall: List<Long> = emptyList()
    ) {
        coEvery { logRepository.getRecentFoodIds(any(), any()) } returns recentSection
        coEvery { logRepository.getFrequentFoodIds(any(), any()) } returns frequentSection
        coEvery { logRepository.getRecentFoodIdsOverall(any()) } returns recentOverall
        coEvery { logRepository.getFrequentFoodIdsOverall(any()) } returns frequentOverall
        coEvery { foodRepository.getFoodById(any()) } answers {
            val id = firstArg<Long>()
            food(id)
        }
    }

    @Test
    fun `blends heuristics without duplicates`() = runTest {
        setup(
            recentSection = listOf(1, 2),
            frequentSection = listOf(2, 3),
            recentOverall = listOf(4, 5),
            frequentOverall = listOf(5, 6)
        )
        val useCase = GetRecommendationsUseCase(logRepository, foodRepository)
        val result = useCase.getRecommendations(sectionId = 10, limit = 10)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), result.map { it.id })
    }

    @Test
    fun `respects limit`() = runTest {
        setup(
            recentSection = listOf(1, 2, 3, 4, 5),
            frequentSection = emptyList(),
            recentOverall = listOf(6, 7, 8, 9, 10, 11, 12),
            frequentOverall = emptyList()
        )
        val useCase = GetRecommendationsUseCase(logRepository, foodRepository)
        val result = useCase.getRecommendations(sectionId = 1, limit = 5)
        assertEquals(5, result.size)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), result.map { it.id })
    }

    @Test
    fun `returns empty when no history`() = runTest {
        setup()
        val useCase = GetRecommendationsUseCase(logRepository, foodRepository)
        assertEquals(emptyList<FoodItem>(), useCase.getRecommendations(sectionId = 1))
    }
}

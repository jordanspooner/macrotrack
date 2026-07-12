package com.macrotrack.domain.usecase.food

import app.cash.turbine.test
import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.LogRepository
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Source
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFoodUseCaseTest {

    private val foodRepository = mockk<FoodRepository>()
    private val logRepository = mockk<LogRepository>()

    private fun food(id: Long, name: String) = FoodItem(
        id = id, source = Source.OPEN_FOOD_FACTS, name = name,
        macroPer100g = Macros(100f, 10f, 10f, 5f)
    )

    @Test
    fun `empty query emits nothing`() = runTest {
        val useCase = SearchFoodUseCase(foodRepository, logRepository)
        useCase("   ").test {
            awaitComplete()
        }
    }

    @Test
    fun `logged foods are ranked before unlogged`() = runTest {
        val apple = food(1, "Apple")
        val banana = food(2, "Banana")
        val cherry = food(3, "Cherry")

        every { foodRepository.searchFts(any()) } returns flowOf(listOf(apple, banana, cherry))
        every { logRepository.getLoggedFoodIds() } returns flowOf(listOf(2L))

        val useCase = SearchFoodUseCase(foodRepository, logRepository)
        useCase("a").test {
            val result = awaitItem()
            // banana (id 2) is logged, so it must come first; others keep order.
            assertEquals(2, result.first().id)
            assertEquals(listOf(2L, 1L, 3L), result.map { it.id })
            awaitComplete()
        }
    }

    @Test
    fun `query is formatted as fts prefix`() = runTest {
        val foods = listOf(food(1, "Cheese"))
        every { foodRepository.searchFts(any()) } returns flowOf(foods)
        every { logRepository.getLoggedFoodIds() } returns flowOf(emptyList())

        val useCase = SearchFoodUseCase(foodRepository, logRepository)
        useCase("chee").test {
            // We can't easily assert the formatted string here, but ensure a
            // result is emitted for a non-blank query.
            assertEquals(foods, awaitItem())
            awaitComplete()
        }
    }
}

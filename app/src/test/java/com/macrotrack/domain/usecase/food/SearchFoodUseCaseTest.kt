package com.macrotrack.domain.usecase.food

import app.cash.turbine.test
import com.macrotrack.data.local.db.dao.FoodItemDao
import com.macrotrack.data.local.db.entity.FoodItemEntity
import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.FoodRepositoryImpl
import com.macrotrack.data.repository.LogRepository
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.FoodUsageStats
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Source
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
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

    private fun stats(id: Long, sectionCount: Int = 0, overallCount: Int = 0) = FoodUsageStats(
        foodItemId = id,
        overallCount = overallCount,
        overallRecentCreatedAt = null,
        sectionCount = sectionCount,
        sectionRecentCreatedAt = null,
    )

    private fun entity(id: Long, name: String) = FoodItemEntity(
        id = id,
        source = "OPEN_FOOD_FACTS",
        name = name,
        kcalPer100g = 100f,
        proteinPer100g = 10f,
        carbsPer100g = 10f,
        fatPer100g = 5f,
    )

    @Test
    fun `empty query emits nothing`() = runTest {
        val useCase = SearchFoodUseCase(foodRepository, logRepository)
        useCase("   ").test {
            awaitComplete()
        }
    }

    @Test
    fun `punctuation-only query emits nothing`() = runTest {
        val useCase = SearchFoodUseCase(foodRepository, logRepository)
        useCase("!?").test {
            awaitComplete()
        }
    }

    @Test
    fun `single-character query is a safe prefix search`() = runTest {
        val foods = listOf(food(1, "Avocado"))
        val ftsQuery = CapturingSlot<String>()
        every { foodRepository.searchFts(capture(ftsQuery)) } returns flowOf(foods)
        every { foodRepository.searchFtsFuzzy(any()) } returns flowOf(emptyList())
        every { logRepository.getFoodUsageStats(any(), any()) } returns flowOf(emptyList())

        val useCase = SearchFoodUseCase(foodRepository, logRepository)
        useCase("a").test {
            assertEquals("\"a\"*", ftsQuery.captured)
            assertEquals(foods, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `section-scoped usage ranks used foods first among equal matches`() = runTest {
        val cheeseA = food(1, "Cheese")
        val cheeseB = food(2, "Cheese")
        val cheesecake = food(3, "Cheesecake")

        every { foodRepository.searchFts(any()) } returns flowOf(listOf(cheeseA, cheeseB, cheesecake))
        every { foodRepository.searchFtsFuzzy(any()) } returns flowOf(emptyList())
        every { logRepository.getFoodUsageStats(any(), any()) } returns flowOf(listOf(stats(2, sectionCount = 5, overallCount = 8)))

        val useCase = SearchFoodUseCase(foodRepository, logRepository)
        useCase("cheese", 7).test {
            // Both "Cheese" foods are exact matches; the one used 5x in section 7 wins.
            assertEquals(listOf(2L, 1L, 3L), awaitItem().map { it.id })
            awaitComplete()
        }
    }

    @Test
    fun `fuzzy candidates never outrank exact or prefix matches`() = runTest {
        val exact = food(1, "Cheese")
        val prefix = food(2, "Cheesecake")
        val fuzzy = food(3, "Chese Bites")

        every { foodRepository.searchFts(any()) } returns flowOf(listOf(exact, prefix))
        every { foodRepository.searchFtsFuzzy(any()) } returns flowOf(listOf(fuzzy))
        every { logRepository.getFoodUsageStats(any(), any()) } returns flowOf(
            listOf(stats(3, sectionCount = 100, overallCount = 100))
        )

        val useCase = SearchFoodUseCase(foodRepository, logRepository)
        useCase("cheese").test {
            // The heavily-used fuzzy match must not outrank the exact/prefix ones.
            assertEquals(listOf(1L, 2L, 3L), awaitItem().map { it.id })
            awaitComplete()
        }
    }

    @Test
    fun `query is formatted with the safe fts prefix formatter`() = runTest {
        val foods = listOf(food(1, "Cheese"))
        val ftsQuery = CapturingSlot<String>()
        every { foodRepository.searchFts(capture(ftsQuery)) } returns flowOf(foods)
        every { foodRepository.searchFtsFuzzy(any()) } returns flowOf(emptyList())
        every { logRepository.getFoodUsageStats(any(), any()) } returns flowOf(emptyList())

        val useCase = SearchFoodUseCase(foodRepository, logRepository)
        useCase("chee").test {
            assertEquals("\"chee\"*", ftsQuery.captured)
            assertEquals(foods, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `usage stats updates re-rank results without changing the query`() = runTest {
        val cheeseA = food(1, "Cheese")
        val cheeseB = food(2, "Cheese")
        val usageStats = MutableStateFlow(listOf(stats(2, sectionCount = 5, overallCount = 8)))

        every { foodRepository.searchFts(any()) } returns flowOf(listOf(cheeseA, cheeseB))
        every { foodRepository.searchFtsFuzzy(any()) } returns flowOf(emptyList())
        every { logRepository.getFoodUsageStats(any(), any()) } returns usageStats

        val useCase = SearchFoodUseCase(foodRepository, logRepository)
        useCase("cheese", 7).test {
            // Identical names tie on every text signal, so usage breaks the tie.
            assertEquals(listOf(2L, 1L), awaitItem().map { it.id })

            // The same query must re-emit re-ranked results when usage changes.
            usageStats.value = listOf(stats(1, sectionCount = 9, overallCount = 8))
            assertEquals(listOf(1L, 2L), awaitItem().map { it.id })
        }
    }

    @Test
    fun `one-argument invoke defaults to no section scope`() = runTest {
        val foods = listOf(food(1, "Cheese"))
        val usedSection = CapturingSlot<Long>()
        every { foodRepository.searchFts(any()) } returns flowOf(foods)
        every { foodRepository.searchFtsFuzzy(any()) } returns flowOf(emptyList())
        every { logRepository.getFoodUsageStats(capture(usedSection), any()) } returns flowOf(emptyList())

        val useCase = SearchFoodUseCase(foodRepository, logRepository)
        useCase("cheese").test {
            awaitItem()
            awaitComplete()
        }
        assertEquals(0L, usedSection.captured)
    }

    @Test
    fun `one and two character queries still emit exact candidates when fuzzy yields nothing`() = runTest {
        // Real repository behavior contract: for queries with no token >= 3 chars,
        // FuzzyQueryFormatter.format returns null and the fuzzy path contributes a
        // finite empty list (not an empty flow), so combine still emits exact results.
        val dao = mockk<FoodItemDao>()
        every { dao.searchFoods(any()) } answers {
            when (firstArg<String>()) {
                "\"a\"*" -> flowOf(listOf(entity(1, "Avocado"), entity(2, "Abalone")))
                "\"ab\"*" -> flowOf(listOf(entity(2, "Abalone")))
                else -> flowOf(emptyList())
            }
        }
        every { dao.searchFoodsFuzzy(any()) } returns flowOf(emptyList())
        every { logRepository.getFoodUsageStats(any(), any()) } returns flowOf(emptyList())

        val useCase = SearchFoodUseCase(FoodRepositoryImpl(dao), logRepository)
        useCase("a").test {
            assertEquals(listOf(1L, 2L), awaitItem().map { it.id })
            awaitComplete()
        }
        useCase("ab").test {
            assertEquals(listOf(2L), awaitItem().map { it.id })
            awaitComplete()
        }
    }
}
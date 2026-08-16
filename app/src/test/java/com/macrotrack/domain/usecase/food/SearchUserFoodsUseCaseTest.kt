package com.macrotrack.domain.usecase.food

import app.cash.turbine.test
import com.macrotrack.data.local.db.dao.FoodItemDao
import com.macrotrack.data.local.db.entity.FoodItemEntity
import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.FoodRepositoryImpl
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Source
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchUserFoodsUseCaseTest {

    private val foodRepository = mockk<FoodRepository>()

    private fun food(id: Long, name: String) = FoodItem(
        id = id, source = Source.USER, name = name,
        macroPer100g = Macros(100f, 10f, 10f, 5f)
    )

    private fun entity(id: Long, name: String) = FoodItemEntity(
        id = id,
        source = "USER",
        name = name,
        kcalPer100g = 100f,
        proteinPer100g = 10f,
        carbsPer100g = 10f,
        fatPer100g = 5f,
    )

    @Test
    fun `blank query returns all user foods`() = runTest {
        val foods = listOf(food(1, "Chicken Breast"), food(2, "Oatmeal"))
        every { foodRepository.getAllUserFoods() } returns flowOf(foods)

        val useCase = SearchUserFoodsUseCase(foodRepository)
        useCase("   ").test {
            assertEquals(foods, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `typed query uses the shared fts prefix formatter`() = runTest {
        val foods = listOf(food(1, "Chicken Breast"))
        val ftsQuery = CapturingSlot<String>()
        every { foodRepository.searchUserFoods(capture(ftsQuery)) } returns flowOf(foods)
        every { foodRepository.searchUserFoodsFuzzy(any()) } returns flowOf(emptyList())

        val useCase = SearchUserFoodsUseCase(foodRepository)
        useCase("chick").test {
            assertEquals("\"chick\"*", ftsQuery.captured)
            assertEquals(foods, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `fuzzy candidates are merged behind exact matches`() = runTest {
        val exact = food(1, "Chicken Breast")
        val fuzzy = food(2, "Chickn Strips")

        every { foodRepository.searchUserFoods(any()) } returns flowOf(listOf(exact))
        every { foodRepository.searchUserFoodsFuzzy(any()) } returns flowOf(listOf(fuzzy))

        val useCase = SearchUserFoodsUseCase(foodRepository)
        useCase("chicken").test {
            assertEquals(listOf(1L, 2L), awaitItem().map { it.id })
            awaitComplete()
        }
    }

    @Test
    fun `one and two character queries still emit exact candidates when fuzzy yields nothing`() = runTest {
        // Real repository behavior contract: for queries with no token >= 3 chars,
        // FuzzyQueryFormatter.format returns null and the fuzzy path contributes a
        // finite empty list (not an empty flow), so combine still emits exact results.
        val dao = mockk<FoodItemDao>()
        every { dao.searchUserFoods(any()) } answers {
            when (firstArg<String>()) {
                "\"a\"*" -> flowOf(listOf(entity(1, "Avocado"), entity(2, "Abalone")))
                "\"ab\"*" -> flowOf(listOf(entity(2, "Abalone")))
                else -> flowOf(emptyList())
            }
        }
        every { dao.searchUserFoodsFuzzy(any()) } returns flowOf(emptyList())

        val useCase = SearchUserFoodsUseCase(FoodRepositoryImpl(dao))
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
package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.UserFoodRepository
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Source
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LookupBarcodeUseCaseTest {

    private val foodRepository = mockk<FoodRepository>()
    private val userFoodRepository = mockk<UserFoodRepository>()
    private val useCase = LookupBarcodeUseCase(foodRepository, userFoodRepository)

    private fun food(id: Long, source: Source) = FoodItem(
        id = id, source = source, name = "Food $id",
        macroPer100g = Macros(100f, 10f, 10f, 5f)
    )

    @Test
    fun `prefers prebuilt database over user foods`() = runTest {
        val prebuilt = food(1, Source.OPEN_FOOD_FACTS)
        coEvery { foodRepository.getFoodByEan("123") } returns prebuilt
        coEvery { userFoodRepository.getByEan("123") } returns food(2, Source.USER)

        assertEquals(prebuilt, useCase("123"))
    }

    @Test
    fun `falls back to user foods when not in prebuilt db`() = runTest {
        val userFood = food(2, Source.USER)
        coEvery { foodRepository.getFoodByEan("123") } returns null
        coEvery { userFoodRepository.getByEan("123") } returns userFood

        assertEquals(userFood, useCase("123"))
    }

    @Test
    fun `returns null when barcode unknown`() = runTest {
        coEvery { foodRepository.getFoodByEan("999") } returns null
        coEvery { userFoodRepository.getByEan("999") } returns null

        assertNull(useCase("999"))
    }

    @Test
    fun `returns null for blank ean`() = runTest {
        assertNull(useCase("  "))
    }
}

package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
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
    private val useCase = LookupBarcodeUseCase(foodRepository)

    private fun food(id: Long, source: Source) = FoodItem(
        id = id, source = source, name = "Food $id",
        macroPer100g = Macros(100f, 10f, 10f, 5f)
    )

    @Test
    fun `finds food by ean`() = runTest {
        val food = food(1, Source.OPEN_FOOD_FACTS)
        coEvery { foodRepository.getFoodByEan("123") } returns food

        assertEquals(food, useCase("123"))
    }

    @Test
    fun `returns null when barcode unknown`() = runTest {
        coEvery { foodRepository.getFoodByEan("999") } returns null

        assertNull(useCase("999"))
    }

    @Test
    fun `returns null for blank ean`() = runTest {
        assertNull(useCase("  "))
    }
}

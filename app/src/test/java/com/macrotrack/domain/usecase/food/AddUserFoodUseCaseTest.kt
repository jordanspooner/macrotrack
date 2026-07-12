package com.macrotrack.domain.usecase.food

import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.UserFoodRepository
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Source
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AddUserFoodUseCaseTest {

    private val userFoodRepository = mockk<UserFoodRepository>()
    private val useCase = AddUserFoodUseCase(userFoodRepository)

    @Test
    fun `persists food and returns it with assigned id`() = runTest {
        val input = FoodItem(
            id = 0, source = Source.USER, name = "Homemade curry",
            macroPer100g = Macros(150f, 10f, 12f, 5f)
        )
        val saved = input.copy(id = 42)
        coEvery { userFoodRepository.insert(input) } returns saved

        val result = useCase(input)
        assertEquals(42, result.id)
        assertEquals(Source.USER, result.source)
        coVerify(exactly = 1) { userFoodRepository.insert(input) }
    }
}

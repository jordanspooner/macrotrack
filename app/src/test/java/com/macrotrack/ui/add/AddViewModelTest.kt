package com.macrotrack.ui.add

import androidx.lifecycle.SavedStateHandle
import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.SettingsRepository
import com.macrotrack.domain.usecase.food.AddUserFoodUseCase
import com.macrotrack.domain.usecase.food.GetRecommendationsUseCase
import com.macrotrack.domain.usecase.food.LookupBarcodeUseCase
import com.macrotrack.domain.usecase.food.SearchFoodUseCase
import com.macrotrack.domain.usecase.log.AddLogEntryUseCase
import com.macrotrack.domain.usecase.settings.GetSectionsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AddViewModelTest {

    private val mainDispatcher: TestDispatcher = StandardTestDispatcher()

    private val getSectionsUseCase = mockk<GetSectionsUseCase>()
    private val searchFoodUseCase = mockk<SearchFoodUseCase>()
    private val getRecommendationsUseCase = mockk<GetRecommendationsUseCase>()
    private val addLogEntryUseCase = mockk<AddLogEntryUseCase>(relaxed = true)
    private val lookupBarcodeUseCase = mockk<LookupBarcodeUseCase>(relaxed = true)
    private val addUserFoodUseCase = mockk<AddUserFoodUseCase>(relaxed = true)
    private val foodRepository = mockk<FoodRepository>()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { getSectionsUseCase() } returns flowOf(emptyList())
        every { foodRepository.observeCount() } returns flowOf(0)
        every { settingsRepository.getLastPortions() } returns flowOf(emptyMap())
        every { searchFoodUseCase(any()) } returns flowOf(emptyList())
        coEvery { getRecommendationsUseCase.getRecommendations(any(), any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AddViewModel(
        savedStateHandle = SavedStateHandle(),
        getSectionsUseCase = getSectionsUseCase,
        searchFoodUseCase = searchFoodUseCase,
        getRecommendationsUseCase = getRecommendationsUseCase,
        addLogEntryUseCase = addLogEntryUseCase,
        lookupBarcodeUseCase = lookupBarcodeUseCase,
        addUserFoodUseCase = addUserFoodUseCase,
        foodRepository = foodRepository,
        settingsRepository = settingsRepository,
    )

    @Test
    fun `no food data keeps results empty and never searches`() = runTest(mainDispatcher) {
        val viewModel = viewModel()
        val states = mutableListOf<AddUiState>()
        val collectJob = launch { viewModel.uiState.collect { states += it } }
        advanceUntilIdle()

        viewModel.onQueryChanged("chicken")
        advanceUntilIdle()

        assertTrue(states.any { !it.hasFoodData })
        assertEquals(emptyList<com.macrotrack.domain.model.FoodItem>(), states.last().results)
        verify(exactly = 0) { searchFoodUseCase(any()) }
        coVerify(exactly = 0) { getRecommendationsUseCase.getRecommendations(any(), any()) }
        collectJob.cancel()
    }

    @Test
    fun `blank query with food data asks for recommendations`() = runTest(mainDispatcher) {
        every { foodRepository.observeCount() } returns flowOf(1)

        val viewModel = viewModel()
        val states = mutableListOf<AddUiState>()
        val collectJob = launch { viewModel.uiState.collect { states += it } }
        advanceUntilIdle()

        assertTrue(states.any { it.hasFoodData })
        coVerify(exactly = 1) { getRecommendationsUseCase.getRecommendations(any(), any()) }
        verify(exactly = 0) { searchFoodUseCase(any()) }
        collectJob.cancel()
    }

    @Test
    fun `typed query with food data is passed to search use case`() = runTest(mainDispatcher) {
        every { foodRepository.observeCount() } returns flowOf(1)

        val viewModel = viewModel()
        val states = mutableListOf<AddUiState>()
        val collectJob = launch { viewModel.uiState.collect { states += it } }
        advanceUntilIdle()

        viewModel.onQueryChanged("apple")
        advanceUntilIdle()

        assertEquals("apple", states.last().query)
        verify(exactly = 1) { searchFoodUseCase("apple") }
        coVerify(exactly = 1) { getRecommendationsUseCase.getRecommendations(any(), any()) }
        collectJob.cancel()
    }
}
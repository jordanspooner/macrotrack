package com.macrotrack.ui.editfood

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Source
import com.macrotrack.domain.usecase.food.AddUserFoodUseCase
import com.macrotrack.domain.usecase.food.DeleteUserFoodUseCase
import com.macrotrack.domain.usecase.food.UpdateUserFoodUseCase
import com.macrotrack.domain.validation.NutritionValidator
import com.macrotrack.ui.add.QuickAddDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditFoodViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val foodRepository: FoodRepository,
    private val updateUserFoodUseCase: UpdateUserFoodUseCase,
    private val addUserFoodUseCase: AddUserFoodUseCase,
    private val deleteUserFoodUseCase: DeleteUserFoodUseCase,
) : ViewModel() {

    private val foodId: Long = savedStateHandle.get<String>("foodId")?.toLongOrNull() ?: 0L
    val isNew: Boolean get() = foodId == 0L

    private val _uiState = MutableStateFlow(EditFoodUiState())

    val uiState: StateFlow<EditFoodUiState> = _uiState

    init {
        if (foodId > 0) {
            loadFood()
        }
    }

    private fun loadFood() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val food = foodRepository.getFoodById(foodId)
            if (food != null) {
                _uiState.value = _uiState.value.copy(
                    food = food,
                    draft = food.toDraft(),
                    isLoading = false,
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "This food item may have been deleted.")
            }
        }
    }

    fun updateDraft(draft: QuickAddDraft) {
        val kcal = draft.kcal.toFloatOrNull()
        val protein = draft.protein.toFloatOrNull()
        val carbs = draft.carbs.toFloatOrNull()
        val fat = draft.fat.toFloatOrNull()
        val hasAnyMacro = listOf(kcal, protein, carbs, fat).any { it != null }
        val nameError = draft.name.isBlank()
        val macros = Macros(kcal ?: 0f, protein ?: 0f, carbs ?: 0f, fat ?: 0f)
        val inconsistent = hasAnyMacro && !NutritionValidator.areMacrosConsistent(macros)
        val draftValid = !nameError && hasAnyMacro && !inconsistent
        val consistencyWarning = if (inconsistent) {
            "Macros don't add up to the stated kcal (~${macros.computedKcal.toInt()} kcal from macros)."
        } else null

        _uiState.value = _uiState.value.copy(
            draft = draft,
            hasChanges = true,
            submitEnabled = draftValid,
            consistencyWarning = consistencyWarning,
        )
    }

    fun save() {
        val draft = _uiState.value.draft
        val food = FoodItem(
            id = if (isNew) 0 else foodId,
            source = Source.USER,
            sourceId = null,
            dataSourceId = "my-foods",
            ean = null,
            brand = draft.brand.takeIf { it.isNotBlank() },
            name = draft.name,
            defaultPortionG = draft.portionG.toFloatOrNull(),
            defaultPortionLabel = draft.portionLabel.takeIf { it.isNotBlank() },
            macroPer100g = Macros(
                kcal = draft.kcal.toFloatOrNull() ?: 0f,
                proteinG = draft.protein.toFloatOrNull() ?: 0f,
                carbsG = draft.carbs.toFloatOrNull() ?: 0f,
                fatG = draft.fat.toFloatOrNull() ?: 0f,
            )
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            if (isNew) {
                addUserFoodUseCase(food)
            } else {
                updateUserFoodUseCase(food)
            }
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                isDone = true,
                message = "Saved",
            )
        }
    }

    fun delete() {
        viewModelScope.launch {
            deleteUserFoodUseCase(foodId)
            _uiState.value = _uiState.value.copy(isDone = true)
        }
    }
}

private fun FoodItem.toDraft(): QuickAddDraft = QuickAddDraft(
    name = name,
    brand = brand ?: "",
    portionLabel = defaultPortionLabel ?: "",
    portionG = defaultPortionG?.let {
        if (it == it.toInt().toFloat()) it.toInt().toString() else "%.1f".format(it)
    } ?: "",
    kcal = macroPer100g.kcal.takeIf { it > 0 }?.let {
        if (it == it.toInt().toFloat()) it.toInt().toString() else "%.1f".format(it)
    } ?: "",
    protein = macroPer100g.proteinG.takeIf { it > 0 }?.let { "%.1f".format(it) } ?: "",
    carbs = macroPer100g.carbsG.takeIf { it > 0 }?.let { "%.1f".format(it) } ?: "",
    fat = macroPer100g.fatG.takeIf { it > 0 }?.let { "%.1f".format(it) } ?: "",
)

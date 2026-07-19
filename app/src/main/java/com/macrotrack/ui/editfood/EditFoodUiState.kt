package com.macrotrack.ui.editfood

import com.macrotrack.domain.model.FoodItem
import com.macrotrack.ui.add.QuickAddDraft

data class EditFoodUiState(
    val food: FoodItem? = null,
    val error: String? = null,
    val draft: QuickAddDraft = QuickAddDraft(),
    val hasChanges: Boolean = false,
    val submitEnabled: Boolean = true,
    val consistencyWarning: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDone: Boolean = false,
    val message: String? = null,
)

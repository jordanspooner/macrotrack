package com.macrotrack.ui.add

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.validation.NutritionValidator
import com.macrotrack.ui.myfoods.FoodItemEditorForm

@Composable
fun QuickAddContent(
    uiState: AddUiState,
    onDraftChanged: (QuickAddDraft) -> Unit,
    onSubmit: () -> Unit
) {
    val draft = uiState.quickAddDraft
    val kcal = draft.kcal.toFloatOrNull()
    val protein = draft.protein.toFloatOrNull()
    val carbs = draft.carbs.toFloatOrNull()
    val fat = draft.fat.toFloatOrNull()

    val nameError = draft.name.isBlank()
    val hasAnyMacro = listOf(kcal, protein, carbs, fat).any { it != null }
    val macros = Macros(kcal ?: 0f, protein ?: 0f, carbs ?: 0f, fat ?: 0f)
    val inconsistent = hasAnyMacro && !NutritionValidator.areMacrosConsistent(macros)
    val draftValid = !nameError && hasAnyMacro && !inconsistent

    val consistencyWarning = if (inconsistent) {
        "Macros don't add up to the stated kcal (~${macros.computedKcal.toInt()} kcal from macros)."
    } else if (!hasAnyMacro) {
        "Enter at least one nutrition value (kcal or a macro)."
    } else null

    FoodItemEditorForm(
        title = "Quick add",
        draft = draft,
        onDraftChanged = onDraftChanged,
        onSubmit = onSubmit,
        submitEnabled = draftValid,
        consistencyWarning = consistencyWarning,
        modifier = Modifier.fillMaxSize()
    )
}

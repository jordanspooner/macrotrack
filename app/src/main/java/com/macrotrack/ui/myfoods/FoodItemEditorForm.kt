package com.macrotrack.ui.myfoods

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.macrotrack.ui.add.QuickAddDraft
import com.macrotrack.ui.components.MacroDot
import com.macrotrack.ui.components.SaveButton
import com.macrotrack.ui.theme.MacroTrackShapes
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandPrimary
import com.macrotrack.ui.theme.macroCaloriesColor
import com.macrotrack.ui.theme.macroCarbsColor
import com.macrotrack.ui.theme.macroFatColor
import com.macrotrack.ui.theme.macroProteinColor

@Composable
fun FoodItemEditorForm(
    title: String,
    draft: QuickAddDraft,
    onDraftChanged: (QuickAddDraft) -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean,
    consistencyWarning: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MacroTrackShapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraftChanged(draft.copy(name = it)) },
                    label = { Text("Name") },
                    leadingIcon = { MacroDot(brandPrimary()) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.brand,
                    onValueChange = { onDraftChanged(draft.copy(brand = it)) },
                    label = { Text("Brand (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MacroTrackShapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    OutlinedTextField(
                        value = draft.portionG,
                        onValueChange = { onDraftChanged(draft.copy(portionG = it.filter { c -> c.isDigit() || c == '.' })) },
                        label = { Text("Portion g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draft.portionLabel,
                        onValueChange = { onDraftChanged(draft.copy(portionLabel = it)) },
                        label = { Text("Portion label") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            keyboardType = KeyboardType.Text,
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MacroTrackShapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text("Nutrition per 100g", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    OutlinedTextField(
                        value = draft.kcal,
                        onValueChange = { onDraftChanged(draft.copy(kcal = it.filter { c -> c.isDigit() || c == '.' })) },
                        label = { Text("kcal") },
                        leadingIcon = { MacroDot(macroCaloriesColor()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draft.protein,
                        onValueChange = { onDraftChanged(draft.copy(protein = it.filter { c -> c.isDigit() || c == '.' })) },
                        label = { Text("Protein g") },
                        leadingIcon = { MacroDot(macroProteinColor()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    OutlinedTextField(
                        value = draft.carbs,
                        onValueChange = { onDraftChanged(draft.copy(carbs = it.filter { c -> c.isDigit() || c == '.' })) },
                        label = { Text("Carbs g") },
                        leadingIcon = { MacroDot(macroCarbsColor()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draft.fat,
                        onValueChange = { onDraftChanged(draft.copy(fat = it.filter { c -> c.isDigit() || c == '.' })) },
                        label = { Text("Fat g") },
                        leadingIcon = { MacroDot(macroFatColor()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (consistencyWarning != null) {
                    Text(
                        consistencyWarning,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        SaveButton(
            hasChanges = submitEnabled,
            label = "Save",
            onClick = onSubmit
        )
    }
}

package com.macrotrack.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.ui.components.MacroDonut
import com.macrotrack.ui.components.SaveButton
import com.macrotrack.ui.theme.MacroTrackPillShape
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandOnPrimary
import com.macrotrack.ui.theme.brandPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortionSizeScreen(
    food: FoodItem,
    sectionName: String,
    onConfirm: (portionG: Float, portionLabel: String?) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(food.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        PortionSizeContent(
            food = food,
            confirmLabel = "Add to $sectionName · ",
            initialPortionG = null,
            initialPortionLabel = null,
            onConfirm = onConfirm,
            modifier = Modifier.padding(padding),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortionSizeContent(
    food: FoodItem,
    confirmLabel: String,
    initialPortionG: Float?,
    initialPortionLabel: String?,
    onConfirm: (portionG: Float, portionLabel: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultPortionG = initialPortionG ?: food.defaultPortionG ?: 100f
    val defaultLabel = initialPortionLabel ?: food.defaultPortionLabel

    var portionG by remember { mutableFloatStateOf(defaultPortionG) }
    var selectedMult by remember { mutableStateOf<Float?>(if (initialPortionG == null) 1f else null) }

    val portioned = food.macroPer100g * (portionG / 100f)

    val servingInfo = buildString {
        defaultLabel?.let { append(it).append(" (") }
        append("${if (defaultPortionG % 1 == 0f) defaultPortionG.toInt() else defaultPortionG}g")
        defaultLabel?.let { append(")") }
    }

    val presets = listOf(
        0.25f to "1/4",
        0.5f to "1/2",
        1f to "1",
        1.5f to "1.5",
        2f to "2",
        3f to "3"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MacroDonut(
            macros = portioned,
            diameter = 180.dp,
            centerText = "${portioned.kcal.toInt()} kcal"
        )

        Text(
            "P ${portioned.proteinG.toInt()}g · C ${portioned.carbsG.toInt()}g · F ${portioned.fatG.toInt()}g",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            servingInfo,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                presets.forEach { (mult, label) ->
                    FilterChip(
                        selected = selectedMult == mult,
                        onClick = {
                            portionG = defaultPortionG * mult
                            selectedMult = mult
                        },
                        label = { Text(label) },
                        shape = MacroTrackPillShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = brandPrimary(),
                            selectedLabelColor = brandOnPrimary()
                        )
                    )
                }
            }
        }

        OutlinedTextField(
            value = if (portionG % 1 == 0f) portionG.toInt().toString() else portionG.toString(),
            onValueChange = {
                val v = it.filter { c -> c.isDigit() || c == '.' }.toFloatOrNull()
                if (v != null && v > 0f) {
                    portionG = v
                    selectedMult = null
                }
            },
            label = { Text("Custom amount (g)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        SaveButton(
            hasChanges = true,
            label = "$confirmLabel${portioned.kcal.toInt()} kcal",
            onClick = {
                val label = if (portionG == defaultPortionG) defaultLabel else null
                onConfirm(portionG, label)
            }
        )
    }
}

package com.macrotrack.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortionSizeScreen(
    food: FoodItem,
    onConfirm: (portionG: Float, portionLabel: String?) -> Unit,
    onBack: () -> Unit
) {
    val defaultPortionG = food.defaultPortionG ?: 100f
    val defaultLabel = food.defaultPortionLabel

    var portionG by remember { mutableFloatStateOf(defaultPortionG) }
    var customMultiple by remember { mutableStateOf("") }

    val portioned = food.macroPer100g * (portionG / 100f)

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                MacroPieChart(macros = portioned, diameter = 160.dp)
                Text(
                    "${portioned.kcal.toInt()} kcal",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                "P ${portioned.proteinG.toInt()}g · C ${portioned.carbsG.toInt()}g · F ${portioned.fatG.toInt()}g",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Exact grams
            OutlinedTextField(
                value = if (portionG % 1 == 0f) portionG.toInt().toString() else portionG.toString(),
                onValueChange = {
                    val v = it.filter { c -> c.isDigit() || c == '.' }.toFloatOrNull()
                    if (v != null && v > 0f) portionG = v
                },
                label = { Text("Portion size (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.6f)
            )

            // Fraction / multiplier presets
            val presets = listOf(0.25f to "1/4", 0.33f to "1/3", 0.5f to "1/2", 1f to "1", 2f to "2", 3f to "3", 4f to "4")
            presets.chunked(4).forEach { rowPresets ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowPresets.forEach { (mult, label) ->
                        Button(
                            onClick = {
                                portionG = defaultPortionG * mult
                                customMultiple = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            // Custom multiple
            OutlinedTextField(
                value = customMultiple,
                onValueChange = {
                    customMultiple = it.filter { c -> c.isDigit() || c == '.' }
                    val m = customMultiple.toFloatOrNull()
                    if (m != null && m > 0f) portionG = defaultPortionG * m
                },
                label = { Text("Custom multiple of ${defaultPortionG.toInt()}g") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.6f)
            )

            Button(
                onClick = {
                    val label = if (portionG == defaultPortionG) defaultLabel else null
                    onConfirm(portionG, label)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Text("Add to log")
            }
        }
    }
}

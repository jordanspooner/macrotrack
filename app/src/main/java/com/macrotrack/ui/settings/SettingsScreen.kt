package com.macrotrack.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
        ) {
            // ----- Block 1: Daily Goals -----
            item {
                Text(
                    text = "Daily Goals",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
            }

            item {
                OutlinedTextField(
                    value = uiState.draftGoals.proteinG.toString(),
                    onValueChange = { viewModel.updateDraftGoalProtein(it) },
                    label = { Text("Protein (g)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.draftGoals.carbsG.toString(),
                    onValueChange = { viewModel.updateDraftGoalCarbs(it) },
                    label = { Text("Carbs (g)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.draftGoals.fatG.toString(),
                    onValueChange = { viewModel.updateDraftGoalFat(it) },
                    label = { Text("Fat (g)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total kcal: ${uiState.draftGoals.kcal}")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Protein: ${uiState.draftGoals.proteinPercent.toInt()}% | " +
                                "Carbs: ${uiState.draftGoals.carbsPercent.toInt()}% | " +
                                "Fat: ${uiState.draftGoals.fatPercent.toInt()}%",
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { viewModel.saveGoals() }) {
                        Text("Save Goals")
                    }
                    if (uiState.goalsSaved) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Saved!",
                            color = Color.Green,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }

            // ----- Block 2: Meal Sections -----
            item {
                Text(
                    text = "Meal Sections",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
            }

            itemsIndexed(uiState.draftSections) { index, ds ->
                key(ds.id) {
                    val showTimePicker = remember { mutableStateOf(false) }
                    val timePickerState = rememberTimePickerState(
                        initialHour = ds.timeOfDay.hour,
                        initialMinute = ds.timeOfDay.minute,
                    )
                    if (showTimePicker.value) {
                        TimePickerDialog(
                            onDismiss = { showTimePicker.value = false },
                            onConfirm = { time -> viewModel.updateDraftSectionTime(index, time) },
                            state = timePickerState,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(
                            onClick = { viewModel.moveDraftSectionUp(index) },
                            enabled = index != 0,
                        ) {
                            Icon(Icons.Default.ArrowUpward, "Move up")
                        }
                        IconButton(
                            onClick = { viewModel.moveDraftSectionDown(index) },
                            enabled = index != uiState.draftSections.lastIndex,
                        ) {
                            Icon(Icons.Default.ArrowDownward, "Move down")
                        }
                        OutlinedTextField(
                            value = ds.name,
                            onValueChange = { viewModel.updateDraftSectionName(index, it) },
                            label = { Text("Name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        TextButton(
                            onClick = { showTimePicker.value = true },
                        ) {
                            Text(ds.timeOfDay.format(DateTimeFormatter.ofPattern("HH:mm")))
                        }
                        IconButton(
                            onClick = { viewModel.removeDraftSection(index) },
                        ) {
                            Icon(Icons.Default.Delete, "Delete section")
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.addDraftSection("New Section") }) {
                    Text("Add Section")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.resetSectionsToDefaults() }) {
                    Text("Reset to Defaults")
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { viewModel.saveSections() }) {
                        Text("Save Sections")
                    }
                    if (uiState.sectionsSaved) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Saved!",
                            color = Color.Green,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }

            // ----- Block 3: Section Distribution -----
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Distribute goals across sections",
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = uiState.sectionGoalsEnabled,
                        onCheckedChange = { viewModel.setSectionGoalsEnabled(it) },
                    )
                }
            }

            if (uiState.sectionGoalsEnabled) {
                val macroConfigs = listOf(
                    Triple(MacroType.PROTEIN, "Protein distribution", uiState.draftGoals.proteinG),
                    Triple(MacroType.CARBS, "Carbs distribution", uiState.draftGoals.carbsG),
                    Triple(MacroType.FAT, "Fat distribution", uiState.draftGoals.fatG),
                )
                items(macroConfigs) { (macroType, header, goalsG) ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = header,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    uiState.draftSections.forEach { ds ->
                        val percentage =
                            uiState.sectionDistribution[ds.id]?.get(macroType) ?: 0f
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = ds.name,
                                modifier = Modifier.width(96.dp),
                            )
                            Slider(
                                value = percentage,
                                onValueChange = {
                                    viewModel.updateDistribution(ds.id, macroType, it)
                                },
                                valueRange = 0f..100f,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${percentage.toInt()}%")
                            Spacer(Modifier.width(4.dp))
                            Text("${(percentage / 100 * goalsG).toInt()}g")
                        }
                    }
                    val totalPercent = uiState.draftSections.sumOf {
                        (uiState.sectionDistribution[it.id]?.get(macroType) ?: 0f).toDouble()
                    }.toFloat()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Total: ${totalPercent.toInt()}%",
                        color = if (totalPercent.toInt() == 100) Color.Green else Color.Red,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    state: TimePickerState,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(LocalTime.of(state.hour, state.minute))
                    onDismiss()
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = { TimePicker(state = state) },
    )
}

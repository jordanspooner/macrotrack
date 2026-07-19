package com.macrotrack.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotrack.ui.components.SaveButton
import com.macrotrack.ui.components.MacroDot
import com.macrotrack.ui.theme.MacroTrackPillShape
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandPrimary
import com.macrotrack.ui.theme.macroCaloriesColor
import com.macrotrack.ui.theme.macroCarbsColor
import com.macrotrack.ui.theme.macroFatColor
import com.macrotrack.ui.theme.macroProteinColor
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.goalsSaved) {
        if (uiState.goalsSaved) {
            snackbarHostState.showSnackbar("Goals saved")
            viewModel.onSnackbarShown()
        }
    }
    LaunchedEffect(uiState.sectionsSaved) {
        if (uiState.sectionsSaved) {
            snackbarHostState.showSnackbar("Sections saved")
            viewModel.onSnackbarShown()
        }
    }

    BackHandler(enabled = uiState.hasUnsavedChanges) { showDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.hasUnsavedChanges) showDiscard = true else onBack()
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        ) {
            // ----- Block 1: Daily Goals -----
            item {
                Text(
                    text = "Daily Goals",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        GoalField(
                            value = uiState.draftGoals.proteinG.toString(),
                            onValueChange = { viewModel.updateDraftGoalProtein(it) },
                            label = "Protein (g)",
                            dotColor = macroProteinColor(),
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        GoalField(
                            value = uiState.draftGoals.carbsG.toString(),
                            onValueChange = { viewModel.updateDraftGoalCarbs(it) },
                            label = "Carbs (g)",
                            dotColor = macroCarbsColor(),
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        GoalField(
                            value = uiState.draftGoals.fatG.toString(),
                            onValueChange = { viewModel.updateDraftGoalFat(it) },
                            label = "Fat (g)",
                            dotColor = macroFatColor(),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(4.dp),
                                    ),
                            ) {
                                Row(Modifier.fillMaxSize()) {
                                    Box(
                                        Modifier
                                            .weight(uiState.draftGoals.proteinPercent)
                                            .fillMaxHeight()
                                            .background(macroProteinColor()),
                                    )
                                    Box(
                                        Modifier
                                            .weight(uiState.draftGoals.carbsPercent)
                                            .fillMaxHeight()
                                            .background(macroCarbsColor()),
                                    )
                                    Box(
                                        Modifier
                                            .weight(uiState.draftGoals.fatPercent)
                                            .fillMaxHeight()
                                            .background(macroFatColor()),
                                    )
                                }
                            }
                            Spacer(Modifier.width(Spacing.md))
                            Text(
                                text = "${uiState.draftGoals.kcal} kcal",
                                style = MaterialTheme.typography.titleLarge,
                                color = macroCaloriesColor(),
                            )
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "P ${uiState.draftGoals.proteinPercent.toInt()}%",
                                color = macroProteinColor(),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = "  ·  C ${uiState.draftGoals.carbsPercent.toInt()}%",
                                color = macroCarbsColor(),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = "  ·  F ${uiState.draftGoals.fatPercent.toInt()}%",
                                color = macroFatColor(),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                SaveButton(
                    hasChanges = uiState.hasUnsavedChanges,
                    label = "Save goals",
                    onClick = { viewModel.saveGoals() },
                )
            }

            item {
                Spacer(Modifier.height(Spacing.lg))
                HorizontalDivider()
                Spacer(Modifier.height(Spacing.lg))
            }

            // ----- Block 2: Meal Sections -----
            item {
                Text(
                    text = "Meal Sections",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
            }

            itemsIndexed(uiState.draftSections) { index, ds ->
                key(ds.id) {
                    val showTimePicker = remember { mutableStateOf(false) }
                    val showDelete = remember { mutableStateOf(false) }
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
                    if (showDelete.value) {
                        AlertDialog(
                            onDismissRequest = { showDelete.value = false },
                            title = { Text("Delete section?") },
                            text = { Text("Delete '${ds.name}'? Its logged meals move to the section above.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showDelete.value = false
                                        viewModel.removeDraftSection(index)
                                    },
                                ) { Text("Delete") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDelete.value = false }) { Text("Cancel") }
                            },
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                    ) {
                        OutlinedTextField(
                            value = ds.name,
                            onValueChange = { viewModel.updateDraftSectionName(index, it) },
                            label = { Text("Name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Surface(
                            shape = MacroTrackPillShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clickable { showTimePicker.value = true }
                                .padding(Spacing.xs),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(
                                    horizontal = Spacing.sm,
                                    vertical = Spacing.xs,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(Spacing.xs))
                                Text(
                                    text = ds.timeOfDay.format(DateTimeFormatter.ofPattern("HH:mm")),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { showDelete.value = true }) {
                            Icon(Icons.Filled.Delete, "Delete section")
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(Spacing.md))
                OutlinedButton(
                    onClick = { viewModel.addDraftSection("New Section") },
                    shape = MacroTrackPillShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add section")
                }
                Spacer(Modifier.height(Spacing.sm))
                val showReset = remember { mutableStateOf(false) }
                if (showReset.value) {
                    AlertDialog(
                        onDismissRequest = { showReset.value = false },
                        title = { Text("Reset to defaults?") },
                        text = { Text("This replaces your sections with the default set.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showReset.value = false
                                    viewModel.resetSectionsToDefaults()
                                },
                            ) { Text("Reset") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showReset.value = false }) { Text("Cancel") }
                        },
                    )
                }
                TextButton(onClick = { showReset.value = true }) {
                    Text("Reset to defaults")
                }
                Spacer(Modifier.height(Spacing.md))
                SaveButton(
                    hasChanges = uiState.hasUnsavedChanges,
                    label = "Save sections",
                    onClick = { viewModel.saveSections() },
                )
            }

            item {
                Spacer(Modifier.height(Spacing.lg))
                HorizontalDivider()
                Spacer(Modifier.height(Spacing.lg))
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
                    Triple(MacroType.PROTEIN, "Protein", uiState.draftGoals.proteinG),
                    Triple(MacroType.CARBS, "Carbs", uiState.draftGoals.carbsG),
                    Triple(MacroType.FAT, "Fat", uiState.draftGoals.fatG),
                )
                items(macroConfigs) { (macroType, name, goalsG) ->
                    val macroColor = when (macroType) {
                        MacroType.PROTEIN -> macroProteinColor()
                        MacroType.CARBS -> macroCarbsColor()
                        MacroType.FAT -> macroFatColor()
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MacroDot(macroColor)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "$goalsG g",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(4.dp),
                            ),
                    ) {
                        Row(Modifier.fillMaxSize()) {
                            uiState.draftSections.forEachIndexed { i, section ->
                                val pct = uiState.sectionDistribution[section.id]?.get(macroType) ?: 0f
                                if (pct > 0f) {
                                    Box(
                                        Modifier
                                            .weight(pct)
                                            .fillMaxHeight()
                                            .background(
                                                macroColor.copy(
                                                    alpha = (1f - i * 0.18f).coerceIn(0.2f, 1f),
                                                ),
                                            ),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    uiState.draftSections.forEach { section ->
                        val percentage = uiState.sectionDistribution[section.id]?.get(macroType) ?: 0f
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = section.name,
                                modifier = Modifier.width(96.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Slider(
                                value = percentage,
                                onValueChange = { viewModel.updateDistribution(section.id, macroType, it) },
                                valueRange = 0f..100f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = macroColor,
                                    activeTrackColor = macroColor,
                                    inactiveTrackColor = macroColor.copy(alpha = 0.24f),
                                ),
                            )
                            Text("${percentage.toInt()}%")
                            Spacer(Modifier.width(Spacing.xs))
                            Text("${(percentage / 100 * goalsG).toInt()}g")
                        }
                    }
                    val totalPercent = uiState.draftSections.sumOf {
                        (uiState.sectionDistribution[it.id]?.get(macroType) ?: 0f).toDouble()
                    }.toFloat()
                    if (totalPercent.toInt() != 100) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = "Total: ${totalPercent.toInt()}%",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            text = "Adjust so totals equal 100%.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            } else {
                item {
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = "Goals apply per section (manual logging recommended)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Discard them and leave?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.discardChanges()
                        showDiscard = false
                        onBack()
                    },
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GoalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    dotColor: Color,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        leadingIcon = { MacroDot(dotColor) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
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

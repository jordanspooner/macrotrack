package com.macrotrack.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotrack.ui.components.CollapsibleSectionCard
import com.macrotrack.ui.components.MacroDot
import com.macrotrack.ui.components.SaveButton
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandPrimary
import com.macrotrack.ui.theme.macroCaloriesColor
import com.macrotrack.ui.theme.macroCarbsColor
import com.macrotrack.ui.theme.macroFatColor
import com.macrotrack.ui.theme.macroProteinColor
import kotlin.math.roundToInt
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
                .padding(paddingValues),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.lg,
                top = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.xxxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // ===== Block 1: Daily Goals =====
            item {
                CollapsibleSectionCard("Daily Goals") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                            Spacer(Modifier.height(Spacing.md))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)),
                                ) {
                                    Row(Modifier.fillMaxSize()) {
                                        Box(Modifier.weight(uiState.draftGoals.proteinPercent).fillMaxHeight().background(macroProteinColor()))
                                        Box(Modifier.weight(uiState.draftGoals.carbsPercent).fillMaxHeight().background(macroCarbsColor()))
                                        Box(Modifier.weight(uiState.draftGoals.fatPercent).fillMaxHeight().background(macroFatColor()))
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
                                Text("P ${uiState.draftGoals.proteinPercent.toInt()}%", color = macroProteinColor(), style = MaterialTheme.typography.labelMedium)
                                Text("  ·  C ${uiState.draftGoals.carbsPercent.toInt()}%", color = macroCarbsColor(), style = MaterialTheme.typography.labelMedium)
                                Text("  ·  F ${uiState.draftGoals.fatPercent.toInt()}%", color = macroFatColor(), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    SaveButton(
                        hasChanges = uiState.hasUnsavedChanges,
                        label = "Save goals",
                        onClick = { viewModel.saveGoals() },
                    )
                }
            }

            // ===== Block 2: Meal Sections =====
            item {
                CollapsibleSectionCard("Meal Sections") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        for (ds in uiState.sortedDraftSections) {
                            key(ds.id) {
                                SectionCard(
                                    draftSection = ds,
                                    onNameChange = { viewModel.updateDraftSectionName(ds.id, it) },
                                    onTimeChange = { viewModel.updateDraftSectionTime(ds.id, it) },
                                    onRemove = { viewModel.removeDraftSection(ds.id) },
                                )
                            }
                        }

                        Spacer(Modifier.height(Spacing.sm))
                        OutlinedButton(
                            onClick = { viewModel.addDraftSection("New Section") },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Add section")
                        }
                        SaveButton(
                            hasChanges = uiState.hasUnsavedChanges,
                            label = "Save sections",
                            onClick = { viewModel.saveSections() },
                        )
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
                                    ) { Text("Reset", color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showReset.value = false }) { Text("Cancel") }
                                },
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(
                                onClick = { showReset.value = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                ),
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(Spacing.xs))
                                Text("Reset to defaults")
                            }
                        }
                    }
                }
            }

            // ===== Block 3: Distribution =====
            item {
                CollapsibleSectionCard("Macro Distribution") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(Spacing.lg)) {
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
                            if (!uiState.sectionGoalsEnabled) {
                                Spacer(Modifier.height(Spacing.sm))
                                Text(
                                    text = "Goals apply per section (manual logging recommended)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (uiState.sectionGoalsEnabled) {
                        val macroConfigs = listOf(
                            Triple(MacroType.PROTEIN, "Protein", uiState.draftGoals.proteinG),
                            Triple(MacroType.CARBS, "Carbs", uiState.draftGoals.carbsG),
                            Triple(MacroType.FAT, "Fat", uiState.draftGoals.fatG),
                        )
                        Spacer(Modifier.height(Spacing.md))
                        for (mc in macroConfigs) {
                            val (macroType, name, goalsG) = mc
                            val macroColor = when (macroType) {
                                MacroType.PROTEIN -> macroProteinColor()
                                MacroType.CARBS -> macroCarbsColor()
                                MacroType.FAT -> macroFatColor()
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(Spacing.lg)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        MacroDot(macroColor)
                                        Spacer(Modifier.width(Spacing.sm))
                                        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        Text("$goalsG g", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.height(Spacing.sm))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)),
                                    ) {
                                        Row(Modifier.fillMaxSize()) {
                                            uiState.sortedDraftSections.forEachIndexed { i, section ->
                                                val pct = uiState.sectionDistribution[section.id]?.get(macroType) ?: 0f
                                                if (pct > 0f) {
                                                    Box(
                                                        Modifier
                                                            .weight(pct)
                                                            .fillMaxHeight()
                                                            .background(macroColor.copy(alpha = (1f - i * 0.18f).coerceIn(0.2f, 1f))),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(Spacing.sm))
                                    for (section in uiState.sortedDraftSections) {
                                        val percentage = uiState.sectionDistribution[section.id]?.get(macroType) ?: 0f
                                        val othersTotal = uiState.sortedDraftSections.filter { it.id != section.id }
                                            .sumOf { (uiState.sectionDistribution[it.id]?.get(macroType) ?: 0f).toDouble() }
                                            .toFloat()
                                        val maxForThis = (100f - othersTotal).coerceAtLeast(0f)
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = section.name,
                                                modifier = Modifier.width(96.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                            Slider(
                                                value = percentage.coerceIn(0f, maxForThis),
                                                onValueChange = { viewModel.updateDistribution(section.id, macroType, it) },
                                                valueRange = 0f..maxForThis,
                                                modifier = Modifier.weight(1f),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = macroColor,
                                                    activeTrackColor = macroColor,
                                                    inactiveTrackColor = macroColor.copy(alpha = 0.24f),
                                                ),
                                            )
                                            Text("${percentage.roundToInt()}%", modifier = Modifier.width(40.dp))
                                            Spacer(Modifier.width(Spacing.xs))
                                            Text("${((percentage / 100f) * goalsG).roundToInt()}g")
                                        }
                                    }
                                    val totalPercent = uiState.sortedDraftSections.sumOf {
                                        (uiState.sectionDistribution[it.id]?.get(macroType) ?: 0f).toDouble()
                                    }.toFloat()
                                    val balanceColor = if (totalPercent.roundToInt() == 100) brandPrimary() else MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    val balanceMessage = when {
                                        totalPercent.roundToInt() == 100 -> "Balanced · 100%"
                                        totalPercent < 100f -> "Total: ${totalPercent.roundToInt()}% · adjust remaining ${(100 - totalPercent.roundToInt()).coerceAtLeast(0)}%"
                                        else -> "Total: ${totalPercent.roundToInt()}%"
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Box(Modifier.size(8.dp).background(balanceColor, CircleShape))
                                        Spacer(Modifier.width(Spacing.xs))
                                        Text(balanceMessage, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Spacer(Modifier.height(Spacing.md))
                        }
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionCard(
    draftSection: DraftSection,
    onNameChange: (String) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onRemove: () -> Unit,
) {
    val showTimePicker = remember { mutableStateOf(false) }
    val showDelete = remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = draftSection.timeOfDay.hour,
        initialMinute = draftSection.timeOfDay.minute,
    )

    if (showTimePicker.value) {
        TimePickerDialog(
            onDismiss = { showTimePicker.value = false },
            onConfirm = { time ->
                onTimeChange(time)
                showTimePicker.value = false
            },
            state = timePickerState,
        )
    }
    if (showDelete.value) {
        AlertDialog(
            onDismissRequest = { showDelete.value = false },
            title = { Text("Delete section?") },
            text = { Text("Delete '${draftSection.name}'? Its logged meals move to the section above.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete.value = false
                        onRemove()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete.value = false }) { Text("Cancel") }
            },
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
        ) {
            OutlinedTextField(
                value = draftSection.name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            AssistChip(
                onClick = { showTimePicker.value = true },
                label = {
                    Text(
                        text = draftSection.timeOfDay.format(DateTimeFormatter.ofPattern("HH:mm")),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
            IconButton(onClick = { showDelete.value = true }) {
                Icon(Icons.Default.Delete, "Delete section")
            }
        }
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

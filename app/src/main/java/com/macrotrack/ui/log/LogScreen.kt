package com.macrotrack.ui.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotrack.ui.components.*
import com.macrotrack.ui.settings.CalendarModal
import com.macrotrack.domain.model.Section
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogScreen(
    viewModel: LogViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToAddFood: (sectionId: Long, date: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val reseedMessage by viewModel.reseedMessage.collectAsState()
    var showDevMenu by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(reseedMessage) {
        reseedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.reseedMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MacroTrack") },
                actions = {
                    Box {
                        IconButton(onClick = { showDevMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showDevMenu,
                            onDismissRequest = { showDevMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rebuild food database") },
                                onClick = {
                                    showDevMenu = false
                                    viewModel.onReseedFoodDatabase()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    showDevMenu = false
                                    onNavigateToSettings()
                                }
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            when (val selectionMode = uiState.selectionMode) {
                is SelectionMode.Selecting -> {
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    ) {
                        SelectionBottomBar(
                            selectedCount = selectionMode.selectedIds.size,
                            onCopyClick = { viewModel.copySelectedEntries() },
                            onMoveClick = { viewModel.moveSelectedEntries() },
                            onDeleteClick = { viewModel.deleteSelectedEntries() },
                            onCloseClick = { viewModel.exitSelectionMode() }
                        )
                    }
                }
                is SelectionMode.ChoosingDestination -> {
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    ) {
                        DestinationPickerBar(
                            selectedDate = uiState.selectedDate,
                            onSelectDestination = { date -> viewModel.confirmCopyMove(date) },
                            onCancel = { viewModel.cancelChoosingDestination() },
                        )
                    }
                }
                SelectionMode.Off -> {
                    val dateIso = uiState.selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val defaultSectionId = defaultSectionId(uiState.sections.map { it.section })
                    AddFoodBottomBar(
                        onSearchClick = { onNavigateToAddFood(defaultSectionId, dateIso) },
                        onLabelScanClick = { onNavigateToAddFood(defaultSectionId, dateIso) },
                        onBarcodeScanClick = { onNavigateToAddFood(defaultSectionId, dateIso) },
                        onQuickAddClick = { onNavigateToAddFood(defaultSectionId, dateIso) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            // Loading indicator
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                WeekDateStrip(
                    weekDays = uiState.weekDates,
                    onDateSelected = { day -> viewModel.onDateSelected(day.date) },
                    onOpenCalendar = { showCalendar = true }
                )
            }

            item {
                MacroSummaryCard(summary = uiState.dailySummary)
            }

            uiState.sections.forEach { sectionWithEntries ->
                item {
                    SectionHeader(
                        name = sectionWithEntries.section.name,
                        totalMacros = sectionWithEntries.totalMacros,
                        isExpanded = sectionWithEntries.isExpanded,
                        onToggleExpand = { viewModel.toggleSectionExpanded(sectionWithEntries.section.id) }
                    )
                }

                if (sectionWithEntries.isExpanded) {
                    if (sectionWithEntries.entries.isEmpty()) {
                        item {
                            Text(
                                text = "No items logged",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        items(
                            items = sectionWithEntries.entries,
                            key = { it.id }
                        ) { entry ->
                            val isSelected = when (val mode = uiState.selectionMode) {
                                is SelectionMode.Selecting -> mode.selectedIds.contains(entry.id)
                                is SelectionMode.ChoosingDestination -> mode.selectedIds.contains(entry.id)
                                SelectionMode.Off -> false
                            }

                            FoodItemCard(
                                entry = entry,
                                isSelected = isSelected,
                                onClick = {
                                    if (uiState.selectionMode != SelectionMode.Off) {
                                        viewModel.toggleSelectionMode(entry.id)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleSelectionMode(entry.id)
                                }
                            )
                        }
                    }
                }
            }
            
            // Add a spacer at the bottom
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showCalendar) {
        CalendarModal(
            selectedDate = uiState.selectedDate,
            onDateSelected = { date ->
                viewModel.onDateSelected(date)
                showCalendar = false
            },
            onDismiss = { showCalendar = false },
        )
    }
}

/**
 * Picks the section closest to (but not after) the current time; if none qualify,
 * returns the latest section of the day. Falls back to the first section.
 */
private fun defaultSectionId(sections: List<Section>): Long {
    if (sections.isEmpty()) return 0L
    val now = LocalTime.now()
    val sorted = sections.sortedBy { it.timeOfDay }
    val past = sorted.filter { !it.timeOfDay.isAfter(now) }
    return (past.lastOrNull() ?: sorted.last()).id
}

@Composable
private fun DestinationPickerBar(
    selectedDate: LocalDate,
    onSelectDestination: (LocalDate) -> Unit,
    onCancel: () -> Unit,
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, "Cancel")
                }
                Text("Copy/Move to:", style = MaterialTheme.typography.titleMedium)
            }
            Row {
                val yesterday = LocalDate.now().minusDays(1)
                val today = LocalDate.now()
                val tomorrow = LocalDate.now().plusDays(1)
                TextButton(onClick = { onSelectDestination(yesterday) }) { Text("Yesterday") }
                TextButton(onClick = { onSelectDestination(today) }) { Text("Today") }
                TextButton(onClick = { onSelectDestination(tomorrow) }) { Text("Tomorrow") }
                if (selectedDate != yesterday && selectedDate != today && selectedDate != tomorrow) {
                    TextButton(onClick = { onSelectDestination(selectedDate) }) {
                        Text(selectedDate.format(DateTimeFormatter.ofPattern("MMM d")))
                    }
                }
            }
        }
    }
}

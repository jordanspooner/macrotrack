package com.macrotrack.ui.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotrack.ui.components.*
import com.macrotrack.ui.settings.CalendarModal
import com.macrotrack.ui.theme.MacroTrackPillShape
import com.macrotrack.ui.theme.MacroTrackShapes
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandOnPrimary
import com.macrotrack.ui.theme.brandPrimary
import com.macrotrack.ui.theme.restingSurfaceColor
import com.macrotrack.domain.model.Section
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogScreen(
    viewModel: LogViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToAddFood: (sectionId: Long, date: String, mode: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val reseedMessage by viewModel.reseedMessage.collectAsState()
    var showCalendar by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
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
                title = { Text("MacroTrack", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(24.dp)
                        )
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
                            onDeleteClick = { showDeleteConfirm = true },
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
                SelectionMode.Off -> {}
            }
        },
        floatingActionButton = {
            val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            AnimatedVisibility(
                visible = uiState.selectionMode == SelectionMode.Off && !imeVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                FloatingActionButton(
                    onClick = { showAddMenu = true },
                    containerColor = brandPrimary(),
                    contentColor = brandOnPrimary()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add food")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 88.dp)
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

                val isEmpty =
                    uiState.sections.isNotEmpty() && uiState.sections.all { it.entries.isEmpty() }

                if (isEmpty) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(Spacing.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = restingSurfaceColor(),
                                shape = MacroTrackShapes.large,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(Spacing.xxl)
                                ) {
                                    Surface(
                                        color = restingSurfaceColor(),
                                        shape = CircleShape,
                                        modifier = Modifier.size(72.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.RestaurantMenu,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(Spacing.md))
                                    Text(
                                        text = "Nothing logged for this day",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Text(
                                        text = "Tap the + button below to add a meal",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
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
                                    val sectionId = sectionWithEntries.section.id
                                    val dateIso = uiState.selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                    OutlinedButton(
                                        onClick = { onNavigateToAddFood(sectionId, dateIso, "search") },
                                        shape = MacroTrackPillShape,
                                        modifier = Modifier
                                            .padding(horizontal = Spacing.xxl, vertical = Spacing.sm),
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(Spacing.xs))
                                        Text("Add food")
                                    }
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
                }

            }
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

    if (showAddMenu) {
        val defaultId = defaultSectionId(uiState.sections.map { it.section })
        val defaultName =
            uiState.sections.firstOrNull { it.section.id == defaultId }?.section?.name ?: "today"
        val dateIso = uiState.selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        ModalBottomSheet(onDismissRequest = { showAddMenu = false }) {
            Column(Modifier.padding(bottom = Spacing.xl)) {
                Text(
                    "Add food to $defaultName",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
                )
                AddMenuOption(
                    icon = Icons.Default.Search,
                    title = "Search foods",
                    subtitle = "Browse the food database",
                    onClick = { onNavigateToAddFood(defaultId, dateIso, "search") }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AddMenuOption(
                    icon = Icons.Default.DocumentScanner,
                    title = "Scan nutrition label",
                    subtitle = "OCR from a packaged food label",
                    onClick = { onNavigateToAddFood(defaultId, dateIso, "label") }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AddMenuOption(
                    icon = Icons.Default.QrCodeScanner,
                    title = "Scan barcode",
                    subtitle = "Look up by EAN",
                    onClick = { onNavigateToAddFood(defaultId, dateIso, "barcode") }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AddMenuOption(
                    icon = Icons.Default.Edit,
                    title = "Quick add macros",
                    subtitle = "Enter macros manually",
                    onClick = { onNavigateToAddFood(defaultId, dateIso, "quick") }
                )
            }
        }
    }

    if (showDeleteConfirm) {
        val selectedCount =
            (uiState.selectionMode as? SelectionMode.Selecting)?.selectedIds?.size ?: 0
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete $selectedCount item(s)?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedEntries()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AddMenuOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
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

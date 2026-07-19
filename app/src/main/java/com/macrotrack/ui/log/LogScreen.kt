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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.filled.CalendarMonth
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
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogScreen(
    viewModel: LogViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToAddFood: (sectionId: Long, date: String, mode: String) -> Unit,
    onEditEntry: (entryId: Long, date: LocalDate) -> Unit,
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
                            action = selectionMode.action,
                            selectedCount = selectionMode.selectedIds.size,
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
        if (uiState.isLoading || uiState.currentDay == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LogContent(
                uiState = uiState,
                paddingValues = paddingValues,
                viewModel = viewModel,
                onShowCalendar = { showCalendar = true },
                onNavigateToAddFood = onNavigateToAddFood,
                onEditEntry = onEditEntry,
            )
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
        val sections = uiState.currentDay?.sections ?: emptyList()
        val defaultId = defaultSectionId(sections.map { it.section })
        val defaultName = sections.firstOrNull { it.section.id == defaultId }?.section?.name ?: "today"
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

private val DATE_EPOCH = LocalDate.of(1970, 1, 1)
private fun pageForDate(d: LocalDate): Int = ChronoUnit.DAYS.between(DATE_EPOCH, d).toInt()
private fun dateForPage(p: Int): LocalDate = DATE_EPOCH.plusDays(p.toLong())
private fun weekPageForDate(d: LocalDate): Int {
    val ws = d.minusDays(d.dayOfWeek.value.toLong() - 1)
    return (ChronoUnit.DAYS.between(DATE_EPOCH, ws) / 7).toInt()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogContent(
    uiState: LogUiState,
    paddingValues: PaddingValues,
    viewModel: LogViewModel,
    onShowCalendar: () -> Unit,
    onNavigateToAddFood: (sectionId: Long, date: String, mode: String) -> Unit,
    onEditEntry: (entryId: Long, date: LocalDate) -> Unit,
) {
    val contentPagerState = rememberPagerState(
        initialPage = pageForDate(uiState.selectedDate),
        pageCount = { Int.MAX_VALUE },
    )
    val weekPagerState = rememberPagerState(
        initialPage = weekPageForDate(uiState.selectedDate),
        pageCount = { Int.MAX_VALUE },
    )

    LaunchedEffect(contentPagerState) {
        snapshotFlow { contentPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val date = dateForPage(page)
                if (date != uiState.selectedDate) {
                    viewModel.onDateSelected(date)
                }
            }
    }

    LaunchedEffect(uiState.selectedDate) {
        val cp = pageForDate(uiState.selectedDate)
        if (contentPagerState.currentPage != cp) {
            contentPagerState.animateScrollToPage(cp)
        }
        val wp = weekPageForDate(uiState.selectedDate)
        if (weekPagerState.currentPage != wp) {
            weekPagerState.animateScrollToPage(wp)
        }
    }

    LaunchedEffect(weekPagerState, uiState.selectedDate) {
        snapshotFlow { weekPagerState.settledPage }
            .collect { page ->
                val target = weekPageForDate(uiState.selectedDate)
                if (page > target + 1) {
                    weekPagerState.animateScrollToPage(target + 1)
                } else if (page < target - 1) {
                    weekPagerState.animateScrollToPage(target - 1)
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        HorizontalPager(
            state = weekPagerState,
            beyondViewportPageCount = 0,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val weekDays = weekDaysForPage(page, uiState)
            WeekDateStrip(
                weekDays = weekDays,
                onDateSelected = { day -> viewModel.onDateSelected(day.date) },
                onOpenCalendar = onShowCalendar,
            )
        }

        HorizontalPager(
            state = contentPagerState,
            beyondViewportPageCount = 0,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val dayContent = dayContentForPage(page, uiState)
            if (dayContent != null) {
                DayContentPage(
                    dayContent = dayContent,
                    selectionMode = uiState.selectionMode,
                    onToggleSectionExpanded = { viewModel.toggleSectionExpanded(dayContent.date, it) },
                    onToggleSelection = { viewModel.toggleSelectionMode(it) },
                    onEditEntry = onEditEntry,
                    onNavigateToAddFood = onNavigateToAddFood,
                )
            }
        }
    }
}

@Composable
private fun DayContentPage(
    dayContent: DayContent,
    selectionMode: SelectionMode,
    onToggleSectionExpanded: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onEditEntry: (entryId: Long, date: LocalDate) -> Unit,
    onNavigateToAddFood: (sectionId: Long, date: String, mode: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        item {
            MacroSummaryCard(summary = dayContent.summary)

            val isEmpty = dayContent.sections.isNotEmpty() && dayContent.sections.all { it.entries.isEmpty() }

            if (isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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
            } else {
                Column {
                    dayContent.sections.forEach { sectionWithEntries ->
                        SectionHeader(
                            name = sectionWithEntries.section.name,
                            totalMacros = sectionWithEntries.totalMacros,
                            isExpanded = sectionWithEntries.isExpanded,
                            onToggleExpand = { onToggleSectionExpanded(sectionWithEntries.section.id) }
                        )

                        if (sectionWithEntries.isExpanded) {
                            if (sectionWithEntries.entries.isEmpty()) {
                                val sectionId = sectionWithEntries.section.id
                                val dateIso = dayContent.date.format(DateTimeFormatter.ISO_LOCAL_DATE)
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
                            } else {
                                sectionWithEntries.entries.forEach { entry ->
                                    val isSelected = when (val mode = selectionMode) {
                                        is SelectionMode.Selecting -> mode.selectedIds.contains(entry.id)
                                        is SelectionMode.ChoosingDestination -> mode.selectedIds.contains(entry.id)
                                        SelectionMode.Off -> false
                                    }

                                    FoodItemCard(
                                        entry = entry,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (selectionMode != SelectionMode.Off) {
                                                onToggleSelection(entry.id)
                                            } else {
                                                onEditEntry(entry.id, dayContent.date)
                                            }
                                        },
                                        onLongClick = {
                                            onToggleSelection(entry.id)
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

private fun weekDaysForPage(page: Int, uiState: LogUiState): List<WeekDay> {
    val currentWeekPage = weekPageForDate(uiState.selectedDate)
    return when (page) {
        currentWeekPage -> uiState.currentWeek
        currentWeekPage - 1 -> uiState.prevWeek
        currentWeekPage + 1 -> uiState.nextWeek
        else -> emptyList()
    }
}

private fun dayContentForPage(page: Int, uiState: LogUiState): DayContent? {
    val currentDayPage = pageForDate(uiState.selectedDate)
    return when (page) {
        currentDayPage -> uiState.currentDay
        currentDayPage - 1 -> uiState.prevDay
        currentDayPage + 1 -> uiState.nextDay
        else -> null
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
    action: Action,
    selectedCount: Int,
    onSelectDestination: (LocalDate) -> Unit,
    onCancel: () -> Unit,
) {
    var showCalendar by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val tomorrow = today.plusDays(1)
    val targetDates = listOf(yesterday, today, tomorrow)
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                    Text(
                        text = if (action is Action.Copy) "Copy $selectedCount to" else "Move $selectedCount to",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = { showCalendar = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Pick a date")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                targetDates.forEach { date ->
                    val label = when (date) {
                        yesterday -> "Yesterday"
                        today -> "Today"
                        tomorrow -> "Tomorrow"
                        else -> date.format(DateTimeFormatter.ofPattern("MMM d"))
                    }
                    val isSelected = pickedDate == date
                    val isEnabled = !(action is Action.Move && date == selectedDate)
                    FilterChip(
                        selected = isSelected,
                        onClick = { if (isEnabled) pickedDate = date },
                        enabled = isEnabled,
                        label = { Text(label) },
                        shape = MacroTrackPillShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = brandPrimary(),
                            selectedLabelColor = brandOnPrimary(),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val destination = pickedDate
            val canConfirm = destination != null
            val confirmLabel = if (destination != null) {
                val dateLabel = when (destination) {
                    yesterday -> "yesterday"
                    today -> "today"
                    tomorrow -> "tomorrow"
                    else -> destination.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                }
                val verb = if (action is Action.Copy) "Copy" else "Move"
                "$verb $selectedCount → $dateLabel"
            } else {
                val verb = if (action is Action.Copy) "Copy" else "Move"
                "$verb $selectedCount"
            }
            Button(
                onClick = { destination?.let { onSelectDestination(it) } },
                enabled = canConfirm,
                shape = MacroTrackPillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = brandPrimary(),
                    contentColor = brandOnPrimary(),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(confirmLabel)
            }
        }
    }

    if (showCalendar) {
        CalendarModal(
            selectedDate = selectedDate,
            onDateSelected = { date ->
                pickedDate = date
                showCalendar = false
            },
            onDismiss = { showCalendar = false },
        )
    }
}

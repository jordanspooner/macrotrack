package com.macrotrack.ui.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotrack.ui.components.*
import com.macrotrack.ui.settings.CalendarModal
import com.macrotrack.ui.theme.MacroTrackPillShape
import com.macrotrack.ui.theme.MacroTrackShapes
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.MotionTokens
import com.macrotrack.ui.theme.brandOnPrimary
import com.macrotrack.ui.theme.brandPrimary
import com.macrotrack.ui.theme.restingSurfaceColor
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Section
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogScreen(
    viewModel: LogViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToAddFood: (sectionId: Long, date: String, mode: String) -> Unit,
    onEditEntry: (entryId: Long, date: LocalDate) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCalendar by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                            onDuplicateClick = { viewModel.duplicateSelectedEntries() },
                            onCopyClick = { viewModel.copySelectedEntries() },
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
                            selectedDate = selectionMode.sourceDate,
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
        if (uiState.isLoading && uiState.currentWeek.isEmpty()) {
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

/** Upper bound for a single edge step to land in the UI state before pacing the next. */
private const val EDGE_TARGET_TIMEOUT_MILLIS = 1_500L

/** Poll cadence for observing the edge step target in the latest UI state. */
private const val EDGE_TARGET_POLL_MILLIS = 25L
private fun pageForDate(d: LocalDate): Int = ChronoUnit.DAYS.between(DATE_EPOCH, d).toInt()
private fun dateForPage(p: Int): LocalDate = DATE_EPOCH.plusDays(p.toLong())
internal fun weekPageForDate(d: LocalDate): Int {
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
        initialPage = weekPageForDate(uiState.displayedWeekStart),
        pageCount = { Int.MAX_VALUE },
    )
    var userDraggedWeekPager by remember { mutableStateOf(false) }
    var userDraggedContentPager by remember { mutableStateOf(false) }
    val latestSelectedDate = rememberUpdatedState(uiState.selectedDate)
    val latestDisplayedWeekStart = rememberUpdatedState(uiState.displayedWeekStart)
    val latestUserDraggedWeekPager = rememberUpdatedState(userDraggedWeekPager)
    val latestUserDraggedContentPager = rememberUpdatedState(userDraggedContentPager)
    val latestUiState = rememberUpdatedState(uiState)

    // dragState holds comparatively stable drag metadata (entries, source
    // date, active target, active edge); it only changes when one of those
    // values changes, so LogContent recomposes sparingly during a drag.  The
    // high-frequency pointer position lives in a separate state so the drag
    // preview relayouts (layout phase) without recomposing the whole tree.
    var dragState by remember { mutableStateOf<DragState?>(null) }
    var dragPointerPosition by remember { mutableStateOf(Offset.Zero) }
    val latestDragActive = rememberUpdatedState(dragState != null)
    var overlayRootOrigin by remember { mutableStateOf(Offset.Zero) }
    var weekStripBounds by remember { mutableStateOf<Rect?>(null) }
    var dailyBodyBounds by remember { mutableStateOf<Rect?>(null) }
    val edgeWidthPx = with(LocalDensity.current) { EdgeHold.ZONE_WIDTH.toPx() }
    val dwellProgress = remember { Animatable(0f) }
    val mealTargets = remember { mutableStateMapOf<Pair<LocalDate, Long>, MealDropTarget>() }
    val weekTargets = remember { mutableStateMapOf<LocalDate, WeekDropTarget>() }
    val haptics = LocalHapticFeedback.current

    fun startDrag(snapshot: List<LogEntry>, sourceDate: LocalDate, position: Offset) {
        if (dragState != null || snapshot.isEmpty()) return
        // A drag must never be misread as a pending normal pager swipe: stale
        // settles from programmatic scrolling cannot generate a false delta.
        userDraggedWeekPager = false
        userDraggedContentPager = false
        dragPointerPosition = position
        dragState = DragState(entries = snapshot, sourceDate = sourceDate)
    }

    fun moveDrag(position: Offset) {
        val current = dragState ?: return
        // Always update the high-frequency position state so the drag preview
        // relayouts — but do NOT touch dragState unless the resolved target or
        // edge actually changed.  This keeps LogContent from recomposing on
        // every pointer move (60+ Hz), which was the main cause of the freeze.
        dragPointerPosition = position
        val target = resolveDropTarget(position, mealTargets, weekTargets)
        val redundant = target is DragTarget.Meal && isRedundantMealTarget(current.entries, target)
        val activeEdge = activeEdgeFor(
            position = position,
            weekStripBounds = weekStripBounds,
            dailyBodyBounds = dailyBodyBounds,
            edgeWidthPx = edgeWidthPx,
        )
        if (target == current.activeTarget &&
            redundant == current.isRedundantTarget &&
            activeEdge == current.activeEdge
        ) {
            return
        }
        if (target != null && target != current.activeTarget) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        dragState = current.copy(
            activeTarget = target,
            isRedundantTarget = redundant,
            activeEdge = activeEdge,
        )
    }

    fun pageForEdge(edge: ActiveEdge) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val step = edgeStepFor(edge)
        if (step.weekDelta != 0L) viewModel.navigateWeekDuringDrag(step.weekDelta)
        if (step.dayDelta != 0L) viewModel.advanceDayDuringDrag(step.dayDelta)
    }

    fun endDrag(position: Offset) {
        val drag = dragState ?: return
        dragState = null
        dragPointerPosition = Offset.Zero
        userDraggedWeekPager = false
        userDraggedContentPager = false
        if (shouldCancelDrop(position, weekStripBounds, dailyBodyBounds, edgeWidthPx)) {
            return
        }
        when (val target = resolveDropTarget(position, mealTargets, weekTargets)) {
            is DragTarget.Meal -> {
                viewModel.moveDraggedEntries(drag.entries, target.date, target.sectionId)
            }
            is DragTarget.WeekDay -> {
                viewModel.moveDraggedEntries(drag.entries, target.date)
            }
            null -> Unit
        }
    }

    val cancelDrag = {
        dragState = null
        dragPointerPosition = Offset.Zero
        userDraggedWeekPager = false
        userDraggedContentPager = false
    }
    val latestDragState = rememberUpdatedState(dragState)
    val latestMoveDrag = rememberUpdatedState<(Offset) -> Unit>(::moveDrag)
    val latestEndDrag = rememberUpdatedState<(Offset) -> Unit>(::endDrag)
    val latestCancelDrag = rememberUpdatedState<() -> Unit>(cancelDrag)
    val latestOverlayOrigin = rememberUpdatedState(overlayRootOrigin)

    LaunchedEffect(weekPagerState) {
        weekPagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                userDraggedWeekPager = true
            }
        }
    }

    LaunchedEffect(contentPagerState) {
        contentPagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                userDraggedContentPager = true
            }
        }
    }

    LaunchedEffect(contentPagerState) {
        snapshotFlow { contentPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val userSwiped = latestUserDraggedContentPager.value
                userDraggedContentPager = false
                // Only a genuine user swipe outside a drag may navigate the day.
                // Programmatic sync settles and drag auto-navigation settles must
                // never reach onDateSelected (which would clear the selection).
                val target = daySettleTargetPage(
                    settledPage = page,
                    currentDayPage = pageForDate(latestSelectedDate.value),
                    userSwiped = userSwiped,
                    dragActive = latestDragActive.value,
                ) ?: return@collect
                viewModel.onDateSelected(dateForPage(target))
            }
    }

    LaunchedEffect(uiState.selectedDate) {
        userDraggedContentPager = false
        val cp = pageForDate(uiState.selectedDate)
        val current = contentPagerState.currentPage
        when (current - cp) {
            1, -1 -> contentPagerState.animateScrollToPage(cp)
            else -> if (current != cp) contentPagerState.scrollToPage(cp)
        }
    }

    LaunchedEffect(weekPagerState) {
        snapshotFlow { weekPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val delta = weekNavigationDelta(
                    settledPage = page,
                    targetWeekPage = weekPageForDate(latestDisplayedWeekStart.value),
                    userSwiped = latestUserDraggedWeekPager.value,
                    dragActive = latestDragActive.value,
                )
                userDraggedWeekPager = false
                if (delta != 0L) {
                    viewModel.onDateSelected(latestSelectedDate.value.plusWeeks(delta))
                }
            }
    }

    LaunchedEffect(uiState.displayedWeekStart) {
        userDraggedWeekPager = false
        val target = weekPageForDate(uiState.displayedWeekStart)
        val current = weekPagerState.currentPage
        if (current != target) {
            when (current - target) {
                1, -1 -> weekPagerState.animateScrollToPage(target)
                else -> weekPagerState.scrollToPage(target)
            }
        }
    }

    // Edge dwell: page the daily/week pager while the dragged pointer stays in
    // an edge zone. Keying on the active edge restarts the initial delay every
    // time the pointer enters a new zone and cancels as soon as it leaves.
    // Every repeated step waits (with a bounded timeout) for the UI state to
    // catch up to the step it just issued, so the pace can never outrun a slow
    // day/week load and pile up unobserved steps.
    LaunchedEffect(dragState?.activeEdge) {
        val edge = dragState?.activeEdge ?: return@LaunchedEffect
        delay(EdgeHold.INITIAL_DELAY_MILLIS)
        var target = edgePageTarget(edge, latestUiState.value)
        pageForEdge(edge)
        while (isActive) {
            // If the step's content never arrives within the bound, stop paging
            // instead of issuing another step on top of a still-unloaded day or
            // week. Piling up steps was a freeze source on slow loads.
            if (!awaitEdgeTargetReached(edge, target, latestUiState)) break
            delay(EdgeHold.REPEAT_INTERVAL_MILLIS)
            target = edgePageTarget(edge, latestUiState.value)
            pageForEdge(edge)
        }
    }

    // Dwell countdown mirror: fills 0 -> 1 over each dwell delay so the edge
    // affordance reads as progress and stays coherent across repeat page steps.
    LaunchedEffect(dragState?.activeEdge) {
        val edge = dragState?.activeEdge ?: return@LaunchedEffect
        var first = true
        while (isActive) {
            val durationMillis = if (first) {
                first = false
                EdgeHold.INITIAL_DELAY_MILLIS
            } else {
                EdgeHold.REPEAT_INTERVAL_MILLIS
            }
            dwellProgress.snapTo(0f)
            dwellProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = durationMillis.toInt(),
                    easing = MotionTokens.slowEasing,
                ),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .onGloballyPositioned { overlayRootOrigin = it.boundsInRoot().topLeft }
            // Sole owner of drag movement and release. This stable observer
            // reads the original pointer in the Initial pass, so it keeps
            // tracking the in-flight drag even after pager navigation disposes
            // the source card. Cards only announce onDragStart; card-level
            // onDragMove/onDragEnd are no-ops so no pointer is processed twice.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    var lastRootPosition = latestOverlayOrigin.value + down.position
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastRootPosition = latestOverlayOrigin.value + change.position
                            if (latestDragState.value != null) {
                                latestMoveDrag.value(lastRootPosition)
                            }
                            if (!change.pressed) {
                                released = true
                                if (latestDragState.value != null) {
                                    latestEndDrag.value(lastRootPosition)
                                }
                                break
                            }
                        }
                    } finally {
                        if (!released && latestDragState.value != null) {
                            latestCancelDrag.value()
                        }
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = weekPagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = dragState == null,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = weekPagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    // Register from the visible pager container so offscreen
                    // pages can never overwrite the shared strip bounds.
                    .onGloballyPositioned { weekStripBounds = it.boundsInRoot() },
            ) { page ->
                weekDaysForPage(page, uiState)?.let { weekDays ->
                    WeekDateStrip(
                        weekDays = weekDays,
                        dragActive = dragState != null,
                        dragCount = (uiState.selectionMode as? SelectionMode.Selecting)
                            ?.selectedEntries?.size ?: 0,
                        activeDragDate = (dragState?.activeTarget as? DragTarget.WeekDay)?.date,
                        onDateSelected = { day ->
                            if (dragState == null) viewModel.onDateSelected(day.date)
                        },
                        onOpenCalendar = onShowCalendar,
                        onRegisterDayTarget = { date, bounds ->
                            weekTargets[date] = WeekDropTarget(date, bounds)
                        },
                        onUnregisterDayTarget = { date -> weekTargets.remove(date) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { dailyBodyBounds = it.boundsInRoot() }
            ) {
                HorizontalPager(
                    state = contentPagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = dragState == null,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val dayContent = dayContentForPage(page, uiState)
                    if (dayContent != null) {
                        DayContentPage(
                            dayContent = dayContent,
                            selectionMode = uiState.selectionMode,
                            dragState = dragState,
                            onToggleSectionExpanded = { viewModel.toggleSectionExpanded(dayContent.date, it) },
                            onToggleSelection = { entry, date -> viewModel.toggleSelection(entry, date) },
                            onStartDrag = { snapshot, sourceDate, position ->
                                startDrag(snapshot, sourceDate, position)
                            },
                            onRegisterMealTarget = { date, sectionId, name, bounds ->
                                mealTargets[date to sectionId] = MealDropTarget(date, sectionId, name, bounds)
                            },
                            onUnregisterMealTarget = { date, sectionId ->
                                mealTargets.remove(date to sectionId)
                            },
                            onMoveToSection = { date, sectionId ->
                                val snapshot = (uiState.selectionMode as? SelectionMode.Selecting)
                                    ?.selectedEntries ?: emptyList()
                                viewModel.moveDraggedEntries(snapshot, date, sectionId)
                            },
                            onEditEntry = onEditEntry,
                            onNavigateToAddFood = onNavigateToAddFood,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        dragState?.let { drag ->
            DragPreviewCard(
                name = drag.entries.first().name,
                count = drag.entries.size,
                modifier = Modifier.offset {
                    IntOffset(
                        x = (dragPointerPosition.x - overlayRootOrigin.x - 110.dp.toPx()).roundToInt(),
                        y = (dragPointerPosition.y - overlayRootOrigin.y - 10.dp.toPx()).roundToInt(),
                    )
                },
            )
        }

        EdgeAffordance(
            edge = dragState?.activeEdge,
            weekStripBounds = weekStripBounds,
            dailyBodyBounds = dailyBodyBounds,
            overlayOrigin = overlayRootOrigin,
            progress = dwellProgress.value,
        )
    }
}

/**
 * Non-interactive overlay hint shown on the active left/right edge during a
 * drag. Rendered over the matching surface (daily body or week strip), it
 * never captures a second pointer or changes layout: it is positioned with
 * [Modifier.offset] and carries no pointer handlers, so the in-flight drag
 * gesture keeps receiving moves through it. [progress] mirrors the dwell
 * countdown so the affordance stays coherent across repeated page steps.
 */
@Composable
private fun EdgeAffordance(
    edge: ActiveEdge?,
    weekStripBounds: Rect?,
    dailyBodyBounds: Rect?,
    overlayOrigin: Offset,
    progress: Float,
) {
    var lastEdge by remember { mutableStateOf(edge) }
    if (edge != null) lastEdge = edge
    val shownEdge = edge ?: lastEdge ?: return
    val bounds = when (shownEdge.surface) {
        EdgeSurface.Week -> weekStripBounds
        EdgeSurface.Daily -> dailyBodyBounds
    } ?: return

    val isLeft = shownEdge.zone == EdgeZone.Left
    val label = when (shownEdge.surface) {
        EdgeSurface.Week -> if (isLeft) "Previous week" else "Next week"
        EdgeSurface.Daily -> if (isLeft) "Previous day" else "Next day"
    }
    val icon = if (isLeft) Icons.Default.ChevronLeft else Icons.Default.ChevronRight

    AnimatedVisibility(
        visible = edge != null,
        enter = fadeIn(tween(MotionTokens.medium, easing = MotionTokens.fastEasing)),
        exit = fadeOut(tween(MotionTokens.medium, easing = MotionTokens.fastEasing)),
        ) {
            Box(
            modifier = Modifier
                .offset {
                    val width = 64.dp.toPx()
                    val height = 56.dp.toPx()
                    val inset = 8.dp.toPx()
                    IntOffset(
                        x = (if (isLeft) {
                            bounds.left - overlayOrigin.x + inset
                        } else {
                            bounds.right - overlayOrigin.x - width - inset
                        }).roundToInt(),
                        y = (bounds.top - overlayOrigin.y + (bounds.height - height) / 2f).roundToInt(),
                    )
                }
                .width(64.dp)
                .height(56.dp)
                .clip(MacroTrackShapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = brandPrimary(),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(brandPrimary()),
            )
        }
    }
}

@Composable
private fun DayContentPage(
    dayContent: DayContent,
    selectionMode: SelectionMode,
    dragState: DragState?,
    onToggleSectionExpanded: (Long) -> Unit,
    onToggleSelection: (LogEntry, LocalDate) -> SelectionMode,
    onStartDrag: (List<LogEntry>, LocalDate, Offset) -> Unit,
    onRegisterMealTarget: (LocalDate, Long, String, Rect) -> Unit,
    onUnregisterMealTarget: (LocalDate, Long) -> Unit,
    onMoveToSection: (LocalDate, Long) -> Unit,
    onEditEntry: (entryId: Long, date: LocalDate) -> Unit,
    onNavigateToAddFood: (sectionId: Long, date: String, mode: String) -> Unit,
) {
    val isDragActive = dragState != null
    val selectedCount = (selectionMode as? SelectionMode.Selecting)?.selectedEntries?.size ?: 0
    var pendingDragAnchor by remember(dayContent.date) {
        mutableStateOf<Pair<List<LogEntry>, LocalDate>?>(null)
    }
    // A day with no entries still shows its meal sections (never the
    // "Nothing logged" placeholder) so they register as drop targets and a
    // dragged food can always be dropped into a meal, even on an empty date.
    val dayHasEntries = dayContent.sections.any { it.entries.isNotEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        item(key = "summary-${dayContent.date}") {
            MacroSummaryCard(summary = dayContent.summary)
        }

        dayContent.sections.forEach { sectionWithEntries ->
            item(key = "section-${dayContent.date}-${sectionWithEntries.section.id}") {
                val section = sectionWithEntries.section
                // Expand empty sections on a day with nothing logged so the drop
                // zone is obvious and easy to hit; otherwise honor the saved state.
                val expanded = sectionWithEntries.isExpanded || !dayHasEntries
                val activeMealTarget = dragState?.activeTarget as? DragTarget.Meal
                val isActiveTarget =
                    activeMealTarget?.date == dayContent.date && activeMealTarget.sectionId == section.id
                val isRedundantTarget = if (isActiveTarget) {
                    checkNotNull(dragState).isRedundantTarget
                } else {
                    false
                }

                Column(
                    modifier = Modifier.onGloballyPositioned {
                        onRegisterMealTarget(
                            dayContent.date,
                            section.id,
                            section.name,
                            it.boundsInRoot(),
                        )
                    }
                ) {
                    SectionHeader(
                        name = section.name,
                        totalMacros = sectionWithEntries.totalMacros,
                        goalMacros = sectionWithEntries.goalMacros,
                        hasEntries = sectionWithEntries.entries.isNotEmpty(),
                        isExpanded = expanded,
                        onToggleExpand = { onToggleSectionExpanded(section.id) },
                        enabled = !isDragActive,
                        isActiveDropTarget = isActiveTarget,
                        isInvalidDropTarget = isRedundantTarget,
                        moveAccessibilityLabel = if (selectionMode is SelectionMode.Selecting) {
                            "Move $selectedCount selected to ${section.name}"
                        } else {
                            null
                        },
                        onMoveAccessibilityAction = {
                            onMoveToSection(dayContent.date, section.id)
                            true
                        },
                    )

                    if (expanded) {
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
                                    isDragSource = isDragActive && isSelected,
                                    gesturesEnabled = !isDragActive,
                                    onClick = {
                                        if (selectionMode != SelectionMode.Off) {
                                            onToggleSelection(entry, dayContent.date)
                                        } else {
                                            onEditEntry(entry.id, dayContent.date)
                                        }
                                    },
                                    onLongPress = {
                                        // A stationary long-press never arms a drag:
                                        // an unselected food only toggles selection,
                                        // and a selected card must keep its selection
                                        // intact so a later drag payload is the whole
                                        // current snapshot. Drag arming happens on
                                        // post-long-press movement (onDragStart).
                                        pendingDragAnchor = when {
                                            selectionMode is SelectionMode.Selecting && isSelected ->
                                                selectionMode.selectedEntries to selectionMode.sourceDate
                                            selectionMode !is SelectionMode.ChoosingDestination -> {
                                                val updatedMode = onToggleSelection(entry, dayContent.date)
                                                (updatedMode as? SelectionMode.Selecting)
                                                    ?.takeIf { it.selectedEntries.any { selected -> selected.id == entry.id } }
                                                    ?.let { it.selectedEntries to it.sourceDate }
                                            }
                                            else -> null
                                        }
                                    },
                                    onDragStart = { position ->
                                        val pending = pendingDragAnchor
                                        pendingDragAnchor = null
                                        dragStartAnchorWithPending(selectionMode, entry, pending)
                                            ?.let { (snapshot, sourceDate) ->
                                            onStartDrag(snapshot, sourceDate, position)
                                        }
                                    },
                                    // Drag movement and release are owned solely by the
                                    // stable root pointer observer in LogContent (it sees
                                    // the original pointer in the Initial pass and keeps
                                    // tracking it after pager navigation disposes this card.
                                    // Card-level move and end callbacks are no-ops so the same
                                    // pointer is never processed twice.
                                    onDragMove = { },
                                    onDragEnd = { },
                                )
                            }
                        }
                    }
                }

                DisposableEffect(dayContent.date, section.id) {
                    onDispose { onUnregisterMealTarget(dayContent.date, section.id) }
                }
            }
        }
    }
}

@Composable
private fun DragPreviewCard(
    name: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.5.dp, brandPrimary()),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            if (count > 1) {
                Spacer(modifier = Modifier.width(Spacing.sm))
                Surface(
                    shape = CircleShape,
                    color = brandPrimary(),
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelMedium,
                        color = brandOnPrimary(),
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                    )
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

internal fun weekDaysForPage(page: Int, uiState: LogUiState): List<WeekDay>? {
    val currentWeekPage = weekPageForDate(uiState.displayedWeekStart)
    return when (page) {
        currentWeekPage -> uiState.currentWeek
        currentWeekPage - 1 -> uiState.prevWeek
        currentWeekPage + 1 -> uiState.nextWeek
        else -> null
    }
}

/**
 * Navigation delta for a settled week-pager page. Only a genuine user swipe
 * outside a drag navigates: programmatic sync settles and drag-driven settles
 * return zero so they can never change the daily log or clear the selection.
 */
internal fun weekNavigationDelta(
    settledPage: Int,
    targetWeekPage: Int,
    userSwiped: Boolean,
    dragActive: Boolean = false,
): Long {
    if (!userSwiped || dragActive) return 0L
    return (settledPage - targetWeekPage).toLong()
}

/**
 * The page a settled day-pager reflects when it came from a genuine user swipe
 * outside a drag, or null when the settle was programmatic, drag-driven, or a
 * bounce-back on the same page. Null settles never reach onDateSelected.
 */
internal fun daySettleTargetPage(
    settledPage: Int,
    currentDayPage: Int,
    userSwiped: Boolean,
    dragActive: Boolean,
): Int? {
    if (!userSwiped || dragActive) return null
    return settledPage.takeIf { it != currentDayPage }
}

/**
 * The state a single page step on [edge] must reach: the selected date moves
 * ±1 day for a daily edge, the displayed week start ±1 week for a week edge.
 * Computed from the latest [LogUiState] so repeated edge steps always advance
 * from the UI's actual position, never a stale captured snapshot.
 */
internal fun edgePageTarget(edge: ActiveEdge, uiState: LogUiState): LocalDate = when (edge.surface) {
    EdgeSurface.Daily -> uiState.selectedDate.plusDays(edgeStepFor(edge).dayDelta)
    EdgeSurface.Week -> uiState.displayedWeekStart.plusWeeks(edgeStepFor(edge).weekDelta)
}

/**
 * True when the latest [LogUiState] has caught up to [target] for [edge]'s surface.
 *
 * The daily gate additionally requires the target day's content to be loaded
 * (`currentDay != null`); the bare [LogUiState.selectedDate] is a StateFlow
 * value that updates the instant `_selectedDate` is written, so checking it
 * alone would let back-to-back edge steps fire before Room has produced the
 * day's data, causing rapid `flatMapLatest` cancellations and a freeze.
 */
internal fun edgeTargetReached(edge: ActiveEdge, target: LocalDate, uiState: LogUiState): Boolean =
    when (edge.surface) {
        EdgeSurface.Daily -> uiState.selectedDate == target && uiState.currentDay != null
        EdgeSurface.Week -> uiState.displayedWeekStart == target
    }

/**
 * Waits until the latest UI state reaches [target] for [edge]'s surface,
 * bounded by [EDGE_TARGET_TIMEOUT_MILLIS] so a slow or failed day/week load
 * can never stall the edge dwell permanently. Reads [latestUiState], the
 * rememberUpdatedState mirror of the collected uiState, so the wait observes
 * fresh values instead of a stale captured snapshot. Returns true when the
 * target was reached and false when the bound expired first.
 */
private suspend fun awaitEdgeTargetReached(
    edge: ActiveEdge,
    target: LocalDate,
    latestUiState: State<LogUiState>,
): Boolean {
    var reached = false
    withTimeoutOrNull(EDGE_TARGET_TIMEOUT_MILLIS) {
        while (isActive && !edgeTargetReached(edge, target, latestUiState.value)) {
            delay(EDGE_TARGET_POLL_MILLIS)
        }
        reached = edgeTargetReached(edge, target, latestUiState.value)
    }
    return reached
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
    selectedCount: Int,
    onSelectDestination: (LocalDate) -> Unit,
    onCancel: () -> Unit,
) {
    var showCalendar by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val targetDates = destinationDatesFor(selectedDate)
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
                        text = "Copy $selectedCount to",
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
                    val label = destinationChipLabel(date, selectedDate, today)
                    val isSelected = pickedDate == date
                    FilterChip(
                        selected = isSelected,
                        onClick = { pickedDate = date },
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
                val dateLabel = destinationConfirmationLabel(destination, selectedDate, today)
                "Copy $selectedCount → $dateLabel"
            } else {
                "Copy $selectedCount"
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
            selectedDate = pickedDate ?: selectedDate,
            onDateSelected = { date ->
                pickedDate = date
                showCalendar = false
            },
            onDismiss = { showCalendar = false },
        )
    }
}

internal fun destinationDatesFor(selectedDate: LocalDate): List<LocalDate> = listOf(
    selectedDate.minusDays(1),
    selectedDate,
    selectedDate.plusDays(1),
)

internal fun destinationChipLabel(
    date: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
): String {
    if (selectedDate == today) {
        return when (date) {
            today.minusDays(1) -> "Yesterday"
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> date.format(DateTimeFormatter.ofPattern("MMM d"))
        }
    }
    return date.format(DateTimeFormatter.ofPattern("MMM d"))
}

internal fun destinationConfirmationLabel(
    date: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
): String {
    if (selectedDate == today) {
        return when (date) {
            today.minusDays(1) -> "yesterday"
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        }
    }
    return date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
}

package com.macrotrack.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotrack.data.local.db.dao.DailyMacroRow
import com.macrotrack.domain.model.DailySummary
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Section
import com.macrotrack.domain.model.SectionGoals
import com.macrotrack.domain.usecase.log.CopyLogEntriesUseCase
import com.macrotrack.domain.usecase.log.DeleteLogEntriesUseCase
import com.macrotrack.domain.usecase.log.GetDailyLogUseCase
import com.macrotrack.domain.usecase.log.GetWeeklyMacrosUseCase
import com.macrotrack.domain.usecase.log.MoveLogEntriesUseCase
import com.macrotrack.domain.usecase.settings.GetSectionsUseCase
import com.macrotrack.domain.usecase.settings.GetSectionGoalsUseCase
import com.macrotrack.domain.usecase.settings.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** Upper bound for a single drop transaction so a hung DB op can never stall the UI. */
private const val DROP_OPERATION_TIMEOUT_MILLIS = 4_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogViewModel @Inject constructor(
    private val getDailyLogUseCase: GetDailyLogUseCase,
    private val getSectionsUseCase: GetSectionsUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val getSectionGoalsUseCase: GetSectionGoalsUseCase,
    private val deleteLogEntriesUseCase: DeleteLogEntriesUseCase,
    private val copyLogEntriesUseCase: CopyLogEntriesUseCase,
    private val moveLogEntriesUseCase: MoveLogEntriesUseCase,
    private val getWeeklyMacrosUseCase: GetWeeklyMacrosUseCase,
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _displayedWeekStart = MutableStateFlow(mondayOf(LocalDate.now()))
    private val _selectionMode = MutableStateFlow<SelectionMode>(SelectionMode.Off)
    private val _collapsedSections = MutableStateFlow<Map<LocalDate, Set<Long>>>(emptyMap())
    // Guards concurrent drag drops (drag drop + accessibility move) so two
    // transactions can never stack on the same snapshot. Read/written only on
    // the main thread (viewModelScope), so it stays a plain field.
    private var dropOpInFlight = false
    private val sections: StateFlow<List<Section>> = getSectionsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Keep loaded dates available while a newly selected date is queried. */
    private val entriesByDate: StateFlow<Map<LocalDate, List<LogEntry>>> = _selectedDate
        .flatMapLatest { date ->
            combine(
                getDailyLogUseCase(date.minusDays(1)),
                getDailyLogUseCase(date),
                getDailyLogUseCase(date.plusDays(1)),
            ) { prev, current, next ->
                mapOf(
                    date.minusDays(1) to prev,
                    date to current,
                    date.plusDays(1) to next,
                )
            }
        }
        .scan(emptyMap<LocalDate, List<LogEntry>>()) { cached, latest ->
            cached + latest
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Re-query weekly macros only when the displayed week changes. */
    private val weeklyRowsByDate: StateFlow<Map<String, DailyMacroRow>> = _displayedWeekStart
        .flatMapLatest { weekStart ->
            getWeeklyMacrosUseCase(weekStart.minusDays(7), weekStart.plusDays(13))
                .map { rows -> rows.associateBy { it.date } }
                .onStart { emit(emptyMap()) }
        }
        .scan(emptyMap<String, DailyMacroRow>()) { cached, latest ->
            cached + latest
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val uiState: StateFlow<LogUiState> = combine(
        combine(
            _selectedDate,
            _displayedWeekStart,
            entriesByDate,
            sections,
            weeklyRowsByDate,
) { date, displayedWeekStart, allEntries, sections, allWeeklyRows ->
            Quadro(date, displayedWeekStart, allEntries, sections, allWeeklyRows.values.toList())
        },
        combine(
            getSettingsUseCase(),
            getSectionGoalsUseCase(),
            _selectionMode,
            _collapsedSections,
        ) { goals, sectionGoals, selectionMode, collapsedSections ->
            GoalsBlock(goals, sectionGoals, selectionMode, collapsedSections)
        }
    ) { data1, data2 ->
        val date = data1.date
        val displayedWeekStart = data1.displayedWeekStart
        val prevEntries = data1.allEntries[date.minusDays(1)]
        val currEntries = data1.allEntries[date]
        val nextEntries = data1.allEntries[date.plusDays(1)]
        val sections = data1.sections
        val allWeeklyRows = data1.allWeeklyRows
        val goals = data2.goals
        val sectionGoals = data2.sectionGoals
        val selectionMode = data2.selectionMode
        val collapsedMap = data2.collapsedSections

        val currentDay = currEntries?.let {
            buildDayContent(date, it, sections, goals, sectionGoals, collapsedMap)
        }
        val prevDay = prevEntries?.let {
            buildDayContent(date.minusDays(1), it, sections, goals, sectionGoals, collapsedMap)
        }
        val nextDay = nextEntries?.let {
            buildDayContent(date.plusDays(1), it, sections, goals, sectionGoals, collapsedMap)
        }

        val currentWeek = buildWeekDates(displayedWeekStart, date, goals, allWeeklyRows)
        val prevWeek = buildWeekDates(displayedWeekStart.minusWeeks(1), date, goals, allWeeklyRows)
        val nextWeek = buildWeekDates(displayedWeekStart.plusWeeks(1), date, goals, allWeeklyRows)

        LogUiState(
            selectedDate = date,
            displayedWeekStart = displayedWeekStart,
            prevDay = prevDay,
            currentDay = currentDay,
            nextDay = nextDay,
            prevWeek = prevWeek,
            currentWeek = currentWeek,
            nextWeek = nextWeek,
            selectionMode = selectionMode,
            isLoading = currentDay == null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LogUiState(isLoading = true)
    )

    private fun buildDayContent(
        date: LocalDate,
        entries: List<LogEntry>,
        sections: List<Section>,
        goals: com.macrotrack.domain.model.DailyGoals,
        sectionGoals: SectionGoals,
        collapsedMap: Map<LocalDate, Set<Long>>,
    ): DayContent {
        val collapsed = collapsedMap[date] ?: computeDefaultCollapsed(date, sections)
        val totalLoggedMacros = entries.fold(Macros(0f, 0f, 0f, 0f)) { acc, entry -> acc + entry.macros }
        val summary = DailySummary(date, totalLoggedMacros, goals)
        val sectionMap = entries.groupBy { it.sectionId }
        val sectionIds = sections.map { it.id }
        val sectionsWithEntries = sections.map { section ->
            val sectionEntries = sectionMap[section.id] ?: emptyList()
            val sectionMacros =
                sectionEntries.fold(Macros(0f, 0f, 0f, 0f)) { acc, entry -> acc + entry.macros }
            SectionWithEntries(
                section = section,
                entries = sectionEntries.sortedWith(compareBy({ it.sortOrder }, { it.id })),
                totalMacros = sectionMacros,
                isExpanded = !collapsed.contains(section.id),
                goalMacros = sectionGoals.macroGoalFor(goals, section.id, sectionIds),
            )
        }.sortedBy { it.section.timeOfDay }
        return DayContent(date, summary, sectionsWithEntries)
    }

    fun onDateSelected(date: LocalDate) {
        if (_selectedDate.value != date) {
            _selectedDate.value = date
        }
        val monday = mondayOf(date)
        if (_displayedWeekStart.value != monday) {
            _displayedWeekStart.value = monday
        }
        _selectionMode.value = SelectionMode.Off
    }

    fun toggleSectionExpanded(date: LocalDate, sectionId: Long) {
        val current = _collapsedSections.value.toMutableMap()
        val set = (current[date] ?: computeDefaultCollapsed(date, sections.value)).toMutableSet()
        if (set.contains(sectionId)) {
            set.remove(sectionId)
        } else {
            set.add(sectionId)
        }
        current[date] = set
        _collapsedSections.value = current
    }

    fun toggleSelectionMode(entryId: Long) {
        val entry = currentDayEntry(entryId) ?: return
        toggleSelection(entry, _selectedDate.value)
    }

    /**
     * Toggle selection for [entry] anchored at [sourceDate], resolving the entry
     * directly instead of via the current day. Selecting with a different
     * [sourceDate] while a selection is active starts a fresh selection there.
     */
    fun toggleSelection(entry: LogEntry, sourceDate: LocalDate): SelectionMode {
        when (val currentMode = _selectionMode.value) {
            SelectionMode.Off -> {
                _selectionMode.value = SelectionMode.Selecting(
                    sourceDate = sourceDate,
                    selectedEntries = listOf(entry),
                )
            }
            is SelectionMode.Selecting -> {
                if (currentMode.sourceDate != sourceDate) {
                    _selectionMode.value = SelectionMode.Selecting(
                        sourceDate = sourceDate,
                        selectedEntries = listOf(entry),
                    )
                } else {
                    val newEntries = if (currentMode.selectedEntries.any { it.id == entry.id }) {
                        currentMode.selectedEntries.filterNot { it.id == entry.id }
                    } else {
                        (currentMode.selectedEntries + entry)
                            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                    }
                    _selectionMode.value = if (newEntries.isEmpty()) {
                        SelectionMode.Off
                    } else {
                        currentMode.copy(selectedEntries = newEntries)
                    }
                }
            }
            is SelectionMode.ChoosingDestination -> Unit
        }
        return _selectionMode.value
    }

    private fun currentDayEntry(entryId: Long): LogEntry? =
        currentDaySections().asSequence()
            .flatMap { it.entries.asSequence() }
            .find { it.id == entryId }

    fun exitSelectionMode() {
        _selectionMode.value = SelectionMode.Off
    }

    private fun currentDaySections(): List<SectionWithEntries> {
        return uiState.value.currentDay?.sections ?: emptyList()
    }

    fun copySelectedEntries() {
        val mode = _selectionMode.value as? SelectionMode.Selecting ?: return
        _selectionMode.value = SelectionMode.ChoosingDestination(
            sourceDate = mode.sourceDate,
            selectedEntries = mode.selectedEntries,
            action = Action.Copy,
        )
    }

    /**
     * Compatibility shim for the pre-drag UI: the Move button now enters the
     * Copy destination flow. Real moves are driven by [moveDraggedEntries].
     */
    @Deprecated("Move selection is replaced by drag; use moveDraggedEntries")
    fun moveSelectedEntries() {
        val mode = _selectionMode.value as? SelectionMode.Selecting ?: return
        _selectionMode.value = SelectionMode.ChoosingDestination(
            sourceDate = mode.sourceDate,
            selectedEntries = mode.selectedEntries,
            action = Action.Copy,
        )
    }

    fun duplicateSelectedEntries() {
        val mode = _selectionMode.value as? SelectionMode.Selecting ?: return
        if (mode.selectedEntries.isEmpty()) return
        viewModelScope.launch {
            copyLogEntriesUseCase(mode.selectedEntries, mode.sourceDate)
        }
    }

    fun deleteSelectedEntries() {
        val mode = _selectionMode.value as? SelectionMode.Selecting ?: return
        if (mode.selectedEntries.isEmpty()) return
        viewModelScope.launch {
            deleteLogEntriesUseCase(mode.selectedEntries)
            exitSelectionMode()
        }
    }

    fun confirmCopyMove(targetDate: LocalDate) {
        val mode = _selectionMode.value as? SelectionMode.ChoosingDestination ?: return
        if (mode.selectedEntries.isEmpty()) return
        viewModelScope.launch {
            when (mode.action) {
                Action.Copy -> copyLogEntriesUseCase(mode.selectedEntries, targetDate)
                // Never produced: moves are handled by the drag path.
                Action.Move -> error("Move is not a selection action")
            }
            _selectionMode.value = SelectionMode.Selecting(
                sourceDate = mode.sourceDate,
                selectedEntries = mode.selectedEntries,
            )
        }
    }

    fun cancelChoosingDestination() {
        val mode = _selectionMode.value as? SelectionMode.ChoosingDestination ?: return
        _selectionMode.value = SelectionMode.Selecting(
            sourceDate = mode.sourceDate,
            selectedEntries = mode.selectedEntries,
        )
    }

    /**
     * Moves [entries] to [targetDate], clearing the selection once the mutation
     * commits. A null [targetSectionId] — the date-bar (week strip) drop path —
     * preserves each entry's own section, matching the "maintains the sections
     * (if possible)" copy/move policy. A specific [targetSectionId] — the meal
     * drop path — retargets every entry to that section.
     */
    fun moveDraggedEntries(entries: List<LogEntry>, targetDate: LocalDate, targetSectionId: Long? = null) {
        if (entries.isEmpty()) return
        val movesNothing = if (targetSectionId != null) {
            entries.all { it.date == targetDate && it.sectionId == targetSectionId }
        } else {
            entries.all { it.date == targetDate }
        }
        if (movesNothing) return
        if (dropOpInFlight) return
        dropOpInFlight = true
        viewModelScope.launch {
            val completed = withTimeoutOrNull(DROP_OPERATION_TIMEOUT_MILLIS) {
                moveLogEntriesUseCase(entries, targetDate, targetSectionId)
                true
            }
            dropOpInFlight = false
            // Clear selection only after the mutation committed. If the DB op
            // timed out (never blocked the main thread), keep the selection so
            // the user can retry rather than leaving a silently-failed drop.
            if (completed == true) exitSelectionMode()
        }
    }

    /**
     * Advances the live selected date by [delta] days during a drag, preserving
     * any active selection. Steps compound on the ViewModel's authoritative
     * [_selectedDate] rather than a stale UI snapshot. Realigns
     * [displayedWeekStart] only when the new date crosses into another week.
     */
    fun advanceDayDuringDrag(delta: Long) {
        val current = _selectedDate.value
        val next = current.plusDays(delta)
        _selectedDate.value = next
        if (mondayOf(current) != mondayOf(next)) {
            _displayedWeekStart.value = mondayOf(next)
        }
    }

    /**
     * Compatibility wrapper for the pre-[advanceDayDuringDrag] drag UI. Prefer
     * [advanceDayDuringDrag], which steps the ViewModel's live date; callers
     * must not derive repeated steps from a stale UI date. Preserves selection
     * and realigns the week anchor only when [date] crosses into another week.
     */
    fun navigateDuringDrag(date: LocalDate) {
        val current = _selectedDate.value
        if (current == date) return
        _selectedDate.value = date
        if (mondayOf(current) != mondayOf(date)) {
            _displayedWeekStart.value = mondayOf(date)
        }
    }

    /**
     * Moves only the displayed week anchor by [weeks] during a drag. Keeps
     * [selectedDate] and any active selection untouched, so the daily log
     * never teleports while the week strip pages over the anchor.
     */
    fun navigateWeekDuringDrag(weeks: Long) {
        _displayedWeekStart.value = _displayedWeekStart.value.plusWeeks(weeks)
    }

    private fun computeDefaultCollapsed(
        date: LocalDate,
        sections: List<Section>,
    ): Set<Long> {
        if (sections.isEmpty()) return emptySet()
        return if (date == LocalDate.now()) {
            val timeWindowId = defaultTimeWindowSection(sections)
            sections.map { it.id }.filter { it != timeWindowId }.toSet()
        } else {
            sections.map { it.id }.toSet()
        }
    }

    private fun defaultTimeWindowSection(sections: List<Section>): Long {
        val now = java.time.LocalTime.now()
        val sorted = sections.sortedBy { it.timeOfDay }
        val past = sorted.filter { !it.timeOfDay.isAfter(now) }
        return (past.lastOrNull() ?: sorted.last()).id
    }

    private fun buildWeekDates(
        referenceDate: LocalDate,
        selectedDate: LocalDate,
        goals: com.macrotrack.domain.model.DailyGoals,
        weeklyRows: List<DailyMacroRow>,
    ): List<WeekDay> {
        val rowMap = weeklyRows.associateBy { it.date }
        val startOfWeek = mondayOf(referenceDate)
        val today = LocalDate.now()
        val shares = macroGoalShares(goals.proteinG, goals.carbsG, goals.fatG)

        return (0..6).map { i ->
            val date = startOfWeek.plusDays(i.toLong())
            val row = rowMap[date.toString()]

            WeekDay(
                date = date,
                dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dayNumber = date.dayOfMonth,
                isSelected = date == selectedDate,
                isToday = date == today,
                proteinProgress = macroGoalProgress(row?.protein ?: 0f, goals.proteinG),
                carbsProgress = macroGoalProgress(row?.carbs ?: 0f, goals.carbsG),
                fatProgress = macroGoalProgress(row?.fat ?: 0f, goals.fatG),
                proteinShare = shares.protein,
                carbsShare = shares.carbs,
                fatShare = shares.fat,
            )
        }
    }

    private data class Quadro(
        val date: LocalDate,
        val displayedWeekStart: LocalDate,
        val allEntries: Map<LocalDate, List<LogEntry>>,
        val sections: List<Section>,
        val allWeeklyRows: List<DailyMacroRow>,
    )

    private data class GoalsBlock(
        val goals: com.macrotrack.domain.model.DailyGoals,
        val sectionGoals: SectionGoals,
        val selectionMode: SelectionMode,
        val collapsedSections: Map<LocalDate, Set<Long>>,
    )
}

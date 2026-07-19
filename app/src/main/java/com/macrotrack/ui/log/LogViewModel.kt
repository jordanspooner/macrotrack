package com.macrotrack.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotrack.data.local.db.dao.DailyMacroRow
import com.macrotrack.domain.model.DailySummary
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Section
import com.macrotrack.domain.usecase.food.ReseedFoodDatabaseUseCase
import com.macrotrack.domain.usecase.log.CopyLogEntriesUseCase
import com.macrotrack.domain.usecase.log.DeleteLogEntriesUseCase
import com.macrotrack.domain.usecase.log.GetDailyLogUseCase
import com.macrotrack.domain.usecase.log.GetWeeklyMacrosUseCase
import com.macrotrack.domain.usecase.log.MoveLogEntriesUseCase
import com.macrotrack.domain.usecase.settings.GetSectionsUseCase
import com.macrotrack.domain.usecase.settings.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val getDailyLogUseCase: GetDailyLogUseCase,
    private val getSectionsUseCase: GetSectionsUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val deleteLogEntriesUseCase: DeleteLogEntriesUseCase,
    private val copyLogEntriesUseCase: CopyLogEntriesUseCase,
    private val moveLogEntriesUseCase: MoveLogEntriesUseCase,
    private val reseedFoodDatabaseUseCase: ReseedFoodDatabaseUseCase,
    private val getWeeklyMacrosUseCase: GetWeeklyMacrosUseCase,
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _selectionMode = MutableStateFlow<SelectionMode>(SelectionMode.Off)
    private val _collapsedSections = MutableStateFlow<Map<LocalDate, Set<Long>>>(emptyMap())
    private val seededDates = mutableSetOf<LocalDate>()

    private val _reseedMessage = MutableStateFlow<String?>(null)
    val reseedMessage: StateFlow<String?> = _reseedMessage

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LogUiState> = combine(
        combine(
            _selectedDate,
            _selectedDate.flatMapLatest { date ->
                combine(
                    getDailyLogUseCase(date.minusDays(1)),
                    getDailyLogUseCase(date),
                    getDailyLogUseCase(date.plusDays(1)),
                ) { prev, curr, next -> Triple(prev, curr, next) }
            },
            getSectionsUseCase(),
            _selectedDate.flatMapLatest { date ->
                val weekStart = date.minusDays(date.dayOfWeek.value.toLong() - 1)
                getWeeklyMacrosUseCase(weekStart.minusDays(7), weekStart.plusDays(13))
            }
        ) { date, allEntries, sections, allWeeklyRows ->
            Quadro(date, allEntries, sections, allWeeklyRows)
        },
        combine(
            getSettingsUseCase(),
            _selectionMode,
            _collapsedSections,
        ) { goals, selectionMode, _ ->
            Triple(goals, selectionMode, Unit)
        }
    ) { data1, data2 ->
        val date = data1.date
        val (prevEntries, currEntries, nextEntries) = data1.allEntries
        val sections = data1.sections
        val allWeeklyRows = data1.allWeeklyRows
        val goals = data2.first
        val selectionMode = data2.second

        val dates = listOf(date.minusDays(1), date, date.plusDays(1))
        for (d in dates) {
            if (!seededDates.contains(d)) {
                val collapsed = _collapsedSections.value.toMutableMap()
                collapsed[d] = computeDefaultCollapsed(d, sections)
                _collapsedSections.value = collapsed
                seededDates.add(d)
            }
        }
        val collapsedMap = _collapsedSections.value

        val currentDay = buildDayContent(date, currEntries, sections, goals, collapsedMap)
        val prevDay = buildDayContent(date.minusDays(1), prevEntries, sections, goals, collapsedMap)
        val nextDay = buildDayContent(date.plusDays(1), nextEntries, sections, goals, collapsedMap)

        val currentWeek = buildWeekDates(date, date, goals, allWeeklyRows)
        val prevWeek = buildWeekDates(date.minusWeeks(1), date, goals, allWeeklyRows)
        val nextWeek = buildWeekDates(date.plusWeeks(1), date, goals, allWeeklyRows)

        LogUiState(
            selectedDate = date,
            prevDay = prevDay,
            currentDay = currentDay,
            nextDay = nextDay,
            prevWeek = prevWeek,
            currentWeek = currentWeek,
            nextWeek = nextWeek,
            selectionMode = selectionMode,
            isLoading = false,
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
        collapsedMap: Map<LocalDate, Set<Long>>,
    ): DayContent {
        val collapsed = collapsedMap[date] ?: emptySet()
        val totalLoggedMacros = entries.fold(Macros(0f, 0f, 0f, 0f)) { acc, entry -> acc + entry.macros }
        val summary = DailySummary(date, totalLoggedMacros, goals)
        val sectionMap = entries.groupBy { it.sectionId }
        val sectionsWithEntries = sections.map { section ->
            val sectionEntries = sectionMap[section.id] ?: emptyList()
            val sectionMacros =
                sectionEntries.fold(Macros(0f, 0f, 0f, 0f)) { acc, entry -> acc + entry.macros }
            SectionWithEntries(
                section = section,
                entries = sectionEntries.sortedBy { it.sortOrder },
                totalMacros = sectionMacros,
                isExpanded = !collapsed.contains(section.id)
            )
        }.sortedBy { it.section.timeOfDay }
        return DayContent(date, summary, sectionsWithEntries)
    }

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
        _selectionMode.value = SelectionMode.Off
    }

    fun toggleSectionExpanded(date: LocalDate, sectionId: Long) {
        val current = _collapsedSections.value.toMutableMap()
        val set = current[date].orEmpty().toMutableSet()
        if (set.contains(sectionId)) {
            set.remove(sectionId)
        } else {
            set.add(sectionId)
        }
        current[date] = set
        _collapsedSections.value = current
    }

    fun toggleSelectionMode(entryId: Long) {
        val currentMode = _selectionMode.value
        if (currentMode is SelectionMode.Selecting) {
            val newSelection = currentMode.selectedIds.toMutableSet()
            if (newSelection.contains(entryId)) {
                newSelection.remove(entryId)
            } else {
                newSelection.add(entryId)
            }
            if (newSelection.isEmpty()) {
                _selectionMode.value = SelectionMode.Off
            } else {
                _selectionMode.value = SelectionMode.Selecting(newSelection)
            }
        } else if (currentMode == SelectionMode.Off) {
            _selectionMode.value = SelectionMode.Selecting(setOf(entryId))
        }
    }

    fun exitSelectionMode() {
        _selectionMode.value = SelectionMode.Off
    }

    fun reseedMessageShown() {
        _reseedMessage.value = null
    }

    fun onReseedFoodDatabase() {
        viewModelScope.launch {
            val count = runCatching { reseedFoodDatabaseUseCase() }.getOrDefault(-1)
            _reseedMessage.value = if (count >= 0) {
                "Rebuilt food database with $count foods"
            } else {
                "Failed to rebuild food database"
            }
        }
    }

    private fun currentDaySections(): List<SectionWithEntries> {
        return uiState.value.currentDay?.sections ?: emptyList()
    }

    fun copySelectedEntries() {
        val ids = (_selectionMode.value as? SelectionMode.Selecting)?.selectedIds ?: return
        _selectionMode.value = SelectionMode.ChoosingDestination(ids, Action.Copy)
    }

    fun moveSelectedEntries() {
        val ids = (_selectionMode.value as? SelectionMode.Selecting)?.selectedIds ?: return
        _selectionMode.value = SelectionMode.ChoosingDestination(ids, Action.Move)
    }

    fun deleteSelectedEntries() {
        val ids = (_selectionMode.value as? SelectionMode.Selecting)?.selectedIds ?: return
        val entries = currentDaySections().flatMap { it.entries }.filter { it.id in ids }
        if (entries.isEmpty()) return
        viewModelScope.launch {
            deleteLogEntriesUseCase(entries)
            exitSelectionMode()
        }
    }

    fun confirmCopyMove(targetDate: LocalDate) {
        val mode = _selectionMode.value as? SelectionMode.ChoosingDestination ?: return
        val entries = currentDaySections().flatMap { it.entries }.filter { it.id in mode.selectedIds }
        if (entries.isEmpty()) return
        viewModelScope.launch {
            when (mode.action) {
                Action.Copy -> copyLogEntriesUseCase(entries, targetDate)
                Action.Move -> moveLogEntriesUseCase(entries, targetDate)
            }
            _selectionMode.value = SelectionMode.Off
        }
    }

    fun cancelChoosingDestination() {
        val mode = _selectionMode.value as? SelectionMode.ChoosingDestination ?: return
        _selectionMode.value = SelectionMode.Selecting(mode.selectedIds)
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
        val startOfWeek = referenceDate.minusDays(referenceDate.dayOfWeek.value.toLong() - 1)
        val today = LocalDate.now()
        val totalGoalKcal = goals.kcal.toFloat().coerceAtLeast(1f)

        val proteinGoalKcalShare = (goals.proteinG * 4f) / totalGoalKcal
        val carbsGoalKcalShare = (goals.carbsG * 4f) / totalGoalKcal
        val fatGoalKcalShare = (goals.fatG * 9f) / totalGoalKcal

        return (0..6).map { i ->
            val date = startOfWeek.plusDays(i.toLong())
            val dateStr = date.toString()
            val row = rowMap[dateStr]

            val proteinKcalActual = (row?.protein ?: 0f) * 4f
            val carbsKcalActual = (row?.carbs ?: 0f) * 4f
            val fatKcalActual = (row?.fat ?: 0f) * 9f

            WeekDay(
                date = date,
                dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dayNumber = date.dayOfMonth,
                isSelected = date == selectedDate,
                isToday = date == today,
                proteinKcalGoal = proteinGoalKcalShare,
                carbsKcalGoal = carbsGoalKcalShare,
                fatKcalGoal = fatGoalKcalShare,
                proteinKcalActual = (proteinKcalActual / totalGoalKcal).coerceAtMost(1f),
                carbsKcalActual = (carbsKcalActual / totalGoalKcal).coerceAtMost(1f),
                fatKcalActual = (fatKcalActual / totalGoalKcal).coerceAtMost(1f),
            )
        }
    }

    private data class Quadro(
        val date: LocalDate,
        val allEntries: Triple<List<LogEntry>, List<LogEntry>, List<LogEntry>>,
        val sections: List<Section>,
        val allWeeklyRows: List<DailyMacroRow>,
    )
}

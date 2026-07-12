package com.macrotrack.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotrack.domain.model.DailySummary
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Section
import com.macrotrack.domain.usecase.food.ReseedFoodDatabaseUseCase
import com.macrotrack.domain.usecase.log.CopyLogEntriesUseCase
import com.macrotrack.domain.usecase.log.DeleteLogEntriesUseCase
import com.macrotrack.domain.usecase.log.GetDailyLogUseCase
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
    private val reseedFoodDatabaseUseCase: ReseedFoodDatabaseUseCase
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _selectionMode = MutableStateFlow<SelectionMode>(SelectionMode.Off)
    private val _expandedSections = MutableStateFlow<Set<Long>>(emptySet())

    /** One-shot message surfaced to the UI after a reseed (or reseed failure). */
    private val _reseedMessage = MutableStateFlow<String?>(null)
    val reseedMessage: StateFlow<String?> = _reseedMessage

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LogUiState> = combine(
        combine(
            _selectedDate,
            _selectedDate.flatMapLatest { date -> getDailyLogUseCase(date) },
            getSectionsUseCase()
        ) { date, entries, sections ->
            Triple(date, entries, sections)
        },
        combine(
            getSettingsUseCase(),
            _selectionMode,
            _expandedSections
        ) { goals, selectionMode, expandedSections ->
            Triple(goals, selectionMode, expandedSections)
        }
    ) { data1, data2 ->
        val date = data1.first
        val entries = data1.second
        val sections = data1.third
        val goals = data2.first
        val selectionMode = data2.second
        val expandedSections = data2.third
        
        // Calculate daily summary
        val totalLoggedMacros = entries.fold(Macros(0f, 0f, 0f, 0f)) { acc, entry -> acc + entry.macros }
        val dailySummary = DailySummary(date, totalLoggedMacros, goals)

        // Build week dates
        val weekDates = buildWeekDates(date)

        // Group by sections
        val sectionMap = entries.groupBy { it.sectionId }
        val sectionsWithEntries = sections.map { section ->
            val sectionEntries = sectionMap[section.id] ?: emptyList()
            val sectionMacros = sectionEntries.fold(Macros(0f, 0f, 0f, 0f)) { acc, entry -> acc + entry.macros }
            SectionWithEntries(
                section = section,
                entries = sectionEntries.sortedBy { it.sortOrder },
                totalMacros = sectionMacros,
                isExpanded = !expandedSections.contains(section.id) // Default is true, so if in set, it's collapsed
            )
        }.sortedBy { it.section.sortOrder }

        LogUiState(
            selectedDate = date,
            weekDates = weekDates,
            dailySummary = dailySummary,
            sections = sectionsWithEntries,
            selectionMode = selectionMode,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LogUiState(isLoading = true)
    )

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
        // Optional: Reset selection mode when changing date
        _selectionMode.value = SelectionMode.Off
    }

    fun toggleSectionExpanded(sectionId: Long) {
        val current = _expandedSections.value.toMutableSet()
        if (current.contains(sectionId)) {
            current.remove(sectionId)
        } else {
            current.add(sectionId)
        }
        _expandedSections.value = current
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

    fun copySelectedEntries(targetDate: LocalDate, targetSectionId: Long?) {
        val selectedIds = (_selectionMode.value as? SelectionMode.Selecting)?.selectedIds ?: return
        viewModelScope.launch {
            // Need to get the actual entries to copy.
            // In a full implementation we'd fetch them from the repo.
            // For now, this is a placeholder to consume the UseCase and suppress warnings.
            // copyLogEntriesUseCase(entriesToCopy, targetDate, targetSectionId)
        }
        exitSelectionMode()
    }

    fun moveSelectedEntries(targetDate: LocalDate, targetSectionId: Long?) {
        val selectedIds = (_selectionMode.value as? SelectionMode.Selecting)?.selectedIds ?: return
        viewModelScope.launch {
            // moveLogEntriesUseCase(entriesToMove, targetDate, targetSectionId)
        }
        exitSelectionMode()
    }

    fun deleteSelectedEntries() {
        val selectedIds = (_selectionMode.value as? SelectionMode.Selecting)?.selectedIds ?: return
        viewModelScope.launch {
            // deleteLogEntriesUseCase(entriesToDelete)
        }
        exitSelectionMode()
    }

    private fun buildWeekDates(currentDate: LocalDate): List<WeekDay> {
        val startOfWeek = currentDate.minusDays(currentDate.dayOfWeek.value.toLong() - 1)
        return (0..6).map { i ->
            val date = startOfWeek.plusDays(i.toLong())
            WeekDay(
                date = date,
                dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dayNumber = date.dayOfMonth,
                kcalPercent = 0f, // TODO: Fetch weekly summary to get actual percents
                isSelected = date == currentDate,
                isToday = date == LocalDate.now()
            )
        }
    }
}

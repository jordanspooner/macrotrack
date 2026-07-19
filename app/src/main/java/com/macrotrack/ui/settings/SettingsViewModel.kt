package com.macrotrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotrack.data.repository.SettingsRepository
import com.macrotrack.domain.model.DailyGoals
import com.macrotrack.domain.model.Section
import com.macrotrack.domain.usecase.settings.GetSectionsUseCase
import com.macrotrack.domain.usecase.settings.GetSettingsUseCase
import com.macrotrack.domain.usecase.settings.SaveSectionDistributionUseCase
import com.macrotrack.domain.usecase.settings.UpdateDailyGoalsUseCase
import com.macrotrack.domain.usecase.settings.UpdateSectionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateDailyGoalsUseCase: UpdateDailyGoalsUseCase,
    private val getSectionsUseCase: GetSectionsUseCase,
    private val updateSectionsUseCase: UpdateSectionsUseCase,
    private val saveSectionDistributionUseCase: SaveSectionDistributionUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _draftGoals = MutableStateFlow(DailyGoals(150, 250, 65))
    private val _draftSections = MutableStateFlow(emptyList<DraftSection>())
    private val _isSavingGoals = MutableStateFlow(false)
    private val _goalsSaved = MutableStateFlow(false)
    private val _isSavingSections = MutableStateFlow(false)
    private val _sectionsSaved = MutableStateFlow(false)
    private val _sectionGoalsEnabled = MutableStateFlow(false)
    private val _sectionDistribution = MutableStateFlow<Map<Long, Map<MacroType, Float>>>(emptyMap())
    private val _distributionDirty = MutableStateFlow(false)
    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    init {
        viewModelScope.launch {
            getSettingsUseCase().collect { goals ->
                if (_draftGoals.value == DailyGoals(150, 250, 65)) {
                    _draftGoals.value = goals
                }
            }
        }
        viewModelScope.launch {
            getSectionsUseCase().collect { sections ->
                if (_draftSections.value.isEmpty()) {
                    _draftSections.value = sections.map { it.toDraftSection() }
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.getSectionGoalsEnabled().collect { enabled ->
                _sectionGoalsEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            settingsRepository.getSectionGoalDistribution().collect { json ->
                if (!json.isNullOrBlank()) {
                    _sectionDistribution.value = parseDistribution(json)
                }
            }
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(getSettingsUseCase(), _draftGoals, _isSavingGoals, _goalsSaved) { goals, draft, saving, saved ->
            Quad(goals, draft, saving, saved)
        },
        combine(getSectionsUseCase(), _draftSections, _isSavingSections, _sectionsSaved) { sections, draft, saving, saved ->
            Quad(sections, draft, saving, saved)
        },
        combine(_sectionGoalsEnabled, _sectionDistribution, _distributionDirty, _hasUnsavedChanges) { enabled, distribution, dirty, unsaved ->
            Quad(enabled, distribution, dirty, unsaved)
        }
    ) { goalsQuad, sectionsQuad, distQuad ->
        SettingsUiState(
            dailyGoals = goalsQuad.first,
            draftGoals = goalsQuad.second,
            isSavingGoals = goalsQuad.third,
            goalsSaved = goalsQuad.fourth,
            sections = sectionsQuad.first,
            draftSections = sectionsQuad.second,
            sortedDraftSections = sectionsQuad.second.sortedBy { it.timeOfDay },
            isSavingSections = sectionsQuad.third,
            sectionsSaved = sectionsQuad.fourth,
            sectionGoalsEnabled = distQuad.first,
            sectionDistribution = distQuad.second,
            distributionDirty = distQuad.third,
            hasUnsavedChanges = distQuad.fourth,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    fun updateDraftGoalProtein(value: String) {
        val g = value.toIntOrNull() ?: return
        _draftGoals.value = _draftGoals.value.copy(proteinG = g)
        _hasUnsavedChanges.value = true
    }

    fun updateDraftGoalCarbs(value: String) {
        val g = value.toIntOrNull() ?: return
        _draftGoals.value = _draftGoals.value.copy(carbsG = g)
        _hasUnsavedChanges.value = true
    }

    fun updateDraftGoalFat(value: String) {
        val g = value.toIntOrNull() ?: return
        _draftGoals.value = _draftGoals.value.copy(fatG = g)
        _hasUnsavedChanges.value = true
    }

    fun saveGoals() {
        viewModelScope.launch {
            _isSavingGoals.value = true
            updateDailyGoalsUseCase(_draftGoals.value)
            _isSavingGoals.value = false
            _goalsSaved.value = true
            _hasUnsavedChanges.value = false
        }
    }

    fun updateDraftSectionName(id: Long, name: String) {
        val list = _draftSections.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index in list.indices) {
            list[index] = list[index].copy(name = name)
            _draftSections.value = list
            _hasUnsavedChanges.value = true
        }
    }

    fun updateDraftSectionTime(id: Long, time: LocalTime) {
        val list = _draftSections.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index in list.indices) {
            list[index] = list[index].copy(timeOfDay = time)
            _draftSections.value = list
            _hasUnsavedChanges.value = true
        }
    }

    fun removeDraftSection(id: Long) {
        _draftSections.value = _draftSections.value.filter { it.id != id }
        _hasUnsavedChanges.value = true
    }

    fun addDraftSection(name: String) {
        val maxId = _draftSections.value.maxOfOrNull { it.id } ?: 0L
        val newSection = DraftSection(
            id = maxId + 1,
            name = name,
            timeOfDay = LocalTime.of(12, 0),
            isNew = true
        )
        _draftSections.value = _draftSections.value + newSection
        _hasUnsavedChanges.value = true
    }


    fun resetSectionsToDefaults() {
        _draftSections.value = listOf(
            DraftSection(id = 1, name = "Breakfast", timeOfDay = LocalTime.of(6, 0)),
            DraftSection(id = 2, name = "Lunch", timeOfDay = LocalTime.of(12, 0)),
            DraftSection(id = 3, name = "Dinner", timeOfDay = LocalTime.of(18, 0)),
        )
        _hasUnsavedChanges.value = true
    }

    fun saveSections() {
        viewModelScope.launch {
            _isSavingSections.value = true
            val sections = _draftSections.value
                .sortedBy { it.timeOfDay }
                .map { ds ->
                    Section(
                        id = ds.id,
                        name = ds.name,
                        timeOfDay = ds.timeOfDay,
                    )
                }
            updateSectionsUseCase(sections)
            _isSavingSections.value = false
            _sectionsSaved.value = true
            _hasUnsavedChanges.value = false
        }
    }

    fun onSnackbarShown() {
        _goalsSaved.value = false
        _sectionsSaved.value = false
    }

    fun discardChanges() {
        _hasUnsavedChanges.value = false
        _draftGoals.value = DailyGoals(150, 250, 65)
        _draftSections.value = emptyList()
    }

    fun setSectionGoalsEnabled(enabled: Boolean) {
        _sectionGoalsEnabled.value = enabled
        if (enabled && _sectionDistribution.value.isEmpty()) {
            _sectionDistribution.value = initDistribution(_draftSections.value)
        }
        persistDistribution()
    }

    fun updateDistribution(sectionId: Long, macroType: MacroType, rawValue: Float) {
        val newValue = rawValue.coerceIn(0f, 100f)
        val current = _sectionDistribution.value.toMutableMap()
        val sectionIds = _draftSections.value.map { it.id }
        val touchedMacros = current.getOrPut(sectionId) { mutableMapOf() }.toMutableMap()
        touchedMacros[macroType] = newValue
        current[sectionId] = touchedMacros

        val othersTotal = sectionIds.filter { it != sectionId }
            .sumOf { (current[it]?.get(macroType) ?: 0f).toDouble() }
            .toFloat()
        val totalAfter = newValue + othersTotal
        if (totalAfter > 100f && sectionIds.size > 1) {
            val targetOthersTotal = (100f - newValue).coerceAtLeast(0f)
            val factor = if (othersTotal > 0f) targetOthersTotal / othersTotal else 0f
            for (otherId in sectionIds.filter { it != sectionId }) {
                val om = current.getOrPut(otherId) { mutableMapOf() }.toMutableMap()
                val otherOld = om[macroType] ?: 0f
                om[macroType] = (otherOld * factor).coerceIn(0f, 100f)
                current[otherId] = om
            }
        }
        _sectionDistribution.value = current
        normalizeResidual(macroType)
        persistDistribution()
    }

    private fun normalizeResidual(macroType: MacroType) {
        val sectionIds = _draftSections.value.map { it.id }
        val total = sectionIds.sumOf {
            (_sectionDistribution.value[it]?.get(macroType) ?: 0f).toDouble()
        }.toFloat()
        if (total >= 99.95f && total < 100f) {
            val residual = 100f - total
            val target = sectionIds.minByOrNull {
                _sectionDistribution.value[it]?.get(macroType) ?: 0f
            } ?: return
            val map = _sectionDistribution.value.toMutableMap()
            val tm = (map[target] ?: emptyMap()).toMutableMap()
            tm[macroType] = (tm[macroType] ?: 0f) + residual
            map[target] = tm
            _sectionDistribution.value = map
        }
    }

    private fun Section.toDraftSection() = DraftSection(
        id = id,
        name = name,
        timeOfDay = timeOfDay,
    )

    private fun persistDistribution() {
        viewModelScope.launch {
            val json = serializeDistribution(_sectionDistribution.value)
            saveSectionDistributionUseCase(_sectionGoalsEnabled.value, json)
            _distributionDirty.value = false
        }
    }

    companion object {
        fun initDistribution(sections: List<DraftSection>): Map<Long, Map<MacroType, Float>> {
            if (sections.isEmpty()) return emptyMap()
            val percentPerSection = 100f / sections.size
            val result = mutableMapOf<Long, Map<MacroType, Float>>()
            for (ds in sections) {
                result[ds.id] = mapOf(MacroType.PROTEIN to percentPerSection, MacroType.CARBS to percentPerSection, MacroType.FAT to percentPerSection)
            }
            return result
        }

        fun serializeDistribution(distribution: Map<Long, Map<MacroType, Float>>): String {
            val sb = StringBuilder()
            sb.append("{")
            distribution.entries.forEachIndexed { i, (sectionId, macros) ->
                if (i > 0) sb.append(",")
                sb.append("\"$sectionId\":{")
                macros.entries.forEachIndexed { j, (type, percent) ->
                    if (j > 0) sb.append(",")
                    sb.append("\"${type.name}\":${"%.1f".format(percent)}")
                }
                sb.append("}")
            }
            sb.append("}")
            return sb.toString()
        }

        fun parseDistribution(json: String): Map<Long, Map<MacroType, Float>> {
            val result = mutableMapOf<Long, Map<MacroType, Float>>()
            val trimmed = json.trim().removeSurrounding("{", "}")
            if (trimmed.isBlank()) return result
            val parts = splitTopLevel(trimmed)
            for (part in parts) {
                val colonIdx = part.indexOf(':')
                if (colonIdx < 0) continue
                val key = part.substring(0, colonIdx).trim().removeSurrounding("\"")
                val sectionId = key.toLongOrNull() ?: continue
                val innerJson = part.substring(colonIdx + 1).trim().removeSurrounding("{", "}")
                val macroMap = mutableMapOf<MacroType, Float>()
                for (item in splitTopLevel(innerJson)) {
                    val mColon = item.indexOf(':')
                    if (mColon < 0) continue
                    val macroKey = item.substring(0, mColon).trim().removeSurrounding("\"")
                    val macroVal = item.substring(mColon + 1).trim().toFloatOrNull() ?: continue
                    try { macroMap[MacroType.valueOf(macroKey)] = macroVal } catch (_: Exception) {}
                }
                result[sectionId] = macroMap
            }
            return result
        }

        private fun splitTopLevel(s: String): List<String> {
            val result = mutableListOf<String>()
            var depth = 0
            val current = StringBuilder()
            for (c in s) {
                when (c) {
                    '{' -> { depth++; current.append(c) }
                    '}' -> { depth--; current.append(c) }
                    ',' -> {
                        if (depth == 0) {
                            if (current.isNotEmpty()) result.add(current.toString().trim())
                            current.clear()
                        } else current.append(c)
                    }
                    else -> current.append(c)
                }
            }
            if (current.isNotEmpty()) result.add(current.toString().trim())
            return result
        }
    }
}
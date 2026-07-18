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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
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

    init {
        viewModelScope.launch {
            getSettingsUseCase().collect { goals ->
                if (!_draftGoals.compareAndSet(DailyGoals(150, 250, 65), goals)) {
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
        combine(_sectionGoalsEnabled, _sectionDistribution, _distributionDirty) { enabled, distribution, dirty ->
            Triple(enabled, distribution, dirty)
        }
    ) { goalsQuad, sectionsQuad, distTriple ->
        SettingsUiState(
            dailyGoals = goalsQuad.first,
            draftGoals = goalsQuad.second,
            isSavingGoals = goalsQuad.third,
            goalsSaved = goalsQuad.fourth,
            sections = sectionsQuad.first,
            draftSections = sectionsQuad.second,
            isSavingSections = sectionsQuad.third,
            sectionsSaved = sectionsQuad.fourth,
            sectionGoalsEnabled = distTriple.first,
            sectionDistribution = distTriple.second,
            distributionDirty = distTriple.third,
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
    }

    fun updateDraftGoalCarbs(value: String) {
        val g = value.toIntOrNull() ?: return
        _draftGoals.value = _draftGoals.value.copy(carbsG = g)
    }

    fun updateDraftGoalFat(value: String) {
        val g = value.toIntOrNull() ?: return
        _draftGoals.value = _draftGoals.value.copy(fatG = g)
    }

    fun saveGoals() {
        viewModelScope.launch {
            _isSavingGoals.value = true
            updateDailyGoalsUseCase(_draftGoals.value)
            _isSavingGoals.value = false
            _goalsSaved.value = true
            delay(2000)
            _goalsSaved.value = false
        }
    }

    fun updateDraftSectionName(index: Int, name: String) {
        val list = _draftSections.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(name = name)
            _draftSections.value = list
        }
    }

    fun updateDraftSectionTime(index: Int, time: LocalTime) {
        val list = _draftSections.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(timeOfDay = time)
            _draftSections.value = list
        }
    }

    fun removeDraftSection(index: Int) {
        val list = _draftSections.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _draftSections.value = list
        }
    }

    fun addDraftSection(name: String) {
        val maxId = _draftSections.value.maxOfOrNull { it.id } ?: 0L
        val maxOrder = _draftSections.value.maxOfOrNull { it.sortOrder } ?: 0
        val newSection = DraftSection(
            id = maxId + 1,
            name = name,
            timeOfDay = LocalTime.of(12, 0),
            sortOrder = maxOrder + 1,
            isNew = true
        )
        _draftSections.value = _draftSections.value + newSection
    }

    fun moveDraftSectionUp(index: Int) {
        val list = _draftSections.value.toMutableList()
        if (index > 0 && index < list.size) {
            val item = list.removeAt(index)
            list.add(index - 1, item)
            _draftSections.value = list.mapIndexed { i, ds -> ds.copy(sortOrder = i) }
        }
    }

    fun moveDraftSectionDown(index: Int) {
        val list = _draftSections.value.toMutableList()
        if (index < list.size - 1) {
            val item = list.removeAt(index)
            list.add(index + 1, item)
            _draftSections.value = list.mapIndexed { i, ds -> ds.copy(sortOrder = i) }
        }
    }

    fun resetSectionsToDefaults() {
        _draftSections.value = listOf(
            DraftSection(id = 1, name = "Breakfast", timeOfDay = LocalTime.of(7, 30), sortOrder = 0),
            DraftSection(id = 2, name = "Lunch", timeOfDay = LocalTime.of(12, 30), sortOrder = 1),
            DraftSection(id = 3, name = "Dinner", timeOfDay = LocalTime.of(19, 0), sortOrder = 2),
            DraftSection(id = 4, name = "Snacks", timeOfDay = LocalTime.of(15, 0), sortOrder = 3),
        )
    }

    fun saveSections() {
        viewModelScope.launch {
            _isSavingSections.value = true
            val sections = _draftSections.value.map { ds ->
                Section(
                    id = ds.id,
                    name = ds.name,
                    timeOfDay = ds.timeOfDay,
                    sortOrder = ds.sortOrder,
                )
            }
            updateSectionsUseCase(sections)
            _isSavingSections.value = false
            _sectionsSaved.value = true
            delay(2000)
            _sectionsSaved.value = false
        }
    }

    fun setSectionGoalsEnabled(enabled: Boolean) {
        _sectionGoalsEnabled.value = enabled
        if (enabled && _sectionDistribution.value.isEmpty()) {
            _sectionDistribution.value = initDistribution(_draftSections.value)
        }
        persistDistribution()
    }

    fun updateDistribution(sectionId: Long, macroType: MacroType, value: Float) {
        val current = _sectionDistribution.value.toMutableMap()
        val macroMap = current.getOrPut(sectionId) { mutableMapOf() }.toMutableMap()
        val oldValue = macroMap[macroType] ?: 0f
        macroMap[macroType] = value.coerceIn(0f, 100f)
        current[sectionId] = macroMap

        val sectionIds = _draftSections.value.map { it.id }
        val otherIds = sectionIds.filter { it != sectionId }
        if (otherIds.isNotEmpty()) {
            val remaining = (100f - value) / (100f - oldValue).coerceAtLeast(1f)
            for (otherId in otherIds) {
                val otherMacros = current.getOrPut(otherId) { mutableMapOf() }.toMutableMap()
                val otherOld = otherMacros[macroType] ?: 0f
                otherMacros[macroType] = (otherOld * remaining).coerceIn(0f, 100f)
                current[otherId] = otherMacros
            }
        }
        _sectionDistribution.value = current
        _distributionDirty.value = true
    }

    private fun Section.toDraftSection() = DraftSection(
        id = id,
        name = name,
        timeOfDay = timeOfDay,
        sortOrder = sortOrder,
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
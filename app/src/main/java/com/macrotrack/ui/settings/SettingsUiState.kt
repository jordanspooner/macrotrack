package com.macrotrack.ui.settings

import com.macrotrack.domain.model.DailyGoals
import com.macrotrack.domain.model.Section
import java.time.LocalTime

/**
 * Re-export of the domain macro type so existing settings UI callers and the
 * persisted distribution model keep compiling unchanged.
 */
typealias MacroType = com.macrotrack.domain.model.MacroType

data class DraftSection(
    val id: Long,
    val name: String,
    val timeOfDay: LocalTime,
    val isNew: Boolean = false,
)

data class SettingsUiState(
    val dailyGoals: DailyGoals = DailyGoals(150, 250, 65),
    val draftGoals: DailyGoals = DailyGoals(150, 250, 65),
    val isSavingGoals: Boolean = false,
    val goalsSaved: Boolean = false,
    val sections: List<Section> = emptyList(),
    val draftSections: List<DraftSection> = emptyList(),
    val sortedDraftSections: List<DraftSection> = emptyList(),
    val isSavingSections: Boolean = false,
    val sectionsSaved: Boolean = false,
    val sectionGoalsEnabled: Boolean = false,
    val sectionDistribution: Map<Long, Map<MacroType, Float>> = emptyMap(),
    val distributionDirty: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
)
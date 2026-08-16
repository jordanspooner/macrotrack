package com.macrotrack.domain.usecase.settings

import com.macrotrack.data.local.datastore.SectionGoalCodec
import com.macrotrack.data.repository.SettingsRepository
import com.macrotrack.domain.model.SectionGoals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Combines the section-goals enabled flag with the persisted JSON distribution,
 * surfacing them as a typed [SectionGoals] model that consuming layers (e.g.
 * LogViewModel) can use without depending on settings UI types.
 */
class GetSectionGoalsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<SectionGoals> {
        return combine(
            settingsRepository.getSectionGoalsEnabled(),
            settingsRepository.getSectionGoalDistribution(),
        ) { enabled, json ->
            SectionGoals(
                enabled = enabled,
                percentages = SectionGoalCodec.parse(json),
            )
        }
    }
}
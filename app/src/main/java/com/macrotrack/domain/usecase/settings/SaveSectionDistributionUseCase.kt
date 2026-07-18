package com.macrotrack.domain.usecase.settings

import com.macrotrack.data.repository.SettingsRepository
import javax.inject.Inject

class SaveSectionDistributionUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        enabled: Boolean,
        distributionJson: String
    ) {
        settingsRepository.setSectionGoalsEnabled(enabled)
        if (enabled) {
            settingsRepository.setSectionGoalDistribution(distributionJson)
        }
    }
}
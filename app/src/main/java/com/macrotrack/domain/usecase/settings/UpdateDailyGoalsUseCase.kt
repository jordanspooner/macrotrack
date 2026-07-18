package com.macrotrack.domain.usecase.settings

import com.macrotrack.data.repository.SettingsRepository
import com.macrotrack.domain.model.DailyGoals
import javax.inject.Inject

class UpdateDailyGoalsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(goals: DailyGoals) {
        settingsRepository.updateDailyGoals(goals)
    }
}
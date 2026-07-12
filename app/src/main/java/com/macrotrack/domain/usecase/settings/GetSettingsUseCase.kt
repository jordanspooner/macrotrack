package com.macrotrack.domain.usecase.settings

import com.macrotrack.data.repository.SettingsRepository
import com.macrotrack.domain.model.DailyGoals
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<DailyGoals> {
        return settingsRepository.getDailyGoals()
    }
}

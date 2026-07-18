package com.macrotrack.data.repository

import com.macrotrack.domain.model.DailyGoals
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getDailyGoals(): Flow<DailyGoals>
    suspend fun updateDailyGoals(goals: DailyGoals)

    fun getSectionGoalsEnabled(): Flow<Boolean>
    suspend fun setSectionGoalsEnabled(enabled: Boolean)
    fun getSectionGoalDistribution(): Flow<String?>
    suspend fun setSectionGoalDistribution(json: String)
}

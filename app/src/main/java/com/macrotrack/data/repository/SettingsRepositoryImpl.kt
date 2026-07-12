package com.macrotrack.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.macrotrack.data.local.datastore.SettingsKeys
import com.macrotrack.domain.model.DailyGoals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {
    override fun getDailyGoals(): Flow<DailyGoals> {
        return dataStore.data.map { prefs ->
            DailyGoals(
                proteinG = prefs[SettingsKeys.PROTEIN_GOAL_G] ?: 150,
                carbsG = prefs[SettingsKeys.CARBS_GOAL_G] ?: 250,
                fatG = prefs[SettingsKeys.FAT_GOAL_G] ?: 65
            )
        }
    }

    override suspend fun updateDailyGoals(goals: DailyGoals) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.PROTEIN_GOAL_G] = goals.proteinG
            prefs[SettingsKeys.CARBS_GOAL_G] = goals.carbsG
            prefs[SettingsKeys.FAT_GOAL_G] = goals.fatG
            prefs[SettingsKeys.KCAL_GOAL] = goals.kcal
        }
    }
}

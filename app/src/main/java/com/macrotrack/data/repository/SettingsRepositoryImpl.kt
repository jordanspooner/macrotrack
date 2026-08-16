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

    override fun getSectionGoalsEnabled(): Flow<Boolean> {
        return dataStore.data.map { prefs ->
            prefs[SettingsKeys.SECTION_GOALS_ENABLED] ?: false
        }
    }

    override suspend fun setSectionGoalsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.SECTION_GOALS_ENABLED] = enabled
        }
    }

    override fun getSectionGoalDistribution(): Flow<String?> {
        return dataStore.data.map { prefs ->
            prefs[SettingsKeys.SECTION_GOAL_DISTRIBUTION]
        }
    }

    override suspend fun setSectionGoalDistribution(json: String) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.SECTION_GOAL_DISTRIBUTION] = json
        }
    }

    override fun getLastPortions(): Flow<Map<Long, Float>> {
        return dataStore.data.map { prefs ->
            parseLastPortions(prefs[SettingsKeys.LAST_PORTION_MAP])
        }
    }

    override suspend fun setLastPortion(foodId: Long, portionG: Float) {
        dataStore.edit { prefs ->
            val current = parseLastPortions(prefs[SettingsKeys.LAST_PORTION_MAP]).toMutableMap()
            current[foodId] = portionG
            prefs[SettingsKeys.LAST_PORTION_MAP] = serializeLastPortions(current)
        }
    }

    private fun parseLastPortions(json: String?): Map<Long, Float> {
        if (json.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<Long, Float>()
        val trimmed = json.trim().removeSurrounding("{", "}")
        if (trimmed.isBlank()) return result
        for (part in trimmed.split(",")) {
            val kv = part.trim().split(":")
            if (kv.size == 2) {
                val id = kv[0].trim().removeSurrounding("\"").toLongOrNull()
                val value = kv[1].trim().toFloatOrNull()
                if (id != null && value != null) result[id] = value
            }
        }
        return result
    }

    private fun serializeLastPortions(map: Map<Long, Float>): String {
        if (map.isEmpty()) return "{}"
        return "{" + map.entries.joinToString(",") { (id, v) -> "\"$id\":$v" } + "}"
    }
}

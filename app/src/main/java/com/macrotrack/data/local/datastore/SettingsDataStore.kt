package com.macrotrack.data.local.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    val KCAL_GOAL = intPreferencesKey("kcal_goal")           // default: 2000
    val PROTEIN_GOAL_G = intPreferencesKey("protein_goal_g") // default: 150
    val CARBS_GOAL_G = intPreferencesKey("carbs_goal_g")     // default: 250
    val FAT_GOAL_G = intPreferencesKey("fat_goal_g")         // default: 65
    val SECTION_GOALS_ENABLED = booleanPreferencesKey("section_goals_enabled")
    val SECTION_GOAL_DISTRIBUTION = stringPreferencesKey("section_goal_distribution") // JSON string
}

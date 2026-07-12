package com.macrotrack

import android.app.Application
import com.macrotrack.data.local.db.FoodDatabaseSeeder
import com.macrotrack.data.local.db.SectionSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MacroTrackApp : Application() {

    @Inject
    lateinit var foodDatabaseSeeder: FoodDatabaseSeeder

    @Inject
    lateinit var sectionSeeder: SectionSeeder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            foodDatabaseSeeder.seedIfEmpty()
            sectionSeeder.seedIfEmpty()
        }
    }
}

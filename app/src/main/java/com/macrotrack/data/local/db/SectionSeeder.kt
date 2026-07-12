package com.macrotrack.data.local.db

import com.macrotrack.data.local.db.dao.SectionDao
import com.macrotrack.data.local.db.entity.SectionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SectionSeeder @Inject constructor(
    private val sectionDao: SectionDao
) {
    suspend fun seedIfEmpty() {
        if (sectionDao.count() > 0) return
        withContext(Dispatchers.IO) {
            sectionDao.insertAll(defaultSections())
        }
    }

    private fun defaultSections(): List<SectionEntity> = listOf(
        SectionEntity(name = "Breakfast", timeOfDay = "07:30", sortOrder = 0),
        SectionEntity(name = "Lunch",     timeOfDay = "12:30", sortOrder = 1),
        SectionEntity(name = "Dinner",    timeOfDay = "19:00", sortOrder = 2),
        SectionEntity(name = "Snacks",    timeOfDay = "15:00", sortOrder = 3),
    )
}

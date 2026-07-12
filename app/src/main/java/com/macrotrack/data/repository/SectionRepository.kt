package com.macrotrack.data.repository

import com.macrotrack.domain.model.Section
import kotlinx.coroutines.flow.Flow

interface SectionRepository {
    fun getAllSections(): Flow<List<Section>>
    suspend fun insert(section: Section): Long
    suspend fun insertAll(sections: List<Section>)
    suspend fun update(section: Section)
    suspend fun updateAll(sections: List<Section>)
    suspend fun delete(section: Section)
}

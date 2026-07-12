package com.macrotrack.data.repository

import com.macrotrack.data.local.db.dao.SectionDao
import com.macrotrack.data.mapper.toDomain
import com.macrotrack.data.mapper.toEntity
import com.macrotrack.domain.model.Section
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SectionRepositoryImpl @Inject constructor(
    private val sectionDao: SectionDao
) : SectionRepository {
    override fun getAllSections(): Flow<List<Section>> {
        return sectionDao.getAllSections().map { entities -> 
            entities.map { it.toDomain() } 
        }
    }

    override suspend fun insert(section: Section): Long {
        return sectionDao.insertSection(section.toEntity())
    }

    override suspend fun insertAll(sections: List<Section>) {
        sectionDao.insertAll(sections.map { it.toEntity() })
    }

    override suspend fun update(section: Section) {
        sectionDao.updateSection(section.toEntity())
    }

    override suspend fun updateAll(sections: List<Section>) {
        sectionDao.updateAll(sections.map { it.toEntity() })
    }

    override suspend fun delete(section: Section) {
        sectionDao.deleteSection(section.toEntity())
    }
}

package com.macrotrack.data.repository

import com.macrotrack.data.local.db.dao.FoodSourceDao
import com.macrotrack.data.mapper.toDomain
import com.macrotrack.data.mapper.toEntity
import com.macrotrack.domain.model.FoodSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FoodSourceRepositoryImpl @Inject constructor(
    private val foodSourceDao: FoodSourceDao
) : FoodSourceRepository {
    override fun getInstalledSources(): Flow<List<FoodSource>> {
        return foodSourceDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getById(id: String): FoodSource? {
        return foodSourceDao.getById(id)?.toDomain()
    }

    override fun getNonUserSources(): Flow<List<FoodSource>> {
        return foodSourceDao.getNonUserSources().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun countNonUserSources(): Int {
        return foodSourceDao.countNonUserSources()
    }

    override suspend fun upsert(source: FoodSource) {
        foodSourceDao.upsert(source.toEntity())
    }

    override suspend fun delete(id: String) {
        foodSourceDao.delete(id)
    }
}

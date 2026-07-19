package com.macrotrack.data.repository

import com.macrotrack.domain.model.FoodSource
import kotlinx.coroutines.flow.Flow

interface FoodSourceRepository {
    fun getInstalledSources(): Flow<List<FoodSource>>
    suspend fun getById(id: String): FoodSource?
    fun getNonUserSources(): Flow<List<FoodSource>>
    suspend fun countNonUserSources(): Int
    suspend fun upsert(source: FoodSource)
    suspend fun delete(id: String)
}

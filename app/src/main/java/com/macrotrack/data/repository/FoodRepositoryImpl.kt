package com.macrotrack.data.repository

import com.macrotrack.data.local.db.FoodDatabaseSeeder
import com.macrotrack.data.local.db.dao.FoodItemDao
import com.macrotrack.data.mapper.toDomain
import com.macrotrack.domain.model.FoodItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FoodRepositoryImpl @Inject constructor(
    private val foodItemDao: FoodItemDao,
    private val foodDatabaseSeeder: FoodDatabaseSeeder
) : FoodRepository {
    override fun searchFts(query: String): Flow<List<FoodItem>> {
        return foodItemDao.searchFoods(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getFoodByEan(ean: String): FoodItem? {
        return foodItemDao.getFoodByEan(ean)?.toDomain()
    }

    override suspend fun getFoodById(id: Long): FoodItem? {
        return foodItemDao.getFoodById(id)?.toDomain()
    }

    override suspend fun reseedFromAsset(): Int {
        return foodDatabaseSeeder.reseed()
    }
}

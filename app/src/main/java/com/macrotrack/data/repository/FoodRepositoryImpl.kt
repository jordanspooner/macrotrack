package com.macrotrack.data.repository

import com.macrotrack.data.local.db.dao.FoodItemDao
import com.macrotrack.data.mapper.toDomain
import com.macrotrack.data.mapper.toEntity
import com.macrotrack.domain.model.FoodItem
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FoodRepositoryImpl @Inject constructor(
    private val foodItemDao: FoodItemDao
) : FoodRepository {
    override fun searchFts(query: String): Flow<List<FoodItem>> {
        return foodItemDao.searchFoods(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllUserFoods(): Flow<List<FoodItem>> {
        return foodItemDao.getAllUserFoods().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchUserFoods(query: String): Flow<List<FoodItem>> {
        return foodItemDao.searchUserFoods(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getFoodByEan(ean: String): FoodItem? {
        return foodItemDao.getFoodByEan(ean)?.toDomain()
    }

    override suspend fun getFoodById(id: Long): FoodItem? {
        return foodItemDao.getFoodById(id)?.toDomain()
    }

    override suspend fun insertUserFood(food: FoodItem): FoodItem {
        val id = foodItemDao.insert(food.toEntity().copy(id = 0, source = "USER", dataSourceId = "my-foods"))
        return food.copy(id = id, dataSourceId = "my-foods")
    }

    override suspend fun insertAll(foods: List<FoodItem>) {
        foodItemDao.insertAll(foods.map { it.toEntity() })
    }

    override suspend fun deleteByDataSource(sourceId: String) {
        foodItemDao.deleteByDataSource(sourceId)
    }

    override suspend fun countByDataSource(sourceId: String): Int {
        return foodItemDao.countByDataSource(sourceId)
    }

    override suspend fun countUserFoods(): Int {
        return foodItemDao.countUserFoods()
    }

    override suspend fun updateUserFood(food: FoodItem) {
        foodItemDao.update(food.toEntity())
    }

    override suspend fun deleteUserFood(id: Long) {
        foodItemDao.deleteById(id)
    }

    override suspend fun count(): Int {
        return foodItemDao.count()
    }

    override suspend fun clearAll() {
        foodItemDao.clearAll()
    }
}

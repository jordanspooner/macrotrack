package com.macrotrack.data.repository

import com.macrotrack.data.local.db.dao.UserFoodDao
import com.macrotrack.data.mapper.toDomain
import com.macrotrack.data.mapper.toUserFoodEntity
import com.macrotrack.domain.model.FoodItem
import javax.inject.Inject

class UserFoodRepositoryImpl @Inject constructor(
    private val userFoodDao: UserFoodDao
) : UserFoodRepository {
    override suspend fun insert(food: FoodItem): FoodItem {
        val id = userFoodDao.insertUserFood(food.toUserFoodEntity())
        return food.copy(id = id, source = com.macrotrack.domain.model.Source.USER)
    }

    override suspend fun getByEan(ean: String): FoodItem? {
        return userFoodDao.getUserFoodByEan(ean)?.toDomain()
    }

    override suspend fun getById(id: Long): FoodItem? {
        return userFoodDao.getUserFoodById(id)?.toDomain()
    }
}

package com.macrotrack.data.local.db

import com.macrotrack.data.local.db.dao.FoodSourceDao
import com.macrotrack.data.local.db.entity.FoodSourceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodSourceSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foodSourceDao: FoodSourceDao
) {
    suspend fun seedIfNeeded() {
        val existing = foodSourceDao.getById("my-foods")
        if (existing != null) return
        withContext(Dispatchers.IO) {
            foodSourceDao.upsert(
                FoodSourceEntity(
                    id = "my-foods",
                    name = "My foods",
                    description = "Foods you have added yourself",
                    version = null,
                    publisher = null,
                    itemCount = 0,
                    installedAt = System.currentTimeMillis(),
                    isUserSource = true
                )
            )
        }
    }
}

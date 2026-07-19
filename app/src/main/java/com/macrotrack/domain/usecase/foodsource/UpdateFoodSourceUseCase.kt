package com.macrotrack.domain.usecase.foodsource

import com.macrotrack.domain.model.FoodSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateFoodSourceUseCase @Inject constructor(
    private val downloadFoodSourceUseCase: DownloadFoodSourceUseCase
) {
    suspend operator fun invoke(source: FoodSource, onProgress: (Float) -> Unit): Result<Unit> {
        return downloadFoodSourceUseCase(source, onProgress)
    }
}

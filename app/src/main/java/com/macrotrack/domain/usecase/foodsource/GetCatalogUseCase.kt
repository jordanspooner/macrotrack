package com.macrotrack.domain.usecase.foodsource

import com.macrotrack.data.remote.FoodSourceCatalogRepository
import com.macrotrack.data.repository.FoodSourceRepository
import com.macrotrack.domain.model.FoodSource
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetCatalogUseCase @Inject constructor(
    private val catalogRepository: FoodSourceCatalogRepository,
    private val foodSourceRepository: FoodSourceRepository
) {
    suspend operator fun invoke(): Result<List<FoodSource>> {
        val catalogResult = catalogRepository.fetchCatalog()
        val installedSources = foodSourceRepository.getNonUserSources().first()
        val installedMap = installedSources.associateBy { it.id }

        return catalogResult.map { catalog ->
            val catalogMapped = catalog.map { remote ->
                val installed = installedMap[remote.id]
                when {
                    installed == null -> remote.copy(status = FoodSource.Status.NOT_INSTALLED)
                    installed.version == remote.latestVersion -> installed.copy(
                        status = FoodSource.Status.INSTALLED_UP_TO_DATE,
                        latestVersion = remote.latestVersion,
                        downloadSizeBytes = remote.downloadSizeBytes,
                        downloadUrl = remote.downloadUrl,
                        checksumSha256 = remote.checksumSha256
                    )
                    else -> installed.copy(
                        status = FoodSource.Status.INSTALLED_UPDATE_AVAILABLE,
                        latestVersion = remote.latestVersion,
                        downloadSizeBytes = remote.downloadSizeBytes,
                        downloadUrl = remote.downloadUrl,
                        checksumSha256 = remote.checksumSha256
                    )
                }
            }

            val installedNotInCatalog = installedSources.filter { installed ->
                catalogMapped.none { it.id == installed.id }
            }

            listOf(
                FoodSource(
                    id = "my-foods",
                    name = "My foods",
                    description = "Foods you have added yourself",
                    status = FoodSource.Status.MY_FOODS,
                    isUserSource = true
                )
            ) + installedNotInCatalog + catalogMapped
        }
    }
}

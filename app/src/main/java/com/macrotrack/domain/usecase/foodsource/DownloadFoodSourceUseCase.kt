package com.macrotrack.domain.usecase.foodsource

import android.content.Context
import com.macrotrack.data.local.db.FoodSourceInstaller
import com.macrotrack.data.remote.FoodSourceDownloader
import com.macrotrack.data.repository.FoodRepository
import com.macrotrack.data.repository.FoodSourceRepository
import com.macrotrack.domain.model.FoodSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadFoodSourceUseCase @Inject constructor(
    private val downloader: FoodSourceDownloader,
    private val installer: FoodSourceInstaller,
    private val foodSourceRepository: FoodSourceRepository,
    private val foodRepository: FoodRepository,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(source: FoodSource, onProgress: (Float) -> Unit): Result<Unit> {
        val tempDir = File(context.cacheDir, "food_source_downloads")
        tempDir.mkdirs()
        val gzFile = File(tempDir, "${source.id}.db.gz")
        val dbFile = File(tempDir, "${source.id}.db")

        return try {
            onProgress(0f)

            downloader.download(source.downloadUrl ?: error("No download URL"), gzFile)
                .getOrElse { return Result.failure(it) }
            onProgress(0.5f)

            if (source.checksumSha256 != null) {
                val valid = downloader.verifySha256(gzFile, source.checksumSha256)
                if (!valid) return Result.failure(RuntimeException("SHA-256 mismatch"))
            }

            downloader.gunzip(gzFile, dbFile).getOrElse { return Result.failure(it) }
            onProgress(0.6f)

            val existingCount = foodRepository.countByDataSource(source.id)
            if (existingCount > 0) {
                foodRepository.deleteByDataSource(source.id)
            }

            val sourceType = deriveSourceType(source.id)
            val insertedCount = installer.install(dbFile, source.id, sourceType)
            onProgress(0.95f)

            val now = System.currentTimeMillis()
            foodSourceRepository.upsert(
                source.copy(
                    version = source.latestVersion,
                    installedAt = java.time.Instant.ofEpochMilli(now),
                    itemCount = insertedCount,
                    status = FoodSource.Status.INSTALLED_UP_TO_DATE
                )
            )

            onProgress(1f)
            Result.success(Unit)
        } finally {
            if (gzFile.exists()) gzFile.delete()
            if (dbFile.exists()) dbFile.delete()
            if (tempDir.exists() && tempDir.listFiles().orEmpty().isEmpty()) tempDir.delete()
        }
    }

    private fun deriveSourceType(id: String): String = when (id) {
        "open-food-facts-uk" -> "OPEN_FOOD_FACTS"
        "usda-sr-legacy" -> "USDA"
        else -> "OPEN_FOOD_FACTS"
    }
}

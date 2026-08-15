package com.macrotrack.data.remote

import com.macrotrack.BuildConfig
import com.macrotrack.data.remote.dto.CatalogDto
import com.macrotrack.data.remote.dto.SourceEntryDto
import com.macrotrack.domain.model.FoodSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodSourceCatalogRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : FoodSourceCatalogRepository {

    override suspend fun fetchCatalog(): Result<List<FoodSource>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(BuildConfig.FOOD_SOURCES_CATALOG_URL).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw RuntimeException("Catalog fetch failed: ${response.code}")
            }
            val body = response.body?.string() ?: throw RuntimeException("Empty catalog response")
            val dto = json.decodeFromString<CatalogDto>(body)
            dto.sources.map { it.toFoodSource() }
        }
    }

    private fun SourceEntryDto.toFoodSource() = FoodSource(
        id = id,
        name = name,
        description = description,
        publisher = publisher,
        version = null,
        itemCount = itemCount,
        installedAt = Instant.now(),
        isUserSource = false,
        latestVersion = latestVersion,
        downloadSizeBytes = downloadSizeBytes,
        status = FoodSource.Status.NOT_INSTALLED,
        downloadUrl = downloadUrl,
        checksumSha256 = checksumSha256,
    )
}

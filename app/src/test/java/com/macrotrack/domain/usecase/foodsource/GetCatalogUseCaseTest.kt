package com.macrotrack.domain.usecase.foodsource

import com.google.common.truth.Truth.assertThat
import com.macrotrack.data.remote.FoodSourceCatalogRepository
import com.macrotrack.data.repository.FoodSourceRepository
import com.macrotrack.domain.model.FoodSource
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetCatalogUseCaseTest {

    private val catalogRepository = mockk<FoodSourceCatalogRepository>()
    private val foodSourceRepository = mockk<FoodSourceRepository>()

    private fun catalogSource(id: String, latestVersion: String) = FoodSource(
        id = id,
        name = "Catalog $id",
        description = "Catalog entry for $id",
        publisher = "Test Publisher",
        itemCount = 100,
        latestVersion = latestVersion,
        downloadSizeBytes = 2048L,
        status = FoodSource.Status.NOT_INSTALLED,
        downloadUrl = "https://example.com/catalog/$id.db",
        checksumSha256 = "checksum-$id",
    )

    private fun installedSource(id: String, version: String) = FoodSource(
        id = id,
        name = "Installed $id",
        description = "Installed entry for $id",
        publisher = "Test Publisher",
        version = version,
        itemCount = 50,
        installedAt = Instant.parse("2026-01-01T00:00:00Z"),
        status = FoodSource.Status.INSTALLED_UP_TO_DATE,
    )

    private fun stubCatalog(catalog: List<FoodSource>, installed: List<FoodSource>) {
        coEvery { catalogRepository.fetchCatalog() } returns Result.success(catalog)
        coEvery { foodSourceRepository.getNonUserSources() } returns flowOf(installed)
    }

    @Test
    fun `catalog source with no installed match maps to NOT_INSTALLED`() = runTest {
        val remote = catalogSource("usda-standard", "2026-08-01")
        stubCatalog(listOf(remote), installed = emptyList())

        val result = GetCatalogUseCase(catalogRepository, foodSourceRepository)().getOrThrow()

        val usda = result.single { it.id == "usda-standard" }
        assertThat(usda.status).isEqualTo(FoodSource.Status.NOT_INSTALLED)
        assertThat(usda.name).isEqualTo("Catalog usda-standard")
        assertThat(usda.version).isNull()
        assertThat(usda.latestVersion).isEqualTo("2026-08-01")
        assertThat(usda.downloadSizeBytes).isEqualTo(2048L)
        assertThat(usda.downloadUrl).isEqualTo("https://example.com/catalog/usda-standard.db")
        assertThat(usda.checksumSha256).isEqualTo("checksum-usda-standard")
        assertThat(usda.isUserSource).isFalse()
    }

    @Test
    fun `installed source matching latest version maps to INSTALLED_UP_TO_DATE`() = runTest {
        val remote = catalogSource("usda-standard", "2026-08-01")
        val installed = installedSource("usda-standard", "2026-08-01")
        stubCatalog(listOf(remote), installed = listOf(installed))

        val result = GetCatalogUseCase(catalogRepository, foodSourceRepository)().getOrThrow()

        val usda = result.single { it.id == "usda-standard" }
        assertThat(usda.status).isEqualTo(FoodSource.Status.INSTALLED_UP_TO_DATE)
        assertThat(usda.name).isEqualTo("Installed usda-standard")
        assertThat(usda.version).isEqualTo("2026-08-01")
        assertThat(usda.latestVersion).isEqualTo("2026-08-01")
        assertThat(usda.downloadSizeBytes).isEqualTo(2048L)
        assertThat(usda.downloadUrl).isEqualTo("https://example.com/catalog/usda-standard.db")
        assertThat(usda.checksumSha256).isEqualTo("checksum-usda-standard")
    }

    @Test
    fun `installed source behind latest version maps to INSTALLED_UPDATE_AVAILABLE`() = runTest {
        val remote = catalogSource("usda-standard", "2026-08-01")
        val installed = installedSource("usda-standard", "2026-05-01")
        stubCatalog(listOf(remote), installed = listOf(installed))

        val result = GetCatalogUseCase(catalogRepository, foodSourceRepository)().getOrThrow()

        val usda = result.single { it.id == "usda-standard" }
        assertThat(usda.status).isEqualTo(FoodSource.Status.INSTALLED_UPDATE_AVAILABLE)
        assertThat(usda.version).isEqualTo("2026-05-01")
        assertThat(usda.latestVersion).isEqualTo("2026-08-01")
        assertThat(usda.downloadSizeBytes).isEqualTo(2048L)
        assertThat(usda.downloadUrl).isEqualTo("https://example.com/catalog/usda-standard.db")
        assertThat(usda.checksumSha256).isEqualTo("checksum-usda-standard")
    }

    @Test
    fun `installed source missing from catalog is kept and listed before catalog entries`() = runTest {
        val remote = catalogSource("usda-standard", "2026-08-01")
        val orphan = installedSource("legacy-dataset", "1.0.0")
        stubCatalog(listOf(remote), installed = listOf(orphan))

        val result = GetCatalogUseCase(catalogRepository, foodSourceRepository)().getOrThrow()

        val legacy = result.single { it.id == "legacy-dataset" }
        assertThat(legacy.status).isEqualTo(FoodSource.Status.INSTALLED_UP_TO_DATE)
        assertThat(legacy.version).isEqualTo("1.0.0")
        assertThat(legacy.latestVersion).isNull()

        val ids = result.map { it.id }
        assertThat(ids.indexOf("legacy-dataset")).isLessThan(ids.indexOf("usda-standard"))
    }

    @Test
    fun `my foods synthetic source is always first`() = runTest {
        stubCatalog(catalog = emptyList(), installed = emptyList())

        val result = GetCatalogUseCase(catalogRepository, foodSourceRepository)().getOrThrow()

        assertThat(result).hasSize(1)
        val myFoods = result.first()
        assertThat(myFoods.id).isEqualTo("my-foods")
        assertThat(myFoods.name).isEqualTo("My foods")
        assertThat(myFoods.description).isEqualTo("Foods you have added yourself")
        assertThat(myFoods.status).isEqualTo(FoodSource.Status.MY_FOODS)
        assertThat(myFoods.isUserSource).isTrue()
    }

    @Test
    fun `my foods precedes installed and catalog entries`() = runTest {
        val remote = catalogSource("usda-standard", "2026-08-01")
        val orphan = installedSource("legacy-dataset", "1.0.0")
        stubCatalog(listOf(remote), installed = listOf(orphan))

        val result = GetCatalogUseCase(catalogRepository, foodSourceRepository)().getOrThrow()

        assertThat(result.first().id).isEqualTo("my-foods")
        assertThat(result.map { it.id })
            .containsExactly("my-foods", "legacy-dataset", "usda-standard")
            .inOrder()
    }

    @Test
    fun `catalog fetch failure is propagated`() = runTest {
        coEvery { catalogRepository.fetchCatalog() } returns
            Result.failure(RuntimeException("network down"))
        coEvery { foodSourceRepository.getNonUserSources() } returns flowOf(emptyList())

        val result = GetCatalogUseCase(catalogRepository, foodSourceRepository)()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().isEqualTo("network down")
    }
}
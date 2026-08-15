package com.macrotrack.data.remote.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class CatalogDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val fixture = """
        {
          "schemaVersion": 1,
          "sources": [
            {
              "id": "usda-standard",
              "name": "USDA Standard Reference",
              "description": "USDA FoodData Central reference foods",
              "publisher": "USDA",
              "latestVersion": "2026-08-01",
              "itemCount": 8789,
              "downloadUrl": "https://example.com/catalog/usda.db",
              "downloadSizeBytes": 24576000,
              "checksumSha256": "aaaabbbbcccc",
              "unknownField": "must be ignored"
            },
            {
              "id": "open-food-facts",
              "name": "Open Food Facts",
              "latestVersion": "2026-07-15",
              "itemCount": 0,
              "downloadUrl": "https://example.com/catalog/off.db",
              "downloadSizeBytes": 0,
              "checksumSha256": "ddddddd"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `decodes catalog fixture with all fields`() {
        val dto = json.decodeFromString<CatalogDto>(fixture)

        assertThat(dto.schemaVersion).isEqualTo(1)
        assertThat(dto.sources).hasSize(2)

        val first = dto.sources[0]
        assertThat(first.id).isEqualTo("usda-standard")
        assertThat(first.name).isEqualTo("USDA Standard Reference")
        assertThat(first.description).isEqualTo("USDA FoodData Central reference foods")
        assertThat(first.publisher).isEqualTo("USDA")
        assertThat(first.latestVersion).isEqualTo("2026-08-01")
        assertThat(first.itemCount).isEqualTo(8789)
        assertThat(first.downloadUrl).isEqualTo("https://example.com/catalog/usda.db")
        assertThat(first.downloadSizeBytes).isEqualTo(24_576_000L)
        assertThat(first.checksumSha256).isEqualTo("aaaabbbbcccc")
    }

    @Test
    fun `decodes optional fields as null when absent`() {
        val dto = json.decodeFromString<CatalogDto>(fixture)

        val second = dto.sources[1]
        assertThat(second.id).isEqualTo("open-food-facts")
        assertThat(second.description).isNull()
        assertThat(second.publisher).isNull()
        assertThat(second.latestVersion).isEqualTo("2026-07-15")
        assertThat(second.itemCount).isEqualTo(0)
    }

    @Test
    fun `decoding ignores unknown keys like production json config`() {
        val dto = json.decodeFromString<CatalogDto>(fixture)

        assertThat(dto.sources[0].id).isEqualTo("usda-standard")
        assertThat(dto.sources).hasSize(2)
    }
}
package com.macrotrack.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.URI

class FoodSourceCatalogRepositoryUrlTest {

    @Test
    fun `aligns same repository download url to catalog branch`() {
        val catalogUrl = URI(
            "https://raw.githubusercontent.com/jordanspooner/macrotrack-food-data/master/catalog.json"
        )
        val downloadUrl =
            "https://raw.githubusercontent.com/jordanspooner/macrotrack-food-data/main/sources/off/1.0.0/source.db.gz"

        assertThat(alignDownloadUrl(downloadUrl, catalogUrl)).isEqualTo(
            "https://raw.githubusercontent.com/jordanspooner/macrotrack-food-data/master/sources/off/1.0.0/source.db.gz"
        )
    }

    @Test
    fun `leaves download url from another host unchanged`() {
        val catalogUrl = URI(
            "https://raw.githubusercontent.com/jordanspooner/macrotrack-food-data/master/catalog.json"
        )
        val downloadUrl = "https://example.com/foods/source.db.gz"

        assertThat(alignDownloadUrl(downloadUrl, catalogUrl)).isEqualTo(downloadUrl)
    }
}

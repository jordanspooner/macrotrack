package com.macrotrack.domain.model

import java.time.Instant

data class FoodSource(
    val id: String,
    val name: String,
    val description: String? = null,
    val version: String? = null,
    val publisher: String? = null,
    val itemCount: Int = 0,
    val installedAt: Instant = Instant.now(),
    val isUserSource: Boolean = false,
    val latestVersion: String? = null,
    val downloadSizeBytes: Long? = null,
    val status: Status,
    val downloadUrl: String? = null,
    val checksumSha256: String? = null,
) {
    enum class Status {
        MY_FOODS,
        INSTALLED_UP_TO_DATE,
        INSTALLED_UPDATE_AVAILABLE,
        NOT_INSTALLED
    }
}

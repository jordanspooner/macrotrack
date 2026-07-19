package com.macrotrack.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CatalogDto(
    val schemaVersion: Int,
    val sources: List<SourceEntryDto>,
)

@Serializable
data class SourceEntryDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val publisher: String? = null,
    val latestVersion: String,
    val itemCount: Int,
    val downloadUrl: String,
    val downloadSizeBytes: Long,
    val checksumSha256: String,
)

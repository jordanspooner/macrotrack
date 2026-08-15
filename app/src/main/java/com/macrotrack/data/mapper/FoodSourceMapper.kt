package com.macrotrack.data.mapper

import com.macrotrack.data.local.db.entity.FoodSourceEntity
import com.macrotrack.domain.model.FoodSource
import java.time.Instant

fun FoodSourceEntity.toDomain() = FoodSource(
    id = id,
    name = name,
    description = description,
    version = version,
    publisher = publisher,
    itemCount = itemCount,
    installedAt = Instant.ofEpochMilli(installedAt),
    isUserSource = isUserSource,
    latestVersion = null,
    downloadSizeBytes = null,
    status = if (isUserSource) FoodSource.Status.MY_FOODS else FoodSource.Status.INSTALLED_UP_TO_DATE
)

fun FoodSource.toEntity() = FoodSourceEntity(
    id = id,
    name = name,
    description = description,
    version = version,
    publisher = publisher,
    itemCount = itemCount,
    installedAt = installedAt.toEpochMilli(),
    isUserSource = isUserSource
)

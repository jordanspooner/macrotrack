package com.macrotrack.data.mapper

import com.macrotrack.data.local.db.entity.LogEntryEntity
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Macros
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun LogEntryEntity.toDomain() = LogEntry(
    id = id,
    date = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE),
    sectionId = sectionId,
    foodItemId = foodItemId,
    name = name,
    brand = brand,
    portionG = portionG,
    portionLabel = portionLabel,
    macros = Macros(
        kcal = kcal,
        proteinG = protein,
        carbsG = carbs,
        fatG = fat
    ),
    sortOrder = sortOrder,
    createdAt = Instant.ofEpochMilli(createdAt)
)

fun LogEntry.toEntity() = LogEntryEntity(
    id = id,
    date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
    sectionId = sectionId,
    foodItemId = foodItemId,
    name = name,
    brand = brand,
    portionG = portionG,
    portionLabel = portionLabel,
    kcal = macros.kcal,
    protein = macros.proteinG,
    carbs = macros.carbsG,
    fat = macros.fatG,
    sortOrder = sortOrder,
    createdAt = createdAt.toEpochMilli()
)

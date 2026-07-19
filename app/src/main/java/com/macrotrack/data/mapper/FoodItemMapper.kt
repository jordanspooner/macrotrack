package com.macrotrack.data.mapper

import com.macrotrack.data.local.db.entity.FoodItemEntity
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Source

fun FoodItemEntity.toDomain() = FoodItem(
    id = id,
    source = Source.valueOf(source),
    sourceId = sourceId,
    dataSourceId = dataSourceId,
    ean = ean,
    brand = brand,
    name = name,
    defaultPortionG = defaultPortionG,
    defaultPortionLabel = defaultPortionLabel,
    macroPer100g = Macros(
        kcal = kcalPer100g,
        proteinG = proteinPer100g,
        carbsG = carbsPer100g,
        fatG = fatPer100g
    )
)

fun FoodItem.toEntity() = FoodItemEntity(
    id = id,
    source = source.name,
    sourceId = sourceId,
    dataSourceId = dataSourceId,
    ean = ean,
    brand = brand,
    name = name,
    defaultPortionG = defaultPortionG,
    defaultPortionLabel = defaultPortionLabel,
    kcalPer100g = macroPer100g.kcal,
    proteinPer100g = macroPer100g.proteinG,
    carbsPer100g = macroPer100g.carbsG,
    fatPer100g = macroPer100g.fatG
)

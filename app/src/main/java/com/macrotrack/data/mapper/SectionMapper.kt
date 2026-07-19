package com.macrotrack.data.mapper

import com.macrotrack.data.local.db.entity.SectionEntity
import com.macrotrack.domain.model.Section
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun SectionEntity.toDomain() = Section(
    id = id,
    name = name,
    timeOfDay = LocalTime.parse(timeOfDay, DateTimeFormatter.ofPattern("HH:mm"))
)

fun Section.toEntity() = SectionEntity(
    id = id,
    name = name,
    timeOfDay = timeOfDay.format(DateTimeFormatter.ofPattern("HH:mm"))
)

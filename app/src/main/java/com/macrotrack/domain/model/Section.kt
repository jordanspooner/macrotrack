package com.macrotrack.domain.model

import java.time.LocalTime

data class Section(
    val id: Long = 0,
    val name: String,
    val timeOfDay: LocalTime,       // For default section selection and ordering
)

fun defaultSectionIdForTime(sections: List<Section>, now: LocalTime): Long {
    if (sections.isEmpty()) return 0L
    val sorted = sections.sortedBy { it.timeOfDay }
    val past = sorted.filter { !it.timeOfDay.isAfter(now) }
    return (past.lastOrNull() ?: sorted.last()).id
}

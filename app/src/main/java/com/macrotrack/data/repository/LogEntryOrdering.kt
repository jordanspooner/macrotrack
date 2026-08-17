package com.macrotrack.data.repository

import com.macrotrack.data.local.db.entity.LogEntryEntity

/**
 * Deterministic ordering for multi-entry log operations, operating on
 * [LogEntryEntity] so it can run inside a Room transaction without depending
 * on domain objects.
 *
 * Incoming entries (in source visual order) are appended after the entries
 * already stored for their (date, sectionId) group, receiving unique, stable
 * sortOrder values. Combined with the `ORDER BY sortOrder ASC, id ASC`
 * tie-break in [com.macrotrack.data.local.db.dao.LogEntryDao], display order
 * stays deterministic even when legacy entries share a sortOrder.
 */
internal object LogEntryOrdering {

    /**
     * Assigns fresh append sort orders to [incoming], grouped by sectionId.
     *
     * [existing] holds the entities currently stored for the target date; the
     * first incoming entity of each group gets `max(existing.sortOrder) + 1`
     * (or 0 when the group is empty) and each following entity increments by
     * one, preserving [incoming] order within the group.
     */
    fun appendSortOrders(
        incoming: List<LogEntryEntity>,
        existing: List<LogEntryEntity>,
    ): List<LogEntryEntity> {
        if (incoming.isEmpty()) return incoming
        val targetDate = incoming.first().date
        val nextBySection = existing
            .asSequence()
            .filter { it.date == targetDate }
            .groupBy { it.sectionId }
            .mapValues { (_, entries) -> (entries.maxOfOrNull { it.sortOrder } ?: -1) + 1 }
            .toMutableMap()
        return incoming.map { entry ->
            val next = nextBySection[entry.sectionId] ?: 0
            nextBySection[entry.sectionId] = next + 1
            entry.copy(sortOrder = next)
        }
    }
}
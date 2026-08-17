package com.macrotrack.data.repository

import com.macrotrack.data.local.db.entity.LogEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LogEntryOrderingTest {

    private val monday = "2026-08-10"
    private val tuesday = "2026-08-11"

    private fun entry(id: Long, date: String, sectionId: Long, sortOrder: Int) = LogEntryEntity(
        id = id,
        date = date,
        sectionId = sectionId,
        foodItemId = if (id > 0) id else null,
        name = "Food $id",
        brand = "Brand",
        portionG = 150f,
        portionLabel = "1.5 servings",
        kcal = 250f,
        protein = 20f,
        carbs = 30f,
        fat = 8f,
        sortOrder = sortOrder,
        createdAt = 1000L,
    )

    @Test
    fun `appends after existing sort orders preserving input order`() {
        val existing = listOf(
            entry(1, monday, sectionId = 1, sortOrder = 0),
            entry(2, monday, sectionId = 1, sortOrder = 3),
        )
        val incoming = listOf(
            entry(10, monday, sectionId = 1, sortOrder = 99),
            entry(11, monday, sectionId = 1, sortOrder = 98),
        )

        val result = LogEntryOrdering.appendSortOrders(incoming, existing)

        assertEquals(listOf(4, 5), result.map { it.sortOrder })
        assertEquals(listOf(10L, 11L), result.map { it.id })
    }

    @Test
    fun `starts at zero when group is empty`() {
        val incoming = listOf(
            entry(10, tuesday, sectionId = 5, sortOrder = 0),
            entry(11, tuesday, sectionId = 5, sortOrder = 0),
        )

        val result = LogEntryOrdering.appendSortOrders(incoming, emptyList())

        assertEquals(listOf(0, 1), result.map { it.sortOrder })
    }

    @Test
    fun `orders groups independently per section`() {
        val existing = listOf(
            entry(1, tuesday, sectionId = 1, sortOrder = 0),
            entry(2, tuesday, sectionId = 1, sortOrder = 2),
            entry(3, tuesday, sectionId = 2, sortOrder = 7),
        )
        val incoming = listOf(
            entry(10, tuesday, sectionId = 1, sortOrder = 99),
            entry(11, tuesday, sectionId = 2, sortOrder = 99),
            entry(12, tuesday, sectionId = 1, sortOrder = 98),
        )

        val result = LogEntryOrdering.appendSortOrders(incoming, existing)

        assertEquals(listOf(3, 8, 4), result.map { it.sortOrder })
        assertEquals(listOf(10L, 11L, 12L), result.map { it.id })
    }

    @Test
    fun `ignores existing entries from other dates`() {
        val existing = listOf(
            entry(1, monday, sectionId = 1, sortOrder = 42),
        )
        val incoming = listOf(
            entry(10, tuesday, sectionId = 1, sortOrder = 0),
        )

        val result = LogEntryOrdering.appendSortOrders(incoming, existing)

        assertEquals(listOf(0), result.map { it.sortOrder })
    }

    @Test
    fun `returns empty input unchanged`() {
        assertEquals(
            emptyList<LogEntryEntity>(),
            LogEntryOrdering.appendSortOrders(emptyList(), emptyList())
        )
    }

    @Test
    fun `only sort order is modified`() {
        val incoming = listOf(entry(10, tuesday, sectionId = 1, sortOrder = 99))
        val existing = listOf(entry(1, tuesday, sectionId = 1, sortOrder = 5))

        val result = LogEntryOrdering.appendSortOrders(incoming, existing).single()

        assertEquals(6, result.sortOrder)
        assertEquals(10L, result.id)
        assertEquals(tuesday, result.date)
        assertEquals(1L, result.sectionId)
        assertEquals(10L, result.foodItemId)
        assertEquals("Food 10", result.name)
        assertEquals("Brand", result.brand)
        assertEquals(150f, result.portionG, 0f)
        assertEquals("1.5 servings", result.portionLabel)
        assertEquals(250f, result.kcal, 0f)
        assertEquals(20f, result.protein, 0f)
        assertEquals(30f, result.carbs, 0f)
        assertEquals(8f, result.fat, 0f)
        assertEquals(1000L, result.createdAt)
    }
}
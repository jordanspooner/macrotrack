package com.macrotrack.ui.log

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekPagerMappingTest {

    private val selectedDate = LocalDate.of(2026, 8, 15)

    @Test
    fun `current week page returns currentWeek`() {
        val uiState = uiState(
            prevWeek = weekDays(1),
            currentWeek = weekDays(8),
            nextWeek = weekDays(15),
        )
        val result = weekDaysForPage(weekPageForDate(selectedDate), uiState)
        assertEquals(uiState.currentWeek, result)
        assertEquals(uiState.prevWeek, weekDaysForPage(weekPageForDate(selectedDate) - 1, uiState))
        assertEquals(uiState.nextWeek, weekDaysForPage(weekPageForDate(selectedDate) + 1, uiState))
    }

    @Test
    fun `previous week page returns prevWeek`() {
        val uiState = uiState(
            prevWeek = weekDays(1),
            currentWeek = weekDays(8),
            nextWeek = weekDays(15),
        )
        val result = weekDaysForPage(weekPageForDate(selectedDate) - 1, uiState)
        assertEquals(uiState.prevWeek, result)
    }

    @Test
    fun `next week page returns nextWeek`() {
        val uiState = uiState(
            prevWeek = weekDays(1),
            currentWeek = weekDays(8),
            nextWeek = weekDays(15),
        )
        val result = weekDaysForPage(weekPageForDate(selectedDate) + 1, uiState)
        assertEquals(uiState.nextWeek, result)
    }

    @Test
    fun `page two weeks ahead returns null`() {
        val uiState = uiState(
            prevWeek = weekDays(1),
            currentWeek = weekDays(8),
            nextWeek = weekDays(15),
        )
        assertNull(weekDaysForPage(weekPageForDate(selectedDate) + 2, uiState))
    }

    @Test
    fun `page two weeks behind returns null`() {
        val uiState = uiState(
            prevWeek = weekDays(1),
            currentWeek = weekDays(8),
            nextWeek = weekDays(15),
        )
        assertNull(weekDaysForPage(weekPageForDate(selectedDate) - 2, uiState))
    }

    private fun uiState(
        prevWeek: List<WeekDay>,
        currentWeek: List<WeekDay>,
        nextWeek: List<WeekDay>,
    ) = LogUiState(
        selectedDate = selectedDate,
        prevWeek = prevWeek,
        currentWeek = currentWeek,
        nextWeek = nextWeek,
    )

    private fun weekDays(dayOfMonth: Int): List<WeekDay> = listOf(
        WeekDay(
            date = LocalDate.of(2026, 8, dayOfMonth),
            dayName = "Mon",
            dayNumber = dayOfMonth,
            isSelected = false,
            isToday = false,
        )
    )
}

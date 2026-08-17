package com.macrotrack.ui.log

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekPagerMappingTest {

    private val selectedDate = LocalDate.of(2026, 8, 15)
    private val displayedWeekStart = mondayOf(selectedDate)

    @Test
    fun `current week page returns currentWeek`() {
        val uiState = uiState(
            prevWeek = weekDays(1),
            currentWeek = weekDays(8),
            nextWeek = weekDays(15),
        )
        val result = weekDaysForPage(weekPageForDate(displayedWeekStart), uiState)
        assertEquals(uiState.currentWeek, result)
    }

    @Test
    fun `previous week page returns prevWeek`() {
        val uiState = uiState(
            prevWeek = weekDays(1),
            currentWeek = weekDays(8),
            nextWeek = weekDays(15),
        )
        val result = weekDaysForPage(weekPageForDate(displayedWeekStart) - 1, uiState)
        assertEquals(uiState.prevWeek, result)
    }

    @Test
    fun `next week page returns nextWeek`() {
        val uiState = uiState(
            prevWeek = weekDays(1),
            currentWeek = weekDays(8),
            nextWeek = weekDays(15),
        )
        val result = weekDaysForPage(weekPageForDate(displayedWeekStart) + 1, uiState)
        assertEquals(uiState.nextWeek, result)
    }

    @Test
    fun `page two weeks ahead returns null`() {
        val uiState = uiState(
            prevWeek = weekDays(1),
            currentWeek = weekDays(8),
            nextWeek = weekDays(15),
        )
        assertNull(weekDaysForPage(weekPageForDate(displayedWeekStart) + 2, uiState))
    }

    @Test
    fun `page two weeks behind returns null`() {
        val uiState = uiState(
            prevWeek = weekDays(1),
            currentWeek = weekDays(8),
            nextWeek = weekDays(15),
        )
        assertNull(weekDaysForPage(weekPageForDate(displayedWeekStart) - 2, uiState))
    }

    @Test
    fun `mapping keys on displayedWeekStart not selectedDate`() {
        val farWeekStart = displayedWeekStart.plusWeeks(3)
        val uiState = uiState(
            prevWeek = weekDays(1),
            currentWeek = weekDays(8),
            nextWeek = weekDays(15),
            weekStart = farWeekStart,
        )
        assertEquals(uiState.currentWeek, weekDaysForPage(weekPageForDate(farWeekStart), uiState))
        assertEquals(uiState.prevWeek, weekDaysForPage(weekPageForDate(farWeekStart) - 1, uiState))
        assertEquals(uiState.nextWeek, weekDaysForPage(weekPageForDate(farWeekStart) + 1, uiState))
        assertNull(weekDaysForPage(weekPageForDate(displayedWeekStart), uiState))
    }

    private fun uiState(
        prevWeek: List<WeekDay>,
        currentWeek: List<WeekDay>,
        nextWeek: List<WeekDay>,
        weekStart: LocalDate? = null,
    ) = LogUiState(
        selectedDate = selectedDate,
        displayedWeekStart = weekStart ?: displayedWeekStart,
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

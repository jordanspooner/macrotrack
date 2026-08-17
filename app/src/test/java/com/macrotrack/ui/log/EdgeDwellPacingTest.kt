package com.macrotrack.ui.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EdgeDwellPacingTest {

    private val date = LocalDate.of(2026, 8, 16)
    private val weekStart = date.minusDays(date.dayOfWeek.value.toLong() - 1)

    private fun uiState(selectedDate: LocalDate, displayedWeekStart: LocalDate) = LogUiState(
        selectedDate = selectedDate,
        displayedWeekStart = displayedWeekStart,
    )

    @Test
    fun `daily edge target moves the selected date one day left`() {
        val edge = ActiveEdge(EdgeSurface.Daily, EdgeZone.Left)
        assertEquals(date.minusDays(1), edgePageTarget(edge, uiState(date, weekStart)))
    }

    @Test
    fun `daily edge target moves the selected date one day right`() {
        val edge = ActiveEdge(EdgeSurface.Daily, EdgeZone.Right)
        assertEquals(date.plusDays(1), edgePageTarget(edge, uiState(date, weekStart)))
    }

    @Test
    fun `daily edge target ignores the displayed week start`() {
        val edge = ActiveEdge(EdgeSurface.Daily, EdgeZone.Right)
        assertEquals(date.plusDays(1), edgePageTarget(edge, uiState(date, weekStart.plusWeeks(3))))
    }

    @Test
    fun `week edge target moves the displayed week start one week left`() {
        val edge = ActiveEdge(EdgeSurface.Week, EdgeZone.Left)
        assertEquals(weekStart.minusWeeks(1), edgePageTarget(edge, uiState(date, weekStart)))
    }

    @Test
    fun `week edge target moves the displayed week start one week right`() {
        val edge = ActiveEdge(EdgeSurface.Week, EdgeZone.Right)
        assertEquals(weekStart.plusWeeks(1), edgePageTarget(edge, uiState(date, weekStart)))
    }

    @Test
    fun `week edge target ignores the selected date`() {
        val edge = ActiveEdge(EdgeSurface.Week, EdgeZone.Left)
        assertEquals(weekStart.minusWeeks(1), edgePageTarget(edge, uiState(date.plusDays(10), weekStart)))
    }

    @Test
    fun `daily edge target is reached when the selected date catches up`() {
        val edge = ActiveEdge(EdgeSurface.Daily, EdgeZone.Right)
        val target = date.plusDays(1)
        assertFalse(edgeTargetReached(edge, target, uiState(date, weekStart)))
        val loaded = LogUiState(
            selectedDate = target,
            displayedWeekStart = weekStart,
            currentDay = DayContent(
                date = target,
                summary = com.macrotrack.domain.model.DailySummary(
                    date = target,
                    logged = com.macrotrack.domain.model.Macros(0f, 0f, 0f, 0f),
                    goals = com.macrotrack.domain.model.DailyGoals(proteinG = 0, carbsG = 0, fatG = 0),
                ),
                sections = emptyList(),
            ),
        )
        assertTrue(edgeTargetReached(edge, target, loaded))
    }

    @Test
    fun `daily edge target is NOT reached when the date matches but content is null`() {
        val edge = ActiveEdge(EdgeSurface.Daily, EdgeZone.Right)
        val target = date.plusDays(1)
        assertFalse(edgeTargetReached(edge, target, uiState(target, weekStart)))
    }

    @Test
    fun `daily edge target is not reached by a week start change`() {
        val edge = ActiveEdge(EdgeSurface.Daily, EdgeZone.Right)
        val target = date.plusDays(1)
        assertFalse(edgeTargetReached(edge, target, uiState(date, weekStart.plusWeeks(1))))
    }

    @Test
    fun `week edge target is reached when the displayed week start catches up`() {
        val edge = ActiveEdge(EdgeSurface.Week, EdgeZone.Right)
        val target = weekStart.plusWeeks(1)
        assertFalse(edgeTargetReached(edge, target, uiState(date, weekStart)))
        assertTrue(edgeTargetReached(edge, target, uiState(date, target)))
    }

    @Test
    fun `week edge target is not reached by a selected date change`() {
        val edge = ActiveEdge(EdgeSurface.Week, EdgeZone.Right)
        val target = weekStart.plusWeeks(1)
        assertFalse(edgeTargetReached(edge, target, uiState(date.plusDays(1), weekStart)))
    }
}

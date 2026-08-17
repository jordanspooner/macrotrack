package com.macrotrack.ui.log

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekPagerNavigationTest {

    @Test
    fun `programmatic scroll produces zero delta`() {
        assertEquals(0L, weekNavigationDelta(settledPage = 100, targetWeekPage = 3, userSwiped = false))
    }

    @Test
    fun `swipe settling on same page produces zero delta`() {
        assertEquals(0L, weekNavigationDelta(settledPage = 5, targetWeekPage = 5, userSwiped = true))
    }

    @Test
    fun `swipe one week forward produces +1`() {
        assertEquals(1L, weekNavigationDelta(settledPage = 6, targetWeekPage = 5, userSwiped = true))
    }

    @Test
    fun `swipe one week backward produces -1`() {
        assertEquals(-1L, weekNavigationDelta(settledPage = 4, targetWeekPage = 5, userSwiped = true))
    }

    @Test
    fun `swipe multiple weeks forward produces positive delta`() {
        assertEquals(3L, weekNavigationDelta(settledPage = 8, targetWeekPage = 5, userSwiped = true))
    }

    @Test
    fun `swipe multiple weeks backward produces negative delta`() {
        assertEquals(-3L, weekNavigationDelta(settledPage = 2, targetWeekPage = 5, userSwiped = true))
    }

    @Test
    fun `drag-active settle never navigates even with the swipe marker`() {
        assertEquals(0L, weekNavigationDelta(settledPage = 6, targetWeekPage = 5, userSwiped = true, dragActive = true))
        assertEquals(0L, weekNavigationDelta(settledPage = 8, targetWeekPage = 5, userSwiped = true, dragActive = true))
    }

    @Test
    fun `drag-active settle with no swipe marker stays zero`() {
        assertEquals(0L, weekNavigationDelta(settledPage = 6, targetWeekPage = 5, userSwiped = false, dragActive = true))
    }

    @Test
    fun `day settle routes a genuine swipe to the settled page`() {
        assertEquals(42, daySettleTargetPage(settledPage = 42, currentDayPage = 41, userSwiped = true, dragActive = false))
    }

    @Test
    fun `day settle with same page produces no navigation`() {
        assertNull(daySettleTargetPage(settledPage = 41, currentDayPage = 41, userSwiped = true, dragActive = false))
    }

    @Test
    fun `day settle ignores programmatic scrolls`() {
        assertNull(daySettleTargetPage(settledPage = 42, currentDayPage = 41, userSwiped = false, dragActive = false))
    }

    @Test
    fun `day settle ignores drag auto-navigation settles`() {
        assertNull(daySettleTargetPage(settledPage = 42, currentDayPage = 41, userSwiped = true, dragActive = true))
        assertNull(daySettleTargetPage(settledPage = 42, currentDayPage = 41, userSwiped = false, dragActive = true))
    }

    @Test
    fun `same week dates share one page`() {
        assertEquals(
            weekPageForDate(LocalDate.of(2026, 8, 10)),
            weekPageForDate(LocalDate.of(2026, 8, 16)),
        )
    }

    @Test
    fun `adjacent weeks differ by one page`() {
        assertEquals(
            weekPageForDate(LocalDate.of(2026, 8, 10)) + 1,
            weekPageForDate(LocalDate.of(2026, 8, 17)),
        )
    }

    @Test
    fun `mid-week date maps to its monday page`() {
        assertEquals(
            weekPageForDate(LocalDate.of(2026, 8, 10)),
            weekPageForDate(LocalDate.of(2026, 8, 15)),
        )
    }

    @Test
    fun `sunday belongs to the current week not the next`() {
        assertEquals(
            weekPageForDate(LocalDate.of(2026, 8, 10)),
            weekPageForDate(LocalDate.of(2026, 8, 16)),
        )
    }

    @Test
    fun `weeks across a year boundary differ by one page`() {
        assertEquals(
            weekPageForDate(LocalDate.of(2026, 1, 5)) - 1,
            weekPageForDate(LocalDate.of(2025, 12, 29)),
        )
    }
}
package com.macrotrack.ui.log

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationPickerTest {

    private val selectedDate = LocalDate.of(2026, 8, 20)
    private val today = LocalDate.of(2026, 8, 15)

    @Test
    fun `quick destinations are relative to selected date`() {
        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 19),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 21),
            ),
            destinationDatesFor(selectedDate),
        )
    }

    @Test
    fun `historical destinations use concrete dates`() {
        assertEquals("Aug 19", destinationChipLabel(selectedDate.minusDays(1), selectedDate, today))
        assertEquals("Aug 20", destinationChipLabel(selectedDate, selectedDate, today))
        assertEquals("Aug 21", destinationChipLabel(selectedDate.plusDays(1), selectedDate, today))
    }

    @Test
    fun `today destinations use relative labels`() {
        assertEquals("Yesterday", destinationChipLabel(today.minusDays(1), today, today))
        assertEquals("Today", destinationChipLabel(today, today, today))
        assertEquals("Tomorrow", destinationChipLabel(today.plusDays(1), today, today))
    }
}

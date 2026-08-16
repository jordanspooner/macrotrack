package com.macrotrack.ui.add

import org.junit.Assert.assertEquals
import org.junit.Test

class PortionSizeFormatTest {

    @Test
    fun wholeGramsFormatWithoutDecimal() {
        assertEquals("100", formatPortionG(100f))
    }

    @Test
    fun fractionalGramsKeepDecimal() {
        assertEquals("1.5", formatPortionG(1.5f))
    }

    @Test
    fun fractionalHalfIsNotRoundedUp() {
        assertEquals("37.5", formatPortionG(37.5f))
    }

    @Test
    fun emptyTextParsesToZero() {
        assertEquals(0f, parsePortionG(""), 0f)
    }

    @Test
    fun invalidTextParsesToZero() {
        assertEquals(0f, parsePortionG("abc"), 0f)
    }

    @Test
    fun zeroParsesToZero() {
        assertEquals(0f, parsePortionG("0"), 0f)
    }

    @Test
    fun negativeParsesToZero() {
        assertEquals(0f, parsePortionG("-5"), 0f)
    }

    @Test
    fun positiveWholeParses() {
        assertEquals(250f, parsePortionG("250"), 0f)
    }

    @Test
    fun positiveDecimalParses() {
        assertEquals(1.5f, parsePortionG("1.5"), 0f)
    }
}

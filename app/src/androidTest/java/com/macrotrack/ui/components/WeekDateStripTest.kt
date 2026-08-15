package com.macrotrack.ui.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test

class WeekDateStripTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `empty week list does not crash`() {
        composeRule.setContent {
            WeekDateStrip(weekDays = emptyList(), onDateSelected = {}, onOpenCalendar = {})
        }
        composeRule.waitForIdle()
    }
}
package com.macrotrack.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.macrotrack.ui.log.WeekDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WeekDateStripTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyWeekListDoesNotCrash() {
        composeRule.setContent {
            WeekDateStrip(weekDays = emptyList(), onDateSelected = {}, onOpenCalendar = {})
        }
        composeRule.waitForIdle()
    }

    @Test
    fun todayAndSelectedDaysExposeDistinctSemantics() {
        val today = LocalDate.now()
        val selected = weekStartingOn(today).first { it != today }
        val week = weekStartingOn(today).map { date ->
            WeekDay(
                date = date,
                dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dayNumber = date.dayOfMonth,
                isSelected = date == selected,
                isToday = date == today,
            )
        }
        composeRule.setContent {
            WeekDateStrip(weekDays = week, onDateSelected = {}, onOpenCalendar = {})
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Today", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Selected", substring = true).assertCountEquals(1)

        composeRule.onNodeWithTag("week-day-$today")
            .assert(reportsTodayAndSelected(expectToday = true, expectSelected = false))
        composeRule.onNodeWithTag("week-day-$selected")
            .assert(reportsTodayAndSelected(expectToday = false, expectSelected = true))
    }

    @Test
    fun todayAlsoSelectedReportsBothStates() {
        val today = LocalDate.now()
        val week = weekStartingOn(today).map { date ->
            WeekDay(
                date = date,
                dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dayNumber = date.dayOfMonth,
                isSelected = date == today,
                isToday = date == today,
            )
        }
        composeRule.setContent {
            WeekDateStrip(weekDays = week, onDateSelected = {}, onOpenCalendar = {})
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("week-day-$today")
            .assert(reportsTodayAndSelected(expectToday = true, expectSelected = true))
    }

    @Test
    fun clickingDayInvokesOnDateSelected() {
        val today = LocalDate.now()
        val target = weekStartingOn(today)[3]
        val week = weekStartingOn(today).map { date ->
            WeekDay(
                date = date,
                dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dayNumber = date.dayOfMonth,
                isSelected = date == today,
                isToday = date == today,
            )
        }
        var clicked: LocalDate? = null
        composeRule.setContent {
            WeekDateStrip(
                weekDays = week,
                onDateSelected = { clicked = it.date },
                onOpenCalendar = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("week-day-$target").performClick()
        assertEquals(target, clicked)
    }

    @Test
    fun clickingMonthHeaderInvokesOnOpenCalendar() {
        val today = LocalDate.now()
        val week = weekStartingOn(today).map { date ->
            WeekDay(
                date = date,
                dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dayNumber = date.dayOfMonth,
                isSelected = date == today,
                isToday = date == today,
            )
        }
        var opened = false
        composeRule.setContent {
            WeekDateStrip(weekDays = week, onDateSelected = {}, onOpenCalendar = { opened = true })
        }
        composeRule.waitForIdle()

        val monthLabel = today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        composeRule.onNodeWithText(monthLabel).performClick()
        assertTrue(opened)
    }

    @Test
    fun progressReportedViaSemanticsWithoutInlineLabels() {
        val today = LocalDate.now()
        val target = weekStartingOn(today)[2]
        val week = weekStartingOn(today).map { date ->
            WeekDay(
                date = date,
                dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dayNumber = date.dayOfMonth,
                isSelected = date == target,
                isToday = date == today,
                proteinProgress = if (date == target) 0.25f else 0f,
                carbsProgress = if (date == target) 0.5f else 0f,
                fatProgress = if (date == target) 1.5f else 0f,
            )
        }
        composeRule.setContent {
            WeekDateStrip(weekDays = week, onDateSelected = {}, onOpenCalendar = {})
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("week-day-$target").assert(
            SemanticsMatcher("reports all three macro percentages") {
                val description = it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                description.any { d -> d.contains("Protein 25%") }
            }
        )
        composeRule.onNodeWithText("P 25%", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("C 50%", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("F 150%", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun percentLabelsAreAbsentWhenNothingLogged() {
        val today = LocalDate.now()
        val week = weekStartingOn(today).map { date ->
            WeekDay(
                date = date,
                dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dayNumber = date.dayOfMonth,
                isSelected = date == today,
                isToday = date == today,
            )
        }
        composeRule.setContent {
            WeekDateStrip(weekDays = week, onDateSelected = {}, onOpenCalendar = {})
        }
        composeRule.waitForIdle()

        // Progress is only exposed through the perimeter ring's semantics, never inline text.
        composeRule.onAllNodesWithText("P 0%", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("C 0%", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("F 0%", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun todayWhenSelectedStillReportsTodaySemantics() {
        val today = LocalDate.now()
        val week = weekStartingOn(today).map { date ->
            WeekDay(
                date = date,
                dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dayNumber = date.dayOfMonth,
                isSelected = date == today,
                isToday = date == today,
            )
        }
        composeRule.setContent {
            WeekDateStrip(weekDays = week, onDateSelected = {}, onOpenCalendar = {})
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("week-day-$today")
            .assert(reportsTodayAndSelected(expectToday = true, expectSelected = true))
    }

    @Test
    fun draggingDayExposesMoveDescriptionInsteadOfReplacingItWithDayDescription() {
        val today = LocalDate.now()
        val target = weekStartingOn(today)[2]
        val week = weekStartingOn(today).map { date ->
            WeekDay(
                date = date,
                dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dayNumber = date.dayOfMonth,
                isSelected = date == today,
                isToday = date == today,
            )
        }
        composeRule.setContent {
            WeekDateStrip(
                weekDays = week,
                onDateSelected = {},
                onOpenCalendar = {},
                dragActive = true,
                dragCount = 2,
            )
        }
        composeRule.waitForIdle()

        val expected = "Move 2 to ${target.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}"
        composeRule.onNodeWithTag("week-day-$target").assert(
            SemanticsMatcher("exposes drag move description") {
                it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains(expected)
            }
        )
    }

    private fun weekStartingOn(reference: LocalDate): List<LocalDate> {
        val start = reference.minusDays(reference.dayOfWeek.value.toLong() - 1)
        return (0L..6L).map { start.plusDays(it) }
    }

    private fun reportsTodayAndSelected(expectToday: Boolean, expectSelected: Boolean): SemanticsMatcher =
        SemanticsMatcher(
            "day description reports today=${if (expectToday) "today" else "not today"} " +
                "and selected=${if (expectSelected) "selected" else "not selected"}"
        ) {
            val description = it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            val hasToday = description.any { d -> d.contains("Today") }
            val hasSelected = description.any { d -> d.contains("Selected") }
            hasToday == expectToday && hasSelected == expectSelected
        }
}

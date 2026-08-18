package com.macrotrack.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.macrotrack.domain.model.Macros
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SectionHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actual = Macros(kcal = 400f, proteinG = 30f, carbsG = 40f, fatG = 12f)
    private val goal = Macros(kcal = 600f, proteinG = 60f, carbsG = 80f, fatG = 20f)

    @Test
    fun withoutGoalShowsActualKcalAndPcfSummary() {
        composeRule.setContent {
            SectionHeader(
                name = "Lunch",
                totalMacros = actual,
                goalMacros = null,
                hasEntries = true,
                isExpanded = true,
                onToggleExpand = {},
            )
        }
        composeRule.onNodeWithText("400 kcal").assertExists()
        composeRule.onNodeWithText("400 / 600 kcal").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("P 30 g of 60 g goal").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("P 30 g").assertExists()
        composeRule.onNodeWithContentDescription("C 40 g").assertExists()
        composeRule.onNodeWithContentDescription("F 12 g").assertExists()
    }

    @Test
    fun withGoalShowsActualOverGoalKcalAndMeterLanes() {
        composeRule.setContent {
            SectionHeader(
                name = "Lunch",
                totalMacros = actual,
                goalMacros = goal,
                hasEntries = true,
                isExpanded = true,
                onToggleExpand = {},
            )
        }
        composeRule.onNodeWithText("400 / 600 kcal").assertExists()
        composeRule.onNodeWithContentDescription("P 30 g of 60 g goal").assertExists()
        composeRule.onNodeWithContentDescription("C 40 g of 80 g goal").assertExists()
        composeRule.onNodeWithContentDescription("F 12 g of 20 g goal").assertExists()
    }

    @Test
    fun meterExposesAccessibleLabels() {
        composeRule.setContent {
            MealMacroMeter(actual = actual, goal = goal)
        }
        composeRule.onNodeWithContentDescription("P 30 g of 60 g goal").assertExists()
        composeRule.onNodeWithContentDescription("C 40 g of 80 g goal").assertExists()
        composeRule.onNodeWithContentDescription("F 12 g of 20 g goal").assertExists()
    }

    @Test
    fun emptySectionHidesKcalAndMacroData() {
        composeRule.setContent {
            SectionHeader(
                name = "Breakfast",
                totalMacros = Macros(kcal = 0f, proteinG = 0f, carbsG = 0f, fatG = 0f),
                goalMacros = goal,
                hasEntries = false,
                isExpanded = true,
                onToggleExpand = {},
            )
        }
        composeRule.onNodeWithText("Breakfast").assertExists()
        composeRule.onNodeWithText("0 / 600 kcal").assertDoesNotExist()
        composeRule.onNodeWithText("0 kcal").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("P 0 g of 60 g goal").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("P 0 g").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("C 0 g").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("F 0 g").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("Expand").assertCountEquals(1)
    }

    @Test
    fun emptySectionShowsNeutralStatusBar() {
        composeRule.setContent {
            SectionHeader(
                name = "Breakfast",
                totalMacros = Macros(kcal = 0f, proteinG = 0f, carbsG = 0f, fatG = 0f),
                goalMacros = null,
                hasEntries = false,
                isExpanded = true,
                onToggleExpand = {},
            )
        }
        composeRule.onNodeWithTag("section-status-bar", useUnmergedTree = true)
            .assert(reportsStatus("Meal status: empty"))
    }

    @Test
    fun populatedSectionShowsPopulatedStatusBar() {
        composeRule.setContent {
            SectionHeader(
                name = "Lunch",
                totalMacros = actual,
                goalMacros = goal,
                hasEntries = true,
                isExpanded = true,
                onToggleExpand = {},
            )
        }
        composeRule.onNodeWithTag("section-status-bar", useUnmergedTree = true)
            .assert(reportsStatus("Meal status: populated"))
    }

    @Test
    fun emptySectionStillTogglesOnTap() {
        var toggles = 0
        composeRule.setContent {
            SectionHeader(
                name = "Breakfast",
                totalMacros = Macros(kcal = 0f, proteinG = 0f, carbsG = 0f, fatG = 0f),
                goalMacros = null,
                hasEntries = false,
                isExpanded = true,
                onToggleExpand = { toggles++ },
            )
        }
        composeRule.onNodeWithText("Breakfast").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun tappingHeaderInvokesToggle() {
        var toggles = 0
        composeRule.setContent {
            SectionHeader(
                name = "Lunch",
                totalMacros = actual,
                goalMacros = goal,
                hasEntries = true,
                isExpanded = true,
                onToggleExpand = { toggles++ },
            )
        }
        composeRule.onNodeWithText("Lunch").performClick()
        assertEquals(1, toggles)
    }

    private fun reportsStatus(expected: String): SemanticsMatcher =
        SemanticsMatcher("status bar reports '$expected'") {
            val description = it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            description.contains(expected)
        }
}

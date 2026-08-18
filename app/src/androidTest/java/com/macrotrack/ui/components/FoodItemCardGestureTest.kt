package com.macrotrack.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import com.macrotrack.domain.model.LogEntry
import com.macrotrack.domain.model.Macros
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FoodItemCardGestureTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stationaryLongPressDoesNotBlockTheNextDrag() {
        var longPresses = 0
        var dragStarts = 0
        val entry = LogEntry(
            id = 1,
            date = LocalDate.of(2026, 8, 17),
            sectionId = 1,
            foodItemId = 1,
            name = "Test food",
            brand = "Test brand",
            portionG = 100f,
            portionLabel = null,
            macros = Macros(kcal = 100f, proteinG = 10f, carbsG = 10f, fatG = 1f),
            sortOrder = 0,
            createdAt = Instant.EPOCH,
        )

        composeRule.setContent {
            FoodItemCard(
                entry = entry,
                isSelected = true,
                onClick = {},
                onLongPress = { longPresses++ },
                onDragStart = { dragStarts++ },
                onDragMove = {},
                onDragEnd = {},
                modifier = Modifier.testTag("food-card"),
            )
        }

        composeRule.onNodeWithTag("food-card").performTouchInput {
            down(center)
            advanceEventTime(700)
            up()
        }

        composeRule.onNodeWithTag("food-card").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(100f, 0f))
            up()
        }

        assertEquals(2, longPresses)
        assertEquals(1, dragStarts)
    }
}

package com.macrotrack.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.horizontalSwipeNav(
    thresholdDp: Dp = 48.dp,
    enabled: () -> Boolean = { true },
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier {
    val thresholdPx = with(LocalDensity.current) { thresholdDp.toPx() }

    return pointerInput(Unit) {
        var accumulator = 0f
        detectHorizontalDragGestures(
            onDragEnd = {
                if (!enabled()) return@detectHorizontalDragGestures
                if (accumulator > thresholdPx) {
                    onSwipeLeft()
                } else if (accumulator < -thresholdPx) {
                    onSwipeRight()
                }
                accumulator = 0f
            },
            onHorizontalDrag = { _, dragAmount ->
                accumulator += dragAmount
            },
            onDragCancel = {
                accumulator = 0f
            },
        )
    }
}

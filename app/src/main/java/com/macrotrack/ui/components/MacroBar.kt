package com.macrotrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun MacroBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    overageColor: Color? = null,
) {
    val goal = min(progress.coerceAtLeast(0f), 1f)
    val over = (progress - 1f).coerceIn(0f, 1f)
    val track = (2f - progress).coerceIn(0f, 2f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(color.copy(alpha = 0.12f)),
    ) {
        if (goal > 0f) Box(Modifier.weight(goal).fillMaxHeight().background(color))
        if (over > 0f && overageColor != null) Box(Modifier.weight(over).fillMaxHeight().background(overageColor))
        if (track > 0f) Box(Modifier.weight(track).fillMaxHeight())
    }
}

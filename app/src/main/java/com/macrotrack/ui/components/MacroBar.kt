package com.macrotrack.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.macrotrack.ui.theme.MotionTokens

@Composable
fun MacroBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    overageTint: Color? = null,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceAtLeast(0f),
        animationSpec = tween(MotionTokens.medium),
    )

    val goalFraction = animatedProgress.coerceAtMost(1f)
    val overFraction = if (overageTint != null) (animatedProgress - 1f).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.12f)),
    ) {
        if (goalFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(goalFraction)
                    .background(color),
            )
        }
        if (overFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(overFraction)
                    .background(overageTint!!),
            )
        }
    }
}

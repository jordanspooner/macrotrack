package com.macrotrack.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.macrotrack.ui.theme.MotionTokens

/**
 * Thin progress bar. Size is supplied by the caller via [modifier] (the bar
 * has no intrinsic width/height of its own) so it can be reused at various
 * dimensions (e.g. a 20x3dp at-a-glance indicator or a 4dp tall macro row).
 *
 * When [progress] exceeds 1.0 and [overageColor] is non-null, the bar renders
 * a two-tone split: the goal portion (1/progress of the width) in [color] and
 * the remaining overage in [overageColor], with the whole bar full width. When
 * [progress] is at or below 1.0, or [overageColor] is null, the bar behaves as
 * a single-tone fill of [color] to [progress].
 */
@Composable
fun MacroBar(
    progress: Float, // 0.0 to 1.0 (can go over 1.0)
    color: Color,
    modifier: Modifier = Modifier,
    overageColor: Color? = null
) {
    val showSplit = overageColor != null && progress > 1f

    val animatedProgress by animateFloatAsState(
        targetValue = if (showSplit) 1f else progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = MotionTokens.medium),
    )
    val animatedSplit by animateFloatAsState(
        targetValue = if (showSplit) (1f / progress) else 1f,
        animationSpec = tween(durationMillis = MotionTokens.medium),
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (showSplit) {
            Row(modifier = Modifier.fillMaxWidth(animatedProgress).fillMaxHeight()) {
                Box(modifier = Modifier.fillMaxWidth(animatedSplit).fillMaxHeight().background(color))
                Box(modifier = Modifier.fillMaxWidth(1f - animatedSplit).fillMaxHeight().background(overageColor))
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(color)
            )
        }
    }
}

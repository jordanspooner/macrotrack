package com.macrotrack.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing

object MotionTokens {
    val fast = 150
    val medium = 280
    val slow = 500
    val fastEasing: Easing = FastOutSlowInEasing
    val slowEasing: Easing = LinearOutSlowInEasing
}

package com.macrotrack.domain.model

data class MacroProgressSegments(
    val goalFraction: Float,
    val overageFraction: Float,
)

fun macroProgressSegments(progress: Float): MacroProgressSegments {
    val normalized = progress.coerceAtLeast(0f)
    return MacroProgressSegments(
        goalFraction = normalized.coerceAtMost(1f),
        overageFraction = (normalized - 1f).coerceIn(0f, 1f),
    )
}

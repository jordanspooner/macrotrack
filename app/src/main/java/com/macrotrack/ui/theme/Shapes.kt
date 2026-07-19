package com.macrotrack.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 shape scale for MacroTrack.
 *
 * - ExtraSmall: chips, pills, day cells (8dp)
 * - Small: section header backgrounds, list rows (12dp)
 * - Medium: food cards, text fields, dialogs top-tier (16dp)
 * - Large: macro summary card, calendar bottom sheet (24dp)
 * - ExtraLarge: FAB-adjacent surfaces, hero elements (28dp)
 */
val MacroTrackShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Fully-rounded pill for chips and CTA buttons. */
val pillShape = RoundedCornerShape(50)

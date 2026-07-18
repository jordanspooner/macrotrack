package com.macrotrack.ui.theme

import androidx.compose.ui.graphics.Color

// Macro colours — kept as fallback constants for non-dynamic contexts.
// In dynamic-color contexts, prefer the @Composable accessors in Theme.kt
// (macroCaloriesColor, macroProteinColor, macroCarbsColor, macroFatColor)
// which derive from the Material 3 color scheme so they adapt to the
// wallpaper-derived palette on Android 12+.
val MacroCalories = Color(0xFFFFB300) // Amber/Gold
val MacroProtein = Color(0xFF42A5F5)  // Blue
val MacroCarbs = Color(0xFF66BB6A)    // Green
val MacroFat = Color(0xFFEF5350)      // Rose/Red

// ---------------- Dark scheme ----------------
val md_theme_dark_primary = Color(0xFFABC7FF)
val md_theme_dark_onPrimary = Color(0xFF002F66)
val md_theme_dark_primaryContainer = Color(0xFF004590)
val md_theme_dark_onPrimaryContainer = Color(0xFFD7E2FF)
val md_theme_dark_secondary = Color(0xFFBEC6DC)
val md_theme_dark_onSecondary = Color(0xFF283141)
val md_theme_dark_secondaryContainer = Color(0xFF3E4758)
val md_theme_dark_onSecondaryContainer = Color(0xFFDAE2F9)
val md_theme_dark_tertiary = Color(0xFFDDBCE0)
val md_theme_dark_onTertiary = Color(0xFF3F2844)
val md_theme_dark_tertiaryContainer = Color(0xFF573E5C)
val md_theme_dark_onTertiaryContainer = Color(0xFFFAD8FD)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF1B1B1F)
val md_theme_dark_onBackground = Color(0xFFE3E2E6)
val md_theme_dark_surface = Color(0xFF1B1B1F)
val md_theme_dark_onSurface = Color(0xFFE3E2E6)
val md_theme_dark_surfaceVariant = Color(0xFF44474E)
val md_theme_dark_onSurfaceVariant = Color(0xFFC4C6CF)
val md_theme_dark_outline = Color(0xFF8E9099)
val md_theme_dark_inverseOnSurface = Color(0xFF1B1B1F)
val md_theme_dark_inverseSurface = Color(0xFFE3E2E6)
val md_theme_dark_inversePrimary = Color(0xFF005BC0)
val md_theme_dark_surfaceTint = Color(0xFFABC7FF)
val md_theme_dark_outlineVariant = Color(0xFF44474E)
val md_theme_dark_scrim = Color(0xFF000000)

// ---------------- Light scheme ----------------
// A complete light palette seeded from the same blue/violet family as the
// dark scheme so the brand identity is preserved across both modes.
val md_theme_light_primary = Color(0xFF005BC0)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFD7E2FF)
val md_theme_light_onPrimaryContainer = Color(0xFF001A40)
val md_theme_light_secondary = Color(0xFF555F71)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFD9E3F8)
val md_theme_light_onSecondaryContainer = Color(0xFF121C2B)
val md_theme_light_tertiary = Color(0xFF704574)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFFAD8FD)
val md_theme_light_onTertiaryContainer = Color(0xFF2A012F)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFFEFBFF)
val md_theme_light_onBackground = Color(0xFF1B1B1F)
val md_theme_light_surface = Color(0xFFFEFBFF)
val md_theme_light_onSurface = Color(0xFF1B1B1F)
val md_theme_light_surfaceVariant = Color(0xFFE0E2EC)
val md_theme_light_onSurfaceVariant = Color(0xFF44474E)
val md_theme_light_outline = Color(0xFF74777F)
val md_theme_light_inverseOnSurface = Color(0xFFF2F0F4)
val md_theme_light_inverseSurface = Color(0xFF2F3033)
val md_theme_light_inversePrimary = Color(0xFFABC7FF)
val md_theme_light_surfaceTint = Color(0xFF005BC0)
val md_theme_light_outlineVariant = Color(0xFFC4C6CF)
val md_theme_light_scrim = Color(0xFF000000)

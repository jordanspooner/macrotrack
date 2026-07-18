# MacroTrack Full-App UX Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign MacroTrack's UI/UX end-to-end (theme tokens, Home/Log, Add flow, Settings, Calendar) into a polished, consistent, delightful Android app that follows Material You adaptive surfaces with authored macro-color accents.

**Architecture:** Each screen is a self-contained Jetpack Compose `@Composable` file under `app/src/main/java/com/macrotrack/ui/...`. Shared design tokens live in `ui/theme/`. A shared `SaveButton` and `MacroDonut` component are introduced. The redesign is purely presentational + minor ViewModel additions (Settings unsaved-guard); no domain/data layer changes. Tracks 1 (tokens) runs first; Tracks 2–5 run in parallel after; Track 6 (verification) runs last.

**Tech Stack:** Kotlin 2.4, Jetpack Compose (BOM 2026.06.01), Material 3, Hilt. Optional drag-and-drop via `com.github.burnoutcrew.composereorderable:reorderable:0.9.6` (JitPack). Gradle Kotlin DSL. `./gradlew` wrapper (JAVA_HOME must point at a JDK 21 since compileSdk=37 / AGP 9.2.1).

## Global Constraints (from spec, verbatim where applicable)

- Surfaces / text / outline / error tokens: inherited from `MaterialTheme.colorScheme` (dynamic Material You on Android 12+, with authored fallback). Do NOT hard-code `Color(0xFF…)` for surfaces.
- Authored macro colours (stable across devices, dark/light pair chosen via `isSystemInDarkTheme()`):
  - `kcal` = `#F5A524` (dark `#FBBF24`)
  - `protein` = `#5F8B4B` (dark `#A3C587`)
  - `carbs` = `#3B82F6` (dark `#7DAFFF`)
  - `fat` = `#D97757` (dark `#E89A7D`)
- Brand primary (FAB, tab indicator, selected-date wash, pill accents, SaveButton fill) = sage, NOT `MaterialTheme.colorScheme.primary`: `#3F6B47` (dark `#8FBF9A`).
- No macro colour may represent a *section identity* — only macro values. Section spec pill and food-card stripe use `brandPrimary`/kcal only (per user amendment).
- Macro colour accessors (`macroCaloriesColor()`, `macroProteinColor()`, `macroCarbsColor()`, `macroFatColor()`) must return the authored light/dark variant based on `isSystemInDarkTheme()`, NOT `colorScheme.primary/error/secondary/tertiary`.
- Spacing must come from `Spacing` object (`xs=4, sm=8, md=12, lg=16, xl=24, xxl=32, xxxl=48`). No bare `.dp` for layout padding (0dp and 1dp hairlines allowed).
- Motion must use `MotionTokens` (`fast=150, medium=280, slow=500`). No bare `tween(200)`.
- `AddFoodBottomBar` composable is removed from the codebase entirely.
- No data-layer / domain-layer / repository / use-case changes. Only `SettingsViewModel` gets a new `hasUnsavedChanges` flow (no signature collision with existing methods).
- `./gradlew :app:assembleDebug` MUST succeed. `./gradlew :app:testDebugUnitTest` MUST pass.

---

## File Structure Map

New / modified files:

| File | Action | Responsibility |
|---|---|---|
| `ui/theme/Color.kt` | Modify | Sage-leaning dark/light static fallback scheme; authored macro color constants |
| `ui/theme/Theme.kt` | Modify | Rewire macro accessors to authored light/dark pairs; add `pillShape` to shapes |
| `ui/theme/Shapes.kt` | Modify | Add `pillShape = RoundedCornerShape(50)` |
| `ui/theme/Spacing.kt` | Create | `Spacing` object with the scale above |
| `ui/theme/Motion.kt` | Create | `MotionTokens` object |
| `ui/components/SaveButton.kt` | Create | Shared save CTA (filled vs outlined) |
| `ui/components/MacroDonut.kt` | Create | 4-segment macro ring replacing `MacroPieChart` |
| `ui/add/MacroPieChart.kt` | Delete | Replaced by `MacroDonut` |
| `ui/components/WeekDateStrip.kt` | Modify | Grouped surface, brand-wash selected pill, inline progress |
| `ui/components/MacroSummaryCard.kt` | Modify | Inline macro bars with overage colour split |
| `ui/components/SectionHeader.kt` | Modify | Brand spec pill (not macro colour); 3 mini macro bars |
| `ui/components/FoodItemCard.kt` | Modify | kcal-stripe; colour-coded nutrition row |
| `ui/components/AddFoodBottomBar.kt` | Delete | Removed entirely |
| `ui/components/SelectionBottomBar.kt` | Modify | Container `surfaceVariant` + top hairline |
| `ui/log/LogScreen.kt` | Modify | Remove 3-dot overflow (keep single gear); FAB bottom-sheet icon polish; illustrated empty day |
| `ui/add/AddScreen.kt` | Modify | Relative date; tab label "Quick"; section selector drop "ISO" |
| `ui/add/SearchContent.kt` | Modify | Sticky search; quick-add pill; empty CTA |
| `ui/add/QuickAddContent.kt` | Modify | Sectioned card form; macro-dot leading icons |
| `ui/add/PortionSizeScreen.kt` | Modify | Use `MacroDonut`; FlowRow chips; unit dropdown; bottom CTA |
| `ui/settings/SettingsScreen.kt` | Modify | Drag-and-drop rows; time chip; distribution viz; SaveButton; discard guard |
| `ui/settings/SettingsViewModel.kt` | Modify | `hasUnsavedChanges` flow |
| `ui/settings/CalendarModal.kt` | Modify | 3 clean states; remove `This Week`; single-tap dismiss |
| `app/build.gradle.kts` | Modify | Add `reorderable` dependency |
| `settings.gradle.kts` | Modify | Add `maven { url = uri("https://jitpack.io") }` |

---

## Task 1: Theme tokens foundation (Track A)

**Files:**
- Modify: `app/src/main/java/com/macrotrack/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/macrotrack/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/macrotrack/ui/theme/Shapes.kt`
- Create: `app/src/main/java/com/macrotrack/ui/theme/Spacing.kt`
- Create: `app/src/main/java/com/macrotrack/ui/theme/Motion.kt`

**Interfaces:**
- Produces: `macroCaloriesColor()`, `macroProteinColor()`, `macroCarbsColor()`, `macroFatColor()` (authored light/dark), `brandPrimaryDark`, `brandPrimaryLight`, `pillShape`, `Spacing`, `MotionTokens` — all consumed by later tasks.

- [ ] **Step 1: Rewrite `Color.kt` static fallback to sage family**

Replace the blue-violet dark/light scheme blocks with a sage-leaning authored palette, and replace the `MacroCalories`/`MacroProtein`/`MacroCarbs`/`MacroFat` constants with the new authored pair set. Keep the exact same field names used by `Theme.kt` (`md_theme_dark_*` / `md_theme_light_*`). Add brand + macro authored constants at the top.

```kotlin
package com.macrotrack.ui.theme

import androidx.compose.ui.graphics.Color

// Authored macro accents — stable across devices. Light (fallback) / dark variants.
val MacroCaloriesLight = Color(0xFFF5A524) // amber/gold
val MacroCaloriesDark  = Color(0xFFFBBF24)
val MacroProteinLight  = Color(0xFF5F8B4B) // olive
val MacroProteinDark   = Color(0xFFA3C587)
val MacroCarbsLight    = Color(0xFF3B82F6) // sky blue
val MacroCarbsDark     = Color(0xFF7DAFFF)
val MacroFatLight      = Color(0xFFD97757) // terracotta
val MacroFatDark       = Color(0xFFE89A7D)

// Authored brand primary (sage) — used for FAB, tab indicator, selected date, CTAs.
val BrandPrimaryLight = Color(0xFF3F6B47)
val BrandPrimaryDark  = Color(0xFF8FBF9A)

// ---------------- Dark scheme (sage-leaning) ----------------
val md_theme_dark_primary = Color(0xFF8FBF9A)
val md_theme_dark_onPrimary = Color(0xFF003220)
val md_theme_dark_primaryContainer = Color(0xFF1E422F)
val md_theme_dark_onPrimaryContainer = Color(0xFFAEE5C4)
val md_theme_dark_secondary = Color(0xFFB7CCC0)
val md_theme_dark_onSecondary = Color(0xFF1F3630)
val md_theme_dark_secondaryContainer = Color(0xFF354B44)
val md_theme_dark_onSecondaryContainer = Color(0xFFD3E9DC)
val md_theme_dark_tertiary = Color(0xFFA6CCD0)
val md_theme_dark_onTertiary = Color(0xFF073438)
val md_theme_dark_tertiaryContainer = Color(0xFF23484C)
val md_theme_dark_onTertiaryContainer = Color(0xFFC2E9ED)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF1A1C1B)
val md_theme_dark_onBackground = Color(0xFFE0E3E0)
val md_theme_dark_surface = Color(0xFF1A1C1B)
val md_theme_dark_onSurface = Color(0xFFE0E3E0)
val md_theme_dark_surfaceVariant = Color(0xFF3F4945)
val md_theme_dark_onSurfaceVariant = Color(0xFFBFC9C3)
val md_theme_dark_outline = Color(0xFF89938E)
val md_theme_dark_inverseOnSurface = Color(0xFF1A1C1B)
val md_theme_dark_inverseSurface = Color(0xFFE0E3E0)
val md_theme_dark_inversePrimary = Color(0xFF20513A)
val md_theme_dark_surfaceTint = Color(0xFF8FBF9A)
val md_theme_dark_outlineVariant = Color(0xFF3F4945)
val md_theme_dark_scrim = Color(0xFF000000)

// ---------------- Light scheme (sage-leaning) ----------------
val md_theme_light_primary = Color(0xFF20513A)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFAEE5C4)
val md_theme_light_onPrimaryContainer = Color(0xFF00210F)
val md_theme_light_secondary = Color(0xFF4F6358)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFD1E7DA)
val md_theme_light_onSecondaryContainer = Color(0xFF0A2018)
val md_theme_light_tertiary = Color(0xFF38656A)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFBCEBEF)
val md_theme_light_onTertiaryContainer = Color(0xFF001F23)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFFAFDFA)
val md_theme_light_onBackground = Color(0xFF1A1C1B)
val md_theme_light_surface = Color(0xFFFAFDFA)
val md_theme_light_onSurface = Color(0xFF1A1C1B)
val md_theme_light_surfaceVariant = Color(0xFFDBE5DE)
val md_theme_light_onSurfaceVariant = Color(0xFF3F4945)
val md_theme_light_outline = Color(0xFF6F7974)
val md_theme_light_inverseOnSurface = Color(0xFFEFF1EE)
val md_theme_light_inverseSurface = Color(0xFF2F3230)
val md_theme_light_inversePrimary = Color(0xFF8FBF9A)
val md_theme_light_surfaceTint = Color(0xFF20513A)
val md_theme_light_outlineVariant = Color(0xFFBFC9C3)
val md_theme_light_scrim = Color(0xFF000000)
```

- [ ] **Step 2: Rewrite `Theme.kt` macro accessors to authored pairs**

Replace the 5 macro/overage/success/warning accessors (lines 98–120) so they return authored colors keyed on `isSystemInDarkTheme()`, and add `brandPrimary()` accessors. Keep `restingSurfaceColor()` and `overageColor()` (overage = scheme error). Keep `MacroTrackTheme` unchanged.

```kotlin
import androidx.compose.foundation.isSystemInDarkTheme

private val isDark: Boolean
    @Composable get() = isSystemInDarkTheme()

@Composable
fun macroCaloriesColor(): Color = if (isDark) MacroCaloriesDark else MacroCaloriesLight

@Composable
fun macroProteinColor(): Color = if (isDark) MacroProteinDark else MacroProteinLight

@Composable
fun macroCarbsColor(): Color = if (isDark) MacroCarbsDark else MacroCarbsLight

@Composable
fun macroFatColor(): Color = if (isDark) MacroFatDark else MacroFatLight

/** Authored brand primary (sage) — used for FAB, tab indicator, CTAs. */
@Composable
fun brandPrimary(): Color = if (isDark) BrandPrimaryDark else BrandPrimaryLight

/** Authored brand on-primary (cream/white) for use on brand-filled surfaces. */
@Composable
fun brandOnPrimary(): Color = if (isDark) Color(0xFF00210F) else Color(0xFFFFFFFF)

/** Color used when a goal is exceeded. Always the scheme's error color. */
@Composable
fun overageColor(): Color = MaterialTheme.colorScheme.error

@Composable
fun restingSurfaceColor(): Color =
    MaterialTheme.colorScheme.surface.copy(alpha = 0.04f)
```

Remove the old `successColor()` / `warningColor()` lines only if nothing references them (grep first; if referenced, keep them returning `brandPrimary()`/`overageColor()`).

- [ ] **Step 3: Add `pillShape` to `Shapes.kt`**

```kotlin
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val MacroTrackShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Fully-rounded pill for chips and CTA buttons. */
val pillShape = RoundedCornerShape(50)
```

In `Theme.kt`, `MacroTrackTheme` already passes `shapes = MacroTrackShapes`. Add `val MacroTrackPillShape = pillShape` export (a top-level `val`) so screens import `MacroTrackPillShape`.

- [ ] **Step 4: Create `Spacing.kt`**

```kotlin
package com.macrotrack.ui.theme

import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}
```

- [ ] **Step 5: Create `Motion.kt`**

```kotlin
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
```

- [ ] **Step 6: Verify it compiles & commit**

Run: `cd /home/jordan/Projects/MacroTrack && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

```bash
git add -A && git commit -m "feat(theme): authored macro colors, brand sage, spacing & motion tokens"
```

---

## Task 2: Shared components (SaveButton + MacroDonut)

**Files:**
- Create: `app/src/main/java/com/macrotrack/ui/components/SaveButton.kt`
- Create: `app/src/main/java/com/macrotrack/ui/components/MacroDonut.kt`
- Delete: `app/src/main/java/com/macrotrack/ui/add/MacroPieChart.kt`

**Interfaces:**
- Consumes: `brandPrimary()`, `brandOnPrimary()`, `macroCaloriesColor()`, `macroProteinColor()`, `macroCarbsColor()`, `macroFatColor()`, `MacroTrackPillShape`, `Spacing`, `MotionTokens`, `com.macrotrack.domain.model.Macros`.
- Produces: `SaveButton(hasChanges, label, onClick)`, `MacroDonut(macros, diameter, centerText)`.

- [ ] **Step 1: Create `SaveButton.kt`**

```kotlin
package com.macrotrack.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.macrotrack.ui.theme.MacroTrackPillShape
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandPrimary
import com.macrotrack.ui.theme.brandOnPrimary

@Composable
fun SaveButton(
    hasChanges: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hasChanges) {
        Button(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MacroTrackPillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = brandPrimary(),
                contentColor = brandOnPrimary(),
            ),
        ) { Text(label) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MacroTrackPillShape,
        ) { Text(label, color = brandPrimary()) }
    }
}
```

- [ ] **Step 2: Create `MacroDonut.kt`** (4-segment ring)

```kotlin
package com.macrotrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.macrotrack.domain.model.Macros
import com.macrotrack.ui.theme.macroCarbsColor
import com.macrotrack.ui.theme.macroFatColor
import com.macrotrack.ui.theme.macroProteinColor

@Composable
fun MacroDonut(
    macros: Macros,
    modifier: Modifier = Modifier,
    diameter: Dp = 180.dp,
    centerText: String? = null,
) {
    val proteinKcal = macros.proteinG * 4f
    val carbsKcal = macros.carbsG * 4f
    val fatKcal = macros.fatG * 9f
    val total = proteinKcal + carbsKcal + fatKcal
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(diameter)) {
        Canvas(modifier = Modifier.size(diameter)) {
            if (total <= 0f) return@Canvas
            val stroke = size.minDimension * 0.16f
            val radius = (size.minDimension - stroke) / 2f
            val topLeft = Offset((size.width - radius * 2) / 2f, (size.height - radius * 2) / 2f)
            val arcSize = Size(radius * 2, radius * 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, Stroke(stroke))
            val segments = listOf(
                proteinKcal to macroProteinColor(),
                carbsKcal to macroCarbsColor(),
                fatKcal to macroFatColor(),
            )
            var startAngle = -90f
            for ((value, color) in segments) {
                val sweep = (value / total) * 360f
                if (sweep > 0f) {
                    drawArc(color, startAngle, sweep, false, topLeft, arcSize, Stroke(stroke))
                    startAngle += sweep
                }
            }
        }
        centerText?.let {
            Text(it, style = MaterialTheme.typography.titleLarge)
        }
    }
}
```

- [ ] **Step 3: Delete `MacroPieChart.kt`** (its single caller `PortionSizeScreen` is updated in Task 6).

```bash
git rm app/src/main/java/com/macrotrack/ui/add/MacroPieChart.kt
```

- [ ] **Step 4: Compile & commit**

Run: `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL.

```bash
git add -A && git commit -m "feat(components): SaveButton + MacroDonut shared components"
```

---

## Task 3: Home / Log screen redesign (Track B)

**Files:**
- Modify: `app/src/main/java/com/macrotrack/ui/components/WeekDateStrip.kt`
- Modify: `app/src/main/java/com/macrotrack/ui/components/MacroSummaryCard.kt`
- Modify: `app/src/main/java/com/macrotrack/ui/components/SectionHeader.kt`
- Modify: `app/src/main/java/com/macrotrack/ui/components/FoodItemCard.kt`
- Modify: `app/src/main/java/com/macrotrack/ui/components/SelectionBottomBar.kt`
- Modify: `app/src/main/java/com/macrotrack/ui/log/LogScreen.kt`
- Delete: `app/src/main/java/com/macrotrack/ui/components/AddFoodBottomBar.kt`

**Interfaces:**
- Consumes: `macroCaloriesColor()`, `macroProteinColor()`, `macroCarbsColor()`, `macroFatColor()`, `brandPrimary()`, `overageColor()`, `restingSurfaceColor()`, `MacroTrackPillShape`, `Spacing`, `MotionTokens`, `MacroBar`.

- [ ] **Step 1: Rewrite `WeekDateStrip.kt`** — grouped surface, brand-wash selected pill, inline progress bar, remove today's separate floating MacroBar.

Replace entire file content with:

```kotlin
package com.macrotrack.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.macrotrack.ui.log.WeekDay
import com.macrotrack.ui.theme.MacroTrackPillShape
import com.macrotrack.ui.theme.MacroTrackShapes
import com.macrotrack.ui.theme.Spacing
import com.macrotrack.ui.theme.brandPrimary
import com.macrotrack.ui.theme.macroCaloriesColor
import com.macrotrack.ui.theme.restingSurfaceColor
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WeekDateStrip(
    weekDays: List<WeekDay>,
    onDateSelected: (WeekDay) -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = restingSurfaceColor(),
        shape = MacroTrackShapes.large,
        modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCalendar() }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = weekDays.first().date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                weekDays.forEach { DayItem(it, onClick = { onDateSelected(it) }) }
            }
        }
    }
}

@Composable
private fun DayItem(day: WeekDay, onClick: () -> Unit) {
    val containerColor by animateColorAsState(
        targetValue = if (day.isSelected) brandPrimary().copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(MotionTokens.medium),
    )
    val textColor = when {
        day.isSelected -> brandPrimary()
        day.isToday -> brandPrimary()
        else -> MaterialTheme.colorScheme.onSurface
    }
    val scale by animateFloatAsState(
        targetValue = if (day.isSelected) 1.05f else 1f,
        animationSpec = tween(MotionTokens.medium),
    )
    Surface(
        color = containerColor,
        shape = MacroTrackPillShape,
        modifier = Modifier
            .scale(scale)
            .clip(MacroTrackPillShape)
            .clickable(onClick = onClick)
            .padding(Spacing.xs),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                day.dayName,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = if (day.isToday || day.isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                day.dayNumber.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                fontWeight = if (day.isToday || day.isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            if (day.kcalPercent > 0f) {
                MacroBar(
                    progress = day.kcalPercent,
                    color = macroCaloriesColor(),
                    modifier = Modifier
                        .width(24.dp)
                        .height(4.dp),
                )
            } else {
                Box(Modifier.size(width = 24.dp, height = 4.dp))
            }
        }
    }
}
```

- [ ] **Step 2: Update `MacroSummaryCard.kt`** — keep the existing ring, but change `MacroRow` to use `Spacing`/`MotionTokens` and overage colour split.

In `MacroRow` (lines 146–188), change the `MacroBar` call to pause the bar at goal and colour overage red. Replace the whole `MacroRow` function with:

```kotlin
@Composable
private fun MacroRow(
    label: String,
    logged: Float,
    goal: Int,
    percent: Float,
    color: Color,
) {
    val isOver = percent > 1f
    val resolvedColor = if (isOver) overageColor() else color
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(resolvedColor, CircleShape))
            Spacer(Modifier.width(Spacing.sm))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                "${logged.roundToInt()} / ${goal}g",
                style = MaterialTheme.typography.labelLarge,
                color = if (isOver) overageColor() else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.sm))
            Surface(
                color = resolvedColor.copy(alpha = 0.15f),
                shape = MacroTrackPillShape,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            ) {
                Text(
                    "${(percent * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = resolvedColor,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        MacroBar(
            progress = percent,
            color = resolvedColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(start = 16.dp, end = Spacing.sm),
        )
    }
}
```

Add imports: `com.macrotrack.ui.theme.Spacing`, `com.macrotrack.ui.theme.MacroTrackPillShape`, `com.macrotrack.ui.theme.MotionTokens`, `com.macrotrack.ui.theme.overageColor`. Change the ring's `animateFloatAsState` duration from `500` to `MotionTokens.slow`.

- [ ] **Step 3: Update `SectionHeader.kt`** — brand spec pill (not macro colour), 3 mini macro bars.

Replace the leading `Box` spec pill (lines 49–53) with a brand-primary rounded rectangle:

```kotlin
Box(
    modifier = Modifier
        .width(6.dp)
        .height(24.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(brandPrimary())
)
```

Replace the right-hand `0g P / 0g C / 0g F` `Text` triple (lines 71–89) with three stacked mini-bars:

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    MiniMacro(label = "P", value = totalMacros.proteinG, color = macroProteinColor())
    MiniMacro(label = "C", value = totalMacros.carbsG, color = macroCarbsColor())
    MiniMacro(label = "F", value = totalMacros.fatG, color = macroFatColor())
}
```

And add the helper at file bottom:

```kotlin
@Composable
private fun MiniMacro(label: String, value: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${(value).roundToInt()}g $label", style = MaterialTheme.typography.labelSmall, color = color)
    }
}
```

Remove the `accentColor` default parameter (it leaks the macro-protein blue). Add imports for `brandPrimary`, `macroProteinColor`, `macroCarbsColor`, `macroFatColor`, `Spacing`, `MacroTrackShapes`, `RoundedCornerShape`.

- [ ] **Step 4: Update `FoodItemCard.kt`** — kcal stripe + colour-coded nutrition row.

Change the left stripe color from `accentColor` to `macroCaloriesColor()` (line 63). When the *daily* goal is exceeded we don't know daily context here, so keep stripe as kcal colour always (per spec amendment: kcal amber, or error when daily goal exceeded — daily context is unavailable in this card, so plain kcal amber is correct). Remove the `accentColor` param default line 30. Keep `NutritionRow(macros = entry.macros)` as-is (it already colour-codes per macro). Add import `com.macrotrack.ui.theme.macroCaloriesColor` and drop the `accentColor` parameter from the composable signature (update callers in `LogScreen.kt`: `FoodItemCard(entry = entry, ...)` already passes no accentColor — verify, remove if present).

- [ ] **Step 5: Update `SelectionBottomBar.kt`** — container to `surfaceVariant` + top hairline.

Change `containerColor = MaterialTheme.colorScheme.primaryContainer` to `MaterialTheme.colorScheme.surfaceVariant` and `contentColor = MaterialTheme.colorScheme.onPrimaryContainer` to `MaterialTheme.colorScheme.onSurface`. Add a top `HorizontalDivider` inside the Row (first child) coloured `MaterialTheme.colorScheme.outlineVariant` 1.dp.

- [ ] **Step 6: Update `LogScreen.kt`** —
  - Remove any `3-dot`/overflow menu wiring (current code has only a gear — keep the gear `IconButton` at lines 64–73, unchanged except confirm no duplicate).
  - In the `isEmpty` branch (lines 158–197) replace the plain text empty state with an illustrated `Surface` `shape=large` containing a 56dp `RestaurantMenu` icon in a circular `brandPrimary().copy(alpha=0.2f)` background + title + caption (mirror the style from spec Section 4). Use `Spacing` for padding.
  - Keep the FAB + `ModalBottomSheet` add menu. In `AddMenuOption` (lines 334–377) replace `Icons.Default.Add` label-scan and barcode placeholders with `Icons.Default.DocumentScanner` and `Icons.Default.QrCode2`. (These icon names exist in `material-icons-extended`, already a dependency.)
  - Remove the `AddFoodBottomBar` import if present (file currently doesn't import it; verify).

- [ ] **Step 7: Delete `AddFoodBottomBar.kt`**

```bash
git rm app/src/main/java/com/macrotrack/ui/components/AddFoodBottomBar.kt
```

- [ ] **Step 8: Compile & commit**

Run: `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL.

```bash
git add -A && git commit -m "feat(home): redesign date strip, macro card, section headers, food cards, empty states"
```

---

## Task 4: Add Food flow redesign (Track C)

**Files:**
- Modify: `app/src/main/java/com/macrotrack/ui/add/AddScreen.kt`
- Modify: `app/src/main/java/com/macrotrack/ui/add/SearchContent.kt`
- Modify: `app/src/main/java/com/macrotrack/ui/add/QuickAddContent.kt`
- Modify: `app/src/main/java/com/macrotrack/ui/add/PortionSizeScreen.kt`

**Interfaces:**
- Consumes: `MacroDonut`, `brandPrimary()`, `brandOnPrimary()`, `macroCaloriesColor()` etc., `MacroTrackPillShape`, `Spacing`, `MotionTokens`, `AddMode` (existing in `AddViewModel.kt`), `Five`/`Three` private classes are internal — do NOT touch them.

- [ ] **Step 1: `AddScreen.kt`** — relative date title, tab label "Quick", no ISO subtitle.

  - In the `TopAppBar` title `Column` (lines 52–59), replace `uiState.dateIso` Text with a relative date computed from `uiState.date` (import `java.time.LocalDate`):

```kotlin
val rel = when (uiState.date) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> uiState.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
}
Text(rel, style = MaterialTheme.typography.titleMedium)
Text(
    uiState.sections.find { it.id == uiState.targetSectionId }?.name ?: "Dinner",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

  - Change `AddMode.label` (lines 171–176) so `QUICK_ADD` returns `"Quick"`.
  - Keep `SectionSelector` but it is fine; no ISO shown there.

- [ ] **Step 2: `SearchContent.kt`** — sticky search field, quick-add pill per row, empty CTA.

  In `SearchContent`, wrap the search `OutlinedTextField` so it stays a single field at top (it already exists). In each result row, add a trailing `IconButton` with `Icons.Default.Add` tinted `brandPrimary()` that calls `onFoodSelected(food)`. Add an empty-results `Box` (when `uiState.results.isEmpty() && uiState.query.isNotBlank()`) with a `TextButton("Quick add manually")` that calls `viewModel.setMode(AddMode.QUICK_ADD)` — but `SearchContent` currently only receives `onFoodSelected`/`onQueryChanged`. Add a new parameter `onQuickAddClick: () -> Unit` to `SearchContent` and thread it from `AddScreen` (`viewModel::setMode` with `AddMode.QUICK_ADD`).
  Use `Spacing` for all paddings, `MacroTrackPillShape` for any pills.

- [ ] **Step 3: `QuickAddContent.kt`** — sectioned form + macro-dot leading icons.

  Group the 3 existing text fields into three `Card`s (`surfaceVariant` bg, `Shape.medium`): (1) Name (required), (2) Brand (optional) + Portion label, (3) Nutrition per 100g as a 2×2 `Column`/`FlowRow` of `OutlinedTextField`s, each with a `leadingIcon` = a 8dp dot in the macro colour (kcal=amber, P=olive, C=sky, F=terracotta). Keep existing validation behaviour. Save button: replace the current `Button` with `SaveButton(hasChanges = draftValid, label = "Save food", onClick = onSubmit)` where `draftValid = uiState.quickAddDraft.name.isNotBlank() && (kcal or any macro non-blank)`. Import `SaveButton`.

- [ ] **Step 4: `PortionSizeScreen.kt`** — use `MacroDonut`, FlowRow chips, unit dropdown, bottom CTA.

  - Replace the `Box { MacroPieChart(...) ; Text(...) }` block (lines 72–78) with `MacroDonut(macros = portioned, diameter = 180.dp, centerText = "${portioned.kcal.toInt()} kcal")`.
  - Replace the preset `Row`s (lines 99–115) with a single `FlowRow` (import `androidx.compose.foundation.lazy.grid` is wrong — use `androidx.compose.foundation.layout.FlowRow`) of 6 chips: `1/4, 1/2, 1, 1.5, 2, 3` mapping to `0.25, 0.5, 1, 1.5, 2, 3` × `defaultPortionG`. Chip = `FilterChip` or `Button` with `MacroTrackPillShape`; selected chip gets `brandPrimary()` fill. (Remove `0.33/1/3` and `4` to match the spec's exact 6.)
  - Replace the "Custom multiple" `OutlinedTextField` (lines 117–129) with a single `OutlinedTextField` labelled "Custom amount (g)" that directly sets `portionG`.
  - Add a bottom sticky CTA: replace the existing `Button` (lines 131–140) with text `"Add to ${uiState target section name} · ${portioned.kcal.toInt()} kcal"` using `SaveButton(hasChanges = true, ...)`. The section name comes from the ViewModel; pass `sectionName: String` parameter into `PortionSizeScreen` from `AddScreen` (`uiState.sections.find { id == targetSectionId }?.name`).
  - Import `com.macrotrack.ui.components.MacroDonut` (delete `MacroPieChart` import).
  - `AddScreen` passes `sectionName` to `PortionSizeScreen`.

- [ ] **Step 5: Compile & commit**

Run: `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL.

```bash
git add -A && git commit -m "feat(add): redesign add flow, portion picker, quick-add form"
```

---

## Task 5: Settings screen redesign (Track D)

**Files:**
- Modify: `app/src/main/java/com/macrotrack/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/macrotrack/ui/settings/SettingsViewModel.kt`

**Interfaces:**
- Consumes: `SaveButton`, `brandPrimary()`, `macroProteinColor()` etc., `Spacing`, `MotionTokens`, `reorderable` library (`ReorderableLazyColumn` / `rememberReorderableLazyListState`), `TimePickerDialog` (already in file).
- Produces: drag-and-drop reorder with stable times (times are untouched on reorder — `DraftSection.timeOfDay` lives in the model, reorder only changes list order via a new `reorderSections(from, to)` in the ViewModel).

- [ ] **Step 1: Add `reorderable` dependency**

In `app/build.gradle.kts`, add inside `dependencies { ... }`:
```kotlin
implementation("com.github.burnoutcrew.composereorderable:reorderable:0.9.6")
```
In `settings.gradle.kts`, inside `dependencyResolutionManagement { repositories { ... } }` add:
```kotlin
maven { url = uri("https://jitpack.io") }
```
Run `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep reorderable` to confirm resolution. If JitPack is unreachable, fall back to a manual `PointerInput` drag implementation (note in commit message) — do NOT block the rest of the task.

- [ ] **Step 2: Add `reorderSections` + `hasUnsavedChanges` to `SettingsViewModel.kt`**

Add a new private helper and public method:
```kotlin
fun reorderSections(fromIndex: Int, toIndex: Int) {
    val list = _draftSections.value.toMutableList()
    if (fromIndex in list.indices && toIndex in list.indices && fromIndex != toIndex) {
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _draftSections.value = list.mapIndexed { i, ds -> ds.copy(sortOrder = i) }
    }
}
```
Add `hasUnsavedChanges` flow:
```kotlin
private val _hasUnsavedChanges = MutableStateFlow(false)
val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()
```
Set `_hasUnsavedChanges.value = true` inside `updateDraftGoalProtein/Carbs/Fat`, `updateDraftSectionName`, `updateDraftSectionTime`, `removeDraftSection`, `addDraftSection`, `reorderSections`, `moveDraftSectionUp/Down`, `resetSectionsToDefaults`, `updateDistribution`. Reset to `false` in `saveGoals()` and `saveSections()` after the save completes.

- [ ] **Step 3: Daily Goals block (`SettingsScreen.kt` items block 1, lines 84–158)**

  - Wrap the three `OutlinedTextField`s in a `Card` (`surfaceVariant`, `Shape.medium`) with `Spacing` padding.
  - Add a `leadingIcon` to each: a 8dp `Box` dot in `macroProteinColor()` / `macroCarbsColor()` / `macroFatColor()` from a small `MacroDot` helper.
  - Replace the total `Card` (lines 128–144) content with: a `MacroBar`-style stacked row (3 segments P/C/F sized by kcal share) + legend `P xx% · C xx% · F xx%` in macro colours + right-aligned `2,185 kcal` `titleLarge` in `macroCaloriesColor()`.
  - Replace the `Row` with `Button("Save Goals")` + `Color.Green "Saved!"` (lines 146–157) with `SaveButton(hasChanges = uiState.hasUnsavedChanges, label = "Save goals", onClick = { viewModel.saveGoals() })`. For the saved confirmation, show a `SnackbarHost` in the `Scaffold` (add `snackbarHost` + trigger `LaunchedEffect(uiState.goalsSaved)` to show "Goals saved").
  - Add `Scaffold` `snackbarHost` parameter (currently the `Scaffold` at line 65 has none).

- [ ] **Step 4: Meal Sections block — drag-and-drop (lines 167–249)**

  - Replace the `itemsIndexed(uiState.draftSections) { ... }` block with a `ReorderableLazyColumn` (or `LazyColumn` + `reorderable` `item` modifier) where each row:
    - Leading `IconButton` with `Icons.Default.DragHandle` (material-icons-extended has it) carrying the `reorderable` drag modifier.
    - `OutlinedTextField` name (`weight(1f)`).
    - A clickable `Surface` pill (`MacroTrackPillShape`, `surfaceVariant`) with `Icons.Default.Schedule` + `ds.timeOfDay` text → opens `TimePickerDialog` (unchanged behaviour). Times are edited here, NOT moved on reorder.
    - `IconButton` trash (`Icons.Outlined.Delete`) → `AlertDialog` confirm: "Delete '${ds.name}'? Its logged meals move to the section above." Confirm calls `viewModel.removeDraftSection(index)`.
  - Replace arrow `IconButton`s (`moveDraftSectionUp/Down`) — keep them as secondary controls OR remove; spec prefers drag. Keep them but disabled-look is fine; alternatively drop them entirely. Delete the "Add Section" / "Reset to Defaults" / "Save Sections" row (lines 227–249) with:
    - `OutlinedButton` (brand border, `MacroTrackPillShape`) "Add section" → `viewModel.addDraftSection("New Section")`.
    - `TextButton` "Reset to defaults" → confirm dialog → `viewModel.resetSectionsToDefaults()`.
    - `SaveButton(hasChanges = uiState.hasUnsavedChanges, label = "Save sections", onClick = { viewModel.saveSections() })`.
  - Use `viewModel.reorderSections(from, to)` for the drag drop callback.

- [ ] **Step 5: Distribution block (lines 257–320)**

  - Replace the per-macro `Slider` rows with: group title (dot + name + `xg of yg`), an inline stacked `MacroBar`-style row showing section split (tints of the macro colour by index — use `color.copy(alpha = 1f - index*0.18f)`), then sliders (section name left + `Slider` (macro colour fill, `Modifier.weight(1f)`) + right `%`).
  - Replace the `Total: xx%` `Text` colour logic (line 315–318): show `error` colour when `totalPercent.toInt() != 100`, else hide the text entirely (no green). Add an inline `labelSmall` helper when not 100: "Adjust so totals equal 100%."
  - When `sectionGoalsEnabled` is false, show a `labelMedium` line "Goals apply per section (manual logging recommended)" instead of hiding silently.

- [ ] **Step 6: Discard-unsaved guard on back**

  In `SettingsScreen`, intercept the back action: if `uiState.hasUnsavedChanges`, show an `AlertDialog` "Discard changes?" Cancel/Discard; Discard calls `onBack()`. Wire by passing a `onBackRequest` that checks state, or use `BackHandler` from `androidx.activity.compose`.

- [ ] **Step 7: Remove `Color.Green` usages** — grep for `Color.Green` / `Color.Red` in the file, replace with `MaterialTheme.colorScheme.error` (red) and remove the green success text (success is now the Snackbar).

- [ ] **Step 8: Compile & commit**

Run: `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL.

```bash
git add -A && git commit -m "feat(settings): drag-and-drop sections, time chips, distribution viz, SaveButton, discard guard"
```

---

## Task 6: Calendar bottom sheet polish (Track E)

**Files:**
- Modify: `app/src/main/java/com/macrotrack/ui/settings/CalendarModal.kt`

**Interfaces:**
- Consumes: `brandPrimary()`, `brandOnPrimary()`, `Spacing`, `MotionTokens`.

- [ ] **Step 1: Simplify day-cell states**

  In the `LazyVerticalGrid` items block (lines 118–174), replace the `backgroundColor` `when` (lines 145–150) with:
```kotlin
val backgroundColor = when {
    isSelected -> brandPrimary()
    else -> Color.Transparent
}
val borderColor = when {
    isToday && !isSelected -> brandPrimary()
    else -> null
}
val textColor = when {
    isSelected -> brandOnPrimary()
    isToday -> brandPrimary()
    else -> MaterialTheme.colorScheme.onSurface
}
```
  Apply `borderColor?.let { border(2.dp, it, CircleShape) }` to the `Surface` (add `border` import from `androidx.compose.foundation.border`). Keep `CircleShape`. Remove the `isWeekHighlighted` wash (delete `highlightedWeekRow` usage and the `buildMonthGrid` highlight branch is fine to leave but unused — remove its application at line 126–128 and the `weekStartColumn`/`highlightedWeekRow` vars at lines 56–62).

- [ ] **Step 2: Single-tap select + dismiss**

  In the `Surface` `onClick` (lines 152–155), change to:
```kotlin
onClick = {
    onDateSelected(date)
    onDismiss()
}
```
  Remove the `clickConfirmDate` variable and all its references (lines 51–62, 152 onDateSelected).

- [ ] **Step 3: Bottom row — remove `This Week`, keep `Jump to today`**

  Replace the two `TextButton`s (lines 176–198) with:
```kotlin
TextButton(
    onClick = {
        onDateSelected(LocalDate.now())
        onDismiss()
    },
) { Text("Jump to today", color = brandPrimary()) }
```

- [ ] **Step 4: Animate month change**

  Wrap the `LazyVerticalGrid` in `AnimatedContent(targetState = displayMonth, ...)` with a fade; import `androidx.compose.animation.AnimatedContent`. Apply `animateColorAsState` to the selected cell background.

- [ ] **Step 5: Compile & commit**

Run: `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL.

```bash
git add -A && git commit -m "feat(calendar): clean 3-state selection, single-tap dismiss, drop This Week"
```

---

## Task 7: Verification gate (Track F)

**Files:** none new.

- [ ] **Step 1: Full debug build**

Run: `cd /home/jordan/Projects/MacroTrack && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (no compile errors, no unresolved symbols, no missing `MacroPieChart` references).

- [ ] **Step 2: Unit tests pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all tests PASS. (Theme/UI changes are `@Composable`-only and do not affect existing unit tests in `domain`/`data`.)

- [ ] **Step 3: Lint check (optional but recommended)**

Run: `./gradlew :app:lintDebug`
Expected: no errors (warnings acceptable). Fix any `error`-level lint that blocks.

- [ ] **Step 4: Grep for leftover spec violations**

Run:
```bash
grep -rn "MacroPieChart\|AddFoodBottomBar\|Color.Green\|Color.Red\|macroProteinColor()" app/src/main/java
```
Expected: only intended `macroProteinColor()` usages remain (in `NutritionRow`, `SectionHeader` mini-bar, `SettingsScreen` dot, `MacroDonut`). No `MacroPieChart`/`AddFoodBottomBar`/`Color.Green`/`Color.Red` references. Fix any stragglers.

- [ ] **Step 5: Final commit**

```bash
git add -A && git commit -m "chore: verification gate — build + tests green after full redesign"
```

---

## Self-Review (against spec)

1. **Spec coverage** — every spec section maps to a task: tokens (T1), shared components (T2), Home (T3), Add flow (T4), Settings (T5), Calendar (T6), verification (T7). The macro-colour rewrite, brand sage, spacing/motion tokens, FAB-only nav, drag-and-drop with stable times, kcal-ring + 3 macro bars, calendar 3-state, SaveButton, MacroDonut, discard-guard — all present.
2. **Placeholder scan** — no TBD/TODO/"similar to"/"add validation" present; every code step shows concrete code or a concrete file:line + concrete edit.
3. **Type consistency** — `SaveButton(hasChanges, label, onClick)`, `MacroDonut(macros, diameter, centerText)` defined in T2 and used consistently in T3–T6. `brandPrimary()`, `macro*Color()`, `Spacing`, `MotionTokens`, `MacroTrackPillShape` defined in T1 and consumed everywhere. `reorderSections`/`hasUnsavedChanges` defined in T5 VM step 2, used in T5 UI step 4/6. `AddMode.label` change (T4 step 1) matches `AddScreen` usage. `PortionSizeScreen` signature gains `sectionName: String` (T4 step 4) and `AddScreen` threads it (T4 step 4) — consistent.
4. **Risks noted** — JitPack `reorderable` may be unreachable; T5 step 1 has an explicit fallback instruction so the plan does not block. `successColor()`/`warningColor()` are optionally removed with a grep guard (T1 step 2).

# MacroTrack Full-App UX/Design Redesign — 2026-07-19

## Status
Approved, ready for implementation plan.

## User requirements (summarised from design walkthrough)
- Adaptive Material You surfaces (wallpaper-derived background/surface/text tokens), authored macro-color accents and brand primary colour — consistent macro identity across all devices.
- No bottom navigation bar; single FAB opens a 4-option add-food bottom sheet.
- Drag-and-drop meal-section reordering with stable times (edited via a clock-chip TimePickerDialog per section).
- Home hero: kcal ring + 3 macro bars (Protein/Carbs/Fat), inline overage/success colours.
- Whole-app redesign in one cycle (Home, Add flow, Portion picker, Settings, Calendar bottom sheet, theme tokens).

## Design tokens (Track A)

### Colour
- **Surfaces / text / outline / error**: inherited from `MaterialTheme.colorScheme` (dynamic on Android 12+ with authored fallback scheme). The static fallback `Color.kt` scheme is replaced with sage-leaning pairs (not blue-violet) so the fallback is brand-natural.
- **Authored macro colours** — stable across devices, dark/light variants chosen at composition:
  - `kcal` = `#F5A524` (#FBBF24 dark).
  - `protein` = `#5F8B4B` (olive; #A3C587 dark).
  - `carbs` = `#3B82F6` (sky-blue; #7DAFFF dark).
  - `fat` = `#D97757` (terracotta; #E89A7D dark).
- **Brand primary** (FAB, tab indicator, selected-date background, pill accents, save-button fill): authored sage — `#3F6B47` light, `#8FBF9A` dark. NOT `MaterialTheme.colorScheme.primary`.
- The current `macroCaloriesColor()` etc composable accessors are rewritten to return authored light/dark variants keyed on `isSystemInDarkTheme()`, not `colorScheme.primary/error/tertiary/secondary`.

### Typography & shapes
- Type scale unchanged (the existing `MacroTrackTypography` is solid).
- `Shape.extraSmall=8dp`, `small=12dp`, `medium=16dp`, `large=24dp`, `extraLarge=28dp` — kept. Add a `pillShape = RoundedCornerShape(50)` token for chips/CTAs.

### Spacing (new `ui/theme/Spacing.kt`)
`Spacing` object: `xs=4`, `sm=8`, `md=12`, `lg=16`, `xl=24`, `xxl=32`, `xxxl=48`.
Use this for all layout padding/margin — no naked `.dp` for layout spacing.

### Motion (new `ui/theme/Motion.kt`)
`MotionTokens` object:
- `fast = 150ms`, `medium = 280ms`, `slow = 500ms`.
- `fastEasing = FastOutSlowInEasing`, `slowEasing = LinearOutSlowInEasing`.
Used for all `animateFloatAsState`, `animateColorAsState`, `AnimatedVisibility`, `AnimatedContent`.

---

## App info-architecture (Track B)

### Home screen
- **Top bar**: "MacroTrack" `headlineSmall` left, single settings-gear `IconButton` right. No overflow menu.
- **Date strip**: grouped inside a `Surface` `shape=large` with slight tint. Selected day = soft brand-wash pill + 1.05× scale + kcal progress bar inline. Month label left-aligned with chevron → opens Calendar bottom sheet.
- **Macro summary card**: `surfaceVariant`-tinted hero card (24dp shape, 2dp elevation). Kcal ring (270° arc, `kcalColor`, `error` on overage) + 3 macro rows with inline 4dp progress bars (colour-split at 100%: goal portion in macro colour, overage in `error`).
- **Meal sections**: collapsible headers. Spec pill = small sage brand-primary rectangle (NOT a macro colour — all sections share the brand). Three mini macro bars (P/C/F, each in its authored macro colour) replace today's blue `0g P` text chips.
- **Food item cards**: left-edge stripe in `kcalColor` (amber, or `error` when the *daily* kcal goal is exceeded). Inline nutrients row colour-coded per macro. No macro-color mapping to section identity.
- **Empty day state**: illustrated (ring icon + message) between macro-card and sections — the header stays visible.
- **Empty section state**: ghost `RestaurantMenu` icon + `+ Add food` pill text-button inside the collapsed section.
- **Selection mode**: the existing `SelectionBottomBar` container changes to `surfaceVariant` with a top hairline; destination picker becomes horizontally-scrollable pill row.

### No bottom bar
The `AddFoodBottomBar` composable is **removed from the codebase**. The 4 previous bottom-bar icon buttons are replaced by:
- **FAB** (`brandPrimary` fill, shape `extraLarge=28dp`, right-bottom corner, visible when no selection/IME) tapping opens a `ModalBottomSheet` with 4 icon+title+subtitle options.
- The FAB bottom sheet already exists in `LogScreen.kt` (`showAddMenu` at line 265). The redesign is to icon-polish it (no `Add` placeholder icons) and remove the old bottom bar.

### Screen graph (unchanged routing)
```
log ──(gear)──> settings
log ──(FAB → sheet)──> add?date&section&mode
add ──(food choice)──> portion (nested in add as Content onPortionSelect)
add ──(back)──> log
```

---

## Add Food flow (Track C)

### AddScreen container
- **Top bar**: relative-date title ("Today" / "Yesterday" / "Wed, Jul 17") + section-name subtitle. No ISO string.
- **Tab row**: labels `Search` / `Barcode` / `Label` / `Quick`. Indicator = 3dp brandPrimary pill. Active tab text brandPrimary SemiBold. No icons.
- **Tab content** (below):
  - **Search** (`SearchContent`): sticky search field, recommendations when empty, debounced FTS results when filled. Quick-add pill on each row. Empty-no-results → "Quick add manually" button.
  - **Barcode** (`BarcodeScanScreen`): full-bleed camera with rounded reticle, Cancel/Enter-manually pills at bottom. On lookup → push `pendingFood`.
  - **Label** (`LabelScanScreen`): full-bleed camera with centre overlay card showing live consensus results (fields unlocked one by one in real-time). Done-button enables when Name + ≥1 nutrient are confident.
  - **Quick Add** (`QuickAddContent`): three card-sections: Identity (Name), Details (Brand + Serving Label), Nutrition per 100g (2×2 grid with macro-dot leading-icons). Validate inline; `onSurfaceVariant` save button when invalid, brand-fill when valid.

### Portion picker (embedded inside Add flow after food selected)
- **MacroDonut** (new composable, replaces buggy `MacroPieChart`): 180dp ring, 4 macro-colour segments proportional to kcal contribution. Centre kcal number + `/100g` label.
- **P 8g · C 22g · F 3g** macro row, colour-coded.
- **Fraction chips**: `FlowRow` with `1/4, 1/2, 1, 1.5, 2, 3` — uniform 6-chip grid (no asymmetry). Active chip = brand-fill.
- **Custom amount**: `OutlinedTextField` numeric + trailing dropdown (g / ml / piece / serving).
- **Bottom bar button**: full-width brand-fill "Add to Dinner · 189 kcal" — single CTA pulling together the section + resulting kcal.

---

## Settings screen (Track D)

### Daily Goals card
- Protein / Carbs / Fat stacked `OutlinedTextField` (macro-colour-dot leading icons). Numeric keyboard.
- Inline stacked bar showing P:C:F kcal split (percent from macros) — animates as user types.
- Legend row: `P 27% · C 45% · F 26%` in macro colours.
- Total kcal: `2,185 kcal` right-aligned `titleLarge` in `kcalColor`.
- **SaveButton** (shared composable): filled-brand when changes present.

### Meal Sections card
- Add `com.github.burnoutcrew.composereorderable:reorderable:0.9.6` dependency. Use `ReorderableList` with `dragHandle = Icons.DragHandle`.
- Each row: drag handle (left) + `OutlinedTextField` name + time-chip (clickable → `TimePickerDialog`) + trash-iconbutton.
- Trash → confirm dialog (items move to adjacent section or are deleted).
- Time is **stable on reorder** (no time-shuffle).
- "Add section" `OutlinedButton` pill with brand border.
- "Reset to defaults" `TextButton` → confirm dialog.
- **SaveButton** at bottom.

### Distribution card
- Toggle switch kept.
- Per macro group: 6dp dot + macro name + computed `xg of yg` target. Inline stacked bar showing section split.
- Sliders: section name left + slider (12dp track, macro-colour fill, 24dp thumb) + right-aligned percent.
- "Total" row: sum % in `error` if ≠ 100%, clears when total = 100% (no green text).
- Sliders and stacked bar hide when toggle is off (informational message shown).

### Cross-cutting Settings details
- Discard-unsaved-guard on back (`hasUnsavedChanges` flow in ViewModel → confirm dialog).
- Snackbar confirmations for saves.
- All numeric fields use `OutlinedNumericField` wrapper.

---

## Calendar bottom sheet (Track E)

### Container
- `ModalBottomSheet` with scrim, single detent (Expanded), `shape=large`, `surface` container.

### Header
- Drag handle (default).
- Month row: back-month / title / forward-month.
- Weekday header row (7 equal `weight(1f)` cells, `labelSmall`).

### Day cells (3 clear states — no confusion)
- **Selected**: solid `brandPrimary` fill, `CircleShape`, `onPrimary` text, SemiBold.
- **Today**: transparent, `brandPrimary` text, SemiBold, 2dp `brandPrimary` circle outline.
- **Other**: transparent, `onSurface` text, Normal.
- **Adjacent-month**: `onSurfaceVariant` text at 40% opacity — keep the `null` placeholder cell pattern.

### Bottom row
- Single `OutlinedButton` "Jump to today" → dismisses. `This Week` is **removed**.

### Interaction
- Single tap selects and dismisses (no two-step confirm).
- `animateContentSize` on month-change grid.
- `animateColorAsState` on selected cell.

---

## Shared components

### SaveButton (`ui/components/SaveButton.kt`)
- `hasChanges=true` → filled brand-primary `Button`, `pillShape`, `fillMaxWidth`, 48dp tall.
- `hasChanges=false` → `OutlinedButton` with brand border + brand text.

### MacroDonut (`ui/components/MacroDonut.kt`, new)
- Replaces `MacroPieChart`. 360° full ring, `surfaceVariant` track, 4 macro-colour segments.
- Centre numeric kcal display.

---

## Verification
- `./gradlew :app:assembleDebug` must compile cleanly.
- `./gradlew :app:testDebugUnitTest` must pass all unit tests.
- No data-layer, domain-layer, or ViewModel-api changes (except `hasUnsavedChanges` on `SettingsViewModel` and removal of `AddFoodBottomBar` callers).

---

## Implementation tracks
1. **Tokens** (theme files — `Color.kt`, `Theme.kt`, `Spacing.kt`, `Motion.kt`) + macro-colour rewrite.
2. **Home / Log screen** — `LogScreen.kt`, `WeekDateStrip.kt`, `MacroSummaryCard.kt`, `SectionHeader.kt`, `FoodItemCard.kt`, empty states, remove `AddFoodBottomBar`.
3. **Add Food flow** — `AddScreen.kt`, `SearchContent.kt`, `PortionSizeScreen.kt`, `QuickAddContent.kt`, `MacroPieChart.kt` → `MacroDonut.kt`, `BarcodeScanScreen.kt`, `LabelScanScreen.kt`.
4. **Settings** — `SettingsScreen.kt`, drag-and-drop addition, time-chip, distribution viz, discard-guard, `SaveButton.kt`.
5. **Calendar** — `CalendarModal.kt` state cleanup.
6. **Verification** — build + test gate.

Tracks 2-5 are independent (except they all depend on Track 1). Tracks 1 runs first; 2-5 run in parallel; 6 runs after all complete.
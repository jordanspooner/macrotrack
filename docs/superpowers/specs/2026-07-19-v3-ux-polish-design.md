# MacroTrack v3 UX Polish — 2026-07-19

## Status
Approved in brainstorming, ready for implementation plan.

## Context
The v2 redesign shipped (`redesign/full-app-ux` branch through commit `16ddb91`). User screenshots and live interaction surfaced a list of small-to-medium UX issues. This spec defines a focused third round of polish.

This round is **fix-and-polish**, not a re-architect. No new routes except an Edit Entry flow that reuses the existing Add infrastructure. No new dependencies. No build.gradle changes.

## Scope summary

### IN
- Home/Log: WeekDateStrip cropping fix, non-scrolling empty state + recoloured tile + FAB-safe bottom padding, two-tone kcal ring on the summary card, MacroBar overflow rendering fix, % pill + label-value spacing fixes, current-time-window default expansion, tap-entry-to-edit (new edit flow reusing `PortionSizeScreen` + new `UpdateLogEntryUseCase`), redesigned Copy/Move destination picker.
- Add: contextual section-empty Add buttons (sheet pre-targeted), PortionSizeScreen centering, tabs functional while food pending (clear `pendingFood` on tab switch), QuickAdd card reorder (Identity / Portion / Nutrition), `KeyboardCapitalization.Words` on text fields, required-field dot instead of asterisk.
- Settings: remove drag handles, sections sort by time automatically, Restore-to-defaults as proper `OutlinedButton` with icon, distribution math fix (redistribute-only-surplus + `roundToInt` + residual snap), persistent non-jarring balance caption (no layout shift), dynamic slider clamp (cannot push total above 100), three-block page structure with cards + section headers.
- Polish: CalendarModal WeekDateStrip parity, item-aware focus, baseline lint note.

### OUT (declared)
- Barcode & Label scan screen camera opt-in lint errors (pre-existing).
- Intra-section entry drag-reorder within a section.
- Day-of-week distribution overrides.
- Undo/redo.
- Server-side recommendations.

---

## Section 1 — Home / Log screen polish

### 1.1 WeekDateStrip day-cell layout
- Replace `Row(Arrangement.SpaceEvenly)` with `Row { weekDays.forEach { DayItem(..., Modifier.weight(1f)) } }` so each of 7 cells shares equal width and the cropped "Sat" label is fixed.
- Strip card already at `Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)`, full width. No change needed there.
- Apply the same fix to the mini-strip in `CalendarModal.kt` for parity.

### 1.2 Empty day state
- Keep the empty state inside `LazyColumn` as a single `item { Box(...) }`; the LazyColumn's own scrolling handles overflow gracefully. Bug observed in screenshot: subhead text clipped at bottom because the LazyColumn had no bottom contentPadding. See §1.9.
- Recolour the tile icon background from `brandPrimary().copy(alpha = 0.2f)` → `restingSurfaceColor()` to keep `brandPrimary` reserved for section identity (FAB, tab indicator, selected date, CTA).
- Tile icon tint changes from `brandPrimary()` → `MaterialTheme.colorScheme.onSurfaceVariant` so the icon reads on the neutral tile.
- Compose layout:
  ```
  Surface(color = restingSurfaceColor(), shape = MacroTrackShapes.large, Modifier.fillMaxWidth().padding(Spacing.lg)) {
      Column(... padding(Spacing.xxl), horizontalAlignment = CenterHorizontally) {
          Surface(color = restingSurfaceColor(), shape = CircleShape, Modifier.size(72.dp)) {
              Box(contentAlignment = Center) { Icon(RestaurantMenu, tint = onSurfaceVariant) }
          }
          Spacer(Modifier.height(Spacing.md))
          Text("Nothing logged for this day", titleMedium)
          Spacer(Modifier.height(Spacing.xs))
          Text("Tap the + button below to add a meal", bodyMedium, onSurfaceVariant)
      }
  }
  ```

### 1.3 MacroSummaryCard two-tone kcal ring
**Bug observed:** the screenshot showed a single-ring arc filled with `overageColor()` once the user exceeded the kcal goal, with a noticeable track gap (~90°). The proportion math (`min(kcalPercent, 1f) * 270f`) is correct, but visually the user cannot tell *how much* of the goal was hit vs how much is overage.

**Fix:** split the arc into two segments like `MacroBar` already does for the macro rows:
1. Draw the track arc `135° → 135°+270°` in `surfaceVariant`.
2. Draw the goal portion: `sweep = min(kcalPercent, 1f) * 270f` in `macroCaloriesColor()` (the kcal macro accent).
3. If `kcalPercent > 1f`, draw an additional overage tail arc:
   - Start angle = `135° + 270°`
   - Sweep = `min(kcalPercent - 1f, 0.5f) * 270°` (cap at 135° so the overage tail cannot wrap past 9 o'clock; the ring never floods with error pink).
   - Color = `overageColor()`.

`progressColor` variable is removed; the kcal-segment-always-uses-kcal-colour rule replaces it.

### 1.4 MacroBar overflow rendering
Verify that `MacroBar` renders the overage tail as a visually distinct segment past 100% (not a continuation of the macro-coloured bar). If currently the overage colour replaces the whole bar when `progress > 1`, change to two-segment rendering:
- 0 → min(progress, 1) in macro colour.
- 1 → min(progress, 2) in `overageColor` (capped at 2× for visual sanity).

Bar scale: `0 → 200%` maps to the bar's full width. `progress > 2` clamps to the right edge.

### 1.5 MacroSummaryCard row overflow fixes
- **Percent pill:** wrap the `Surface` with `Modifier.widthIn(min = 44.dp)`; force `Text(maxLines = 1, softWrap = false)`.
- **Label-value spacing:** change `Text(label) ... Spacer(Modifier.weight(1f)) ... Text("$logged / ${goal}g")` so the label sits naturally with `Spacer(Modifier.width(Spacing.sm))` after the dot, then a `Spacer(Modifier.weight(1f))`, then the value text, then the pill. The bug was `Row` overflowing because the pill had no `widthIn` and the value column hit the pill. Both fixes together.
- **Dot:** already 8dp; keep.
- **Bar:** wrap-protect with `Spacer(Modifier.height(Spacing.xs))` between dot-row and bar — this exists; no change.

### 1.6 Default section expansion
**Rule confirmed:** on Today, only the current time-window section is expanded; all others collapsed. On non-Today, all sections collapsed.

**Implementation:**
- `LogUiState.kt`: rename `isExpanded` flag on `SectionWithEntries` to derive from a fresh field `isCollapsed: Boolean` so the polarity is friendly to the new rule (default true = collapsed).
- `LogViewModel._expandedSections` → rename to `_collapsedSections: MutableStateFlow<Set<Long>>`.
- On `_selectedDate` change (inside `onDateSelected`), recompute `_collapsedSections`:
  - If `date == LocalDate.now()`:
    - Compute the time-window section (closest past or last in the day; reuse `defaultSectionId` logic).
    - `_collapsedSections.value = allSectionIds - setOf(timeWindowSectionId)`.
  - Else: `_collapsedSections.value = allSectionIds`.
- `toggleSectionExpanded(id)` toggles membership in `_collapsedSections`.
- `SectionWithEntries.isExpanded = id !in collapsedSections`.

### 1.7 Tap entry → edit
**Flow:**
1. `FoodItemCard.onClick` in `LogScreen`:
   - If `selectionMode != Off` → toggle selection (current behaviour).
   - If `selectionMode == Off` → call a new `onEditEntry(entryId)` callback passed from `LogScreen` to `MainActivity` that navigates to a new `edit-entry/{entryId}` route.
2. New route `edit-entry/{entryId}` opens `EditEntryScreen`.
3. `EditEntryScreen`:
   - `hiltViewModel<EditEntryViewModel>` loads the entry by ID (new `GetLogEntryUseCase`).
   - Renders the same `PortionSizeScreen` composable, but in **edit mode**: the food's `macroPer100g` is recovered from the entry's `macros / portionG * 100`. Initial `portionG` = the entry's `portionG`. Default label fallback: `entry.portionLabel`.
   - On confirm: calls new `UpdateLogEntryUseCase(entry.copy(portionG = newG, portionLabel = newLabel, macros = food.macroPer100g * newG / 100))`.
   - On back: pop back stack.

**New files:**
- `app/src/main/java/com/macrotrack/ui/edit/EditEntryScreen.kt`
- `app/src/main/java/com/macrotrack/ui/edit/EditEntryViewModel.kt` — observes `GetDailyLogUseCase` filtered by `entryId`. Avoids a new repo method.
- `app/src/main/java/com/macrotrack/domain/usecase/log/UpdateLogEntryUseCase.kt` — wraps `logRepository.update(entry)` for the edit flow. The repo already exposes `update(LogEntry)`.

**Route:** `navController.navigate("edit-entry/$entryId")`.

`PortionSizeScreen` gets two optional params: `initialPortionG: Float?` and `confirmLabel: String` (e.g. "Save changes" vs "Add to $sectionName · X kcal"). When `initialPortionG != null`, edit mode is implied and the CTA label changes accordingly.

### 1.8 Copy/Move destination picker rebuild
**Replace `DestinationPickerBar`** in `LogScreen.kt`:

```
BottomAppBar(containerColor = surfaceVariant, contentColor = onSurfaceVariant) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), SpaceBetween, CenterVertically) {
        // Left: cancel + title
        Row(CenterVertically) {
           IconButton(onCancel) { Icon(Close) }
            Text(if (action == Copy) "Copy to" else "Move to", titleMedium)
        }
        // Right: chips + calendar
        Row(CenterVertically, spacedBy Spacing.xs) {
            FilterChip(Yesterday)
            FilterChip(Today)
            FilterChip(Tomorrow)
            IconButton(onOpenCalendar) { Icon(CalendarToday) }
        }
    }
}
```

When any chip or calendar date is selected, the picker becomes enabled. Confirm `FilledTonalButton("Copy N → Wed Jul 22", enabled = destination != null)` below the chips row, or merge into the bottom-app-bar trailing slot.

**Rules:**
- If `action == Move` and destination `== uiState.selectedDate` → destination invalid, show + disable confirm. The chips still selectable but the CTA stays disabled.
- Destination section is the **same section** as the source entries (no per-section selection in this iteration). Documented as v3.1 candidate.
- Title text is explicit ("Copy to" / "Move to"), single-line, no truncation.

### 1.9 FAB-safe bottom padding
- In `LogScreen`'s `LazyColumn`, replace `item { Spacer(height = 16.dp) }` with `contentPadding = PaddingValues(bottom = 88.dp)` on the `LazyColumn` modifier. Same applies to the empty-day subhead clipping issue from §1.2.

---

## Section 2 — Add Food flow

### 2.1 Contextual add buttons
- FAB: keep 4-option ModalBottomSheet. Already passes `defaultId` to `onNavigateToAddFood`.
- Section-empty "Add food" pill (currently `Text("No items logged")` in `LogScreen.kt:229`) becomes a tappable `OutlinedButton`:
  ```
  OutlinedButton(onClick = { onNavigateToAddFood(section.id, dateIso, lastUsedModeForSection(section.id)) }) {
      Icon(Add); Text("Add food")
  }
  ```
- "Last used mode" tracking is v3.1. v3 default mode = "search".

### 2.2 PortionSizeScreen centering
- Wrap `FlowRow` (presets) in `Box(Modifier.fillMaxWidth()) { FlowRow(Modifier.align(Center)) { ... } }` so chip rows are horizontally centered.
- `OutlinedTextField` custom amount: change `Modifier.fillMaxWidth(0.6f)` → `Modifier.fillMaxWidth(0.7f)` and the parent `Column`'s `horizontalAlignment = Alignment.CenterHorizontally` centers it.
- Save button (a `SaveButton` composable, full-width) — already fills width; the centering is preserved.

### 2.3 Tab state when pendingFood set
- Bug: while `pendingFood != null`, only `PortionSizeScreen` renders; the `PrimaryTabRow` is still rendered *above* the `Box` in the `Column` — so visually tabs remain but clicking them sets mode without clearing `pendingFood`. Effect: in portion-size, tapping "Barcode" silently does nothing visible.
- Fix: in `PrimaryTabRow`'s `Tab.onClick`, wrap to also clear `pendingFood`:
  ```
  onClick = {
      if (uiState.pendingFood != null) viewModel.backFromPortion()
      viewModel.setMode(mode)
  }
  ```
- This restores the "tabs always work" invariant.

### 2.4 QuickAdd card reordering
**Current (3 cards):**
- A: Name
- B: Brand + Portion row
- C: Nutrition

**New (3 cards):**
- **Card A "Identification"**: Name + Brand (both `KeyboardCapitalization.Words`, `KeyboardType.Text`).
- **Card B "Portion"**: Portion g (decimal) + Portion label (`Words` capitalization, `Text`).
- **Card C "Nutrition per 100g"**: 2×2 grid (unchanged fields).

This addresses "brand belongs with name, not portion size details."

### 2.5 Keyboard capitalization
- `Name`, `Brand`, `Portion label` fields → `KeyboardOptions(capitalization = KeyboardCapitalization.Words, keyboardType = KeyboardType.Text)`.
- Numeric fields (`kcal`, `protein`, `carbs`, `fat`, `portionG`) stay at `KeyboardType.Decimal`, no capitalization.
- Auto-focus the Name field on screen entry: `LaunchedEffect(Unit) { focusRequester.requestFocus() }` via `Modifier.focusRequester(focusRequester)`.

### 2.6 QuickAdd error placement
- Move both bare Text error lines (`!hasAnyMacro`, `inconsistent`) into the nutrition Card C, below the 2×2 grid inside the same `Column`. Eliminates the "error floating on background" half-loaded feel.
- Validation colour stays at `MaterialTheme.colorScheme.error` (semantic error).

### 2.7 Required-field affordance
- Replace `"Name *"` and `"kcal *"` Text labels with restrained labels:
  - Drop the asterisk from the label text.
  - The `MacroDot` leadingIcon already exists on the Nutrition fields and acts as the required indicator (kcal's dot is already colored kcal-orange). No additional marker needed; the spec note "(at least one nutrition value: kcal or a macro)" goes inside the nutrition card's footer.
- For Name: add a `MacroDot(brandPrimary())` leadingIcon to match the section-identity convention — name belongs to the section the user is logging it to.

### 2.8 PortionSizeScreen redundant back arrow
- Verify PortionSizeScreen does not render its own TopAppBar on top of AddScreen's. Per `PortionSizeScreen.kt:65-77` it DOES render a `Scaffold { TopAppBar { back arrow + title = food.name }}`. Fix: extract the title-content (donut + chips + fields + SaveButton) into a `PortionSizeContent` composable; have `AddScreen` render it inside its existing Scaffold without the inner Scaffold+TopAppBar. Single back arrow lives on the AddScreen top bar.
- AddScreen's top bar title becomes the food name when `pendingFood != null`, else the relative-date / section subtitle.

### 2.9 Tab row right-edge inset (non-issue verified)
- `PrimaryTabRow` is full-bleed per M3 spec. The "Quick flush-right" perception came from the screenshot indicator offset. Keep M3 default. No change.

---

## Section 3 — Settings: sections + distribution

### 3.1 Remove drag handles, sort by time
**`SettingsViewModel`:**
- Delete `moveDraftSectionUp`, `moveDraftSectionDown`, `reorderSections`.
- `saveSections()`: write each Section with `sortOrder = index` after sorting drafts by `timeOfDay` ascending.
- Remove `kotlin.math.roundToInt` from Settings UI (used only by the deleted drag handler).

**`SettingsScreen`:**
- Replace `Icon(Icons.DragHandle).pointerInput { detectDragGesturesAfterLongPress(...) }` with nothing. Each row is now a Card with Name + TimeChip + Delete.
- Remove `key(ds.id)` wrapper since reordering no longer swaps items mid-list (still useful for stable item identity — keep).

**Downstream consumers of sections:**
- `LogViewModel`: `sectionsWithEntries` already sorts by `section.sortOrder`; since `sortOrder` is now time-derived, sections will render chronologically.
- `AddScreen` SectionSelector: sorts by `sections.also { it.sortedBy(sec -> sec.timeOfDay) }` or assume repo returns sorted. Add `udf.sortedBy { it.timeOfDay }` defensively.
- `defaultSectionId` in `LogScreen.kt:399` already sorts by `timeOfDay`; no change needed.

### 3.2 Section row layout
```
Card(MacroTrackShapes.medium, surfaceVariant) {
    Row(Modifier.padding(Spacing.md), CenterVertically, fillMaxWidth) {
        OutlinedTextField(
            value = ds.name,
            onValueChange = { viewModel.updateDraftSectionName(index, it) },
            label = { Text("Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = Words),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(Spacing.md))
        OutlinedButton(
            onClick = { showTimePicker.value = true },
            shape = MacroTrackPillShape,
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Icon(Schedule, size = 16.dp)
            Spacer(Modifier.width(Spacing.xs))
            Text(ds.timeOfDay.format(HH:mm), labelMedium)
            Spacer(Modifier.width(Spacing.xs))
            Icon(ArrowDropDown, size = 18.dp)
        }
        IconButton(onClick = { showDelete.value = true }) { Icon(Delete, "Delete section") }
    }
}
```

Cards separated by `Spacer(Modifier.height(Spacing.sm))` in the LazyColumn.

### 3.3 Restore to defaults as proper action
Current: `TextButton(onClick = { showReset.value = true }) { Text("Reset to defaults") }`.

New: small `OutlinedButton` below the Save Sections button, with leadingIcon:
```
Row(Modifier.fillMaxWidth(), SpaceBetween) {
    OutlinedButton(
        onClick = { showReset.value = true },
        shape = MacroTrackPillShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
    ) {
        Icon(Icons.Default.Restore, size = 18.dp)
        Spacer(Modifier.width(Spacing.xs))
        Text("Reset to defaults")
    }
    // (empty Spacer for symmetry)
}
```

Confirm dialog unchanged.

### 3.4 Distribution math: redistribute-only-surplus
Replace the body of `SettingsViewModel.updateDistribution(sectionId, macroType, newValue)` with:

```kotlin
fun updateDistribution(sectionId: Long, macroType: MacroType, rawValue: Float) {
    val newValue = rawValue.coerceIn(0f, 100f)
    val current = _sectionDistribution.value.toMutableMap()
    val sectionIds = _draftSections.value.map { it.id }

    // Update the touched section
    val touchedMacros = current.getOrPut(sectionId) { mutableMapOf() }.toMutableMap()
    val oldValue = touchedMacros[macroType] ?: 0f
    touchedMacros[macroType] = newValue
    current[sectionId] = touchedMacros

    // Total including the touched section's new value, excluding others' current
    val othersTotal = sectionIds.filter { it != sectionId }
        .sumOf { (current[it]?.get(macroType) ?: 0f).toDouble() }
        .toFloat()
    val totalAfter = newValue + othersTotal

    if (totalAfter > 100f && sectionIds.size > 1) {
        // Scale others down proportionally so othersTotal' = 100 - newValue
        val targetOthersTotal = (100f - newValue).coerceAtLeast(0f)
        val factor = if (othersTotal > 0f) targetOthersTotal / othersTotal else 0f
        for (otherId in sectionIds.filter { it != sectionId }) {
            val om = current.getOrPut(otherId) { mutableMapOf() }.toMutableMap()
            val otherOld = om[macroType] ?: 0f
            om[macroType] = (otherOld * factor).coerceIn(0f, 100f)
            current[otherId] = om
        }
    }
    // If totalAfter <= 100f: others unchanged. Surplus absorbed (per user rule).

    _sectionDistribution.value = current
    normalizeResidual(macroType)
    persistDistribution()
}

/** Snap small residuals (kbefore FP drift) and round to clean integers in display. */
private fun normalizeResidual(macroType: MacroType) {
    val sectionIds = _draftSections.value.map { it.id }
    val total = sectionIds.sumOf {
        (_sectionDistribution.value[it]?.get(macroType) ?: 0f).toDouble()
    }.toFloat()
    if (total >= 99.95f && total < 100f) {
        val residual = 100f - total
        // Add residual to the smallest non-zero section (or the touched one if all equal)
        val target = sectionIds.minByOrNull {
            _sectionDistribution.value[it]?.get(macroType) ?: 0f
        } ?: return
        val map = _sectionDistribution.value.toMutableMap()
        val tm = (map[target] ?: emptyMap()).toMutableMap()
        tm[macroType] = (tm[macroType] ?: 0f) + residual
        map[target] = tm
        _sectionDistribution.value = map
    }
}
```

Summary of behaviour:
- Touched section increases and total stays ≤ 100 → others unchanged (the surplus is absorbed by the unused slack).
- Touched section increases and total would exceed 100 → others scale proportionally down, each non-negative.
- Touched section decreases → others unchanged. Total is now below 100 (no auto-fill); user can lift other sliders.

Display rounding: `percent.toInt()` → `percent.roundToInt()` in the settings row + total.

### 3.5 Non-jarring balance caption
Replace the in-flow double `Text` with a single `Row` that's always present:
```
Row(Modifier.fillMaxWidth(), CenterVertically) {
    Box(Modifier.size(8.dp).background(balanceColor, CircleShape))
    Spacer(Modifier.width(Spacing.xs))
    Text(balanceMessage, labelSmall)
}
where:
    balanceColor = if (total.roundToInt() == 100) brandPrimary() else MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
    balanceMessage = when {
        total.roundToInt() == 100 -> "Balanced · 100%"
        total < 100 -> "Total: ${total.roundToInt()}% · adjust remaining ${(100 - total.roundToInt()).coerceAtLeast(0)}%"
        else -> "Total: ${total.roundToInt()}%"
    }
```

Always rendered, identical height → no layout shift. Falls back to "Balanced · 100%" when sum hits 100.

### 3.6 Slider dynamic clamp
For each slider row, compute its allowed maximum dynamically:
```kotlin
val others = uiState.draftSections.filter { it.id != section.id }
    .sumOf { (uiState.sectionDistribution[it.id]?.get(macroType) ?: 0f).toDouble() }.toFloat()
val maxForThis = 100f - others
val effectiveValue = percentage.coerceIn(0f, maxForThis)
Slider(
    value = effectiveValue,
    onValueChange = { viewModel.updateDistribution(section.id, macroType, it) },
    valueRange = 0f..maxForThis.coerceAtLeast(0f),
    ...
)
```

User physically cannot push total above 100. With the math rule above, when total is already 100 and the user drags one slider up, others can only decrease to compensate (since `maxForThis` shrinks). When total < 100, the slider allows the user up to the remaining headroom. Eliminates the worst overflow case at the source.

### 3.7 Settings page structure
Replace the long flat LazyColumn with three explicit blocks:

```
LazyColumn(contentPadding = PaddingValues(Spacing.lg), verticalArrangement = spacedBy Spacing.lg) {
    item { SettingsBlockHeader("Daily Goals") }
    item { DailyGoalsCard(...) }
    item { SaveButton("Save goals") }

    item { Spacer(Spacing.xl) }
    item { SettingsBlockHeader("Meal Sections") }
    itemsIndexed(draftSections) { ... }
    item { AddSectionRow + ResetRow + SaveSectionsRow }

    item { Spacer(Spacing.xl) }
    item { SettingsBlockHeader("Macro Distribution") }
    item { DistributeToggleRow }
    if (enabled) items(macroConfigs) { MacroDistributionBlock(...) }
}
```

`SettingsBlockHeader(title)`:
```
@Composable
fun SettingsBlockHeader(title: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(title.uppercase(), titleSmall, color = brandPrimary(), fontWeight = SemiBold)
    }
}
```

Each non-header block renders inside one `Card` (so the Card groups its children). For Meal Sections, since each section is its own card, just rely on spacing + header for grouping (no wrapping card). For Distribution, the toggle and per-macro blocks render inside one card. Visual unity without breaking design hierarchy.

### 3.8 Settings bottom padding
Add `contentPadding = PaddingValues(bottom = Spacing.xxxl)` to the LazyColumn to ensure the last block is reachable past the IME / gesture inset.

---

## Section 4 — Cross-cutting polish

### 4.1 CalendarModal WeekDateStrip parity
- The mini-strip in `CalendarModal.kt` likely mirrors the same `SpaceEvenly`/`weight` issue. Apply the `weight(1f)` per-cell fix for visual parity with the home page strip.
- Audit all date-strip renderers; use the same `DayItem` component if possible.

### 4.2 Lint baseline
Pre-existing lint errors (not regressions caused by this work):
- `BarcodeScanScreen.kt`: camera opt-in (`@OptIn(ExperimentalPermissionsApi::class)`) — flagged by lint as missing permission contract.
- `LabelScanScreen.kt`: same.
- `BoxWithConstraints` opt-in.
- `CalendarModal.kt` / `WeekDateStrip.kt`: NonObservableLocale.

We declare these OUT OF SCOPE in this round. If a `lint-baseline.xml` does not exist, add it via `./gradlew :app:lintBaseline` once and commit. (Decision left to the implementation plan; if it's painful, skip and just document.)

### 4.3 Required-field affordance recap
- Nutrition card: `MacroDot(macroColor)` leadingIcons (already present) communicate the macro. The card footer reads "At least one nutrition value (kcal or a macro)." in `labelSmall`, `onSurfaceVariant`.
- Name field: `MacroDot(brandPrimary())` leadingIcon, label "Name", no asterisk.

---

## File-change preview

**New files (4):**
- `app/src/main/java/com/macrotrack/ui/edit/EditEntryScreen.kt`
- `app/src/main/java/com/macrotrack/ui/edit/EditEntryViewModel.kt` — loads the entry by ID by observing `GetDailyLogUseCase(entry.date)` and filtering by `entry.id`. Avoids adding a new repo method.
- `app/src/main/java/com/macrotrack/domain/usecase/log/UpdateLogEntryUseCase.kt` — wraps `logRepository.update(entry)` for the edit flow.
- `app/src/main/java/com/macrotrack/ui/components/SettingsBlockHeader.kt` — small reused header for the three settings blocks.

The two-tone kcal ring is implemented as a private composable inside `MacroSummaryCard.kt` (no extraction — only one call site).

**Modified files (≥12):**
- `MainActivity.kt` (new edit-entry route)
- `LogScreen.kt` (default expansion, FAB padding, edit-entry navigation, copy/move picker, FAB padding, section-empty OutlinedButton)
- `LogViewModel.kt` (default expansion logic, collapsedSections rename)
- `LogUiState.kt` (collapsedSections polarity swap)
- `MacroSummaryCard.kt` (two-tone ring, pill width, label-value spacing)
- `MacroBar.kt` (overflow rendering fix)
- `WeekDateStrip.kt` (weight-per-cell)
- `CalendarModal.kt` (mini-strip parity)
- `AddScreen.kt` (tab clears pendingFood on switch, PortionSizeContent extraction, title-when-pending)
- `AddViewModel.kt` (tab helper)
- `PortionSizeScreen.kt` (extract `PortionSizeContent`, optional edit-mode params)
- `QuickAddContent.kt` (card reorder, capitalization, error placement, focus)
- `FoodItemCard.kt` (onClick routes to edit when selection off — already does)
- `SettingsScreen.kt` (block structure, drop drag handles, RestoreButton, balance caption, slider dynamic range, distribution display rounding)
- `SettingsViewModel.kt` (drop move functions, sort-by-time save, redistribute-only-surplus, normalizeResidual)

**No new dependencies. No build.gradle changes. JDK 21 required for builds.**

---

## Verification

After implementation:
- `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`
- `./gradlew :app:assembleDebug` — must succeed.
- `./gradlew :app:testDebugUnitTest` — must pass.
- Manual check on device:
  - Empty day state fits without clipping.
  - WeekDateStrip shows all 7 day names + numbers; no cropping.
  - MacroSummaryCard % pill renders "149%" on one line.
  - Overring kcal goal: ring shows kcal-coloured goal arc + pink overage tail.
  - Tap a food entry: opens portion-size edit screen, prefilled with the entry's grams.
  - Update portion: returns to Log, entry updated.
  - Long-press entry → selection → tap Move → destination picker → Today/Yesterday/Tomorrow/Calendar.
  - Settings: Sorting by time, drag handles gone.
  - Distribution: Increase section A when total < 100 — others unchanged.
  - Distribution: bump section to 100% exactly — caption reads "Balanced · 100%" with no layout shift.

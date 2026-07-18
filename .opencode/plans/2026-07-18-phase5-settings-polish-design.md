# Phase 5: Settings & Polish — Design Spec

> Date: 2026-07-18 | Project: MacroTrack | Status: approved

## 1. Overview

Phase 5 delivers the Settings screen, a CalendarModal for week navigation, animation polish across the app, selection-mode action wiring (copy/move/delete), and integration testing. All items from README.md Phase 5 and HANDOVER.md deferred items are covered.

## 2. Settings Screen

### 2.1 Architecture

New files in the `ui/settings/` package:

| File | Purpose |
|------|---------|
| `SettingsScreen.kt` | Composable — single scrollable page with three sections |
| `SettingsUiState.kt` | Single `data class SettingsUiState` with all state |
| `SettingsViewModel.kt` | `@HiltViewModel` — injects use cases, exposes `val uiState: StateFlow<SettingsUiState>` |

New use cases in `domain/usecase/settings/`:

| Use Case | Purpose |
|----------|---------|
| `UpdateDailyGoalsUseCase` | `suspend operator fun invoke(goals: DailyGoals)` — delegates to `SettingsRepository.updateDailyGoals()` |
| `UpdateSectionsUseCase` | `suspend operator fun invoke(sections: List<Section>)` — delegates to `SectionRepository.updateAll()` |
| `SaveSectionDistributionUseCase` | `suspend operator fun invoke(enabled: Boolean, distribution: Map<Long, Map<MacroType, Float>>)` — serializes to JSON and writes to DataStore |

DataStore changes in `SettingsRepositoryImpl`:

- Add methods: `getSectionGoalsEnabled(): Flow<Boolean>`, `setSectionGoalsEnabled(enabled: Boolean)`, `getSectionGoalDistribution(): Flow<String?>`, `setSectionGoalDistribution(json: String)`
- Add corresponding interface entries in `SettingsRepository`

Navigation: Add `"settings"` composable route to `MainActivity.kt` NavHost. Wire existing `onNavigateToSettings` callback.

### 2.2 SettingsUiState

```kotlin
data class SettingsUiState(
    val dailyGoals: DailyGoals = DailyGoals(150, 250, 65),
    val draftGoals: DailyGoals = DailyGoals(150, 250, 65),  // in-progress edits
    val isSavingGoals: Boolean = false,
    val goalsSaved: Boolean = false,
    val sections: List<Section> = emptyList(),
    val draftSections: List<DraftSection> = emptyList(),    // in-progress edits
    val isSavingSections: Boolean = false,
    val sectionsSaved: Boolean = false,
    val sectionGoalsEnabled: Boolean = false,
    val sectionDistribution: Map<Long, Map<MacroType, Float>> = emptyMap(), // sectionId -> macro -> percentage
    val distributionDirty: Boolean = false,
)

data class DraftSection(
    val id: Long,
    val name: String,
    val timeOfDay: LocalTime,
    val sortOrder: Int,
    val isNew: Boolean = false,
)

enum class MacroType { PROTEIN, CARBS, FAT }
```

### 2.3 Screen Layout

Three section blocks inside a `Scaffold` + `TopAppBar("Settings")`:

**Block 1: Daily Goals**
- Three `OutlinedTextField`s: Protein (g), Carbs (g), Fat (g). Numeric keyboard (`KeyboardType.Number`). Bound to `draftGoals`.
- Auto-computed display card below fields:
  - Total kcal: `draftGoals.kcal`
  - Percentage breakdown: `Protein: ${draftGoals.proteinPercent}% | Carbs: ${draftGoals.carbsPercent}% | Fat: ${draftGoals.fatPercent}%`
- "Save Goals" `Button` → calls `viewModel.saveGoals()` → persists via `UpdateDailyGoalsUseCase` → sets `goalsSaved = true` for 2 seconds then clears.

**Block 2: Meal Sections**
- `LazyColumn` of editable section rows. Each row:
  - Drag handle icon (≡) — for reorder intent (reorder happens via move-up/move-down buttons since drag-and-drop in compose is complex)
  - `OutlinedTextField` for name
  - `TimePickerDialog` trigger showing current `timeOfDay`
  - Delete `IconButton` (requires confirmation dialog if section has entries)
- Options below the list:
  - "Add Section" `OutlinedButton` → appends new `DraftSection` with defaults
  - "Reset to Defaults" `TextButton` → replaces draft with 4 seeded defaults (Breakfast 07:30, Lunch 12:30, Dinner 19:00, Snacks 15:00)
- "Save Sections" `Button` → calls `viewModel.saveSections()`

**Block 3: Section Distribution**
- `Switch` toggle "Distribute goals across sections"
- When enabled, for each `MacroType`:
  - Header: "Protein distribution" / "Carbs distribution" / "Fat distribution"
  - One `Slider` per section (0-100, steps of 1)
  - Display: percentage + computed grams (`percentage / 100 * dailyGoals.proteinG`)
- Auto-normalization: when a slider moves, other sliders are proportionally adjusted so total stays 100%. Formula:
  ```
  remainingSlidersRatio = (100 - movedValue) / (100 - originalValueOfMoved)
  for each other slider: newValue = oldValue * remainingSlidersRatio
  ```
- Bottom: "Total: 100% ✓" indicator (red if ≠ 100%, green if = 100%)
- Auto-saves on slider release (debounced 500ms)

### 2.4 ViewModel Pattern

Follows existing pattern from `LogViewModel` and `AddViewModel`:

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateDailyGoalsUseCase: UpdateDailyGoalsUseCase,
    private val getSectionsUseCase: GetSectionsUseCase,
    private val updateSectionsUseCase: UpdateSectionsUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _draftGoals = MutableStateFlow(DailyGoals(150, 250, 65))
    private val _draftSections = MutableStateFlow(emptyList<DraftSection>())
    // ... combine with repository flows into uiState
    val uiState: StateFlow<SettingsUiState> = combine(...)
        .stateIn(viewModelScope, WhileSubscribed(5000), SettingsUiState())
}
```

## 3. CalendarModal

### 3.1 Component

New file in `ui/settings/` (shared with settings package, reused by LogScreen):

```
ui/
  settings/
    CalendarModal.kt   — Composable wrapping ModalBottomSheet
```

### 3.2 Behavior

- Triggers from: `WeekDateStrip` header tap (add `onOpenCalendar: () -> Unit` param to `WeekDateStrip`)
- Presents a `ModalBottomSheet` with:
  - Month/year header with ◀ ▶ navigation arrows
  - 7-column day-of-week headers (Mon-Sun)
  - Grid of day numbers: today highlighted, selected date in `primaryContainer`, week of selected date subtly tinted
  - Bottom row: "Today" and "This Week" quick-jump buttons
- Tapping a day calls `onDateSelected(date)` and dismisses the sheet
- Hosted in `LogScreen` with `var showCalendar = remember { mutableStateOf(false) }`

### 3.3 API

```kotlin
@Composable
fun CalendarModal(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```

## 4. Selection-Mode Wiring

### 4.1 What Exists

- `CopyLogEntriesUseCase`, `MoveLogEntriesUseCase`, `DeleteLogEntriesUseCase` — fully implemented
- `LogViewModel` already injects them
- `SelectionMode.ChoosingDestination` sealed class already defined in `LogUiState.kt`
- `SelectionBottomBar` already renders Copy/Move/Delete buttons

### 4.2 What Changes

**LogViewModel methods (currently no-ops):**

```kotlin
fun deleteSelectedEntries() {
    val ids = selectedIds() ?: return
    viewModelScope.launch {
        deleteLogEntriesUseCase(ids)
        exitSelectionMode()
    }
}

fun copySelectedEntries() {
    val ids = selectedIds() ?: return
    _selectionMode.value = SelectionMode.ChoosingDestination(ids, Action.Copy)
}

fun moveSelectedEntries() {
    val ids = selectedIds() ?: return
    _selectionMode.value = SelectionMode.ChoosingDestination(ids, Action.Move)
}

fun confirmCopyMove(targetDate: LocalDate) {
    val mode = _selectionMode.value as? SelectionMode.ChoosingDestination ?: return
    viewModelScope.launch {
        when (mode.action) {
            Action.Copy -> copyLogEntriesUseCase(mode.selectedIds, targetDate)
            Action.Move -> moveLogEntriesUseCase(mode.selectedIds, targetDate)
        }
        _selectionMode.value = SelectionMode.Off
    }
}
```

**LogScreen destination picker:**

When `selectionMode is ChoosingDestination`, render destination options instead of normal `SelectionBottomBar`:
- "Yesterday" (`LocalDate.now().minusDays(1)`), "Today" (`LocalDate.now()`), "Tomorrow" (`LocalDate.now().plusDays(1)`)
- Plus the current `selectedDate` if it's not one of those three
- Each as a `TextButton` in a `Row` within the bottom bar area

**LogScreen callbacks (currently TODO):**
- `onCopyClick = { viewModel.copySelectedEntries() }`
- `onMoveClick = { viewModel.moveSelectedEntries() }`
- `onDeleteClick = { viewModel.deleteSelectedEntries() }`

### 4.3 Copy/Move Section Mapping

When copying/moving entries to another date:
- If the target date has exactly matching sections (by name + timeOfDay), preserve section mapping
- If not, fall back to the default section for the target time of day
- This logic lives in `CopyLogEntriesUseCase` and `MoveLogEntriesUseCase` — already implemented or needs minor extension

## 5. Animation Polish

### 5.1 Targeted Animations

| Component | Animation | API | Estimated change |
|-----------|-----------|-----|------------------|
| `SectionHeader` collapse/expand | `Modifier.animateContentSize()` | Built-in modifier | +1 line |
| `MacroBar` progress fill | `animateFloatAsState(tween(durationMillis = 300))` | Compose animation | ~5 lines |
| `SelectionBottomBar` enter/exit | `AnimatedVisibility(enter = slideInVertically + fadeIn, exit = slideOutVertically + fadeOut)` | Compose animation | ~5 lines in LogScreen |
| `WeekDateStrip` day selection | `animateColorAsState(tween(200))` on container color | Compose animation | ~3 lines |
| `MacroSummaryCard` donut chart | `animateFloatAsState(tween(500))` on sweep angles | Compose animation | ~3 lines |
| `FoodItemCard` selected state | `animateColorAsState(tween(200))` on background | Compose animation | ~3 lines |

### 5.2 Performance Audit

- Add `compose.performancerendering=true` to `gradle.properties` (opt-in diagnostics)
- Review `LabelScanScreen` for `derivedStateOf` opportunities on consensus field recomputations
- Verify stable params everywhere (`LocalDate`, `Instant` are stable in Compose compiler)
- Check `remember` usage for expensive computations

## 6. Testing

### 6.1 Unit Tests (app/src/test/)

Following existing MockK + kotlinx.coroutines.test + Turbine patterns:

| Test File | Covers |
|-----------|--------|
| `UpdateDailyGoalsUseCaseTest` | Persist to DataStore, flow emits updated values |
| `UpdateSectionsUseCaseTest` | Persist section list to Room DAO, verify updateAll called |
| `SectionDistributionAutoNormalizationTest` | Math: slider adjusts, others rebalance, total = 100% |
| `CalendarModalDateRangeTest` | Month grid generation, week boundary computation |

### 6.2 Android Instrumentation Test (app/src/androidTest/)

| Test File | Covers |
|-----------|--------|
| `SettingsIntegrationTest` | Hilt test: launch activity, navigate to settings, modify goals text fields, verify Save persists, navigate back to log |

## 7. Files Summary

### New Files
- `ui/settings/SettingsScreen.kt`
- `ui/settings/SettingsUiState.kt`
- `ui/settings/SettingsViewModel.kt`
- `ui/settings/CalendarModal.kt`
- `domain/usecase/settings/UpdateDailyGoalsUseCase.kt`
- `domain/usecase/settings/UpdateSectionsUseCase.kt`
- `domain/usecase/settings/SaveSectionDistributionUseCase.kt`
- `app/src/test/.../settings/UpdateDailyGoalsUseCaseTest.kt`
- `app/src/test/.../settings/UpdateSectionsUseCaseTest.kt`
- `app/src/test/.../settings/SectionDistributionTest.kt`
- `app/src/test/.../settings/CalendarModalDateRangeTest.kt`
- `app/src/androidTest/.../SettingsIntegrationTest.kt`

### Modified Files
- `MainActivity.kt` — add `"settings"` route
- `LogScreen.kt` — wire selection callbacks, add `CalendarModal` host, `ChoosingDestination` picker, `AnimatedVisibility` for bottom bar
- `LogViewModel.kt` — implement `copy/move/deleteSelectedEntries()`, add `confirmCopyMove()`
- `LogUiState.kt` — no structural changes needed (types exist)
- `SettingsRepository.kt` — add section goal methods to interface
- `SettingsRepositoryImpl.kt` — implement section goal methods
- `RepositoryModule.kt` — no changes (bindings unchanged)
- `WeekDateStrip.kt` — add `onOpenCalendar` callback param
- `MacroBar.kt` — add `animateFloatAsState`
- `SectionHeader.kt` — add `animateContentSize`
- `MacroSummaryCard.kt` — add `animateFloatAsState` on sweep
- `FoodItemCard.kt` — add `animateColorAsState` on background
- `gradle.properties` — add `compose.performancerendering=true`

### Files NOT Modified
- Database entities, DAOs, mappers — no schema changes
- `DatabaseModule.kt` — DB version stays at 1
- `DataStoreModule.kt` — no changes
- Domain models (`DailyGoals`, `Section`, `Macros`) — no structural changes
- Existing use cases (Copy/Move/Delete) — already implemented
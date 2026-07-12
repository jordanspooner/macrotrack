# MacroTrack — Architecture & Implementation Plan

> A calorie and macro tracking Android app that prioritises usability, simplicity, performance, and beauty.

## 1. Design Principles

| Principle | What it means in practice |
|---|---|
| **Usability first** | Every interaction optimised for minimum taps. Copy/move meals in 2-3 touches. Glanceable daily summary. |
| **Simplicity** | Track only kcal, protein, carbs, fat. No feature bloat. Every button earns its place. |
| **Performance** | Target <100ms for all UI interactions. Pre-computed aggregations. Debounced search. Lazy loading. |
| **Beauty** | Material 3 dynamic colour. Consistent design tokens. Purposeful colour-coding for macros. Smooth animations. |

## 2. Tech Stack

- **Core**: Kotlin, Jetpack Compose, Material 3, MVVM + Clean Architecture, Hilt DI.
- **Data**: Room Database + SQLite FTS4 (Full-Text Search), DataStore Preferences.
- **Features**: CameraX, ML Kit Text Recognition v2 (OCR), ML Kit Barcode Scanning.
- **Build**: Gradle Kotlin DSL, Version Catalogs.

## 3. Iteration Plan & Progress

### ✅ Phase 1: Foundation (Days 1-2)
- [x] Android project with Gradle Kotlin DSL + version catalog
- [x] Hilt setup with `@HiltAndroidApp`, modules
- [x] Room database with all entity definitions and DAOs
- [x] DataStore for settings
- [x] Material 3 theme with macro colour system
- [x] Domain models (`FoodItem`, `LogEntry`, `Section`, `Macros`, `DailyGoals`)
- [x] Entity ↔ Domain mappers
- [x] `NutritionValidator`

### ✅ Phase 2: Daily Log (Days 3-5)
- [x] `WeekDateStrip` component with kcal progress bars
- [x] `MacroSummaryCard` component with ring/bar chart
- [x] `SectionHeader` component (collapsible, with aggregated macros)
- [x] `FoodItemCard` component
- [x] `LogScreen` with sections and entries
- [x] `LogViewModel` with state management
- [x] `GetDailyLogUseCase`
- [x] Selection mode: long-press, multi-select, copy/move/delete
- [x] `CopyLogEntriesUseCase`, `MoveLogEntriesUseCase`, `DeleteLogEntriesUseCase`
- [x] `AddFoodBottomBar` and `SelectionBottomBar` components
- [ ] *Deferred: Swipe navigation between days*
- [ ] *Deferred: Drag-and-drop reordering within/between sections*

### ✅ Phase 3: Food Data (Days 6-8)
- [x] `FoodItemDao` with FTS search
- [x] `FoodRepository` implementation
- [x] `food-db-builder` module setup
- [x] USDA SR Legacy parser
- [x] Open Food Facts parser with UK filter
- [x] Data cleaner: validation, dedup, normalisation
- [x] Serving size parser for OFF data
- [x] SQLite database builder (`food-db-builder` writes `food_items` table)
- [x] Pre-built `food_database.db` asset (`app/src/main/assets/databases/`)
- [x] First-launch seeding via `FoodDatabaseSeeder` (Room FTS4 triggers index data)
- [x] `SearchFoodUseCase` with ranking (FTS rank + logged-food boost)
- [x] `GetRecommendationsUseCase` (blended recency/frequency heuristics)

**Notes on the food data pipeline.** The `food-db-builder` module is a standalone
JVM program that parses USDA SR Legacy + Open Food Facts (UK-filtered) exports,
runs them through `DataCleaner` (validation, consistency, normalisation, dedup),
and writes a plain SQLite DB into `app/src/main/assets/databases/food_database.db`.
The app seeds this into Room's `food_items` table on first launch
(`FoodDatabaseSeeder`); Room's `@Fts4(contentEntity=...)` content-sync triggers
then keep the search index in sync, so we avoid the Room schema-identity
requirement that a raw `createFromAsset` would need. A curated UK sample set is
always merged in so the app ships with usable data even before the multi-GB CSVs
are downloaded. Run it with:

```
./gradlew :food-db-builder:run   # or pass -Dmacrotrack.output=<path>
```

### ✅ Phase 4: Add Food Flows (Days 9-12)
- [x] `AddScreen` container with mode tabs (Search / Barcode / Label / Quick Add)
- [x] **Search**: `SearchContent` with debounced FTS search, recommendations, quick-add
- [x] **Portion Size**: `PortionSizeScreen` with pie chart, fraction/multiplier controls
- [x] **Quick Add**: `QuickAddContent` form with validation (consistency check)
- [x] **Barcode Scan**: CameraX + ML Kit barcode integration, `LookupBarcodeUseCase` EAN lookup flow
- [x] **Label Scan**: CameraX + ML Kit text recognition, `LabelParser`, validation UI
- [x] `AddUserFoodUseCase` — save user-created foods to local DB
- [x] `LookupBarcodeUseCase`

> Note: Quick Add captures nutrition **per 100g** (the canonical model). The
> form validates name + at least one nutrition value and warns when macros
> don't sum to the stated kcal. Full "per-portion ⇒ per-100g" auto-conversion
> can be layered on later.

### ⏳ Phase 5: Settings & Polish (Days 13-15)
- [ ] `SettingsScreen` with goals, sections, section distribution
- [ ] `SettingsViewModel` with auto-computation
- [ ] `CalendarModal` for week selection
- [ ] Animation polish & performance audit
- [ ] Final integration testing

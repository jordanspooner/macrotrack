# MacroTrack Handover Notes

## Project Context
**MacroTrack** is a local-only Android calorie and macronutrient tracking app built from the ground up to prioritise usability, simplicity, performance, and beauty.
- **Constraints**: Track only kcal, protein, carbs, and fat. No server dependency (runs entirely locally).
- **Tech Stack**: Android (Kotlin, Jetpack Compose, Material 3), Hilt for DI, Room + FTS4 for database/search, DataStore for preferences, CameraX + ML Kit (for future barcode/label scanning). 

## Architecture
- **Clean Architecture (MVVM)**: Separated into `domain`, `data`, and `ui` layers.
- **Data Models**: Immutable core domain objects (`FoodItem`, `LogEntry`, `Section`, `Macros`).
- **Database Strategy**: Pre-built SQLite database using `sqlite-jdbc` in a standalone `food-db-builder` module. The app's Room database will use `.createFromAsset()` to load this bundled data and use FTS4 for fast, debounced prefix searching.

## Progress / Current State
We are currently operating according to the `README.md` iteration plan.
- **Phase 1 (Foundation): DONE**. Project structure, build system (Kotlin DSL + Version Catalog), Hilt, Room DAOs/Entities, DataStore, basic Domain Models, and Material 3 Theming are built.
- **Phase 2 (Daily Log UI): DONE**. The `LogScreen` is implemented with state management via `LogViewModel`. Components like `WeekDateStrip`, `MacroSummaryCard`, `SectionHeader`, and `FoodItemCard` are built. Multi-selection logic (Copy/Move/Delete) is set up in the ViewModel.
- **Phase 3 (Food Data Pipeline): DONE**.
  - `FoodItemDao` (FTS search ordered by relevance) and `FoodRepository` implemented.
  - Standalone JVM module `food-db-builder` parses USDA SR Legacy + Open Food Facts (UK-filtered), validates/cleans/dedupes via `DataCleaner` + `ServingSizeParser`, and writes `app/src/main/assets/databases/food_database.db` (currently 38 curated UK sample foods; swap in the real CSVs to build the full DB).
  - `FoodDatabaseSeeder` seeds the bundled DB into Room on first launch; Room's `@Fts4` content-sync triggers keep the FTS index in sync (avoids the Room schema-identity requirement of a raw `createFromAsset`).
  - `SearchFoodUseCase` ranks by FTS relevance and boosts previously-logged foods; `GetRecommendationsUseCase` blends recency/frequency (section + overall) heuristics.
  - Unit tests: `ServingSizeParserTest`, `DataCleanerTest` (food-db-builder); `SearchFoodUseCaseTest`, `GetRecommendationsUseCaseTest` (app).
- **Phase 4 (Add Food Flows): DONE**.
  - `AddScreen` container with Search/Barcode/Label/Quick Add tabs; default section auto-selected by time.
  - `SearchContent` (debounced FTS via `SearchFoodUseCase`, recommendations via `GetRecommendationsUseCase`, quick-add), `PortionSizeScreen` (macro pie chart + fraction/multiplier controls), `QuickAddContent` (validation via `NutritionValidator`).
  - CameraX + ML Kit `BarcodeScanScreen`/`LabelScanScreen`; `LookupBarcodeUseCase` (prebuilt → user foods) and `LabelParser`; `AddUserFoodUseCase` persists to `user_foods`.
  - `LabelScanScreen` UX reworked: history-based consensus model (`LabelConsensus` + `ConsensusField`). Each field keeps a rolling buffer of the last 100 valid parses; the surfaced value is the most common one (once seen ≥5 times), and a field locks as "confirmed" once its most-common value has been seen ≥5 times AND accounts for >half of all valid parses. A confirmed value never changes again. Replaces the old "3 consecutive identical readings" streak model. `LabelParser` is now far more robust (kJ→kcal conversion, per-serving→per-100g scaling, two-line values, comma decimals, varied serving phrasings, saturated-fat exclusion, name heuristics). Camera analysis resolution bumped to 1280×720 for sharper OCR.
  - Quick Add bug fixed: input fields now properly update state (changed from `transform` lambda to direct `QuickAddDraft` setter).
  - Camera binding in both scan screens moved to `factory` lambda (one-time) to avoid re-binding on recomposition.
  - Tests: `LabelParserTest`, `LabelConsensusTest`, `AddUserFoodUseCaseTest`, `LookupBarcodeUseCaseTest`.

## Immediate Next Steps
1. **Phase 5 (Settings & Polish)**:
    - `SettingsScreen` with goals, sections, and section distribution.
    - `SettingsViewModel` with auto-computation.
    - `CalendarModal` for week selection.
    - Animation polish & performance audit; final integration testing.
2. **Refine UI/UX** (deferred in Phase 2):
    - Swipe navigation between days.
    - Drag-and-drop reordering within the daily log.
3. **Wire selection-mode actions**: `LogViewModel.copySelectedEntries` / `moveSelectedEntries` / `deleteSelectedEntries` currently no-op — hook up to the copy/move/delete use cases + destination pickers.

> Phase 4 (Add Food Flows) is now implemented and bug-fixed: `AddScreen` (Search/Barcode/Label/Quick Add tabs), `PortionSizeScreen`, `SearchFoodUseCase`+`GetRecommendationsUseCase` UI, CameraX+ML Kit scanning, `AddUserFoodUseCase`, `LookupBarcodeUseCase`, `LabelParser` (robust) + `LabelConsensus` (history-based, locks once confident). Unit tests: `LabelParserTest`, `LabelConsensusTest`, `AddUserFoodUseCaseTest`, `LookupBarcodeUseCaseTest` (the last two were fixed: suspend repo calls now use `coEvery`/`coVerify`).

## Known Issues & Notes
- `LogViewModel` had a type inference issue with a 6-argument `combine` flow. It was fixed by using nested `combine` blocks with `Triple`s.
- To execute the project, ensure you use a JDK compatible with Gradle 8.7. A `gradle-wrapper.properties` has been added, but the user's container might require using the local wrapper generation.
- There are dummy function calls in `LogViewModel` (`copySelectedEntries`, etc.) that need to be fully hooked up to repo-level operations and target-selection UIs.

# Phase 5: Settings & Polish — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Settings screen with daily goals, meal section editing, and section macro distribution; a CalendarModal for week navigation; wire up selection-mode copy/move/delete actions; add animation polish; and add integration tests.

**Architecture:** Follow existing MVVM + Clean Architecture patterns — new `ui/settings/` package with `SettingsScreen`, `SettingsUiState`, `SettingsViewModel`; three new use cases in `domain/usecase/settings/`; extend `SettingsRepository` with section-goal DataStore methods; add `CalendarModal` as a `ModalBottomSheet` shared by the LogScreen; wire existing selection use cases into `LogViewModel`; add targeted Compose animations to existing components.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, MockK, kotlinx.coroutines.test, Turbine

## Global Constraints

- Follow existing ViewModel pattern: `@HiltViewModel`, `StateFlow<XxxUiState>`, `combine()` with nested `Triple`s, `stateIn(WhileSubscribed(5000))`
- Follow existing test pattern: `runTest`, `mockk<>()`, `coEvery`, `coVerify`, JUnit `@Test`
- All files under `app/src/main/java/com/macrotrack/`
- Tests under `app/src/test/java/com/macrotrack/` and `app/src/androidTest/java/com/macrotrack/`
- No Room schema or DB version changes
- Use `ModalBottomSheet` from Material 3 (`@OptIn(ExperimentalMaterial3Api::class)`)
- `MacroType` enum values: `PROTEIN`, `CARBS`, `FAT`
- `LocalDate`, `Instant`, `LocalTime` are Compose-stable

---
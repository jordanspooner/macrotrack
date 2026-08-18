# MacroTrack Backup & Restore (Google Drive) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users back up their MacroTrack data (custom foods, food logs, meal sections, daily goals, section macro distribution) to a file they can store on **Google Drive**, and restore it on the same or a different device.

**Architecture:** The first version ships **manual, user-initiated** backup/restore through Android's Storage Access Framework (SAF). The user picks Google Drive from the system document picker (`CreateDocument` / `OpenDocument`) — no Google Sign-In, no Drive SDK, no OAuth, no storage permission. Backup payloads are **versioned JSON documents** written with `kotlinx.serialization` (already configured in the app). Restore **replaces** the user's data (no merge). DB changes run inside a single Room transaction with backup-ID → new-ID remapping; settings are restored in a single DataStore edit. Downloaded USDA/OFF catalog rows are NOT backed up — only custom foods (`source = 'USER'`) plus metadata that lets the app re-offer catalog re-downloads. An optional passphrase-encrypted envelope is included as a self-contained task so it can be shipped or deferred independently.

**Tech Stack:** Kotlin 2.4, Jetpack Compose + Material 3, Hilt, Room (2.8.4, bundled SQLite driver), DataStore Preferences, `kotlinx.serialization` (1.7.3), `androidx.activity` result contracts. No new third-party dependencies. JDK 21 required for Gradle (compileSdk=37 / AGP 9.2.1).

## Global Constraints

- **Worktree:** Implement on a dedicated git worktree. Create it from `main` before any edits:
  - `git worktree add -b feature/backup-restore ../MacroTrack-backup main`
  - All subsequent commands run from that worktree root.
- **JDK:** `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` before any Gradle invocation.
- **Build commands:**
  - `./gradlew :app:assembleDebug` — must succeed.
  - `./gradlew :app:testDebugUnitTest` — must pass.
  - `./gradlew :app:connectedDebugAndroidTest` — run if an emulator/device is available (optional but recommended).
- **No new dependencies.** No `build.gradle.kts`, `gradle/libs.versions.toml`, or `settings.gradle.kts` changes. Use the existing `Json { ignoreUnknownKeys = true }` from `di/NetworkModule.kt` (or a dedicated module) and `javax.crypto` / `java.security` for the optional encryption task.
- **No comments in code** unless an existing comment block is being edited.
- **Commit style:** Match repo convention: `feat(scope): ...`, `fix(scope): ...`, `polish(scope): ...`.
- **Domain models must NOT be serialized directly.** Add dedicated `@Serializable` DTOs under `data/backup`. Keep `@Serializable` annotations out of the Room entities and domain models.
- **Never copy `macro_track.db` / `-wal` / `-shm` files.** The DB mixes user data with re-downloadable catalog rows, holds manually-managed FTS5 external-content tables, uses WAL, and is currently opened with `fallbackToDestructiveMigration(dropAllTables = true)` (`di/DatabaseModule.kt:42`). Restore is done logically via DAOs.
- **Backup must not contain catalog rows.** Filter custom foods on `source = 'USER'` / `dataSourceId = 'my-foods'`. `food_sources` metadata is exported for informational/re-download prompting only; `installedSources` are NOT restored into the DB.
- **Restore is replace, not merge.** On restore, log entries are cleared, user foods replaced, sections replaced, and settings overwritten.
- **Version gate:** The backup document carries `formatVersion`. Restore rejects documents with a version newer than the app supports (clear error message) and tolerates older versions with a mapping (initially only v1).

## Current State (verified)

- Room DB `macro_track.db` v4 (`di/DatabaseModule.kt`): entities `FoodItemEntity` (`food_items`), `FoodSourceEntity` (`food_sources`), `LogEntryEntity` (`log_entries`), `SectionEntity` (`sections`).
- Custom foods = `food_items WHERE source = 'USER'` (`dataSourceId = 'my-foods'`).
- `log_entries` snapshots name/brand/portion/macros + `date` (ISO), `sortOrder`, `createdAt` (epoch millis); FKs: `sectionId` CASCADE, `foodItemId` SET_NULL.
- FTS5 search indexes `food_items_fts` / `food_items_fts_trigram` are non-Room virtual tables kept in sync by triggers; `SearchIndexManager.ensureIndexes()` self-heals on open.
- Settings live in DataStore Preferences `settings` (`data/local/datastore/SettingsDataStore.kt`): kcal/protein/carbs/fat goals, `section_goals_enabled`, `section_goal_distribution` (untyped JSON string keyed by section id — see `ui/settings/SettingsViewModel.kt` `serializeDistribution`/`parseDistribution`).
- `kotlinx.serialization` already wired: `di/NetworkModule.kt` provides `Json`.
- No existing export/import, SAF, or document-picker usage. Android OS Auto Backup is enabled (`data_extraction_rules.xml`, `backup_rules.xml`) — left as-is; it is a secondary mechanism, not this feature.

## Backup Document Format (v1)

`application/json` document with a single top-level envelope. Unknown fields are ignored on decode (forward-compatible `ignoreUnknownKeys`). `formatVersion` must decode first and gate validation.

```json
{
  "formatVersion": 1,
  "createdAt": 1755500000000,
  "appVersion": "1.0",
  "sections": [
    { "id": 1, "name": "Breakfast", "timeOfDay": "06:00" }
  ],
  "customFoods": [
    {
      "id": 10,
      "sourceId": null,
      "ean": null,
      "brand": null,
      "name": "Chicken breast",
      "defaultPortionG": 150.0,
      "defaultPortionLabel": "1 fillet",
      "kcalPer100g": 165.0,
      "proteinPer100g": 31.0,
      "carbsPer100g": 0.0,
      "fatPer100g": 3.6
    }
  ],
  "logs": [
    {
      "id": 100,
      "date": "2026-08-18",
      "sectionId": 1,
      "customFoodId": 10,
      "catalogFoodRef": null,
      "name": "Chicken breast",
      "brand": null,
      "portionG": 150.0,
      "portionLabel": null,
      "kcal": 247.5,
      "protein": 46.5,
      "carbs": 0.0,
      "fat": 5.4,
      "sortOrder": 0,
      "createdAt": 1755500000000
    }
  ],
  "settings": {
    "proteinGoalG": 150,
    "carbsGoalG": 250,
    "fatGoalG": 65,
    "sectionGoalsEnabled": false,
    "sectionGoalDistribution": {
      "1": { "PROTEIN": 33.3, "CARBS": 33.3, "FAT": 33.3 }
    }
  },
  "installedSources": [
    { "id": "usda", "name": "USDA", "description": "…", "version": "…", "publisher": "…", "itemCount": 1234, "installedAt": 1750000000000 }
  ]
}
```

### Reference resolution design (critical)

`log_entries.foodItemId` must be resolved losslessly across restore:

- **Export:** For each log, resolve `foodItemId`:
  - `null` → both `customFoodId` and `catalogFoodRef` are `null`.
  - points at a `source='USER'` row → `customFoodId` = that custom food's backup id.
  - points at a catalog row → `catalogFoodRef = { "dataSourceId": ..., "sourceId": ..., "ean": ... }` (lookup key; `ean` fallback).
- **Restore:**
  - `customFoodId` → remap through the custom-food id map (backup id → new DB id).
  - `catalogFoodRef` → look up an installed catalog row by `dataSourceId`+`sourceId`, falling back to `ean`; set to the found row's current id, else `null`.
  - The snapshot fields (`name`, `brand`, `portion*`, macros) are always written, so a `null` food link still preserves the log and its macro totals (the app already tolerates `foodItemId = null`).

### Validation rules (apply before any mutation)

- `formatVersion` present and `<=` supported max (reject with "newer backup" message).
- `createdAt` present and sane; `appVersion` may be blank.
- Section ids unique and non-zero; custom-food ids unique and non-zero; log ids unique.
- Every log's `sectionId` exists in `sections`; every `customFoodId` exists in `customFoods`; at most one of `customFoodId`/`catalogFoodRef` is set.
- `date` matches `YYYY-MM-DD`; numeric fields are non-negative; `portionG > 0` when present; macro values finite.
- Field-length caps (e.g. names/brands <= 200 chars) and row-count caps (e.g. logs <= 1_000_000, custom foods <= 100_000, sections <= 100) to bound memory.
- Unknown enum values and unknown top-level fields: ignore (forward-compatible).

### Optional encryption (deferred / self-contained)

When enabled, the outer file is an encrypted envelope (`application/octet-stream`) instead of plaintext JSON:

- Passphrase → key via `PBKDF2WithHmacSHA256` (`javax.crypto`), payload encrypted with AES/GCM (`AES/GCM/NoPadding`, 128-bit tag, random 12-byte IV + salt stored in envelope header).
- Envelope header carries `formatVersion` (of the envelope, independent of the inner document), salt, IV, and iteration count; inner plaintext is the JSON document above.
- Passphrase is never stored. A wrong passphrase yields a GCM authentication failure → user-visible "wrong passphrase" error.
- If not shipped in this round, the codec interface still returns `BackupDocument` so the transport layer is passphrase-agnostic.

---

## File Structure Map

### New files

| File | Responsibility |
|---|---|
| `data/backup/BackupDocument.kt` | `@Serializable` DTOs: `BackupDocument`, `SectionDto`, `CustomFoodDto`, `LogEntryDto`, `LogFoodRefDto`, `SettingsDto`, `InstalledSourceDto`, `BackupFormat` (version constants) |
| `data/backup/BackupValidator.kt` | Pure validation of a decoded `BackupDocument` (rules above); returns `ValidationResult` with user-readable errors |
| `data/backup/BackupCodec.kt` | `BackupCodec` interface + `JsonBackupCodec` impl (encode/decode via injected `Json`) |
| `data/backup/SectionDistributionCodec.kt` | Extracted `serializeDistribution`/`parseDistribution` (shared with `SettingsViewModel`) |
| `data/backup/BackupFileStore.kt` | `ContentResolver` read/write of a backup given a `Uri`; opens/closes streams; size guard |
| `data/backup/BackupEncryptor.kt` | (Optional task) AES-GCM/PBKDF2 envelope codec implementing the `BackupCodec` interface |
| `data/repository/BackupRepository.kt` + `BackupRepositoryImpl.kt` | Orchestrates export snapshot + transactional restore; resolves food references; reads/writes settings snapshot |
| `domain/usecase/backup/ExportBackupUseCase.kt` | Builds `BackupDocument` from repositories, writes via codec + file store |
| `domain/usecase/backup/ImportBackupUseCase.kt` | Reads + decodes + validates, returns `RestorePreview` without mutating |
| `domain/usecase/backup/RestoreBackupUseCase.kt` | Executes the transactional replace-restore |
| `ui/backup/BackupViewModel.kt` | Drives export/import/restore state for the Settings screen |
| `ui/backup/BackupUiState.kt` | Export/import/restore status, progress, errors, `RestorePreview` |
| `ui/backup/BackupSettingsCard.kt` | "Backup & restore" card: Export, Import/Restore buttons, status/snackbar wiring |
| `ui/backup/RestorePreviewDialog.kt` | Preview + confirm dialog (backup date, counts, missing-catalog list, destructive-replace warning) |
| Tests | `BackupDocumentTest.kt`, `BackupValidatorTest.kt`, `BackupCodecTest.kt`, `RestoreMappingTest.kt`, `BackupEncryptorTest.kt`, `BackupRepositoryRestoreTest.kt` under `app/src/test/java/com/macrotrack/data/backup/...` |
| Instrumented test | `BackupRestoreInstrumentedTest.kt` — real Room round-trip restore on device |

### Modified files

| File | Change |
|---|---|
| `data/local/db/dao/SectionDao.kt` | Add `getAllSectionsOnce()` (suspend), `clearAll()` |
| `data/local/db/dao/LogEntryDao.kt` | Add `getAllOnce()` (suspend), `clearAll()` |
| `data/local/db/dao/FoodItemDao.kt` | Add `getAllUserFoodsOnce()` (suspend), `getFoodsByIds(ids)` (suspend), `deleteUserFoods()` |
| `data/local/db/dao/FoodSourceDao.kt` | Add `getAllOnce()` (suspend) |
| `data/repository/SectionRepository.kt` + `SectionRepositoryImpl.kt` | Expose `getAllSectionsOnce()`, `clearAll()` |
| `data/repository/LogRepository.kt` + `LogRepositoryImpl.kt` | Expose `getAllOnce()`, `clearAll()` |
| `data/repository/FoodRepository.kt` + `FoodRepositoryImpl.kt` | Expose `getAllUserFoodsOnce()`, `getFoodsByIds()`, `deleteUserFoods()` |
| `data/repository/FoodSourceRepository.kt` + `FoodSourceRepositoryImpl.kt` | Expose `getAllOnce()` |
| `data/repository/SettingsRepository.kt` + `SettingsRepositoryImpl.kt` | Add `getSettingsSnapshot()` and `restoreSettingsSnapshot(...)` (single DataStore edit) |
| `ui/settings/SettingsViewModel.kt` | Delegate `serializeDistribution`/`parseDistribution` to `SectionDistributionCodec`; do not duplicate logic |
| `ui/settings/SettingsScreen.kt` | Render `BackupSettingsCard`; wire `rememberLauncherForActivityResult` for `CreateDocument` / `OpenDocument` |
| `di/RepositoryModule.kt` | Bind `BackupRepository` → `BackupRepositoryImpl` |
| `di` (new `BackupModule` or extend `DatabaseModule`) | Provide codec/file-store bindings, `BackupCodec` impl |

### Existing signatures used (no changes)

- `MacroTrackDatabase.withTransaction { }` — already used by `LogRepositoryImpl` (`androidx.room.withTransaction`).
- `SearchIndexManager.ensureIndexes` — self-heals FTS after restore; no action required.
- `Json { ignoreUnknownKeys = true }` from `di/NetworkModule.kt`.

---

## Task 1: Backup DTOs, versioning, and codec (pure Kotlin)

**Files:**
- Create: `app/src/main/java/com/macrotrack/data/backup/BackupDocument.kt`
- Create: `app/src/main/java/com/macrotrack/data/backup/BackupCodec.kt`
- Create: `app/src/main/java/com/macrotrack/data/backup/BackupValidator.kt`

**Interfaces:**
- Produces: `BackupDocument` (serializable), `BackupValidator.validate(doc): ValidationResult`, `BackupCodec.encode(doc): String`, `BackupCodec.decode(text): BackupDocument`.
- Consumes: injected `Json { ignoreUnknownKeys = true }` from `di/NetworkModule.kt`.

- [ ] **Step 1: Create the DTOs**

`BackupDocument.kt` with `@Serializable` classes mirroring the format above. Use `Long` ids, nullable `Float?` fields to match entity nullability, and `@SerialName` only where names differ from properties. Keep DTOs free of Android/Room imports.

- [ ] **Step 2: Define format version handling**

`BackupFormat` object with `const val CURRENT_FORMAT_VERSION = 1` and `const val SUPPORTED_MIN_VERSION = 1`. Decode must not fail on unknown top-level fields.

- [ ] **Step 3: Implement the codec**

`BackupCodec` interface with `encode(document): String` and `decode(text): BackupDocument`. `JsonBackupCodec` wraps the injected `Json`. Reject empty/blank input with a clear exception type (`BackupParseException`). The plaintext codec is the default transport for v1.

- [ ] **Step 4: Implement the validator**

`BackupValidator.validate(document): ValidationResult` — implement every rule in the validation section. Return a single `ValidationResult.Valid` or `ValidationResult.Invalid(reasons: List<String>)` with user-readable messages.

- [ ] **Step 5: Unit tests**

`BackupDocumentTest.kt` (round-trip encode/decode, unknown-field tolerance, version metadata), `BackupCodecTest.kt`, `BackupValidatorTest.kt` (each validation rule; malformed dates; duplicate ids; missing section refs; both/neither food refs).

---

## Task 2: DAO + repository snapshot and clear operations

**Files:**
- Modify: `data/local/db/dao/SectionDao.kt`, `LogEntryDao.kt`, `FoodItemDao.kt`, `FoodSourceDao.kt`
- Modify: `data/repository/SectionRepository.kt` + `Impl`, `LogRepository.kt` + `Impl`, `FoodRepository.kt` + `Impl`, `FoodSourceRepository.kt` + `Impl`

**Interfaces:**
- Produces: suspend snapshot getters (`getAllOnce`-family), `deleteUserFoods()`, `clearAll()` (logs/sections), `getFoodsByIds(ids)`.
- Consumes: nothing new; existing Room DAO patterns.

- [ ] **Step 1: Add snapshot getters to DAOs**

- `SectionDao.getAllSectionsOnce(): List<SectionEntity>` — `SELECT * FROM sections ORDER BY timeOfDay ASC`.
- `LogEntryDao.getAllOnce(): List<LogEntryEntity>` — `SELECT * FROM log_entries ORDER BY date ASC, sortOrder ASC, id ASC`.
- `FoodItemDao.getAllUserFoodsOnce(): List<FoodItemEntity>` — `SELECT * FROM food_items WHERE source = 'USER'`.
- `FoodItemDao.getFoodsByIds(ids: List<Long>): List<FoodItemEntity>` — `WHERE id IN (:ids)`.
- `FoodSourceDao.getAllOnce(): List<FoodSourceEntity>` — `SELECT * FROM food_sources`.

- [ ] **Step 2: Add clear/delete operations**

- `SectionDao.clearAll()` — `DELETE FROM sections`.
- `LogEntryDao.clearAll()` — `DELETE FROM log_entries`.
- `FoodItemDao.deleteUserFoods()` — `DELETE FROM food_items WHERE source = 'USER'`.

- [ ] **Step 3: Expose through repositories**

Mirror the new DAO methods in the four repository interfaces + impls, mapping entities ↔ domain models with the existing mappers.

- [ ] **Step 4: Existing-repo unit tests**

Extend `LogRepositoryImplTest.kt` only if `clearAll`/`getAllOnce` logic requires it; otherwise rely on instrumented coverage in Task 7.

---

## Task 3: Settings snapshot + distribution codec extraction

**Files:**
- Modify: `data/repository/SettingsRepository.kt` + `SettingsRepositoryImpl.kt`
- Create: `data/backup/SectionDistributionCodec.kt`
- Modify: `ui/settings/SettingsViewModel.kt`

**Interfaces:**
- Produces: `SettingsRepository.getSettingsSnapshot(): SettingsSnapshot`, `restoreSettingsSnapshot(snapshot)` in one `dataStore.edit {}`; `SectionDistributionCodec.serialize(map) / parse(json)`.
- Consumes: `SettingsKeys`; existing DataStore.

- [ ] **Step 1: Extract distribution codec**

Move `serializeDistribution`/`parseDistribution` from the `SettingsViewModel` companion into `data/backup/SectionDistributionCodec.kt` as top-level functions; update `SettingsViewModel` to delegate to them (preserve exact behavior — existing `SectionDistributionTest.kt` must still pass).

- [ ] **Step 2: Snapshot API on SettingsRepository**

`SettingsSnapshot` data class: `proteinGoalG`, `carbsGoalG`, `fatGoalG`, `sectionGoalsEnabled`, `sectionDistributionJson` (raw string, may be null). Impl reads all keys in one `dataStore.data.first()` and writes all in one `edit {}`. Note: current code does not store a user-set `KCAL_GOAL`; goals restore as the three macros (kcal is derived).

- [ ] **Step 3: Unit tests**

New round-trip test for `SettingsSnapshot`; existing `SectionDistributionTest.kt` still green after extraction.

---

## Task 4: Backup file store (SAF / ContentResolver)

**Files:**
- Create: `data/backup/BackupFileStore.kt`

**Interfaces:**
- Produces: `BackupFileStore.write(uri: Uri, text: String)` and `read(uri: Uri): InputStream` (or stream helpers) wrapping `ContentResolver`; DI-bound.
- Consumes: `@ApplicationContext`.

- [ ] **Step 1: Implement write**

`suspend fun write(uri: Uri, text: String)`: `contentResolver.openOutputStream(uri, "wt")`, write, flush, close; wrap IO in `withContext(Dispatchers.IO)`; surface `IOException` as `BackupFileException` with a user-readable message.

- [ ] **Step 2: Implement read**

`read(uri: Uri): InputStream` via `openInputStream(uri)`; callers own closing. Read the stream fully in the codec layer with a size guard (reject > 50 MB, configurable constant) to bound memory.

- [ ] **Step 3: DI**

Provide `BackupFileStore` in a Hilt module (new `BackupModule` or extend `DatabaseModule`).

---

## Task 5: Export, import preview, and restore use cases

**Files:**
- Create: `data/repository/BackupRepository.kt`, `data/repository/BackupRepositoryImpl.kt`
- Create: `domain/usecase/backup/ExportBackupUseCase.kt`, `ImportBackupUseCase.kt`, `RestoreBackupUseCase.kt`
- Modify: `di/RepositoryModule.kt` (bind `BackupRepository`), `di` module for codec/file-store bindings

**Interfaces:**
- Produces: `BackupRepository.exportSnapshot(): BackupDocument`, `BackupRepository.restore(document): RestoreResult`; use cases `ExportBackupUseCase(uri)`, `ImportBackupUseCase(uri): RestorePreview`, `RestoreBackupUseCase(preview, document)`.
- Consumes: repositories from Tasks 2–3, `BackupCodec`, `BackupFileStore`, `BackupValidator`.

- [ ] **Step 1: Implement export snapshot**

`BackupRepositoryImpl.exportSnapshot()`:
1. Read sections, custom foods, all logs, installed sources (once-getters), and settings snapshot.
2. Resolve each log's `foodItemId` to `customFoodId` or `catalogFoodRef` per the reference-resolution design. To classify a referenced food, load it via `getFoodById` (or batch `getFoodsByIds` over the union of referenced ids); `source == 'USER'` → custom, else catalog.
3. Map custom food DB ids to the backup `customFoods[].id` (use the original DB id as the backup id — preserves references).
4. Serialize the `sectionGoalDistribution` map (parse the raw json, re-key by backup section id, serialize into the DTO).

- [ ] **Step 2: Implement import/preview**

`ImportBackupUseCase`: read stream (size-guarded), decode via `BackupCodec`, validate. If valid, build `RestorePreview` = backup `createdAt`, section count, custom-food count, log count, and the list of `installedSources` not currently present on the device (candidates for re-download). No mutations.

- [ ] **Step 3: Implement transactional restore**

`RestoreBackupUseCase(document)`. Order inside a single `database.withTransaction { }`:
1. `logEntryDao.clearAll()`.
2. `sectionDao.clearAll()`.
3. `foodItemDao.deleteUserFoods()`.
4. Insert restored sections (`sectionDao.insertAll`) → build `sectionIdMap` (backup id → new id).
5. Insert restored custom foods (`foodItemDao.insertAll` with `id = 0`, preserving `source='USER'`/`dataSourceId='my-foods'`) → build `foodIdMap` (backup id → new id).
6. Insert logs with remapped `sectionId` and resolved `foodItemId` (custom → `foodIdMap`, catalog → lookup by `dataSourceId`+`sourceId` then `ean` among current catalog rows, else `null`).
7. After the transaction, `restoreSettingsSnapshot` (single DataStore edit) with the distribution json re-keyed by the NEW section ids (reverse-map through `sectionIdMap`).

Keep a `RestoreReport`: counts inserted, and the list of catalog-food refs that could not be resolved (snapshot retained, `foodItemId` null) so the UI can explain degraded edit links.

- [ ] **Step 4: Failure safety**

Validation happens fully before any mutation. If the transaction throws, Room rolls back DB changes. Wrap the settings write so a failure after DB commit still leaves the app in a consistent, usable state; log/return the error for the snackbar. Do not attempt an atomic DB+DataStore transaction.

- [ ] **Step 5: Unit tests**

`RestoreMappingTest.kt` (id remapping, catalog lookup hit/miss, null food links), `BackupRepositoryRestoreTest.kt` (order of operations, rollback on throw, settings re-keying) using mockk, mirroring `LogRepositoryImplTest.kt` style where no Room is involved.

---

## Task 6: Settings screen UI (backup & restore card)

**Files:**
- Create: `ui/backup/BackupUiState.kt`, `ui/backup/BackupViewModel.kt`, `ui/backup/BackupSettingsCard.kt`, `ui/backup/RestorePreviewDialog.kt`
- Modify: `ui/settings/SettingsScreen.kt`

**Interfaces:**
- Produces: `BackupViewModel` (Hilt), `BackupSettingsCard(onExport, onImport, state, snackbar)`, `RestorePreviewDialog(preview, onConfirm, onDismiss)`.
- Consumes: `ExportBackupUseCase`, `ImportBackupUseCase`, `RestoreBackupUseCase`.

- [ ] **Step 1: ViewModel state machine**

`BackupUiState`: `isExporting`, `isImporting`, `isRestoring`, `errorMessage: String?`, `preview: RestorePreview?`, `lastResult: String?`. Methods: `exportTo(uri)`, `importFrom(uri)`, `confirmRestore()`, `dismissPreview()`, `consumeResult()`.

- [ ] **Step 2: Settings card**

Add a `BackupSettingsCard` composable (styled like the existing "Food databases" card, using `Card` + `surfaceVariant`): "Backup & restore" title, description, two `OutlinedButton`s — "Back up to file" and "Restore from file" — plus an inline status line / progress indicator during operations. Wire `SnackbarHostState` via the existing `SettingsScreen` snackbar pattern.

- [ ] **Step 3: Document launchers**

In `SettingsScreen`, add:
- `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::exportTo) }` with a filename suggestion like `macrotrack-backup-${LocalDate.now()}.json`.
- `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importFrom) }` launched with `arrayOf("application/json")`.
- Add an "Export" button → launch `CreateDocument`; "Restore" button → launch `OpenDocument`.

- [ ] **Step 4: Preview + confirm dialog**

`RestorePreviewDialog`: shows backup date, counts, and a warning: "Restore replaces your current foods, logs, sections, and settings." Lists missing `installedSources` ("Food database X from the backup isn't installed — you can reinstall it afterwards"). Confirm button disabled while restoring; dismiss cancels.

- [ ] **Step 5: Verification**

Build with `./gradlew :app:assembleDebug`. Manually confirm: tapping export opens the document picker (Google Drive available as a provider); after choosing Drive, the file appears; tapping restore opens the picker, decodes the file, shows the preview, and restores on confirm.

---

## Task 7: Tests

**Files:**
- Add: unit tests under `app/src/test/java/com/macrotrack/data/backup/...` and `domain/usecase/backup/...`
- Add: `app/src/androidTest/java/com/macrotrack/data/backup/BackupRestoreInstrumentedTest.kt`

- [ ] **Step 1: Codec + validator tests** (from Task 1)

- [ ] **Step 2: Restore mapping + repository tests** (from Task 5)

- [ ] **Step 3: Instrumented round-trip test**

`BackupRestoreInstrumentedTest.kt`: build a real in-memory/device Room `MacroTrackDatabase` (mirror `FoodSearchIndexInstrumentedTest.kt` setup, including bundled driver + WAL file handling), seed custom foods, sections, logs, and settings; export snapshot → restore into a second empty database instance → assert counts, ids remapped, FKs valid, `foodItemId` resolution, and that FTS search returns the restored custom foods.

- [ ] **Step 4: Full test suite**

Run `./gradlew :app:testDebugUnitTest` and, if a device is available, `./gradlew :app:connectedDebugAndroidTest`.

---

## Task 8 (OPTIONAL, self-contained): Passphrase encryption envelope

**Files:**
- Create: `data/backup/BackupEncryptor.kt`

**Interfaces:**
- Produces: `BackupEncryptor` implementing the same `BackupCodec` contract (encrypt/decrypt round a `BackupDocument`), plus a passphrase param.
- Consumes: `javax.crypto`, `java.security` (JDK provided; no new deps).

- [ ] **Step 1: Envelope format**

Binary envelope header: magic bytes, envelope version, PBKDF2 salt (16 B), iteration count, AES-GCM IV (12 B); then ciphertext = AES-256-GCM of the JSON UTF-8 bytes (128-bit tag). Key from passphrase via `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` (≥ 120k iterations) → `SecretKeySpec` for AES.

- [ ] **Step 2: Codec plumbing**

Expose an injected `BackupCodec` whose impl is chosen by whether encryption is enabled (a flag/passphrase provider). When disabled, delegate to `JsonBackupCodec`; when enabled, wrap with `BackupEncryptor`. Decode failures distinguish `wrongPassphrase` (GCM `AEADBadTagException`) from parse errors.

- [ ] **Step 3: Tests**

`BackupEncryptorTest.kt`: round-trip, wrong-passphrase failure, tamper detection, envelope header/version handling.

- [ ] **Step 4: UI**

If enabled: export prompts for a passphrase (create/re-enter), import prompts to unlock before preview; passphrase is held only in memory for the operation. If deferred, leave `BackupCodec` interface untouched so this task drops in later without refactor.

---

## Task 9: Final verification

- [ ] **Step 1: Clean build + tests**

`export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 && ./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest` from the worktree root.

- [ ] **Step 2: Manual Drive end-to-end (emulator/device)**

1. Seed a few custom foods, logs across days, custom sections, and non-default goals.
2. Settings → Backup & restore → "Back up to file" → pick a Google Drive folder → confirm the JSON appears in Drive.
3. Reset data (delete a user food + a log; change a goal).
4. "Restore from file" → pick the Drive file → preview shows backup date + counts → confirm → verify foods/logs/sections/goals match the backup and search returns restored custom foods.

- [ ] **Step 3: Commit**

Commit per-task with repo convention (`feat(backup): ...` etc.) on `feature/backup-restore` in the worktree. Do not merge or push unless asked.

---

## Follow-ups (explicitly OUT OF SCOPE)

- Automatic/scheduled backups (needs WorkManager + a stable "last backup" store).
- In-app Drive file management (list/delete/conflict UI) — would need Google Identity Services + Drive API + OAuth (`drive.file` scope) as a separate phase.
- Merge-on-restore semantics (persistent UUIDs, duplicate detection, conflict rules, tombstones).
- Replacing `fallbackToDestructiveMigration` with explicit Room migrations so OS Auto Backup restores stay valid across schema versions (separate hardening task).
- Health-sync (feature #9 in `INSTRUCTIONS.md`) is unrelated to file backup.
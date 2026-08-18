# Health Connect Integration and Insights Data View

> Plan for adding Health Connect (body weight read, nutrition write) and an
> analytics/insights screen to MacroTrack.

## 0. Current State and Constraints

The repository today (`app/src/main/java/com/macrotrack`):

- Single `:app` Android module, Clean Architecture MVVM, Hilt DI, Room v4
  (`MacroTrackDatabase.kt:24`), DataStore for settings.
- Navigation is a single `NavHost` in `MainActivity.kt:34-89` with routes for
  `log`, `settings`, `add`, `edit-entry`, `food-sources`, `my-foods`,
  `edit-food`. There is **no analytics/insights route**.
- Daily aggregates already exist via `LogEntryDao.getMacrosByDateRange`
  (`LogEntryDao.kt:71-72`) returning `Flow<List<DailyMacroRow>>`. This query
  returns only dates that have rows; there is **no zero-fill for empty days**.
- Goals live only in DataStore (`SettingsDataStore.kt`, default
  `150p / 250c / 65f`). There is **no goal history**, so historical analytics
  would incorrectly apply today's goals to old data.
- `Macros.kcal` is the label kcal; `Macros.computedKcal` is `p*4 + c*4 + f*9`.
  These can differ (fiber, alcohol, rounding) and the plan must be honest about
  the discrepancy.
- `DailyGoals.kcal` is derived from gram goals (same 4/4/9).
- Charts are hand-drawn `Canvas` (`MacroDonut`, `MacroSummaryCard`,
  `WeekDateStrip`, `MacroBar`). There is **no chart library**.
- `DatabaseModule.kt:42` uses `fallbackToDestructiveMigration(dropAllTables = true)`.
  This must be replaced with a real Room migration before any new table ships,
  otherwise user food logs are wiped on upgrade.
- `AndroidManifest.xml` declares only `CAMERA` and `INTERNET`. No runtime
  permission flow exists today (CameraX handles its own implicitly).
- The README and `INSTRUCTIONS.md` constrain the app to local-only operation.
  Health Connect is a local on-device API and is compatible with this; online
  backup is explicitly out of scope.
- Weight feature: **none exists** anywhere in the codebase.

Key external constraints for the plan:
- Health Connect requires Android 14+ for the platform permission UI; the
  `connect-client` library backports read/write on lower API levels via the
  Health Connect by Android app. Exact version and permission constants are
  verified in Phase 1 (Health Connect spike).
- Health Connect permissions are declared in the manifest and requested at
  runtime. We request only `READ_WEIGHT` and `WRITE_NUTRITION` in the first
  release.

---

## 1. Product Surface

Add a single new top-level destination, `Insights`, alongside the existing
`Log` and `Settings` flows. Open it on an `Overview` tab, with deeper tabs:

- `Overview`
- `Nutrition`
- `Weight`
- `Energy`
- `Consistency`

Shared period controls used across all tabs:

- Granularity: `Day`, `Week`, `Month`
- Preset ranges: `7D`, `28D`, `90D`, `1Y`
- Custom date range
- Chart/table toggle
- Tap or long-press a point to reveal exact values and navigate to that date
  in the log

The existing daily macro components and color system (`Color.kt`,
`MacroSummaryCard`, `WeekDateStrip`) are reused so Insights feels native.

---

## 2. Health Connect Integration

### 2.1 Scope

Health Connect is treated as a narrowly scoped data exchange layer:

- **Read** `BodyWeightRecord`
- **Write** `NutritionRecord`
- **Do not read** `NutritionRecord` back in the first release
- MacroTrack's local food log remains the intake source of truth

### 2.2 Permissions

Request only:

- Body-weight read permission
- Nutrition write permission

Do **not** request:

- Background-read permission in the first release
- `READ_NUTRITION` (we have the local log; we do not merge other apps' intake)

### 2.3 Connection Flow

Add a `Health Connect` section to `SettingsScreen`:

- Explain exactly what MacroTrack reads and writes
- Show Health Connect availability (installed / not installed / disabled)
- Request permissions only after the user taps `Connect`
- Handle partial grants independently (weight read may succeed while nutrition
  write is denied)
- Show last successful sync time and any sync errors
- Provide `Sync now`
- Link to Health Connect's permission management screen
- Offer `Disconnect and delete imported weight data`

### 2.4 Sync Triggers (foreground-only)

Foreground synchronization means syncing while the app is actively used,
without requesting Health Connect background-read access.

Triggers:

- Immediately after the user connects Health Connect
- When the app returns to the foreground, subject to a short throttle
- When Insights opens
- When the user taps `Sync now`
- After a local nutrition change, while the app is open

Sync must not block the UI. The recommended sequence:

1. Read changed body-weight records.
2. Apply additions, updates, and deletions to the local weight cache.
3. Drain pending nutrition writes.
4. Store last successful sync time and any errors.

### 2.5 Why Foreground-Only First

- The user explicitly sees when data is accessed
- No additional background-read permission is required
- Permission revocation is easier to handle
- Health Connect availability and provider errors are visible
- Sync behavior is easy to test
- No unnecessary battery or background activity
- Real-time weight updates are not needed for trend analytics; a sync when
  Insights opens is sufficient

### 2.6 What Background Sync Adds Later

A later opt-in enhancement uses `WorkManager` plus the Health Connect
background-read permission:

- Persisted Health Connect Changes API token
- Periodic or opportunistic work (not continuous polling)
- Retry with exponential backoff
- Full resync when a changes token expires
- Correct handling of deleted Health Connect records

Before enabling it we should have proven:

- Health Connect record updates are handled correctly
- Deleted weight records disappear from the local cache
- Permission revocation stops sync cleanly
- Repeated nutrition sync is duplicate-free
- Failed writes retry safely
- A stale/expired Changes API token triggers a correct full resync

### 2.7 Nutrition Write Policy (one record per meal section)

Health Connect's `NutritionRecord` represents a food/eating event. Three
strategies were considered:

| Strategy | Benefits | Problems |
|---|---|---|
| One record per food entry | Maximum detail | Many records, hard to dedupe, hard edits/deletes |
| One record per meal section | Good timing/detail balance | Requires aggregating section totals |
| One record per day | Simple, low volume | Loses meal timing, less useful in Health Connect |

**Chosen: one record per non-empty MacroTrack section per date.**

Example record for `2026-08-17 + Dinner`:

- `energy`: 2,140 kcal
- `protein`: 165 g
- `totalCarbohydrate`: 210 g
- `totalFat`: 72 g
- `name`: section name
- `startTime`/`endTime`: anchored on the section's configured `timeOfDay`
- `mealType`: mapped where possible

Custom section names map to `mealType` as follows:

- Contains "breakfast" -> breakfast
- Contains "lunch" -> lunch
- Contains "dinner" -> dinner
- Contains "snack" (or similar) -> snack
- Otherwise -> unknown meal type, retaining the custom section name

The section's configured time anchors the record. We do **not** use the time
the user happened to log the food, because users commonly log meals
retrospectively. `startTime = section.timeOfDay`, `endTime` a short technical
interval later; this is a meal timestamp, not a measured eating duration.

### 2.8 Idempotent Writes

Each `(date, sectionId)` pair gets a deterministic identity:

```
macrotrack:nutrition:{date}:{sectionId}
```

A local sync table tracks:

- Local date and section ID
- Deterministic client record ID
- Health Connect record ID (assigned on insert)
- Last exported macro values (content hash/revision)
- Sync status (pending / in-flight / succeeded / failed)
- Pending update/delete state
- Last attempt timestamp
- Last error message

Behavior:

- Add food to a section -> create or update that section's record
- Edit food -> update the section record
- Move food between sections -> update both sections
- Delete the last item in a section -> delete the remote record (tombstone)
- Rename a section -> update the exported `name`
- Delete a section -> delete all linked remote records
- Re-run sync -> update existing records, never create duplicates
- Initial export is explicit:
  - Default: export from today forward
  - Optional: export the last 30 or 90 days
  - Avoid silently exporting the entire historical log

The local log remains immediately usable even if Health Connect is unavailable.
Export runs asynchronously and surfaces a pending/failed status rather than
blocking food logging.

MacroTrack does **not** read `NutritionRecord`s back in v1. Other apps may
write nutrition data to Health Connect, so Health Connect's total may differ
from MacroTrack's local total. The connection screen explains this.

---

## 3. Persistence Changes

Room is currently v4 with `fallbackToDestructiveMigration(dropAllTables = true)`
in `DatabaseModule.kt:42`. That must be replaced with a real migration before
any new table ships, otherwise user food logs are wiped on upgrade.

### 3.1 `body_weight_records` (read cache)

| Column | Notes |
|---|---|
| `id` | local auto-increment PK |
| `hcRecordId` | unique, from Health Connect `Metadata.id` |
| `dataOriginPackage` | which app wrote the measurement |
| `weightKg` | normalized to kilograms |
| `measurementInstant` | epoch millis of the reading |
| `zoneOffsetSeconds` | original zone offset |
| `localDate` | measured local calendar date (ISO) |
| `hcLastModifiedMillis` | Health Connect last-modified timestamp |

- Unique index on `hcRecordId`
- Index on `localDate`
- Index on `measurementInstant`

This is a **read cache**, not an alternate weight source. The app can delete
it (disconnect flow) but must never delete the upstream Health Connect record.

### 3.2 `nutrition_sync_records` (write outbox)

| Column | Notes |
|---|---|
| `id` | local auto-increment PK |
| `date` | local log date (ISO) |
| `sectionId` | local section ID |
| `clientRecordId` | deterministic `macrotrack:nutrition:{date}:{sectionId}` |
| `hcRecordId` | assigned by Health Connect on insert, nullable |
| `contentHash` | hash of the aggregated macro payload |
| `syncState` | `PENDING`, `IN_FLIGHT`, `SUCCEEDED`, `FAILED` |
| `pendingDelete` | boolean tombstone |
| `lastAttemptMillis` | nullable |
| `errorMessage` | nullable |

- Unique index on `clientRecordId`
- Index on `(date, sectionId)`
- Index on `syncState`

### 3.3 `daily_goal_history`

Goals currently live only in DataStore. Historical analytics must not apply
today's goals to old data.

| Column | Notes |
|---|---|
| `id` | local auto-increment PK |
| `effectiveFrom` | ISO date this snapshot became active |
| `proteinG` | goal grams |
| `carbsG` | goal grams |
| `fatG` | goal grams |
| `kcalGoal` | derived `p*4 + c*4 + f*9` (denormalized for fast queries) |

Changing goals creates a new snapshot effective from the selected date
(normally today). `UpdateDailyGoalsUseCase` writes both DataStore (current
value) and this table (history row) in a single coordinated operation.
Analytics joins daily rows to the snapshot whose `effectiveFrom <= day` is
the most recent.

### 3.4 Units and Types

- Weight: stored and computed in `Double` kilograms; convert to lb only at UI
  boundary.
- Macros: `Float` is retained for parity with the existing schema; analytics
  uses `Double` internally to avoid cumulative error and rounds for display.
- Time: inject a `Clock` for testability; never call `LocalDate.now()` directly
  in domain use cases. Zone offsets come from the device, with the original
  offset preserved on import.

---

## 4. Nutrition Analytics

### 4.1 Daily Normalized Series

Build a normalized daily series that contains **every calendar day** in the
selected period, not just days with rows.

Each daily row carries:

- Date
- Total logged kcal
- Protein, carbohydrate, fat grams
- Macro-derived kcal (`p*4 + c*4 + f*9`)
- Goal snapshot effective on that date
- Entry count
- Has-any-logged-data flag

An unlogged day must never silently appear as `0 kcal`. The UI distinguishes:

- Logged data
- No data
- (Future: explicit zero, if a "day complete" action is added)

### 4.2 Goal Comparisons

For daily, weekly, and monthly views:

- Show both total and average per logged day
- Compare actual kcal with the sum of daily kcal goals over the period
- Compare each macro in grams with the sum of its daily goal
- Show the number of logged days beside the comparison, e.g. `Avg 1,840 kcal
  (12 of 28 days)`
- Default averages and goal comparisons to **logged days** when coverage is
  incomplete
- Allow the user to switch to "all calendar days" explicitly

Do **not** average daily percentages to produce a period percentage.
Calculate period percentages from period totals.

### 4.3 Macro Percentages

Show two distinct concepts clearly:

- **% of goal**: actual grams / goal grams
- **% of macro kcal**: macro-derived energy split (p*4, c*4, f*9)

Macro-derived energy uses the Atwater factors:

```
Protein:       4 kcal/g
Carbohydrate:  4 kcal/g
Fat:           9 kcal/g
```

The logged kcal value remains the source for calorie totals, because food
labels may not reconcile exactly with macro-derived kcal (fiber, alcohol,
rounding). If the discrepancy is substantial (e.g. > 10%), show a small
data-quality note rather than silently correcting it.

### 4.4 Nutrition Screen Layout

Cards:

1. **Period intake summary** - avg kcal/day, total kcal, logged-day coverage
2. **Stacked macro bars** - per day/week/month, kcal split by P/C/F
3. **Goal comparison rows** - kcal and each macro, total and average, vs goal
4. **Macro energy split** - donut or stacked bar of % p/c/f energy
5. **Logged-day coverage** - small calendar/strip showing which days have data
6. **Detail table** - tap a row to open that date in the log

Use neutral language. Being over or under a goal is not automatically
presented as good or bad.

---

## 5. Bodyweight Analytics

### 5.1 Weight Source

Locked: body weight comes **only** from Health Connect.

- No manual weight-entry screen
- No in-app editing or deletion of weight records
- The app can delete its local cache; it never deletes the source record
- Weight analytics show a `Connect Health Connect` state when unavailable
- If permission is revoked, cached values may remain visible as **stale data
  with a last-synced timestamp**, but no new values are accepted

### 5.2 Daily Representative

Health Connect may contain multiple weigh-ins on one day. Do not weight a day
by the number of measurements.

Normalization:

1. Convert all records to kilograms.
2. Group by the measurement's local calendar date.
3. Use the **median** value for that date as the day's representative.
4. Retain raw readings for inspection.
5. Flag unusually divergent same-day readings (e.g. via robust MAD) rather
   than silently discarding them.

The UI explains that consistent weighing conditions (ideally morning, before
food) improve trend quality.

### 5.3 Smoothed Weight View

Display:

- Raw weigh-ins as scatter points
- **7-day rolling average** as the primary smoothed value (conventional
  best-practice)
- **Recency-weighted EWMA** as an optional responsive trend line
- Number of measured days
- Missing-day gaps (gaps, not zeros)
- Change vs the previous equivalent period
- Trend slope in kg/week or lb/week

The rolling average uses one representative value per day. Missing days remain
missing; they are never treated as measurements.

### 5.4 Weight Trajectory Projection

Use the same recent trend model:

- Robust regression over smoothed daily weights
- Default analysis window: 28 days
- User-selectable: 14, 28, or 56 days
- Project 4, 8, and 12 weeks forward
- Show an uncertainty band
- Show a target line only if the user configures an optional target weight/date

If data is sparse, the trend is statistically weak, or the measurement window
is too short, show `Not enough data for a reliable projection` rather than
drawing a confident line.

---

## 6. Estimated Energy Expenditure

Label this **Estimated expenditure**, not "TDEE".

```
estimatedExpenditure
  = averageLoggedKcalPerDay
    - (weightTrendKgPerDay * 7700 kcal/kg)
```

A positive weight trend lowers the estimate relative to intake; a negative
trend raises it. `7700 kcal/kg` is the conventional heuristic; it is an
approximation, not a physiological constant.

Operational rules:

- Use smoothed weight trend, not raw weigh-ins
- Use robust slope estimation
- Default window: 28 days
- Average intake only across days with logged nutrition
- Show coverage and confidence indicators
- Round output to the nearest 50 kcal
- Include a visible explanation of the calculation and its limitations
- Do not present this as exercise calories or medical guidance

Minimum quality threshold (illustrative):

- At least 14 days in the analysis window
- At least 7 distinct weigh-in days
- Meaningful intake coverage
- No major unexplained gaps

If intake logging is incomplete, show the estimate as unavailable or
low-confidence.

---

## 7. Consistency View

This is **not** "adherence" or "compliance". It measures observable logging
behavior.

Display:

- Logged days out of eligible days
- Current and longest logging streak
- Average entries per logged day
- Meal-section coverage
- 12-week calendar heatmap
- Weekly and monthly logging percentages
- Tap a day to open that date in the log

Labels:

- `Logged day`
- `No food logged`
- `Partial coverage`

An unlogged day is never interpreted as fasting, overeating, or failure.

An optional later enhancement is an explicit `Day complete` action so users
can distinguish "I finished tracking" from "I forgot to track". Not required
for v1.

---

## 8. Implementation Phases

1. **Health Connect spike**
   - Verify `connect-client` version and API behavior on the target minSdk
   - Confirm runtime permission flow
   - Map `BodyWeightRecord` read path
   - Create, update, and delete `NutritionRecord` writes
   - Test record identity and deletion behavior

2. **Data and migration layer**
   - Add `body_weight_records`, `nutrition_sync_records`, `daily_goal_history`
     entities + DAOs + repositories
   - Replace destructive migration with a real `Migration 4 -> 5`
   - Wire `UpdateDailyGoalsUseCase` to write goal history snapshots
   - Add an injectable `Clock` and timezone handling

3. **Health Connect read flow**
   - Availability state (installed / not installed / disabled)
   - Permission UI in Settings
   - Initial import of body weight history
   - Incremental sync via the Changes API
   - Deletion handling
   - Local cache + stale-state handling on permission revocation

4. **Health Connect write flow**
   - Section aggregation (one record per non-empty section per date)
   - Transactional sync queue driven by log mutations
   - Idempotent update/delete via `clientRecordId`
   - Retry and error reporting surfaced in Settings
   - Explicit initial-export prompt (today / last 30 / last 90 days)

5. **Analytics domain layer**
   - Daily normalization with zero-fill for missing days
   - Period grouping (day/week/month)
   - Goal comparisons via `daily_goal_history`
   - Weight smoothing (rolling + EWMA)
   - Expenditure estimate
   - Trajectory projection with uncertainty
   - Consistency metrics

6. **Insights UI**
   - New `Insights` route from `LogScreen`
   - `Overview` and detail tabs (Nutrition / Weight / Energy / Consistency)
   - Range and granularity controls
   - Interactive charts and tables (reuse hand-drawn `Canvas` components; add
     new lightweight chart components only where needed)
   - Tap-through to the log date
   - Empty, stale, low-confidence, and permission-denied states

7. **Polish and validation**
   - Accessibility labels for every chart
   - Tablet and landscape layouts
   - Dark mode parity
   - Performance testing over 1+ year date ranges
   - Health Connect permission and data-deletion testing

---

## 9. Testing Requirements

New tests (following the existing MockK + `runTest` + Turbine + Truth style):

- Same-day weight median calculation
- Rolling and exponentially weighted averages
- Missing days and date-boundary handling
- Timezone and daylight-saving transitions
- Historical goal snapshots (effective-date resolution)
- Daily, weekly, monthly aggregation
- Macro energy percentage computation
- Estimated expenditure sign and unit calculations
- Projection thresholds and uncertainty
- Logged-day streaks
- Health Connect `BodyWeightRecord` mapping
- Health Connect `NutritionRecord` mapping
- Permission denial and partial grants
- Repeated nutrition sync produces no duplicates
- Nutrition edits and deletions
- Section rename and section delete propagation
- Room migration from v4 to v5 (instrumented)
- Compose chart selection and tap-through behavior

---

## 10. Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Health Connect unavailable (older devices, uninstalled) | Surface clear unavailability state; Insights still works for nutrition/consistency from the local log |
| Weight cache drifts from source after revocation | Mark cache stale, surface last-synced time, stop accepting new values |
| Goal history missing before first snapshot | Backfill one snapshot at the migration date using current DataStore goals |
| Large historical export surprises users | Make initial export explicit and bounded (today / 30 / 90 days) |
| Label kcal vs macro kcal confusion | Show both, flag discrepancies > 10%, never silently rewrite logged kcal |
| Destructive migration wipes user data | Replace `fallbackToDestructiveMigration` with a tested `Migration 4 -> 5` before any new table ships |
| Background sync complexity | Foreground-only in v1; background sync deferred to a later opt-in phase |

---

## 11. Decisions Locked

1. **Nutrition export granularity**: one `NutritionRecord` per non-empty
   MacroTrack section per date, anchored on the section's configured time.
2. **Weight source**: Health Connect only. No manual weight entry, no in-app
   edit/delete of weight records; the local table is a read cache.
3. **Sync model**: foreground-only in v1; background sync is a later opt-in
   enhancement, gated on proven correctness of updates, deletes, permission
   revocation, and changes-token expiry.

---

## 12. Later Enhancements (Out of Scope for v1)

- Background sync via `WorkManager` (opt-in)
- Reading `NutritionRecord` back for cross-app reconciliation
- Optional `Day complete` action for explicit logging completeness
- Optional target weight/date for projection overlays
- Body-fat or other body-m composition records if Health Connect broadens
- Online backup (explicitly excluded by `INSTRUCTIONS.md`; local-only remains)

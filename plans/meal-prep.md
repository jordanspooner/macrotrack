# MacroTrack Meal Prep (Recipes) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship "Create meal" — the ability to combine several existing foods (by search, barcode, label scan, or quick add) into one saved meal food, with an optional finished-meal weight, then log that meal by fraction or grams. Ingredients are never logged individually; a meal behaves like a normal user food everywhere (search, recommendations, My Foods, portion picker, copy/move).

**Architecture:** Meals are ordinary `Source.USER` `food_items` rows flagged `isMeal`, plus a new `meal_ingredients` table holding the composition. This reuses the entire existing stack (FTS search, recommendations, My Foods, log snapshots, copy/move, daily summaries) with zero new routes for search/portioning — a meal is just a `FoodItem`. New UI is a single `MealBuilderScreen` that reuses existing entry components (`SearchContent`, `BarcodeScanScreen`, `LabelScanScreen`, `FoodItemEditorForm`, `PortionSizeContent`).

**Tech Stack:** Kotlin + Jetpack Compose + Material 3 + Hilt + Coroutines/Flow + Room. JDK 21. Existing tests are pure JUnit (no Compose UI test harness) — we follow that convention.

## Global Constraints

- **Branch:** `master` (continue on this branch).
- **JDK:** `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` before any gradle invocation.
- **Build commands:**
  - `./gradlew :app:assembleDebug` — must succeed.
  - `./gradlew :app:testDebugUnitTest` — must pass.
  - `./gradlew :app:connectedDebugAndroidTest` — must pass (adds the Room migration test).
- **Spacing tokens:** Layout padding uses `Spacing.xs/sm/md/lg/xl/xxl/xxxl`. Bare `.dp` only for 0/1dp hairlines, fixed-size shapes, and inline spacers < 4dp.
- **Brand sage `brandPrimary()`** reserved for section identity and CTAs. Macro accents for data. Existing `MacroTrackPillShape`/`SaveButton` reused.
- **No new dependencies.** No `build.gradle.kts` or `settings.gradle.kts` changes.
- **No comments in code** unless an existing comment block is being edited.
- **Tests:** Pure JUnit (`org.junit.Test`) with mockk, mirroring existing use-case tests. The Room migration test goes in `androidTest` (mirrors existing instrumented DB tests).
- **Commit style:** `feat(scope): ...`, `fix(scope): ...`, `polish(scope): ...`.
- **Snapshot rule (unchanged):** log entries store denormalized name/macros at log time. Editing a meal later never rewrites historical log entries.

## V1 Scope (deliberately minimal)

- One meal = one saved user food + ingredient list.
- Ingredients are added by **search, barcode, label scan, or quick add**, one at a time, and are never logged themselves.
- Optional **prepared weight** gives accurate per-100g nutrition for the finished meal.
- **No prepared weight** ⇒ the whole batch is normalized internally to 100g; the user logs by fraction badges (`1/4`, `1/3`, `1/2`, `1`, …). The UI never shows the normalized grams as if they were real.
- **Out of scope:** a servings-count field, nested meals as ingredients, cooking instructions, a batch planner, a shopping list, ingredient-level recipe search, undo.

## File Structure

### New files
- `app/src/main/java/com/macrotrack/domain/model/MealIngredient.kt` — domain ingredient model.
- `app/src/main/java/com/macrotrack/data/local/db/entity/MealIngredientEntity.kt` — Room entity.
- `app/src/main/java/com/macrotrack/data/local/db/dao/MealIngredientDao.kt` — ingredient DAO with `@Transaction` meal save.
- `app/src/main/java/com/macrotrack/data/mapper/MealIngredientMapper.kt` — entity ↔ domain.
- `app/src/main/java/com/macrotrack/domain/usecase/meal/MealNutritionCalculator.kt` — pure macro math.
- `app/src/main/java/com/macrotrack/domain/usecase/meal/SaveMealUseCase.kt`
- `app/src/main/java/com/macrotrack/domain/usecase/meal/GetMealUseCase.kt`
- `app/src/main/java/com/macrotrack/ui/meal/MealBuilderScreen.kt`
- `app/src/main/java/com/macrotrack/ui/meal/MealBuilderViewModel.kt`
- `app/src/main/java/com/macrotrack/ui/meal/MealBuilderUiState.kt`
- Tests: `MealNutritionCalculatorTest.kt`, `SaveMealUseCaseTest.kt`, `GetMealUseCaseTest.kt`, `AddLogEntryUseCaseTest.kt`, `app/src/androidTest/.../MigrationTest.kt`.

### Modified files
- `app/src/main/java/com/macrotrack/domain/model/FoodItem.kt` — add `isMeal`, `preparedWeightG`.
- `app/src/main/java/com/macrotrack/domain/model/LogEntry.kt` — add `isMeal`, `mealWeightG`.
- `app/src/main/java/com/macrotrack/data/local/db/entity/FoodItemEntity.kt` — add `isMeal`, `preparedWeightG`.
- `app/src/main/java/com/macrotrack/data/local/db/entity/LogEntryEntity.kt` — add `isMeal`, `mealWeightG`.
- `app/src/main/java/com/macrotrack/data/local/db/MacroTrackDatabase.kt` — version 5, list `MealIngredientEntity`, expose `mealIngredientDao()`.
- `app/src/main/java/com/macrotrack/data/local/db/MealMigration.kt` — new: `MIGRATION_4_5`.
- `app/src/main/java/com/macrotrack/di/DatabaseModule.kt` — add migration, provide `MealIngredientDao`.
- `app/src/main/java/com/macrotrack/data/mapper/FoodItemMapper.kt`, `LogEntryMapper.kt` — new fields.
- `app/src/main/java/com/macrotrack/domain/usecase/log/AddLogEntryUseCase.kt` — `isMeal`/`mealWeightG` params.
- `app/src/main/java/com/macrotrack/domain/usecase/log/UpdateLogEntryUseCase.kt` — preserve new fields.
- `app/src/main/java/com/macrotrack/ui/add/AddViewModel.kt` — pass meal fields when logging.
- `app/src/main/java/com/macrotrack/ui/add/PortionSizeScreen.kt` — meal-aware mode + `1/3` chip.
- `app/src/main/java/com/macrotrack/ui/add/SearchContent.kt` — generic signature + meal badge, hide quick-add for meals.
- `app/src/main/java/com/macrotrack/ui/add/QuickAddContent.kt` — generic signature.
- `app/src/main/java/com/macrotrack/ui/myfoods/FoodItemEditorForm.kt` — `submitLabel` param.
- `app/src/main/java/com/macrotrack/ui/components/FoodItemCard.kt` — meal portion display.
- `app/src/main/java/com/macrotrack/ui/edit/EditEntryViewModel.kt` — synthesize meal fields for edit.
- `app/src/main/java/com/macrotrack/ui/myfoods/MyFoodsScreen.kt` — "Create meal" button, meal badge, row routing.
- `app/src/main/java/com/macrotrack/ui/log/LogScreen.kt` — "Create meal" FAB option.
- `app/src/main/java/com/macrotrack/MainActivity.kt` — `meal` route.

---

## Task 1: Meal data model, Room migration (v4 → v5), mappers

**Files:**
- Modify: `domain/model/FoodItem.kt`, `data/local/db/entity/FoodItemEntity.kt`, `data/mapper/FoodItemMapper.kt`
- Create: `domain/model/MealIngredient.kt`, `data/local/db/entity/MealIngredientEntity.kt`, `data/local/db/MealMigration.kt`, `data/mapper/MealIngredientMapper.kt`
- Modify: `data/local/db/MacroTrackDatabase.kt`, `di/DatabaseModule.kt`

**Interfaces:**
- `FoodItem` gains `isMeal: Boolean = false`, `preparedWeightG: Float? = null`.
- `LogEntry` gains `isMeal: Boolean = false`, `mealWeightG: Float? = null` (snapshot of the meal's prepared weight at log time; null for unweighted meals). Handle in the same pass (Task 4 wires the log path; the fields are added here).
- New `MealIngredient`:
  ```kotlin
  data class MealIngredient(
      val id: Long = 0,
      val mealId: Long,
      val ingredientFoodId: Long? = null,
      val name: String,
      val brand: String? = null,
      val amountG: Float,
      val portionLabel: String? = null,
      val macroPer100g: Macros,
  )
  ```
- `MealIngredientEntity`: `mealId` FK → `food_items.id` `ON DELETE CASCADE` (deleting a meal food removes its ingredients); `ingredientFoodId` is a plain nullable column (no FK, so deleting an ingredient food can't break a meal); snapshot columns `name`, `brand`, `amountG`, `portionLabel`, `kcalPer100g/proteinPer100g/carbsPer100g/fatPer100g`; index on `mealId`.

- [ ] **Step 1: Add meal flags to the food model**

`FoodItem.kt`:
```kotlin
data class FoodItem(
    val id: Long = 0,
    val source: Source,
    val sourceId: String? = null,
    val dataSourceId: String? = null,
    val ean: String? = null,
    val brand: String? = null,
    val name: String,
    val defaultPortionG: Float? = null,
    val defaultPortionLabel: String? = null,
    val isMeal: Boolean = false,
    val preparedWeightG: Float? = null,
    val macroPer100g: Macros,
)
```

`FoodItemEntity.kt`: add
```kotlin
val isMeal: Boolean = false,
val preparedWeightG: Float? = null,
```

`FoodItemMapper.kt`: map both fields both directions.

- [ ] **Step 2: Add the meal flag to the log model**

`LogEntry.kt`:
```kotlin
data class LogEntry(
    ...
    val portionLabel: String? = null,
    val isMeal: Boolean = false,
    val mealWeightG: Float? = null,
    val macros: Macros,
    ...
)
```

`LogEntryEntity.kt`: add `isMeal: Boolean = false`, `mealWeightG: Float? = null`.

`LogEntryMapper.kt`: map both directions.

- [ ] **Step 3: Create the ingredient entity + domain model**

Write `MealIngredientEntity`:
```kotlin
@Entity(
    tableName = "meal_ingredients",
    foreignKeys = [ForeignKey(
        entity = FoodItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["mealId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["mealId"])]
)
data class MealIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val ingredientFoodId: Long? = null,
    val name: String,
    val brand: String? = null,
    val amountG: Float,
    val portionLabel: String? = null,
    val kcalPer100g: Float,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
)
```

Write `MealIngredient` and `MealIngredientMapper` (entity ↔ domain, mirroring `FoodItemMapper`).

- [ ] **Step 4: Room migration v4 → v5**

Bump `MacroTrackDatabase.version = 5` and add `MealIngredientEntity::class` to the entity list. `exportSchema` stays `false`.

Create `data/local/db/MealMigration.kt`:
```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE food_items ADD COLUMN isMeal INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE food_items ADD COLUMN preparedWeightG REAL")
        db.execSQL("ALTER TABLE log_entries ADD COLUMN isMeal INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE log_entries ADD COLUMN mealWeightG REAL")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `meal_ingredients` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `mealId` INTEGER NOT NULL,
                `ingredientFoodId` INTEGER,
                `name` TEXT NOT NULL,
                `brand` TEXT,
                `amountG` REAL NOT NULL,
                `portionLabel` TEXT,
                `kcalPer100g` REAL NOT NULL,
                `proteinPer100g` REAL NOT NULL,
                `carbsPer100g` REAL NOT NULL,
                `fatPer100g` REAL NOT NULL,
                FOREIGN KEY(`mealId`) REFERENCES `food_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_ingredients_mealId` ON `meal_ingredients` (`mealId`)")
    }
}
```
The SQL must match exactly what Room generates from `MealIngredientEntity` (verify against a generated schema if `exportSchema` is temporarily enabled during dev; the manual FTS5 tables are external-content and are not part of this migration).

`DatabaseModule.kt`: replace `.fallbackToDestructiveMigration(dropAllTables = true)` with `.addMigrations(MIGRATION_4_5)`. Keep the existing `SearchIndexManager` callbacks (the FTS5 external tables are unaffected by the `ALTER TABLE` columns since FTS indexes only `name`/`brand`).

- [ ] **Step 5: Expose the new DAO**

`MacroTrackDatabase.kt`:
```kotlin
abstract fun mealIngredientDao(): MealIngredientDao
```
`DatabaseModule.kt`: add `provideMealIngredientDao`.

- [ ] **Step 6: Build + unit tests**

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL; existing tests PASS (no mapper tests exist yet to update — `FoodItem`/`LogEntry` gains are all optional-with-default).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/macrotrack/domain/model/FoodItem.kt \
        app/src/main/java/com/macrotrack/domain/model/LogEntry.kt \
        app/src/main/java/com/macrotrack/domain/model/MealIngredient.kt \
        app/src/main/java/com/macrotrack/data/local/db/entity/FoodItemEntity.kt \
        app/src/main/java/com/macrotrack/data/local/db/entity/LogEntryEntity.kt \
        app/src/main/java/com/macrotrack/data/local/db/entity/MealIngredientEntity.kt \
        app/src/main/java/com/macrotrack/data/local/db/MealMigration.kt \
        app/src/main/java/com/macrotrack/data/local/db/MacroTrackDatabase.kt \
        app/src/main/java/com/macrotrack/di/DatabaseModule.kt \
        app/src/main/java/com/macrotrack/data/mapper/FoodItemMapper.kt \
        app/src/main/java/com/macrotrack/data/mapper/LogEntryMapper.kt \
        app/src/main/java/com/macrotrack/data/mapper/MealIngredientMapper.kt
git commit -m "feat(meal): meal/ingredient data model + Room migration v4→v5"
```

---

## Task 2: Meal nutrition math + repository + use cases

**Files:**
- Create: `domain/usecase/meal/MealNutritionCalculator.kt`
- Create: `data/local/db/dao/MealIngredientDao.kt`
- Create: `domain/usecase/meal/SaveMealUseCase.kt`, `domain/usecase/meal/GetMealUseCase.kt`
- Test: `MealNutritionCalculatorTest.kt`, `SaveMealUseCaseTest.kt`, `GetMealUseCaseTest.kt`

**Interfaces:**
- `MealNutritionCalculator` — pure object, no deps.
- `MealIngredientDao` — ingredient CRUD + a `@Transaction` save.
- `SaveMealUseCase.save(mealId, name, preparedWeightG, ingredients): FoodItem` — upserts the meal food row, replaces ingredients, all atomic.
- `GetMealUseCase.get(mealId): Meal?` where `Meal(food: FoodItem, ingredients: List<MealIngredient>)`.

- [ ] **Step 1: Write the calculator (pure, test-first)**

`MealNutritionCalculator.kt`:
```kotlin
object MealNutritionCalculator {
    fun ingredientMacros(ingredient: MealIngredient): Macros =
        ingredient.macroPer100g * (ingredient.amountG / 100f)

    fun totalMacros(ingredients: List<MealIngredient>): Macros =
        ingredients.fold(Macros(0f, 0f, 0f, 0f)) { acc, i -> acc + ingredientMacros(i) }

    /** Unweighted meals treat the whole batch as 100g (internal normalization only). */
    fun mealMacroPer100g(total: Macros, preparedWeightG: Float?): Macros =
        if (preparedWeightG != null && preparedWeightG > 0f) {
            total * (100f / preparedWeightG)
        } else {
            total
        }

    fun defaultPortionG(preparedWeightG: Float?): Float = preparedWeightG ?: 100f
}
```

- [ ] **Step 2: Write the tests first**

`MealNutritionCalculatorTest.kt` covers:
- single-ingredient scaling (`100g @ 200 kcal/100g ⇒ 200 kcal`; non-integer grams)
- multi-ingredient summing
- weighted per-100g (`total 1500 kcal / 1200g ⇒ 125 kcal/100g`)
- unweighted normalization returns totals unchanged
- `defaultPortionG` for weighted vs unweighted

`SaveMealUseCaseTest.kt` (mockk on `MealIngredientDao`):
- insert path persists food + ingredients, returns saved `FoodItem` with `isMeal=true`, `defaultPortionG=preparedWeightG ?: 100f`, `macroPer100g` from the calculator
- update path (`mealId > 0`) clears prior ingredients and inserts the new list
- snapshots ingredient name/macros (verify the DAO receives entity fields)

`GetMealUseCaseTest.kt`: resolves food by id and joins ingredients.

- [ ] **Step 3: Write the DAO**

`MealIngredientDao.kt`:
```kotlin
@Dao
interface MealIngredientDao {
    @Query("DELETE FROM meal_ingredients WHERE mealId = :mealId")
    suspend fun deleteByMealId(mealId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ingredients: List<MealIngredientEntity>)

    @Query("SELECT * FROM meal_ingredients WHERE mealId = :mealId ORDER BY id")
    fun observeIngredients(mealId: Long): Flow<List<MealIngredientEntity>>

    @Query("SELECT * FROM meal_ingredients WHERE mealId = :mealId ORDER BY id")
    suspend fun getIngredientsOnce(mealId: Long): List<MealIngredientEntity>
}
```
The food row upsert and the ingredient replace happen inside `SaveMealUseCase` wrapped in `database.withTransaction { }`.

- [ ] **Step 4: Write the use cases**

`SaveMealUseCase.kt`:
```kotlin
class SaveMealUseCase @Inject constructor(
    private val database: MacroTrackDatabase,
    private val foodItemDao: FoodItemDao,
    private val mealIngredientDao: MealIngredientDao,
    private val addUserFoodUseCase: AddUserFoodUseCase,
) {
    suspend operator fun invoke(
        mealId: Long,
        name: String,
        preparedWeightG: Float?,
        ingredients: List<MealIngredient>,
    ): FoodItem {
        val total = MealNutritionCalculator.totalMacros(ingredients)
        val per100 = MealNutritionCalculator.mealMacroPer100g(total, preparedWeightG)
        val food = FoodItem(
            id = mealId,
            source = Source.USER,
            name = name,
            isMeal = true,
            preparedWeightG = preparedWeightG,
            defaultPortionG = MealNutritionCalculator.defaultPortionG(preparedWeightG),
            defaultPortionLabel = null,
            macroPer100g = per100,
        )
        database.withTransaction {
            val saved = if (mealId > 0) {
                foodItemDao.update(food.toEntity())
                food
            } else {
                addUserFoodUseCase(food)
            }
            mealIngredientDao.deleteByMealId(saved.id)
            mealIngredientDao.insertAll(
                ingredients.map { it.copy(mealId = saved.id).toEntity() }
            )
            saved
        }
    }
}
```
Note: `addUserFoodUseCase` is itself a DB call; running it inside `withTransaction` is safe with the Room connection. Reuse `FoodItemMapper.toEntity` and `MealIngredientMapper.toEntity`.

`GetMealUseCase.kt`:
```kotlin
class GetMealUseCase @Inject constructor(
    private val foodItemDao: FoodItemDao,
    private val mealIngredientDao: MealIngredientDao,
) {
    suspend operator fun invoke(mealId: Long): Meal? {
        val food = foodItemDao.getFoodById(mealId)?.toDomain() ?: return null
        return Meal(food, mealIngredientDao.getIngredientsOnce(mealId).map { it.toDomain() })
    }
}
```
`Meal` lives in `domain/model/Meal.kt` (two-line data class).

Deletion needs no new use case: `DeleteUserFoodUseCase` already deletes the food row, and the `mealId` FK `ON DELETE CASCADE` removes ingredients. Log entries survive via the existing `foodItemId SET_NULL`.

- [ ] **Step 5: Build + unit tests**

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
Expected: PASS (new calculator/use-case tests included).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/macrotrack/domain/usecase/meal \
        app/src/main/java/com/macrotrack/data/local/db/dao/MealIngredientDao.kt \
        app/src/test/java/com/macrotrack/domain/usecase/meal
git commit -m "feat(meal): meal nutrition math, ingredient DAO, save/get use cases"
```

---

## Task 3: Log meals — AddLogEntryUseCase + AddViewModel + EditEntry

**Files:**
- Modify: `domain/usecase/log/AddLogEntryUseCase.kt`, `domain/usecase/log/UpdateLogEntryUseCase.kt`
- Modify: `ui/add/AddViewModel.kt`
- Modify: `ui/edit/EditEntryViewModel.kt`
- Test: `AddLogEntryUseCaseTest.kt`

**Interfaces:**
- `AddLogEntryUseCase` gains `isMeal: Boolean = false`, `mealWeightG: Float? = null` params, stored on the entry.
- `UpdateLogEntryUseCase` preserves the new fields (it copies `entry`).
- `AddViewModel.confirmPortion` / `quickAddFood` pass `food.isMeal` and `food.preparedWeightG`.

- [ ] **Step 1: Update AddLogEntryUseCase**

Add `isMeal: Boolean = false, mealWeightG: Float? = null` parameters; set them on the `LogEntry` construction. Macro math unchanged.

- [ ] **Step 2: Update UpdateLogEntryUseCase**

No change needed beyond confirming `entry.copy(...)` preserves `isMeal`/`mealWeightG` (it does — data class copy). Verify with a read.

- [ ] **Step 3: Update AddViewModel**

`launchAdd` passes `isMeal = food.isMeal, mealWeightG = food.preparedWeightG`. `quickAddFood` should refuse meals (defensive): if `food.isMeal`, call `selectFood(food)` instead of instantly adding a whole batch.

- [ ] **Step 4: Update EditEntryViewModel**

The synthesized `FoodItem` (from the logged entry) gains:
```kotlin
isMeal = entry.isMeal,
preparedWeightG = entry.mealWeightG,
```

- [ ] **Step 5: Write AddLogEntryUseCaseTest**

Verify: meal entries store `isMeal=true` + `mealWeightG`; macros scale by `portionG/100`; sort order appended. Mock `LogRepository`.

- [ ] **Step 6: Build + unit tests**

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/macrotrack/domain/usecase/log/AddLogEntryUseCase.kt \
        app/src/main/java/com/macrotrack/ui/add/AddViewModel.kt \
        app/src/main/java/com/macrotrack/ui/edit/EditEntryViewModel.kt \
        app/src/test/java/com/macrotrack/domain/usecase/log/AddLogEntryUseCaseTest.kt
git commit -m "feat(meal): log meal entries with isMeal + meal weight snapshot"
```

---

## Task 4: Meal-aware portion picker + log card display

**Files:**
- Modify: `ui/add/PortionSizeScreen.kt`
- Modify: `ui/components/FoodItemCard.kt`

**Interfaces:**
- `PortionSizeContent` stays signature-compatible (`food`, `confirmLabel`, `initialPortionG`, `initialPortionLabel`, `onConfirm`) and becomes meal-aware when `food.isMeal`:
  - Base = `food.preparedWeightG ?: 100f` (whole batch), regardless of `initialPortionG`.
  - Presets for meals: `1/4`, `1/3`, `1/2`, `1`, `2`, `3` (adds `1/3`; drops `1.5` for meals).
  - Weighted meals keep the custom-grams field; unweighted meals hide it (fraction-only).
  - Confirm label generation: fraction selection produces `portionLabel = "1/4 meal"`, `"1/3 meal"`, `"1/2 meal"`, `"whole meal"`, `"2 meals"` etc.; custom grams (weighted only) produce `portionLabel = null`.
  - When `initialPortionG` is set for a meal (edit flow), preselect the nearest fraction `initialPortionG / base`.
- Regular foods: unchanged behavior (existing presets `1/4, 1/2, 1, 1.5, 2, 3`, custom grams, label only when equal to default).

- [ ] **Step 1: Read current PortionSizeContent**

`read app/src/main/java/com/macrotrack/ui/add/PortionSizeScreen.kt` — the composable at lines 74-180.

- [ ] **Step 2: Add `1/3` to the shared presets**

Change the regular-food presets to `1/4, 1/3, 1/2, 1, 1.5, 2, 3` (meals use a different list). This matches the product requirement that `1/2, 1/3, 1/4` badges be available when adding food.

- [ ] **Step 3: Add the meal branch**

In `PortionSizeContent`, after computing `defaultPortionG`, branch:
```kotlin
val isMeal = food.isMeal
val baseG = if (isMeal) (food.preparedWeightG ?: 100f) else defaultPortionG
```
- presets list depends on `isMeal` (see Interfaces).
- chip `onClick` sets `portionG = baseG * mult` and records `selectedMult`.
- for meals, replace the label logic in `SaveButton.onClick`:
```kotlin
val label = if (isMeal) {
    when (selectedMult) {
        0.25f -> "1/4 meal"
        0.3333f -> "1/3 meal"
        0.5f -> "1/2 meal"
        1f -> "whole meal"
        2f -> "2 meals"
        3f -> "3 meals"
        else -> null
    }
} else if (portionG == defaultPortionG) {
    defaultLabel
} else {
    null
}
```
- hide the custom-grams field when `isMeal && food.preparedWeightG == null`.
- when editing a meal (`initialPortionG != null && isMeal`), initialize `portionG = initialPortionG` and preselect the chip whose `baseG * mult ≈ initialPortionG`.

- [ ] **Step 4: Update FoodItemCard portion display**

`ui/components/FoodItemCard.kt`, the `portionText` block: when `entry.isMeal && !entry.portionLabel.isNullOrBlank()`, render just `entry.portionLabel` (e.g. `1/4 meal`, `whole meal`) instead of `"1/4 meal · 25g"`. Weighted custom-gram meals have `portionLabel == null` and keep showing `"300g"` via the existing fallback.

- [ ] **Step 5: Build + unit tests**

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
Expected: PASS (no test changes; verify no Compose regressions via build).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/macrotrack/ui/add/PortionSizeScreen.kt \
        app/src/main/java/com/macrotrack/ui/components/FoodItemCard.kt
git commit -m "feat(meal): meal-aware portion picker (fractions, 1/3) + meal log labels"
```

---

## Task 5: Reuse search/quick-add for the meal builder + meal badges

**Files:**
- Modify: `ui/add/SearchContent.kt`
- Modify: `ui/add/QuickAddContent.kt`
- Modify: `ui/myfoods/FoodItemEditorForm.kt`
- Modify: `ui/add/AddScreen.kt` (adapt call sites)

**Interfaces:**
- `SearchContent` becomes signature-generic (no longer takes `AddUiState`):
  ```kotlin
  fun SearchContent(
      query: String,
      results: List<FoodItem>,
      hasFoodData: Boolean,
      onQueryChanged: (String) -> Unit,
      onFoodSelected: (FoodItem) -> Unit,
      onFoodQuickAdd: (FoodItem) -> Unit,
      onQuickAddClick: () -> Unit,
      onManageFoodSources: () -> Unit,
      onEditFood: (Long) -> Unit,
  )
  ```
- `QuickAddContent` becomes:
  ```kotlin
  fun QuickAddContent(
      draft: QuickAddDraft,
      onDraftChanged: (QuickAddDraft) -> Unit,
      onSubmit: () -> Unit,
      title: String = "Quick add",
      submitLabel: String = "Save",
  )
  ```
- `FoodItemEditorForm` gains `submitLabel: String = "Save"` passed to its `SaveButton`.
- `FoodResultItem`:
  - shows a small `Meal` badge (leading or inline chip) when `food.isMeal`
  - hides the quick-add `+` button for meals (`onQuickAdd = null` when `isMeal`)
  - for unweighted meals (`isMeal && preparedWeightG == null`) the portion line reads `"whole meal · N kcal"` instead of `"100g · N kcal"`
  - for weighted meals, portion line shows `"whole meal · N kcal"` (base = prepared weight)

- [ ] **Step 1: Refactor SearchContent**

Move state out of `AddUiState`; keep the empty/error/no-data states identical. Adapt `AddScreen.kt`'s `SearchContent(...)` call to the new signature (it has `uiState.query`, `uiState.results`, `uiState.hasFoodData`).

- [ ] **Step 2: Refactor QuickAddContent + FoodItemEditorForm**

`QuickAddContent` reads `draft` from its parameter and computes validation exactly as today. `FoodItemEditorForm` takes `submitLabel`.

- [ ] **Step 3: Add the meal badge + suppress quick-add in FoodResultItem**

Add a badge label (e.g. a small `Surface`/`Text` "Meal" in `onSurfaceVariant`, matching existing pill styling) beside the name. Compute the portion line:
```kotlin
val displayPortion = when {
    food.isMeal -> "whole meal"
    else -> "$portionG g${label}"
}
```
Suppress `onQuickAdd` for meals.

- [ ] **Step 4: Build + unit tests**

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/macrotrack/ui/add/SearchContent.kt \
        app/src/main/java/com/macrotrack/ui/add/QuickAddContent.kt \
        app/src/main/java/com/macrotrack/ui/myfoods/FoodItemEditorForm.kt \
        app/src/main/java/com/macrotrack/ui/add/AddScreen.kt
git commit -m "feat(meal): reusable search/quick-add components + meal badges in results"
```

---

## Task 6: Meal builder screen + view model

**Files:**
- Create: `ui/meal/MealBuilderScreen.kt`, `ui/meal/MealBuilderViewModel.kt`, `ui/meal/MealBuilderUiState.kt`

**Interfaces:**
- `MealBuilderViewModel` reads nav args `mealId`, `date` (optional ISO), `sectionId` (optional Long, `0` = none).
- State:
  ```kotlin
  enum class MealBuilderMode { BUILD, INGREDIENT }

  data class MealIngredientDraft(
      val foodId: Long?,
      val name: String,
      val brand: String?,
      val amountG: Float,
      val portionLabel: String?,
      val macroPer100g: Macros,
  )

  data class MealBuilderUiState(
      val mealId: Long,
      val name: String,
      val ingredients: List<MealIngredientDraft>,
      val preparedWeight: String,          // text field
      val totals: Macros,                  // MealNutritionCalculator.totalMacros
      val canSave: Boolean,
      val mode: MealBuilderMode,
      val dirty: Boolean,
      val targetDate: LocalDate?,
      val targetSectionId: Long?,
      val targetSectionName: String?,
      val savedMeal: FoodItem?,            // non-null after save (from-log flow)
      // ingredient-entry sub-state
      val entryMode: AddMode,
      val query: String,
      val results: List<FoodItem>,
      val hasFoodData: Boolean,
      val pendingIngredient: FoodItem?,
      val quickAddDraft: QuickAddDraft,
      val message: String?,
  )
  ```
- The builder re-implements the small search plumbing from `AddViewModel` (debounced `SearchFoodUseCase` / `GetRecommendationsUseCase`) rather than reusing `AddViewModel` (which writes log entries). It does NOT persist anything until Save.

- [ ] **Step 1: Write MealBuilderViewModel**

Constructor deps: `SavedStateHandle`, `SearchFoodUseCase`, `GetRecommendationsUseCase`, `GetSectionsUseCase`, `FoodRepository`, `SaveMealUseCase`, `GetMealUseCase`, `AddLogEntryUseCase`, `AddUserFoodUseCase`, `LookupBarcodeUseCase`.

Key functions:
- `onQueryChanged`, `setEntryMode`, `selectFood` (→ pending ingredient), `addIngredientAtDefault(food)`, `confirmIngredient(portionG, portionLabel)`, `removeIngredient(draft)`, `editIngredient(draft)` (opens portion for that ingredient pre-filled), `onNameChanged`, `onPreparedWeightChanged`, `saveAndLog()` (from-log flow), `saveOnly()` (My Foods flow), `onBackRequested()` (discard dialog), `clearMessage`.
- `confirmIngredient` appends a `MealIngredientDraft` and returns to `BUILD`.
- `totals` recomputed via `MealNutritionCalculator.totalMacros` on every draft change.
- `canSave` = name not blank && ingredients non-empty && every `amountG > 0` && (`preparedWeight` blank || value > 0).
- Barcode/label paths mirror `AddViewModel`: known EAN → pending ingredient; unknown → quick-add draft; label → draft. Both land in `INGREDIENT` mode; submitting the quick-add form saves the ingredient food (same as today) and adds it to the draft.
- `saveAndLog()`: `SaveMealUseCase(...)` → set `savedMeal` → view flips to the meal portion picker.
- `saveOnly()`: `SaveMealUseCase(...)` → signal done (pop).

- [ ] **Step 2: Write MealBuilderScreen**

Single route, three phases driven by state:
1. `BUILD` — the editor:
   - Meal name `OutlinedTextField` (auto-focus, `KeyboardCapitalization.Words`)
   - ingredient list (`LazyColumn`), each row: name, `amountG`/`portionLabel` line, kcal, edit on tap, delete icon
   - empty-state: "Add your first ingredient"
   - "Add ingredient" `OutlinedButton` → `INGREDIENT`
   - live totals card (`MacroDonut`-free: simple kcal/P/C/F summary for the whole batch; also show per-100g when weighted)
   - "Prepared weight (g)" optional field with helper text:
     - blank → "Portion with 1/4, 1/3, 1/2 badges when logging"
     - set → "Nutrition per 100g uses this weight"
   - bottom `SaveButton`: label `Save & add to {section}` (from log) or `Save meal` (My Foods)
   - back with `dirty` → "Discard this meal?" `AlertDialog`
2. `INGREDIENT` — reused entry content: `PrimaryTabRow` (Search/Barcode/Label/Quick) + `SearchContent` / `BarcodeScanScreen` / `LabelScanScreen` / `QuickAddContent` (title "Add ingredient", submitLabel "Save ingredient"). When `pendingIngredient != null`, render `PortionSizeContent` with `confirmLabel = "Add ingredient · N kcal"`. A "Done" affordance returns to `BUILD` without saving.
3. `PORTION` — after `savedMeal` is set (from-log): render `PortionSizeContent` for `savedMeal` with `confirmLabel = "Add to {section} · N kcal"`; `onConfirm` → `AddLogEntryUseCase(savedMeal, portionG, portionLabel, date, sectionId, isMeal = true, mealWeightG = preparedWeightG)` → finish. Back here finishes without logging (meal stays saved).

- [ ] **Step 3: Build + unit tests**

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
Expected: PASS. If the ViewModel holds enough pure logic (canSave, totals, ingredient mutations), extract it to a testable pure helper or test the ViewModel with constructed state per the `LogViewModelSelectionTest`/`SectionDistributionTest` style.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/macrotrack/ui/meal
git commit -m "feat(meal): meal builder screen with ingredient entry and save-and-log"
```

---

## Task 7: Navigation + entry points

**Files:**
- Modify: `MainActivity.kt`
- Modify: `ui/log/LogScreen.kt`
- Modify: `ui/myfoods/MyFoodsScreen.kt`

**Interfaces:**
- New route: `meal?mealId={mealId}&date={date}&sectionId={sectionId}`. `mealId=0` ⇒ new meal; `date`/`sectionId` empty or `0` ⇒ save-only context (My Foods).
- `LogScreen` FAB menu gains a "Create meal" option; preserves the selected date/section.
- `MyFoodsScreen` gains a "Create meal" button and routes meal rows to the builder.

- [ ] **Step 1: Add the route**

`MainActivity.kt`:
```kotlin
composable(
    route = "meal?mealId={mealId}&date={date}&sectionId={sectionId}",
    arguments = listOf(
        navArgument("mealId") { type = NavType.LongType; defaultValue = 0L },
        navArgument("date") { type = NavType.StringType; defaultValue = "" },
        navArgument("sectionId") { type = NavType.LongType; defaultValue = 0L },
    ),
) {
    MealBuilderScreen(onDone = { navController.popBackStack() })
}
```
Add the `navArgument`/`NavType` imports.

- [ ] **Step 2: Log FAB option**

In `LogScreen.kt`'s `ModalBottomSheet`, add a fifth `AddMenuOption` (icon `Icons.Default.RestaurantMenu` — already used for the empty state; subtitle "Combine foods into one saved meal"):
```kotlin
onClick = { onNavigateToCreateMeal(dateIso, defaultId) }
```
Add an `onNavigateToCreateMeal: (dateIso: String, sectionId: Long) -> Unit` parameter threaded through `LogScreen` → `MainActivity`:
```kotlin
onNavigateToCreateMeal = { date, sectionId ->
    navController.navigate("meal?mealId=0&date=$date&sectionId=$sectionId")
}
```

- [ ] **Step 3: My Foods entry points**

`MyFoodsScreen.kt`:
- Add a `Button`/`OutlinedButton` "Create meal" above the list (only when the list is not empty, or always; keep simple — always visible above the search field or as a first row).
- `onCreateMeal: () -> Unit` param; `MainActivity` wires `{ navController.navigate("meal?mealId=0&date=&sectionId=0") }`.
- `FoodItemRow`: if `food.isMeal`, show a `Meal` badge and route `onEdit` to the builder (`meal?mealId={id}&date=&sectionId=0`); else keep `edit-food/{id}`.

- [ ] **Step 4: Build + unit tests**

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/macrotrack/MainActivity.kt \
        app/src/main/java/com/macrotrack/ui/log/LogScreen.kt \
        app/src/main/java/com/macrotrack/ui/myfoods/MyFoodsScreen.kt
git commit -m "feat(meal): meal route, log FAB entry point, My Foods create/edit"
```

---

## Task 8: Room migration instrumentation test

**Files:**
- Create: `app/src/androidTest/java/com/macrotrack/data/local/db/MigrationTest.kt`

**Interfaces:**
- Uses `MigrationTestHelper` with `MacroTrackDatabase::class.java` to validate `MIGRATION_4_5`.

- [ ] **Step 1: Write the test**

Mirror the existing `FoodSearchIndexInstrumentedTest` style. Steps:
1. Create a v4 database (`MigrationTestHelper.createDatabase("macro_track_test", 4)`) and insert a food row.
2. Run `.runMigrationsAndValidate("macro_track_test", 5, true, MIGRATION_4_5)`.
3. Assert the food row survived with `isMeal = 0`; assert the new `meal_ingredients` table exists and accepts an insert.

Note: the test DB's FTS5 external tables are created by `SearchIndexManager` via callbacks, so `runMigrationsAndValidate` only validates the Room-managed tables; disable validation against FTS if it complains (the FTS tables are not Room entities). If `MigrationTestHelper` cannot resolve the full schema, fall back to a plain SQLite `SQLiteDatabase` open at version 4, insert sample rows, migrate with `MIGRATION_4_5`, and assert columns/tables exist.

- [ ] **Step 2: Run instrumented tests**

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :app:connectedDebugAndroidTest
```
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/macrotrack/data/local/db/MigrationTest.kt
git commit -m "test(meal): Room v4→v5 migration test"
```

---

## Task 9: Final verification + UX pass

- [ ] **Step 1: Full build + all unit tests**

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```
Expected: all green.

- [ ] **Step 2: Manual UX verification**

On a device/emulator, walk through:
1. Log → FAB → Create meal → add 2-3 ingredients via search (one with the `+` default-add, one via tap → portion).
2. Set a prepared weight; confirm the live totals change and the save CTA shows the section.
3. Save & add → meal portion picker shows `1/4/1/3/1/2/1` badges → pick `1/4` → log shows `1/4 meal` (not `25g`).
4. Back out of the portion picker after save → meal is saved but not logged.
5. Create an unweighted meal → confirm no custom-grams field, fraction-only.
6. My Foods → Create meal → save-only returns to My Foods with a `Meal` badge.
7. Search the meal name in the regular Add flow → badge shown, no quick-add `+`, tapping opens the meal-aware portion picker.
8. Edit a logged meal's portion → fractions work off the meal's base weight.
9. Delete a meal from My Foods → log entries for it survive (name/macros snapshot), ingredients cascade away.

- [ ] **Step 3: Polish pass**

Verify spacing tokens, `brandPrimary`/macro colour rules, `MacroTrackPillShape`, `SaveButton` usage, keyboard capitalization, and that no new dependencies were added.

- [ ] **Step 4: Final commit (any polish)**

```bash
git add -A
git commit -m "polish(meal): final UX pass"
```

---

## Open decisions to confirm before implementation

1. **Terminology:** "Create meal" / "Meal" badge (vs "recipe"). Locked in the plan; confirm it reads well.
2. **Unweighted normalization:** whole batch = internal 100g, never shown to the user as grams. Confirmed acceptable by product.
3. **Servings count field:** intentionally omitted in V1 (fraction badges cover 2/3/4 portioning without weighing). Add later if users ask.
4. **Nested meals:** disallowed in V1 (meal builder's search can list other meals — filter `isMeal = 0` from ingredient search results to prevent nesting; add `AND isMeal = 0` to the search SQL only when in meal-builder context, or filter in the ViewModel). Simplest: filter in the ViewModel after search.
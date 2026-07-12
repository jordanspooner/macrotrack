plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Regenerates the bundled food database asset from any CSVs placed in
// `food-db-builder/data/` (USDA food.csv + food_nutrient.csv, and/or
// en.openfoodfacts.org.products.csv). This is the single build step that
// produces `app/src/main/assets/databases/food_database.db`; afterwards just
// build/reinstall the app. The seeder (gated on an empty `food_items`
// table) runs on first launch, or use the in-app "Rebuild food database"
// dev option to re-seed after dropping in new data.
tasks.register("buildFoodDatabase") {
    group = "build"
    description = "Rebuilds app/src/main/assets/databases/food_database.db from food-db-builder/data/*.csv"
    dependsOn(":food-db-builder:run")
}

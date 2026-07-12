package com.macrotrack.dbbuilder.sample

import com.macrotrack.dbbuilder.parser.ParsedFood

/**
 * A curated set of common UK foods used to populate the pre-built database when
 * the full USDA / Open Food Facts CSV exports are not available. This keeps the
 * app demoable and exercises the full parse -> clean -> insert pipeline.
 *
 * To build the real database, drop the USDA `food.csv` + `food_nutrient.csv` and
 * the OFF `en.openfoodfacts.org.products.csv` next to the builder and they will
 * be parsed in preference to (and merged with) this sample set.
 */
object SampleFoods {

    fun list(): List<ParsedFood> = buildList {
        // --- Common UK branded / packaged foods (OFF-style, with EAN) ---
        add(off("5000159484695", "McVitie's", "Digestive Biscuits", 15.9f, "1 biscuit", 478f, 6.0f, 67.0f, 20.0f))
        add(off("5000159461122", "Mars", "Mars Bar", 48f, "1 bar", 447f, 4.4f, 69.0f, 19.0f))
        add(off("7622210407326", "Kellogg's", "Corn Flakes", 30f, "1 bowl", 375f, 7.0f, 83.0f, 1.0f))
        add(off("5000111009774", "Weetabix", "Weetabix", 37.5f, "2 biscuits", 362f, 12.0f, 68.0f, 3.5f))
        add(off("5010061002206", "Innocent", "Smoothie Strawberry & Banana", 250f, "1 bottle", 51f, 0.8f, 11.0f, 0.2f))
        add(off("5000354800241", "Coca-Cola", "Coca-Cola Classic", 330f, "1 can", 42f, 0.0f, 10.6f, 0.0f))
        add(off("5000112546588", "Walkers", "Ready Salted Crisps", 25f, "1 bag", 536f, 6.2f, 53.0f, 33.0f))
        add(off("4009900405695", "Ritter Sport", "Milk Chocolate", 50f, "1 bar", 546f, 7.1f, 53.0f, 33.0f))
        add(off("5000171016113", "Heinz", "Baked Beans In Tomato Sauce", 200f, "1/2 can", 73f, 5.0f, 12.0f, 0.5f))
        add(off("5000157024534", "Hellmann's", "Real Mayonnaise", 15f, "1 tbsp", 721f, 1.0f, 1.0f, 79.0f))
        add(off("5054787000038", "Pasta", "Dry White Pasta", 75f, "1 serving", 359f, 12.0f, 71.0f, 1.5f))
        add(off("5000171053804", "Heinz", "Tomato Ketchup", 17f, "1 tbsp", 112f, 0.4f, 26.0f, 0.1f))
        add(off("5060144340116", "Yeo Valley", "Natural Yogurt", 125f, "1 pot", 82f, 5.0f, 6.0f, 4.5f))
        add(off("5000225100455", "Quaker", "Porridge Oats", 40f, "1 serving", 375f, 11.0f, 64.0f, 8.0f))
        add(off("7613034627033", "Nescafe", "Instant Coffee", 2f, "1 tsp", 3f, 0.1f, 0.0f, 0.0f))
        add(off("5000000000001", "Tesco", "Whole Milk", 200f, "1 glass", 64f, 3.3f, 4.8f, 3.6f))
        add(off("5000000000002", "Tesco", "White Bread", 40f, "1 slice", 243f, 9.0f, 43.0f, 3.5f))
        add(off("5000000000003", "Tesco", "Free Range Eggs", 50f, "1 large egg", 147f, 12.0f, 1.0f, 10.0f))
        add(off("5000000000004", "Tesco", "Unsalted Butter", 14f, "1 tbsp", 717f, 0.9f, 0.1f, 81.0f))
        add(off("5000000000005", "Tesco", "Chicken Breast Fillets", 120f, "1 fillet", 165f, 31.0f, 0.0f, 3.6f))
        add(off("5000000000006", "Tesco", "Salmon Fillet", 100f, "1 fillet", 208f, 20.0f, 0.0f, 13.0f))
        add(off("5000000000007", "Tesco", "Basmati Rice", 75f, "1 serving", 355f, 8.0f, 79.0f, 0.9f))
        add(off("5000000000008", "Tesco", "Banana", 118f, "1 medium", 89f, 1.1f, 23.0f, 0.3f))
        add(off("5000000000009", "Tesco", "Avocado", 150f, "1 fruit", 160f, 2.0f, 9.0f, 15.0f))
        add(off("5000000000010", "Tesco", "Cheddar Cheese", 30f, "1 slice", 416f, 25.0f, 0.1f, 35.0f))
        add(off("5000000000011", "Tesco", "Orange Juice", 150f, "1 glass", 45f, 0.7f, 9.0f, 0.1f))
        add(off("5000000000012", "Tesco", "Peanut Butter", 15f, "1 tbsp", 588f, 25.0f, 12.0f, 50.0f))
        add(off("5000000000013", "Tesco", "Dark Chocolate 70%", 25f, "1 square", 598f, 7.8f, 46.0f, 43.0f))
        add(off("5000000000014", "Tesco", "Greek Yogurt", 100f, "1 pot", 97f, 9.0f, 4.0f, 4.5f))
        add(off("5000000000015", "Tesco", "Sweet Potato", 130f, "1 medium", 86f, 1.6f, 20.0f, 0.1f))

        // --- USDA / generic whole foods (no EAN) ---
        add(usda("USDA-11124", "Butter, salted", 14f, "1 tbsp", 717f, 0.85f, 0.06f, 81.11f))
        add(usda("USDA-01009", "Beef, ground, raw", 100f, "100g", 250f, 26.0f, 0.0f, 15.0f))
        add(usda("USDA-05023", "Broccoli, raw", 100f, "100g", 34f, 2.8f, 7.0f, 0.4f))
        add(usda("USDA-09003", "Cheese, cheddar", 100f, "100g", 403f, 25.0f, 1.3f, 33.0f))
        add(usda("USDA-01123", "Chicken, breast, raw", 100f, "100g", 120f, 23.0f, 0.0f, 2.6f))
        add(usda("USDA-09040", "Egg, whole, raw", 100f, "100g", 143f, 13.0f, 1.0f, 9.5f))
        add(usda("USDA-16022", "Fish, salmon, atlantic", 100f, "100g", 208f, 20.0f, 0.0f, 13.0f))
        add(usda("USDA-19095", "Milk, whole", 100f, "100g", 61f, 3.2f, 4.8f, 3.3f))
        add(usda("USDA-20038", "Oil, olive", 14f, "1 tbsp", 884f, 0.0f, 0.0f, 100.0f))
        add(usda("USDA-09079", "Rice, white, cooked", 100f, "100g", 130f, 2.7f, 28.0f, 0.3f))
    }

    private fun off(
        ean: String, brand: String, name: String, portionG: Float, portionLabel: String,
        kcal: Float, protein: Float, carbs: Float, fat: Float
    ) = ParsedFood(
        source = "OPEN_FOOD_FACTS", sourceId = ean, ean = ean, brand = brand, name = name,
        defaultPortionG = portionG, defaultPortionLabel = portionLabel,
        kcalPer100g = kcal, proteinPer100g = protein, carbsPer100g = carbs, fatPer100g = fat
    )

    private fun usda(
        id: String, name: String, portionG: Float, portionLabel: String,
        kcal: Float, protein: Float, carbs: Float, fat: Float
    ) = ParsedFood(
        source = "USDA", sourceId = id, ean = null, brand = null, name = name,
        defaultPortionG = portionG, defaultPortionLabel = portionLabel,
        kcalPer100g = kcal, proteinPer100g = protein, carbsPer100g = carbs, fatPer100g = fat
    )
}

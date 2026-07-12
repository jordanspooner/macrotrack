package com.macrotrack.dbbuilder

import com.macrotrack.dbbuilder.cleaner.DataCleaner
import com.macrotrack.dbbuilder.parser.OffParser
import com.macrotrack.dbbuilder.parser.ParsedFood
import com.macrotrack.dbbuilder.parser.ServingSizeParser
import com.macrotrack.dbbuilder.parser.UsdaParser
import com.macrotrack.dbbuilder.sample.SampleFoods
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

fun main() {
    println("Starting MacroTrack Food Database Builder...")

    // Default output: the Android app's bundled assets directory.
    // Override with -Dmacrotrack.output=/path/to/food_database.db if needed.
    val outputPath = System.getProperty("macrotrack.output")
        ?: File("..", "app/src/main/assets/databases/food_database.db").path

    val outputDbFile = File(outputPath)
    outputDbFile.parentFile?.mkdirs()
    if (outputDbFile.exists()) outputDbFile.delete()

    val url = "jdbc:sqlite:${outputDbFile.absolutePath}"

    DriverManager.getConnection(url).use { conn ->
        println("Writing database to ${outputDbFile.absolutePath}")
        createTables(conn)

        val foods = collectFoods()
        val cleaned = DataCleaner().clean(foods)
        insertFoods(conn, cleaned)

        println("Database build completed successfully with ${cleaned.size} foods.")
    }
}

/**
 * Builds the full food list: real USDA / OFF exports (if present next to the
 * builder) are merged with the curated sample set so the asset is always usable.
 */
fun collectFoods(): List<ParsedFood> {
    val foods = mutableListOf<ParsedFood>()

    val usdaFoodCsv = File("data/food.csv")
    val usdaNutrientCsv = File("data/food_nutrient.csv")
    if (usdaFoodCsv.exists() && usdaNutrientCsv.exists()) {
        foods += UsdaParser().parse(usdaFoodCsv, usdaNutrientCsv)
    }

    val offCsv = File("data/en.openfoodfacts.org.products.csv")
    if (offCsv.exists()) {
        foods += OffParser(ServingSizeParser()).parse(offCsv)
    }

    // Always include the curated sample set so the app has data out of the box.
    foods += SampleFoods.list()

    return foods
}

fun createTables(conn: Connection) {
    conn.createStatement().use { statement ->
        // Source table matching the Room `food_items` entity. FTS4 is handled by
        // Room's content-sync triggers when the app seeds this data, so we only
        // need the base table here.
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS food_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                sourceId TEXT,
                ean TEXT,
                brand TEXT,
                name TEXT NOT NULL,
                defaultPortionG REAL,
                defaultPortionLabel TEXT,
                kcalPer100g REAL NOT NULL,
                proteinPer100g REAL NOT NULL,
                carbsPer100g REAL NOT NULL,
                fatPer100g REAL NOT NULL
            )
            """.trimIndent()
        )
    }
    println("Tables created successfully.")
}

fun insertFoods(conn: Connection, foods: List<ParsedFood>) {
    // Wrap the bulk insert in a single transaction; per-row autocommit makes a
    // large load (tens of thousands of rows) orders of magnitude slower.
    val autoCommit = conn.autoCommit
    conn.autoCommit = false
    try {
        val insertSql = """
            INSERT INTO food_items (
                source, sourceId, ean, brand, name, defaultPortionG, defaultPortionLabel,
                kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        conn.prepareStatement(insertSql).use { pstmt ->
            for (food in foods) {
                pstmt.setString(1, food.source)
                pstmt.setString(2, food.sourceId)
                pstmt.setString(3, food.ean)
                pstmt.setString(4, food.brand)
                pstmt.setString(5, food.name)
                if (food.defaultPortionG != null) pstmt.setFloat(6, food.defaultPortionG)
                else pstmt.setNull(6, java.sql.Types.REAL)
                pstmt.setString(7, food.defaultPortionLabel)
                pstmt.setFloat(8, food.kcalPer100g)
                pstmt.setFloat(9, food.proteinPer100g)
                pstmt.setFloat(10, food.carbsPer100g)
                pstmt.setFloat(11, food.fatPer100g)
                pstmt.executeUpdate()
            }
        }
        conn.commit()
    } catch (e: Exception) {
        conn.rollback()
        throw e
    } finally {
        conn.autoCommit = autoCommit
    }
    println("Inserted ${foods.size} foods into food_items.")
}

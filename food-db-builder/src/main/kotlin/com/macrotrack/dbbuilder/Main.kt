package com.macrotrack.dbbuilder

import com.macrotrack.dbbuilder.cleaner.DataCleaner
import com.macrotrack.dbbuilder.parser.OffParser
import com.macrotrack.dbbuilder.parser.ParsedFood
import com.macrotrack.dbbuilder.parser.ServingSizeParser
import com.macrotrack.dbbuilder.parser.UsdaParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.util.zip.GZIPOutputStream

data class SourceDefinition(
    val id: String,
    val name: String,
    val publisher: String,
    val description: String,
    val version: String,
    val sourceType: String,
    val parser: () -> List<ParsedFood>
)

data class CatalogEntry(
    val id: String,
    val name: String,
    val description: String,
    val publisher: String,
    val latestVersion: String,
    val itemCount: Int,
    val downloadUrl: String,
    val downloadSizeBytes: Long,
    val checksumSha256: String
)

fun main() {
    println("Starting MacroTrack Food Database Builder (per-source mode)...")

    val outputDir = System.getProperty("macrotrack.output")
        ?.let { File(it) }
        ?: File("output")

    val catalogUrlBase = System.getProperty("macrotrack.catalogBaseUrl")
        ?: "https://raw.githubusercontent.com/jordanspooner/macrotrack-food-data/main"

    outputDir.mkdirs()

    val sources = listOf(
        SourceDefinition(
            id = "open-food-facts-uk",
            name = "Open Food Facts (UK)",
            publisher = "Open Food Facts",
            description = "UK products from the Open Food Facts database",
            version = "1.0.0",
            sourceType = "OPEN_FOOD_FACTS",
            parser = {
                val csv = File("data/en.openfoodfacts.org.products.csv")
                if (!csv.exists()) {
                    println("OFF CSV not found at ${csv.absolutePath} — skipping.")
                    emptyList()
                } else {
                    OffParser(ServingSizeParser()).parse(csv)
                }
            }
        ),
        SourceDefinition(
            id = "usda-sr-legacy",
            name = "USDA SR Legacy",
            publisher = "USDA FoodData Central",
            description = "USDA Standard Reference Legacy nutrient data",
            version = "1.0.0",
            sourceType = "USDA",
            parser = {
                val foodCsv = File("data/food.csv")
                val nutrientCsv = File("data/food_nutrient.csv")
                if (!foodCsv.exists() || !nutrientCsv.exists()) {
                    println("USDA CSVs not found — skipping.")
                    emptyList()
                } else {
                    UsdaParser().parse(foodCsv, nutrientCsv)
                }
            }
        )
    )

    val catalogEntries = mutableListOf<CatalogEntry>()

    for (source in sources) {
        println("\n--- Building source: ${source.id} v${source.version} ---")

        val raw = source.parser()
        if (raw.isEmpty()) {
            println("No foods parsed for ${source.id} — skipping.")
            continue
        }

        val cleaned = DataCleaner().clean(raw)
        println("${source.id}: ${cleaned.size} foods after cleaning.")

        val sourceOutputDir = File(outputDir, "sources/${source.id}/${source.version}")
        sourceOutputDir.mkdirs()

        val dbFile = File(sourceOutputDir, "${source.id}-${source.version}.db")
        buildSqlite(dbFile, source, cleaned)

        val gzFile = File(sourceOutputDir, "${source.id}-${source.version}.db.gz")
        gzip(dbFile, gzFile)
        dbFile.delete()

        val sha = sha256(gzFile)
        val size = gzFile.length()

        val downloadUrl = "$catalogUrlBase/sources/${source.id}/${source.version}/${source.id}-${source.version}.db.gz"

        catalogEntries.add(
            CatalogEntry(
                id = source.id,
                name = source.name,
                description = source.description,
                publisher = source.publisher,
                latestVersion = source.version,
                itemCount = cleaned.size,
                downloadUrl = downloadUrl,
                downloadSizeBytes = size,
                checksumSha256 = sha
            )
        )

        println("${source.id}: output ${gzFile.absolutePath} (${size} bytes, sha256=${sha.take(12)}...)")
    }

    writeCatalog(outputDir, catalogEntries)
    println("\nAll sources built. Catalog at ${File(outputDir, "catalog.json").absolutePath}")
}

fun buildSqlite(dbFile: File, source: SourceDefinition, foods: List<ParsedFood>) {
    if (dbFile.exists()) dbFile.delete()

    val url = "jdbc:sqlite:${dbFile.absolutePath}"
    DriverManager.getConnection(url).use { conn ->
        createTables(conn)
        insertFoods(conn, foods, source.sourceType, source.id)
    }
}

fun createTables(conn: Connection) {
    conn.createStatement().use { statement ->
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS food_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                sourceId TEXT,
                dataSourceId TEXT NOT NULL,
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

fun insertFoods(conn: Connection, foods: List<ParsedFood>, sourceType: String, dataSourceId: String) {
    val autoCommit = conn.autoCommit
    conn.autoCommit = false
    try {
        val insertSql = """
            INSERT INTO food_items (
                source, sourceId, dataSourceId, ean, brand, name,
                defaultPortionG, defaultPortionLabel,
                kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        conn.prepareStatement(insertSql).use { pstmt ->
            for (food in foods) {
                pstmt.setString(1, sourceType)
                pstmt.setString(2, food.sourceId)
                pstmt.setString(3, dataSourceId)
                pstmt.setString(4, food.ean)
                pstmt.setString(5, food.brand)
                pstmt.setString(6, food.name)
                if (food.defaultPortionG != null) pstmt.setFloat(7, food.defaultPortionG)
                else pstmt.setNull(7, java.sql.Types.REAL)
                pstmt.setString(8, food.defaultPortionLabel)
                pstmt.setFloat(9, food.kcalPer100g)
                pstmt.setFloat(10, food.proteinPer100g)
                pstmt.setFloat(11, food.carbsPer100g)
                pstmt.setFloat(12, food.fatPer100g)
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
    println("Inserted ${foods.size} foods.")
}

fun gzip(input: File, output: File) {
    FileInputStream(input).use { fis ->
        FileOutputStream(output).use { fos ->
            GZIPOutputStream(fos).use { gzos ->
                fis.copyTo(gzos)
            }
        }
    }
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { fis ->
        val buffer = ByteArray(8192)
        var read: Int
        while (fis.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun writeCatalog(outputDir: File, entries: List<CatalogEntry>) {
    val json = buildJsonCatalog(entries)
    File(outputDir, "catalog.json").writeText(json)
}

fun buildJsonCatalog(entries: List<CatalogEntry>): String {
    val sourcesJson = entries.joinToString(",\n    ") { entry ->
        """{
    "id": "${entry.id}",
    "name": "${entry.name}",
    "description": "${entry.description}",
    "publisher": "${entry.publisher}",
    "latestVersion": "${entry.latestVersion}",
    "itemCount": ${entry.itemCount},
    "downloadUrl": "${entry.downloadUrl}",
    "downloadSizeBytes": ${entry.downloadSizeBytes},
    "checksumSha256": "${entry.checksumSha256}"
  }"""
    }

    return """{
  "schemaVersion": 1,
  "sources": [
    $sourcesJson
  ]
}
"""
}

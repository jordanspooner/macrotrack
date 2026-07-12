package com.macrotrack.dbbuilder.parser

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.File
import java.io.FileReader

class OffParser(
    private val servingSizeParser: ServingSizeParser = ServingSizeParser()
) {

    /**
     * Parses an Open Food Facts TSV export.
     *
     * @param csvFile the OFF products TSV file.
     * @param requireUkFilter when true (default) only products tagged for the
     *        United Kingdom are kept.
     */
    fun parse(csvFile: File, requireUkFilter: Boolean = true): List<ParsedFood> {
        if (!csvFile.exists()) return emptyList()

        println("Parsing Open Food Facts Data from ${csvFile.name}...")
        val parsedFoods = mutableListOf<ParsedFood>()

        FileReader(csvFile).use { reader ->
            val parser = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setDelimiter('\t') // OFF uses TSV
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build()
                .parse(reader)

            for (record in parser) {
                try {
                    val code = record.get("code")
                    val name = record.get("product_name")
                    val brands = record.get("brands")
                    val countries = record.get("countries_tags").orEmpty()

                    if (name.isNullOrBlank() || code.isNullOrBlank()) continue

                    // Filter by country if required.
                    if (requireUkFilter && !countries.contains("en:united-kingdom")) continue

                    val kcal = record.get("energy-kcal_100g")?.toFloatOrNull() ?: continue
                    val protein = record.get("proteins_100g")?.toFloatOrNull() ?: continue
                    val carbs = record.get("carbohydrates_100g")?.toFloatOrNull() ?: continue
                    val fat = record.get("fat_100g")?.toFloatOrNull() ?: continue

                    // Require complete, plausible macro data.
                    if (kcal <= 0 || protein < 0 || carbs < 0 || fat < 0) continue
                    if (protein + carbs + fat > 100.5f) continue
                    if (kcal > 900f) continue

                    val serving = servingSizeParser.parse(record.get("serving_size"))
                    val servingG = serving?.grams ?: 100f

                    parsedFoods.add(
                        ParsedFood(
                            source = "OPEN_FOOD_FACTS",
                            sourceId = code, // Use barcode as source ID
                            ean = code,
                            brand = brands?.takeIf { it.isNotBlank() },
                            name = name,
                            defaultPortionG = servingG,
                            defaultPortionLabel = serving?.label ?: "100g",
                            kcalPer100g = kcal,
                            proteinPer100g = protein,
                            carbsPer100g = carbs,
                            fatPer100g = fat
                        )
                    )
                } catch (_: Exception) {
                    // Skip problematic rows.
                }
            }
        }

        println("Parsed ${parsedFoods.size} OFF foods.")
        return parsedFoods
    }
}

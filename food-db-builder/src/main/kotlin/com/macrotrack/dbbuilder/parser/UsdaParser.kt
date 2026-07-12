package com.macrotrack.dbbuilder.parser

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.File
import java.io.FileReader

data class ParsedFood(
    val source: String,
    val sourceId: String,
    val ean: String?,
    val brand: String?,
    val name: String,
    val defaultPortionG: Float,
    val defaultPortionLabel: String?,
    val kcalPer100g: Float,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float
)

class UsdaParser {
    // Maps USDA nutrient IDs
    private val NUTRIENT_ID_KCAL = "1008" // Energy
    private val NUTRIENT_ID_PROTEIN = "1003" // Protein
    private val NUTRIENT_ID_CARBS = "1005" // Carbohydrate
    private val NUTRIENT_ID_FAT = "1004" // Total lipid (fat)

    fun parse(foodCsv: File, nutrientCsv: File): List<ParsedFood> {
        if (!foodCsv.exists() || !nutrientCsv.exists()) return emptyList()

        println("Parsing USDA Data...")
        
        // 1. Read foods
        val foodNames = mutableMapOf<String, String>()
        FileReader(foodCsv).use { reader ->
            val parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)
            for (record in parser) {
                val fdcId = record.get("fdc_id")
                val description = record.get("description")
                foodNames[fdcId] = description
            }
        }

        // 2. Read nutrients
        // fdc_id -> map of nutrient_id -> amount (per 100g)
        val foodNutrients = mutableMapOf<String, MutableMap<String, Float>>()
        FileReader(nutrientCsv).use { reader ->
            val parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)
            for (record in parser) {
                val fdcId = record.get("fdc_id")
                val nutrientId = record.get("nutrient_id")
                val amount = record.get("amount").toFloatOrNull() ?: 0f
                
                if (nutrientId in listOf(NUTRIENT_ID_KCAL, NUTRIENT_ID_PROTEIN, NUTRIENT_ID_CARBS, NUTRIENT_ID_FAT)) {
                    val map = foodNutrients.getOrPut(fdcId) { mutableMapOf() }
                    map[nutrientId] = amount
                }
            }
        }

        val parsedFoods = mutableListOf<ParsedFood>()
        
        for ((fdcId, name) in foodNames) {
            val nutrients = foodNutrients[fdcId] ?: continue
            val kcal = nutrients[NUTRIENT_ID_KCAL] ?: continue
            val protein = nutrients[NUTRIENT_ID_PROTEIN] ?: continue
            val carbs = nutrients[NUTRIENT_ID_CARBS] ?: continue
            val fat = nutrients[NUTRIENT_ID_FAT] ?: continue

            parsedFoods.add(
                ParsedFood(
                    source = "USDA",
                    sourceId = fdcId,
                    ean = null, // USDA doesn't provide EANs in Foundation/SR Legacy
                    brand = null,
                    name = name,
                    defaultPortionG = 100f, // Defaulting to 100g unless portion data is parsed
                    defaultPortionLabel = "100g",
                    kcalPer100g = kcal,
                    proteinPer100g = protein,
                    carbsPer100g = carbs,
                    fatPer100g = fat
                )
            )
        }

        println("Parsed ${parsedFoods.size} USDA foods.")
        return parsedFoods
    }
}

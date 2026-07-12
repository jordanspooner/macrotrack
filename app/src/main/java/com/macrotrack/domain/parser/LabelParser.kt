package com.macrotrack.domain.parser

import java.util.Locale
import javax.inject.Inject

/**
 * Nutrition values extracted from a single OCR scan of a label.
 *
 * UK labels typically show two columns: per-100g and per-serving.
 * The parser extracts both when possible, which allows cross-validation:
 * per100_value * servingSize / 100 ≈ perServing_value.
 *
 * Only one of [per100] or [perServing] is required to be non-null.
 * If only per-serving is found and [servingSizeG] is known, values
 * can be scaled to per-100g.
 */
data class ParsedNutritionLabel(
    val per100: MacroValues?,
    val perServing: MacroValues?,
    val servingSizeG: Float?,
    val servingLabel: String?
)

data class MacroValues(
    val kcal: Float?,
    val fat: Float?,
    val carbs: Float?,
    val protein: Float?
)

class LabelParser @Inject constructor() {

    /**
     * Parse from structured OCR elements with positions.
     * This is the primary entry point — uses Y-position clustering
     * and X-position column detection.
     */
    fun parseStructured(elements: List<OcrElement>): ParsedNutritionLabel {
        if (elements.isEmpty()) return ParsedNutritionLabel(null, null, null, null)

        val rows = clusterIntoRows(elements)
        val serving = findServingFromRows(rows)

        // Try Strategy A: two-column table
        val tableResult = tryTableParse(rows)
        if (tableResult != null) {
            return tableResult.copy(
                servingSizeG = serving?.grams ?: tableResult.servingSizeG,
                servingLabel = serving?.label ?: tableResult.servingLabel
            )
        }

        // Try Strategy B: single-column list (Y-proximity matching)
        val listResult = tryListParse(rows)
        if (listResult != null) {
            return listResult.copy(
                servingSizeG = serving?.grams ?: listResult.servingSizeG,
                servingLabel = serving?.label ?: listResult.servingLabel
            )
        }

        // Strategy C: fallback to flat text parsing
        val flatText = elements.joinToString("\n") { it.text }
        return parse(flatText)
    }

    /**
     * Parse from plain text (backward compatibility for tests).
     * Delegates to structured parsing after creating synthetic elements.
     */
    fun parse(rawText: String): ParsedNutritionLabel {
        val lines = normalize(rawText).lines().map { it.trim() }.filter { it.isNotBlank() }
        val serving = findServing(lines)

        // Try list format with text-based section splitting
        val sections = splitSections(lines)
        val per100 = sections.per100?.let { extractMacrosFromSection(it) }
        val perServing = sections.perServing?.let { extractMacrosFromSection(it) }

        val per100Energy = sections.per100?.let { findEnergyInSection(it) }
        val perServingEnergy = sections.perServing?.let { findEnergyInSection(it) }

        val per100WithEnergy = per100?.let { it.copy(kcal = per100Energy ?: it.kcal) }
        val perServingWithEnergy = perServing?.let { it.copy(kcal = perServingEnergy ?: it.kcal) }

        return ParsedNutritionLabel(
            per100 = per100WithEnergy,
            perServing = perServingWithEnergy,
            servingSizeG = serving?.grams,
            servingLabel = serving?.label
        )
    }

    // ── Row clustering ──────────────────────────────────────────────

    /**
     * Group OcrElements into rows by Y-position proximity.
     * Uses dynamic tolerance: elements far apart in X are allowed
     * a larger Y difference, accounting for table skew (rotation).
     * At 1° rotation, a 1000px-wide table drifts ~18px vertically.
     * Tolerance is capped to prevent merging across distinct rows.
     */
    fun clusterIntoRows(elements: List<OcrElement>, tolerance: Int = 15): List<OcrRow> {
        if (elements.isEmpty()) return emptyList()

        val sorted = elements.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<OcrElement>>()
        var currentRow = mutableListOf(sorted[0])

        for (i in 1 until sorted.size) {
            val elem = sorted[i]
            val rowAvgY = currentRow.map { it.centerY }.average()
            val rowAvgX = currentRow.map { it.centerX }.average()
            val xDist = kotlin.math.abs(elem.centerX - rowAvgX)
            val effectiveTolerance = minOf((tolerance + xDist * 0.02).toFloat(), (tolerance * 3).toFloat())
            if (kotlin.math.abs(elem.centerY - rowAvgY) <= effectiveTolerance) {
                currentRow.add(elem)
            } else {
                rows.add(currentRow)
                currentRow = mutableListOf(elem)
            }
        }
        rows.add(currentRow)

        return rows.map { row ->
            val sortedRow = row.sortedBy { it.left }
            OcrRow(sortedRow, sortedRow.map { it.centerY }.average().toInt())
        }
    }

    // ── Strategy A: Two-column table ────────────────────────────────

    /**
     * Detect a two-column table header and extract values using positions.
     *
     * A table header is a row that contains both "per 100" and another
     * "per <not 100>" element. The header's bounding box tells us the
     * X-ranges of each column.
     */
    private fun tryTableParse(rows: List<OcrRow>): ParsedNutritionLabel? {
        val (headerStart, headerEnd) = findTableHeaderRow(rows) ?: return null

        // Merge the header rows for column detection (header may span multiple rows)
        val mergedElements = (headerStart..headerEnd).flatMap { rows[it].elements }
        val mergedHeader = OcrRow(mergedElements, rows[headerStart].avgY)

        // Determine column X-ranges from the merged header
        val colInfo = determineColumns(mergedHeader) ?: return null

        // Strategy 1: Keywords above header, data rows below (Sabra, Tesco style)
        val aboveKeywords = mutableListOf<Pair<Int, KeywordType>>()
        for (i in 0 until headerStart) {
            val row = rows[i]
            val kw = classifyRowKeyword(row) ?: continue
            aboveKeywords.add(i to kw)
        }

        val dataRowsBelow = rows.drop(headerEnd + 1).filter { row ->
            row.elements.any { elem -> parseMacroValue(elem.text) != null }
        }

        if (aboveKeywords.isNotEmpty() && dataRowsBelow.isNotEmpty()) {
            val result = matchKeywordToDataRows(aboveKeywords, dataRowsBelow, colInfo)
            if (result != null) return result
        }

        // Strategy 2: Keywords and data interleaved below header (Leerdammer style)
        // Scan rows below header; when we see a keyword, extract from same/next row
        val belowRows = rows.drop(headerEnd + 1)
        val per100 = MacroValuesBuilder()
        val perServing = MacroValuesBuilder()
        var lastKeyword: KeywordType? = null

        for (rowIdx in belowRows.indices) {
            val row = belowRows[rowIdx]
            val kw = classifyRowKeyword(row)
            if (kw != null && kw != KeywordType.OTHER) {
                lastKeyword = kw
                // Try inline values in the keyword row itself
                when (kw) {
                    KeywordType.ENERGY -> extractEnergyFromRow(row, colInfo, per100, perServing)
                    KeywordType.FAT -> extractMacroFromRow(row, colInfo, per100, perServing) { it.fat == null }
                    KeywordType.CARBS -> extractMacroFromRow(row, colInfo, per100, perServing) { it.carbs == null }
                    KeywordType.PROTEIN -> extractMacroFromRow(row, colInfo, per100, perServing) { it.protein == null }
                    else -> {}
                }
                // If the keyword row had any parseable values, don't carry to next row
                val hasAnyValue = row.elements.any { parseMacroValue(it.text) != null }
                if (hasAnyValue) lastKeyword = null
                continue
            }

            // If this row has numeric values and we have a pending keyword, extract
            if (lastKeyword != null && row.elements.any { parseMacroValue(it.text) != null }) {
                when (lastKeyword) {
                    KeywordType.ENERGY -> {
                        // Scan up to 3 rows ahead for a proper kcal value
                        val startIdx = maxOf(0, rowIdx - 1)
                        val endIdx = minOf(belowRows.size - 1, rowIdx + 2)
                        for (scanIdx in startIdx..endIdx) {
                            extractEnergyFromRow(belowRows[scanIdx], colInfo, per100, perServing)
                            if (per100.kcal != null || perServing.kcal != null) break
                        }
                    }
                    KeywordType.FAT -> if (per100.fat == null) extractMacroFromRow(row, colInfo, per100, perServing) { it.fat == null }
                    KeywordType.CARBS -> if (per100.carbs == null) extractMacroFromRow(row, colInfo, per100, perServing) { it.carbs == null }
                    KeywordType.PROTEIN -> if (per100.protein == null) extractMacroFromRow(row, colInfo, per100, perServing) { it.protein == null }
                    else -> {}
                }
                lastKeyword = null
            }
        }

        val result100 = per100.build()
        val resultServing = perServing.build()
        if (result100 == null && resultServing == null) return null
        return ParsedNutritionLabel(result100, resultServing, null, null)
    }

    private fun matchKeywordToDataRows(
        keywordRows: List<Pair<Int, KeywordType>>,
        dataRows: List<OcrRow>,
        colInfo: ColumnInfo
    ): ParsedNutritionLabel? {
        val per100 = MacroValuesBuilder()
        val perServing = MacroValuesBuilder()
        var dataIdx = 0

        for ((_, kwType) in keywordRows) {
            if (dataIdx >= dataRows.size) break
            val dataRow = dataRows[dataIdx]
            when (kwType) {
                KeywordType.ENERGY -> {
                    extractEnergyFromRow(dataRow, colInfo, per100, perServing)
                    dataIdx++
                }
                KeywordType.FAT -> {
                    extractMacroFromRow(dataRow, colInfo, per100, perServing) { it.fat == null }
                        .also { if (it) dataIdx++ }
                }
                KeywordType.CARBS -> {
                    extractMacroFromRow(dataRow, colInfo, per100, perServing) { it.carbs == null }
                        .also { if (it) dataIdx++ }
                }
                KeywordType.PROTEIN -> {
                    extractMacroFromRow(dataRow, colInfo, per100, perServing) { it.protein == null }
                        .also { if (it) dataIdx++ }
                }
                KeywordType.OTHER -> {
                    dataIdx++
                }
            }
        }

        val result100 = per100.build()
        val resultServing = perServing.build()
        if (result100 == null && resultServing == null) return null
        return ParsedNutritionLabel(result100, resultServing, null, null)
    }

    private data class ColumnInfo(
        val per100Left: Int,
        val per100Right: Int,
        val perServingLeft: Int,
        val perServingRight: Int
    )

    private enum class KeywordType { ENERGY, FAT, CARBS, PROTEIN, OTHER }

    /**
     * Find a row (or cluster of adjacent rows) that contains both
     * "per 100" and "per <not 100>". ML Kit often splits these across
     * elements or across adjacent rows.
     */
    /**
     * Find a row (or cluster of adjacent rows) that contains both
     * "per 100" and "per <not 100>". ML Kit often splits these across
     * elements or across adjacent rows.
     * Returns the row index and the last row index of the header cluster.
     */
    private fun findTableHeaderRow(rows: List<OcrRow>): Pair<Int, Int>? {
        // Single-row check
        for (i in rows.indices) {
            if (hasPer100AndServing(rows[i])) return i to i
        }
        // Adjacent-row check: merge 2-5 consecutive rows and check
        for (i in rows.indices) {
            for (window in 2..5) {
                val end = minOf(i + window - 1, rows.size - 1)
                if (end - i + 1 < window) break
                val merged = (i..end).flatMap { rows[it].elements }
                val mergedRow = OcrRow(merged, rows[i].avgY)
                if (hasPer100AndServing(mergedRow)) return i to end
            }
        }
        return null
    }

    private fun hasPer100AndServing(row: OcrRow): Boolean {
        val combined = row.text.lowercase(Locale.getDefault())
        val hasPer100 = combined.contains("per") && combined.contains("100")
        val hasPerServing = PER_SERVING_GENERIC.containsMatchIn(combined)
        if (hasPer100 && hasPerServing) return true

        val texts = row.elements.map { it.text.lowercase(Locale.getDefault()) }
        val elemHasPer100 = texts.any { it.contains("per") && it.contains("100") }
        val elemHasPerServing = texts.any { t ->
            t.contains("per") && !t.contains("100") && PER_SERVING_GENERIC.containsMatchIn(t)
        }
        return elemHasPer100 && elemHasPerServing
    }

    /**
     * Determine column X-ranges from the header row elements.
     * Finds the "per 100" and "per <not 100>" elements and uses their positions
     * to define column boundaries. Handles both single-element and multi-element cases.
     */
    private fun determineColumns(headerRow: OcrRow): ColumnInfo? {
        var per100CenterX = -1
        var perServingCenterX = -1
        val elems = headerRow.elements

        // Pass 1: check individual elements containing both "per"+"100" or "per"+serving word
        for (elem in elems) {
            val lower = elem.text.lowercase(Locale.getDefault())
            if (lower.contains("per") && lower.contains("100")) {
                per100CenterX = elem.centerX
            } else if (lower.contains("per") && PER_SERVING_GENERIC.containsMatchIn(lower)) {
                perServingCenterX = elem.centerX
            }
        }

        // Pass 2: scan for "per" elements and look ahead for "100" or serving indicator
        for (j in elems.indices) {
            val lower = elems[j].text.lowercase(Locale.getDefault())
            if (lower != "per") continue

            // Look ahead up to 3 elements for "100" or a serving indicator
            for (k in j + 1..minOf(j + 3, elems.size - 1)) {
                val next = elems[k].text.lowercase(Locale.getDefault())
                if (next.startsWith("100") && per100CenterX < 0) {
                    per100CenterX = elems[j].centerX
                    break
                }
                if (PER_SERVING_GENERIC.containsMatchIn(next) && perServingCenterX < 0) {
                    perServingCenterX = elems[j].centerX
                    break
                }
                // Only set perServing if next element looks like a serving indicator:
                // starts with digit (e.g., "30g", "200ml") or contains serving words
                if (perServingCenterX < 0 && next != "100" && !next.startsWith("100") &&
                    (next[0].isDigit() || PER_SERVING_GENERIC.containsMatchIn("per $next"))
                ) {
                    perServingCenterX = elems[j].centerX
                    break
                }
            }
        }

        if (per100CenterX < 0 || perServingCenterX < 0) return null

        // Use the midpoint between the two column indicators as the boundary
        val midX = (per100CenterX + perServingCenterX) / 2

        // Determine the keyword area: elements to the left of both column headers
        val colLeft = minOf(per100CenterX, perServingCenterX)
        val keywordRight = headerRow.elements
            .filter { it.centerX < colLeft - 20 }
            .maxOfOrNull { it.right } ?: (colLeft - 20)

        return if (per100CenterX < perServingCenterX) {
            ColumnInfo(
                per100Left = keywordRight + 5,
                per100Right = midX,
                perServingLeft = midX,
                perServingRight = headerRow.right + 10
            )
        } else {
            ColumnInfo(
                per100Left = midX,
                per100Right = headerRow.right + 10,
                perServingLeft = keywordRight + 5,
                perServingRight = midX
            )
        }
    }

    private fun classifyRowKeyword(row: OcrRow): KeywordType? {
        val lower = row.text.lowercase(Locale.getDefault())
        if (lower.contains("nutrition") || lower.contains("typical")) return null
        if (lower.startsWith("per ") || lower.contains("reference intake")) return null
        if (lower.contains("less fat") || lower.contains("50%")) return null

        val isFat = FAT_KEYS.any { keywordMatches(lower, it) } && !row.elements.any {
            val elemLower = it.text.lowercase(Locale.getDefault())
            elemLower.contains("saturat") || elemLower.contains("unsaturat") || elemLower.contains("trans")
        }
        if (isFat) return KeywordType.FAT

        return when {
            ENERGY_KEYWORDS.any { keywordMatches(lower, it) } -> KeywordType.ENERGY
            CARB_KEYS.any { keywordMatches(lower, it) } -> KeywordType.CARBS
            PROTEIN_KEYS.any { keywordMatches(lower, it) } -> KeywordType.PROTEIN
            else -> null
        }
    }

    /**
     * Extract energy values from a data row using column positions.
     * Handles:
     * - Slash-separated: "1517/367"
     * - Adjacent "value" + "kcal" elements
     * - Standalone kcal values
     */
    private fun extractEnergyFromRow(
        row: OcrRow,
        colInfo: ColumnInfo,
        per100: MacroValuesBuilder,
        perServing: MacroValuesBuilder
    ) {
        // Try slash-separated energy in the row (e.g., "1517/367")
        for (elem in row.elements) {
            val slash = SLASH_PAIR.find(elem.text)
            if (slash != null) {
                val left = toFloat(slash.groupValues[1])
                val right = toFloat(slash.groupValues[2])
                if (left != null && right != null) {
                    assignByX(elem, colInfo, left, right, per100, perServing) { v -> v }
                    return
                }
            }
        }

        // Try adjacent element pairs: "262" + "kcal"
        for (j in row.elements.indices) {
            val elem = row.elements[j]
            val lower = elem.text.lowercase(Locale.getDefault())
            if (lower.endsWith("kcal") || lower == "kcal") {
                // Extract number from "262 kcal" or "K/52 kcal"
                val num = KCAL.find(elem.text)
                    ?: row.elements.getOrNull(j - 1)?.let { KCAL.find(it.text + " " + elem.text) }
                    ?: Regex("(\\d{2,4})").find(elem.text)
                num?.let {
                    val value = toFloat(it.groupValues[1]) ?: return@let
                    // Use the number element's position, not the "kcal" text element
                    val numElem = if (KCAL.find(elem.text) != null) elem
                        else row.elements.getOrNull(j - 1) ?: elem
                    if (numElem.centerX in colInfo.per100Left..colInfo.per100Right) {
                        per100.kcal = value
                    } else {
                        perServing.kcal = value
                    }
                }
            }
        }

        // Try adjacent element pairs: "2793" + "kJ/" or "kJ" — convert kJ to kcal
        for (j in row.elements.indices) {
            val elem = row.elements[j]
            val lower = elem.text.lowercase(Locale.getDefault())
            if (lower.endsWith("kj/") || lower == "kj" || lower.endsWith("kj")) {
                val num = KJ.find(elem.text)
                    ?: row.elements.getOrNull(j - 1)?.let { KJ.find(it.text + " " + elem.text) }
                    ?: Regex("(\\d{3,5})").find(elem.text)
                num?.let {
                    val value = toFloat(it.groupValues[1]) ?: return@let
                    val kcal = value / 4.184f
                    if (elem.centerX in colInfo.per100Left..colInfo.per100Right) {
                        if (per100.kcal == null) per100.kcal = kcal
                    } else {
                        if (perServing.kcal == null) perServing.kcal = kcal
                    }
                }
            }
        }

        // If still no kcal found, try standalone values (use raw number, no 200 limit)
        // Skip elements containing "k" or "kj" patterns — they're kJ, not kcal
        val per100Values = mutableListOf<Float>()
        val perServingValues = mutableListOf<Float>()
        for (elem in row.elements) {
            val lower = elem.text.lowercase(Locale.getDefault())
            if (lower.contains("kj") || lower.contains("k3") || lower.contains("ki")) continue
            val value = parseEnergyValue(elem.text) ?: continue
            if (elem.centerX in colInfo.per100Left..colInfo.per100Right) {
                per100Values.add(value)
            } else if (elem.centerX in colInfo.perServingLeft..colInfo.perServingRight) {
                perServingValues.add(value)
            }
        }
        if (per100.kcal == null && per100Values.isNotEmpty()) per100.kcal = per100Values.lastOrNull() ?: per100Values.first()
        if (perServing.kcal == null && perServingValues.isNotEmpty()) perServing.kcal = perServingValues.lastOrNull() ?: perServingValues.first()
    }

    private fun parseEnergyValue(text: String): Float? {
        val trimmed = text.trim()
        if (trimmed.lowercase(Locale.getDefault()) == "nil") return 0f
        var value = extractRawNumber(trimmed) ?: return null
        // >1000 kcal is impossible; likely kJ misread or missing decimal
        if (value > 1000f) value /= 10f
        if (value > 1000f) value /= 10f
        return if (value in 0f..1000f) value else null
    }

    /**
     * Extract a macro value from a data row using column positions.
     * Returns true if a value was extracted.
     */
    private fun extractMacroFromRow(
        row: OcrRow,
        colInfo: ColumnInfo,
        per100: MacroValuesBuilder,
        perServing: MacroValuesBuilder,
        alreadySet: (MacroValuesBuilder) -> Boolean
    ): Boolean {
        if (!alreadySet(per100)) return false

        val per100Values = mutableListOf<Float>()
        val perServingValues = mutableListOf<Float>()

        for (elem in row.elements) {
            val value = parseMacroValue(elem.text) ?: continue
            if (elem.centerX in colInfo.per100Left..colInfo.per100Right) {
                per100Values.add(value)
            } else if (elem.centerX in colInfo.perServingLeft..colInfo.perServingRight) {
                perServingValues.add(value)
            }
        }

        if (per100Values.isEmpty() && perServingValues.isEmpty()) return false

        // Assign the first value found in each column
        if (per100Values.isNotEmpty()) {
            when {
                per100.fat == null && alreadySet(per100) -> per100.fat = per100Values.first()
                per100.carbs == null -> per100.carbs = per100Values.first()
                per100.protein == null -> per100.protein = per100Values.first()
            }
        }
        if (perServingValues.isNotEmpty()) {
            when {
                perServing.fat == null -> perServing.fat = perServingValues.first()
                perServing.carbs == null -> perServing.carbs = perServingValues.first()
                perServing.protein == null -> perServing.protein = perServingValues.first()
            }
        }

        return true
    }

    private fun assignByX(
        elem: OcrElement,
        colInfo: ColumnInfo,
        leftVal: Float,
        rightVal: Float,
        per100: MacroValuesBuilder,
        perServing: MacroValuesBuilder,
        transform: (Float) -> Float
    ) {
        if (elem.centerX in colInfo.per100Left..colInfo.per100Right) {
            per100.kcal = transform(leftVal)
            perServing.kcal = transform(rightVal)
        } else {
            per100.kcal = transform(rightVal)
            perServing.kcal = transform(leftVal)
        }
    }

    // ── Strategy B: Single-column list (Y-proximity) ────────────────

    /**
     * Parse as a single-column list: find keyword elements, then find
     * the nearest numeric element below or beside each keyword.
     */
    private fun tryListParse(rows: List<OcrRow>): ParsedNutritionLabel? {
        val per100 = MacroValuesBuilder()
        val perServing: MacroValuesBuilder? = null

        // Find section headers
        val per100Start = findPer100Row(rows)
        val perServingStart = findPerServingRow(rows, per100Start)

        // Extract from per-100g section
        val per100Section = if (per100Start != null) {
            val end = if (perServingStart != null && perServingStart > per100Start) perServingStart else rows.size
            rows.subList(per100Start, end)
        } else {
            // No explicit per-100g header — treat all rows as per-100g
            rows
        }

        extractMacrosFromRows(per100Section, per100)

        // If no macros found in the per100 section, the "per 100g" header
        // might be at the bottom (a footnote). Retry with all rows.
        if (per100.kcal == null && per100.fat == null && per100.carbs == null && per100.protein == null
            && per100Start != null
        ) {
            extractMacrosFromRows(rows, per100)
        }

        // Extract from per-serving section
        val perServingBuilder = if (perServingStart != null) {
            val builder = MacroValuesBuilder()
            val perServSection = rows.subList(perServingStart, rows.size)
            extractMacrosFromRows(perServSection, builder)
            builder
        } else null

        // Also try to find energy in the section
        val per100Energy = findEnergyInRows(per100Section)
        val perServingEnergy = perServingStart?.let { findEnergyInRows(rows.subList(it, rows.size)) }

        val result100 = per100.build()?.let {
            it.copy(kcal = per100Energy ?: it.kcal)
        }
        val resultServing = perServingBuilder?.build()?.let {
            it.copy(kcal = perServingEnergy ?: it.kcal)
        }

        if (result100 == null && resultServing == null) return null

        return ParsedNutritionLabel(result100, resultServing, null, null)
    }

    /**
     * Find the row index containing "per 100g" or "per 100ml".
     */
    private fun findPer100Row(rows: List<OcrRow>): Int? {
        for (i in rows.indices) {
            val lower = rows[i].text.lowercase(Locale.getDefault())
            if (lower.contains("per 100") || lower.contains(Regex("\\b100\\s*g\\b"))) {
                return i
            }
        }
        return null
    }

    /**
     * Find the row index containing "per <not 100>" (serving header).
     */
    private fun findPerServingRow(rows: List<OcrRow>, afterIdx: Int? = null): Int? {
        val start = (afterIdx ?: 0) + 1
        for (i in start until rows.size) {
            val lower = rows[i].text.lowercase(Locale.getDefault())
            if (lower.contains("per ") && !lower.contains("per 100")) {
                return i
            }
        }
        return null
    }

    /**
     * Extract macro values from a list of rows using Y-proximity matching.
     * For each keyword found, look for the nearest numeric element in the
     * same row or the next row.
     */
    private fun extractMacrosFromRows(rows: List<OcrRow>, target: MacroValuesBuilder) {
        for (i in rows.indices) {
            val row = rows[i]
            val lower = row.text.lowercase(Locale.getDefault())

            // Skip context lines
            if (lower.startsWith("per ") || lower.contains("reference intake")) continue
            if (lower.contains("nutrition") || lower.contains("typical")) continue
            if (lower.contains("of which") || lower.contains("%ri")) continue

            when {
                FAT_KEYS.any { keywordMatches(lower, it) } && !isExcludedMacroRow(row) -> {
                    if (target.fat == null) target.fat = findNearestValue(rows, i)
                }
                CARB_KEYS.any { keywordMatches(lower, it) } -> {
                    if (target.carbs == null) target.carbs = findNearestValue(rows, i)
                }
                PROTEIN_KEYS.any { keywordMatches(lower, it) } -> {
                    if (target.protein == null) target.protein = findNearestValue(rows, i)
                }
            }
        }
    }

    /**
     * Find the nearest numeric value to a keyword row.
     * Checks the same row for an inline value, then scans up to 4 nearby rows.
     * Stops if it hits another keyword row (except "of which" sub-keywords).
     */
    private fun findNearestValue(rows: List<OcrRow>, keywordIdx: Int): Float? {
        val keywordRow = rows[keywordIdx]

        for (elem in keywordRow.elements) {
            val value = parseMacroValue(elem.text)
            if (value != null && !KEYWORD_PATTERNS.any { keywordMatches(elem.text.lowercase(Locale.getDefault()), it) }) {
                return value
            }
        }

        for (offset in 1..4) {
            if (keywordIdx + offset >= rows.size) break
            val nextRow = rows[keywordIdx + offset]
            val nextLower = nextRow.text.lowercase(Locale.getDefault())
            if (FAT_KEYS.any { keywordMatches(nextLower, it) } ||
                CARB_KEYS.any { keywordMatches(nextLower, it) } ||
                PROTEIN_KEYS.any { keywordMatches(nextLower, it) } ||
                ENERGY_KEYWORDS.any { keywordMatches(nextLower, it) }
            ) {
                if (!nextLower.contains("of which") && !nextLower.contains("saturat")) break
            }
            for (elem in nextRow.elements) {
                val value = parseMacroValue(elem.text)
                if (value != null) return value
            }
        }

        return null
    }

    private fun isExcludedMacroRow(row: OcrRow): Boolean {
        return row.elements.any { elem ->
            val lower = elem.text.lowercase(Locale.getDefault())
            lower.contains("saturat") || lower.contains("unsaturat") || lower.contains("trans") ||
                lower.contains("of which")
        }
    }

    // ── Energy extraction helpers ───────────────────────────────────

    private fun findEnergyInRows(rows: List<OcrRow>): Float? {
        // Pass 1: scan each row for kcal patterns
        for (row in rows) {
            for (elem in row.elements) {
                KCAL.find(elem.text)?.let { return toFloat(it.groupValues[1]) }
            }
            for (j in row.elements.indices) {
                val elem = row.elements[j]
                if (elem.text.lowercase(Locale.getDefault()).endsWith("kcal")) {
                    val prev = row.elements.getOrNull(j - 1)
                    if (prev != null) {
                        KCAL.find(prev.text + " " + elem.text)?.let { return toFloat(it.groupValues[1]) }
                    }
                }
            }
            for (elem in row.elements) {
                KJ.find(elem.text)?.let { kj ->
                    toFloat(kj.groupValues[1])?.let { return it / 4.184f }
                }
            }
        }
        // Pass 2: cross-row kJ → kcal pattern (kJ on row i, kcal on row i+1)
        for (i in rows.indices) {
            if (i + 1 >= rows.size) continue
            val hasKj = rows[i].elements.any { KJ.containsMatchIn(it.text) }
            val hasKcal = rows[i + 1].elements.any { KCAL.containsMatchIn(it.text) }
            if (hasKj && hasKcal) {
                for (elem in rows[i + 1].elements) {
                    KCAL.find(elem.text)?.let { return toFloat(it.groupValues[1]) }
                }
            }
        }
        // Pass 3: slash-separated kJ/kcal split across rows (e.g. "kJ/679" as one OCR element)
        for (i in rows.indices) {
            for (elem in rows[i].elements) {
                val lower = elem.text.lowercase(Locale.getDefault())
                if (lower.contains("kj") && lower.contains("/")) {
                    Regex("(\\d{1,4}(?:[.,]\\d{1,2})?)/").find(elem.text)?.let {
                        toFloat(it.groupValues[1])?.let { return it }
                    }
                }
            }
            // bare number row + "kJ" row + "kcal" row across 3 rows
            if (i + 2 < rows.size) {
                val hasKjRow = rows[i + 1].elements.any {
                    val l = it.text.lowercase(Locale.getDefault())
                    l.contains("kj") && !l.contains("kcal")
                }
                val hasKcalRow = rows[i + 2].elements.any { KCAL.containsMatchIn(it.text) }
                if (hasKjRow && hasKcalRow) {
                    for (elem in rows[i + 2].elements) {
                        KCAL.find(elem.text)?.let { return toFloat(it.groupValues[1]) }
                    }
                }
            }
        }
        return null
    }

    // ── Serving detection ───────────────────────────────────────────

    private fun findServingFromRows(rows: List<OcrRow>): Serving? {
        for (row in rows) {
            val lower = row.text.lowercase(Locale.getDefault())
            val hasRef = lower.contains("serving") || lower.contains("portion")
            if (!hasRef) continue
            for (elem in row.elements) {
                SERVING_UNIT.find(elem.text)?.let {
                    val amount = toFloat(it.groupValues[1])
                    if (amount != null && amount > 0f) {
                        val label = row.text.replace(Regex("\\s*[:()\\-].*$"), "").trim().take(40).ifBlank { null }
                        return Serving(amount, label)
                    }
                }
            }
        }
        return null
    }

    // ── Text-based fallback (old approach) ──────────────────────────

    private fun normalize(text: String): String =
        text.replace('\u00a0', ' ').replace("\u200b", "")

    private fun splitSections(lines: List<String>): Sections {
        val per100Start = lines.indexOfFirst { line ->
            val lower = line.lowercase(Locale.getDefault())
            lower.contains("per 100") || PER_100_G.containsMatchIn(lower)
        }

        val perServingStart = lines.indexOfFirst { line ->
            isPerServingHeader(line) && !line.lowercase(Locale.getDefault()).contains("per 100")
        }

        val per100Lines = if (per100Start >= 0) {
            val end = if (perServingStart > per100Start) perServingStart else lines.size
            lines.subList(per100Start, end)
        } else null

        val perServingLines = if (perServingStart >= 0) {
            lines.subList(perServingStart, lines.size)
        } else null

        return Sections(per100Lines, perServingLines)
    }

    private fun isPerServingHeader(line: String): Boolean {
        val lower = line.lowercase(Locale.getDefault())
        if (!lower.contains("per ")) return false
        if (lower.contains("per 100")) return false
        return PER_SERVING_GENERIC.containsMatchIn(lower)
    }

    private data class Sections(val per100: List<String>?, val perServing: List<String>?)

    private fun findEnergyInSection(lines: List<String>): Float? {
        for (line in lines) {
            KCAL.find(line)?.let { return toFloat(it.groupValues[1]) }
        }
        for (i in lines.indices) {
            if (i + 1 >= lines.size) continue
            if (KJ.find(lines[i]) != null && KCAL.find(lines[i + 1]) != null) {
                return toFloat(KCAL.find(lines[i + 1])!!.groupValues[1])
            }
        }
        for (line in lines) {
            KJ.find(line)?.let { kj ->
                toFloat(kj.groupValues[1])?.let { return it / 4.184f }
            }
        }
        return null
    }

    private fun extractMacrosFromSection(lines: List<String>): MacroValues {
        val usedLines = mutableSetOf<Int>()

        fun findInline(keys: List<String>): Pair<Float?, Int?> {
            for (i in lines.indices) {
                val line = lines[i]
                if (keys.none { line.contains(it, ignoreCase = true) }) continue
                if (isExcludedMacro(line)) continue
                parseMacroValue(line)?.let { return it to i }
                if (i + 1 < lines.size) {
                    parseMacroValue(lines[i + 1])?.let { return it to (i + 1) }
                }
            }
            return null to null
        }

        fun findForward(keys: List<String>): Pair<Float?, Int?> {
            for (i in lines.indices) {
                val line = lines[i]
                if (keys.none { line.contains(it, ignoreCase = true) }) continue
                if (isExcludedMacro(line)) continue
                for (j in 1..15) {
                    if (i + j >= lines.size) break
                    if (i + j in usedLines) continue
                    val nextLine = lines[i + j]
                    if (isContextLine(nextLine)) continue
                    parseMacroValue(nextLine)?.let { return it to (i + j) }
                }
            }
            return null to null
        }

        val (fatVal, fatLine) = findInline(FAT_KEYS)
        if (fatLine != null) usedLines.add(fatLine)
        val (carbsVal, carbsLine) = findInline(CARB_KEYS)
        if (carbsLine != null) usedLines.add(carbsLine)
        val (proteinVal, proteinLine) = findInline(PROTEIN_KEYS)
        if (proteinLine != null) usedLines.add(proteinLine)

        val fat = fatVal ?: findForward(FAT_KEYS).also { if (it.second != null) usedLines.add(it.second!!) }.first
        val carbs = carbsVal ?: findForward(CARB_KEYS).also { if (it.second != null) usedLines.add(it.second!!) }.first
        val protein = proteinVal ?: findForward(PROTEIN_KEYS).also { if (it.second != null) usedLines.add(it.second!!) }.first

        return MacroValues(kcal = null, fat = fat, carbs = carbs, protein = protein)
    }

    private fun isExcludedMacro(line: String): Boolean =
        line.contains("saturat", ignoreCase = true) ||
            line.contains("unsaturat", ignoreCase = true) ||
            line.contains("trans", ignoreCase = true)

    private fun isContextLine(line: String): Boolean {
        val lower = line.lowercase(Locale.getDefault())
        return lower.startsWith("per ") ||
            lower.contains("reference intake") ||
            lower.contains("of which") ||
            lower.contains("kJ") ||
            lower.contains("kcal") ||
            lower.contains("energy") ||
            lower.contains("typical") ||
            lower.contains("nutrition") ||
            lower.contains("%ri") ||
            lower.contains("% nrv")
    }

    private fun parseMacroValue(line: String): Float? {
        val trimmed = line.trim()
        val lower = trimmed.lowercase(Locale.getDefault())
        if (lower == "nil" || lower == "trace" || lower == "free") return 0f

        var value = extractRawNumber(trimmed) ?: return null

        // OCR missing-decimal heuristic: >100g per 100g is physically impossible
        if (value > 100f) value /= 10f
        if (value > 100f) value /= 10f

        return if (value in 0f..200f) value else null
    }

    private fun extractRawNumber(text: String): Float? {
        val normalized = normalizeOcrDigits(text)
        GRAMS.find(normalized)?.let { return toFloat(it.groupValues[1]) }
        MACRO_OCR_LETTER.find(normalized)?.let { return toFloat(it.groupValues[1]) }
        BARE_NUMBER.find(normalized)?.let { return toFloat(it.groupValues[1]) }
        return null
    }

    /**
     * Fix common OCR digit misreads: O/o instead of 0.
     * Only replaces when the character is in a position where a digit would be valid
     * (start of string, after digit/decimal, before digit/decimal/unit-suffix).
     */
    private fun normalizeOcrDigits(text: String): String {
        return text.replace(Regex("(?<=^|\\d|[.,])[Oo](?=\\d|[.,gml])"), "0")
    }

    private fun findServing(lines: List<String>): Serving? {
        for (line in lines) {
            val lower = line.lowercase(Locale.getDefault())
            val hasRef = lower.contains("serving") || lower.contains("portion")
            if (!hasRef) continue
            SERVING_UNIT.find(line)?.let {
                val amount = toFloat(it.groupValues[1])
                if (amount != null && amount > 0f) {
                    val label = line.replace(Regex("\\s*[:()\\-].*$"), "").trim().take(40).ifBlank { null }
                    return Serving(amount, label)
                }
            }
        }
        return null
    }

    private fun toFloat(raw: String): Float? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val cleaned = if (',' in s && '.' !in s) {
            val parts = s.split(',')
            if (parts.size == 2 && parts[1].length <= 2) parts[0] + "." + parts[1] else s.replace(",", "")
        } else {
            s.replace(",", "")
        }
        return cleaned.toFloatOrNull()
    }

    private data class Serving(val grams: Float, val label: String?)

    private class MacroValuesBuilder {
        var kcal: Float? = null
        var fat: Float? = null
        var carbs: Float? = null
        var protein: Float? = null

        fun build(): MacroValues? {
            if (kcal == null && fat == null && carbs == null && protein == null) return null
            return MacroValues(kcal, fat, carbs, protein)
        }
    }

    companion object {
        private val KCAL = Regex("(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*kcal", RegexOption.IGNORE_CASE)
        private val KJ = Regex("(\\d{2,5}(?:[.,]\\d{1,2})?)\\s*kj", RegexOption.IGNORE_CASE)
        private val GRAMS = Regex("(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*(?:g|grams?)(?!\\w)", RegexOption.IGNORE_CASE)
        private val MACRO_OCR_LETTER = Regex("(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*[a-zA-Z](?:\\s|$)", RegexOption.IGNORE_CASE)
        private val BARE_NUMBER = Regex("^(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*$")
        private val SERVING_UNIT = Regex("(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*(?:g|grams?|ml|millilitres?)(?!\\w)", RegexOption.IGNORE_CASE)
        private val PER_100_G = Regex("(\\b100\\s*g\\b)", RegexOption.IGNORE_CASE)
        private val PER_SERVING_GENERIC = Regex("per\\s+(?!100\\b)\\w+", RegexOption.IGNORE_CASE)
        private val SLASH_PAIR = Regex("(\\d{1,5}(?:[.,]\\d{1,2})?)\\s*[/|]\\s*(\\d{1,5}(?:[.,]\\d{1,2})?)")

        private val ENERGY_KEYWORDS = listOf("energy", "kcal", "kj")
        private val PROTEIN_KEYS = listOf("protein")
        private val CARB_KEYS = listOf("carbohydrate", "carb")
        private val FAT_KEYS = listOf("fat")
        private val KEYWORD_PATTERNS = ENERGY_KEYWORDS + PROTEIN_KEYS + CARB_KEYS + FAT_KEYS +
            listOf("saturat", "sugar", "fibre", "fiber", "salt", "sodium")

        /**
         * Check if [text] matches a keyword, tolerating minor OCR misspellings.
         * Uses prefix matching: if text starts with the first 4+ chars of keyword
         * and has roughly the right length, it's a match.
         */
        fun keywordMatches(text: String, keyword: String): Boolean {
            val lower = text.lowercase(Locale.getDefault())
            if (lower.contains(keyword)) return true
            // Prefix match: "Ptein" matches "protein", "Cartbohyd" matches "carbohydrate"
            val prefixLen = minOf(4, keyword.length)
            if (lower.length >= prefixLen && keyword.startsWith(lower.take(prefixLen))) return true
            // Check if keyword starts with text (text is a prefix of keyword)
            if (keyword.startsWith(lower) && lower.length >= 3) return true
            return false
        }
    }
}

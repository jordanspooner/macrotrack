package com.macrotrack.dbbuilder.parser

import java.util.Locale

/**
 * Parses human-readable serving-size strings from Open Food Facts (and similar
 * sources) into a normalised gram amount plus a clean label.
 *
 * Examples of input seen in the wild:
 *  - "100 g"
 *  - "1 bar (48 g)"
 *  - "1 slice (25g)"
 *  - "1/2 pack (200g)"
 *  - "250 ml"
 */
data class ParsedServingSize(
    val grams: Float,
    val label: String
)

class ServingSizeParser {

    /**
     * Returns the gram amount and a normalised label for the given raw serving
     * size string, or null if no quantity could be extracted.
     */
    fun parse(raw: String?): ParsedServingSize? {
        if (raw.isNullOrBlank()) return null

        val label = raw.trim().replace(Regex("\\s+"), " ")

        // Prefer an explicit quantity inside parentheses, e.g. "(48 g)".
        val explicit = QUANTITY_IN_PARENS.find(label) ?: LEADING_QUANTITY.find(label)
        val grams = explicit?.let { toGrams(it.groupValues[1], it.groupValues[2]) }

        return if (grams != null && grams > 0f) {
            ParsedServingSize(grams, label)
        } else {
            null
        }
    }

    /**
     * Convenience that returns just the gram amount, defaulting to [default]
     * when no quantity can be parsed.
     */
    fun parseGrams(raw: String?, default: Float = 100f): Float {
        return parse(raw)?.grams ?: default
    }

    private fun toGrams(quantity: String, unit: String): Float? {
        val base = parseQuantity(quantity) ?: return null
        val factor = when (unit.lowercase(Locale.getDefault())) {
            "ml" -> 1f // treat ml as ~1g for nutrition density
            "g", "gr", "gram", "grams" -> 1f
            "kg" -> 1000f
            "l", "litre", "litres", "liter", "liters" -> 1000f
            else -> 1f
        }
        return base * factor
    }

    private fun parseQuantity(quantity: String): Float? {
        val trimmed = quantity.trim()
        return if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            val num = parts.getOrNull(0)?.toFloatOrNull() ?: return null
            val den = parts.getOrNull(1)?.toFloatOrNull() ?: return null
            if (den == 0f) null else num / den
        } else {
            trimmed.toFloatOrNull()
        }
    }

    companion object {
        // A quantity + unit, optionally inside parentheses: "(48 g)", "(25g)", "(1/2 pack 200 g)".
        private val QUANTITY_IN_PARENS =
            Regex("\\(.*?(\\d+(?:\\.\\d+)?|\\d+\\s*/\\s*\\d+)\\s*(g|gr|gram|grams|ml|kg|l|litre|litres|liter|liters).*?\\)", RegexOption.IGNORE_CASE)

        // A leading "123 g" / "250ml" quantity at the start of the string.
        private val LEADING_QUANTITY =
            Regex("^(\\d+(?:\\.\\d+)?|\\d+\\s*/\\s*\\d+)\\s*(g|gr|gram|grams|ml|kg|l|litre|litres|liter|liters)\\b", RegexOption.IGNORE_CASE)
    }
}

package com.macrotrack.domain.search

import java.text.Normalizer

/**
 * Deterministic normalization and tokenization of user food-search text.
 *
 * Normalization applies, in order: Unicode lowercase, diacritic removal (NFD
 * decomposition + combining-mark strip), then punctuation and hyphens are
 * treated as separators and whitespace is collapsed to single spaces.
 *
 * [ftsPrefixQuery] builds a safe FTS5 MATCH string: every normalized token is
 * double-quoted and suffixed with `*` (e.g. `"chi"* "bre"*`), so reserved
 * words (`and`, `or`, `not`, `near`), column names, quotes and other FTS
 * operators can never alter query semantics. Returns `null` when there is
 * nothing safe to search (blank or all-punctuation input).
 */
object QueryNormalizer {

    private val separatorRegex = Regex("[^\\p{L}\\p{N}]+")
    private val combiningMarkRegex = Regex("\\p{M}+")
    private val whitespaceRegex = Regex("\\s+")

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val decomposed = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
        val deaccented = combiningMarkRegex.replace(decomposed, "")
        return whitespaceRegex.replace(separatorRegex.replace(deaccented, " ").trim(), " ")
    }

    fun tokenize(input: String): List<String> {
        val normalized = normalize(input)
        if (normalized.isEmpty()) return emptyList()
        return normalized.split(' ')
    }

    /**
     * Safe FTS5 prefix query, e.g. `"cheese"* "crack"*`, or `null` if the input
     * normalizes to no tokens at all.
     */
    fun ftsPrefixQuery(input: String): String? {
        val tokens = tokenize(input)
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "\"$it\"*" }
    }
}
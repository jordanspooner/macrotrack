package com.macrotrack.data.local.db

import com.macrotrack.domain.search.QueryNormalizer

/**
 * Builds the FTS5 MATCH string for the trigram (fuzzy) search path.
 *
 * The trigram tokenizer matches *substrings* of at least 3 characters, so a
 * bare token query like `"chickn"` only matches documents that literally
 * contain "chickn". To surface typo-tolerant candidates (e.g. "chickn" for
 * "Chicken breast"), each normalized query token of at least
 * [MIN_FUZZY_TOKEN_LENGTH] characters is expanded into the set of its distinct
 * 3-character trigrams, combined as an explicit OR group:
 *
 *   "chickn" -> ("chi" OR "hic" OR "ick" OR "ckn")
 *
 * Because trigram matching is substring-based, a document only needs to share
 * one trigram with a token to be a candidate ("chicken" shares "chi"/"hic"/"ick",
 * and a middle typo such as "mango" vs "tango" still shares "ang"/"ngo").
 * Groups are ANDed across tokens, e.g. `("chi" OR "hic" OR "ick" OR "ckn") AND
 * ("bre" OR "res" OR "est")`. The result is a safe MATCH string: it contains
 * only quoted, normalized trigrams and is always passed to SQL as a bound
 * argument, so it can never alter the surrounding query. The application-level
 * ranker is responsible for true edit-distance scoring over the candidates
 * returned by this path.
 *
 * Token normalization is delegated to [QueryNormalizer] so the fuzzy path folds
 * case, diacritics and punctuation exactly like the exact/prefix path; only
 * the trigram expansion ([trigrams]) and the quoted OR/AND grouping are
 * specific to this formatter.
 */
object FuzzyQueryFormatter {

    /** Trigram tokenizer cannot match substrings shorter than 3 characters. */
    const val MIN_FUZZY_TOKEN_LENGTH = 3

    /**
     * Returns a trigram MATCH string for [rawQuery], or `null` when the query
     * normalizes to no token of at least [MIN_FUZZY_TOKEN_LENGTH] characters.
     *
     * Token groups are combined with explicit `AND` (FTS5 does not insert an
     * implicit AND between parenthesized expressions).
     */
    fun format(rawQuery: String): String? {
        val tokens = QueryNormalizer.tokenize(rawQuery)
            .filter { it.length >= MIN_FUZZY_TOKEN_LENGTH }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" AND ") { token -> group(token) }
    }

    private fun group(token: String): String {
        return "(" + trigrams(token).joinToString(" OR ") { "\"$it\"" } + ")"
    }

    /** Distinct 3-character trigrams of [token], in occurrence order. */
    fun trigrams(token: String): List<String> {
        val result = mutableListOf<String>()
        for (start in 0..token.length - MIN_FUZZY_TOKEN_LENGTH) {
            val trigram = token.substring(start, start + MIN_FUZZY_TOKEN_LENGTH)
            if (trigram !in result) result.add(trigram)
        }
        return result
    }

    /** Delegates to the shared [QueryNormalizer]. */
    fun tokenize(input: String): List<String> = QueryNormalizer.tokenize(input)

    /** Delegates to the shared [QueryNormalizer]. */
    fun normalize(input: String): String = QueryNormalizer.normalize(input)
}
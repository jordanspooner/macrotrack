package com.macrotrack.data.local.db

import com.google.common.truth.Truth.assertThat
import com.macrotrack.domain.search.QueryNormalizer
import org.junit.Test

/**
 * Focused checks that [FuzzyQueryFormatter] delegates normalization to the
 * shared [QueryNormalizer] (so the exact/prefix and fuzzy search paths fold
 * case, diacritics and punctuation identically) while keeping its own
 * trigram-specific MATCH output and safe quoting.
 */
class FuzzyQueryFormatterTest {

    @Test
    fun `normalize delegates to the shared QueryNormalizer`() {
        val inputs = listOf(
            "Cheese Cracker",
            "APPLE PIE",
            "Café au Lait",
            "Nestlé Müsli",
            "Pão-de-queijo",
            "low-fat milk, 2%",
            "Ben & Jerry's",
            "fish* (chips)",
            "  a   b\t c\n ",
            "  Chick'n   BREST!! ",
            "",
            "   ",
        )
        for (input in inputs) {
            assertThat(FuzzyQueryFormatter.normalize(input))
                .isEqualTo(QueryNormalizer.normalize(input))
        }
    }

    @Test
    fun `tokenize delegates to the shared QueryNormalizer`() {
        val inputs = listOf("Café au Lait", "low-fat milk", "  ", "Chick'n BREST!!", "a b")
        for (input in inputs) {
            assertThat(FuzzyQueryFormatter.tokenize(input))
                .containsExactlyElementsIn(QueryNormalizer.tokenize(input))
                .inOrder()
        }
    }

    @Test
    fun `format matches QueryNormalizer tokens expanded to quoted trigram groups`() {
        val inputs = listOf(
            "Chick'n BREST",
            "Café au Lait",
            "mango",
            "  padded   query!! ",
            "a abc b",
            "say \"hi\" now",
            "  ",
            "a b",
        )
        for (input in inputs) {
            val expected = QueryNormalizer.tokenize(input)
                .filter { it.length >= FuzzyQueryFormatter.MIN_FUZZY_TOKEN_LENGTH }
                .joinToString(" AND ") { token ->
                    "(" + FuzzyQueryFormatter.trigrams(token).joinToString(" OR ") { "\"$it\"" } + ")"
                }
            assertThat(FuzzyQueryFormatter.format(input)).isEqualTo(expected.ifEmpty { null })
        }
    }

    @Test
    fun `trigram-specific output and safe quoting are preserved`() {
        assertThat(FuzzyQueryFormatter.format("  Chick'n   BREST!! "))
            .isEqualTo("(\"chi\" OR \"hic\" OR \"ick\") AND (\"bre\" OR \"res\" OR \"est\")")
        assertThat(FuzzyQueryFormatter.format("Café")).isEqualTo("(\"caf\" OR \"afe\")")
        assertThat(FuzzyQueryFormatter.format("say \"hi\" now")).isEqualTo("(\"say\") AND (\"now\")")
        assertThat(FuzzyQueryFormatter.format("chicken OR beef")).isEqualTo("(\"chi\" OR \"hic\" OR \"ick\" OR \"cke\" OR \"ken\") AND (\"bee\" OR \"eef\")")
        assertThat(FuzzyQueryFormatter.trigrams("chickn")).containsExactly("chi", "hic", "ick", "ckn").inOrder()
    }
}
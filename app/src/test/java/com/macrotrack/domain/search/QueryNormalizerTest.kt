package com.macrotrack.domain.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QueryNormalizerTest {

    @Test
    fun `normalize lowercases consistently`() {
        assertThat(QueryNormalizer.normalize("Cheese Cracker")).isEqualTo("cheese cracker")
        assertThat(QueryNormalizer.normalize("APPLE PIE")).isEqualTo("apple pie")
    }

    @Test
    fun `normalize removes unicode diacritics`() {
        assertThat(QueryNormalizer.normalize("Café au Lait")).isEqualTo("cafe au lait")
        assertThat(QueryNormalizer.normalize("Nestlé Müsli")).isEqualTo("nestle musli")
        assertThat(QueryNormalizer.normalize("Pão-de-queijo")).isEqualTo("pao de queijo")
    }

    @Test
    fun `normalize treats punctuation and hyphens as separators`() {
        assertThat(QueryNormalizer.normalize("low-fat milk, 2%")).isEqualTo("low fat milk 2")
        assertThat(QueryNormalizer.normalize("Ben & Jerry's")).isEqualTo("ben jerry s")
        assertThat(QueryNormalizer.normalize("fish* (chips)")).isEqualTo("fish chips")
    }

    @Test
    fun `normalize collapses whitespace`() {
        assertThat(QueryNormalizer.normalize("  a   b\t c\n ")).isEqualTo("a b c")
    }

    @Test
    fun `normalize handles blank input safely`() {
        assertThat(QueryNormalizer.normalize("")).isEqualTo("")
        assertThat(QueryNormalizer.normalize("   ")).isEqualTo("")
    }

    @Test
    fun `tokenize exposes normalized tokens`() {
        assertThat(QueryNormalizer.tokenize("Café au Lait"))
            .containsExactly("cafe", "au", "lait").inOrder()
        assertThat(QueryNormalizer.tokenize("low-fat")).containsExactly("low", "fat")
        assertThat(QueryNormalizer.tokenize("  ")).isEmpty()
    }

    @Test
    fun `fts prefix query quotes and stars each token`() {
        assertThat(QueryNormalizer.ftsPrefixQuery("cheese cracker"))
            .isEqualTo("\"cheese\"* \"cracker\"*")
        assertThat(QueryNormalizer.ftsPrefixQuery("Café")).isEqualTo("\"cafe\"*")
    }

    @Test
    fun `fts prefix query neutralizes operators quotes and punctuation`() {
        assertThat(QueryNormalizer.ftsPrefixQuery("chicken OR beef"))
            .isEqualTo("\"chicken\"* \"or\"* \"beef\"*")
        assertThat(QueryNormalizer.ftsPrefixQuery("say \"hi\" now"))
            .isEqualTo("\"say\"* \"hi\"* \"now\"*")
        assertThat(QueryNormalizer.ftsPrefixQuery("fish* AND (chips)"))
            .isEqualTo("\"fish\"* \"and\"* \"chips\"*")
        assertThat(QueryNormalizer.ftsPrefixQuery("name:brand")).isEqualTo("\"name\"* \"brand\"*")
    }

    @Test
    fun `fts prefix query includes one-character tokens`() {
        assertThat(QueryNormalizer.ftsPrefixQuery("a")).isEqualTo("\"a\"*")
        assertThat(QueryNormalizer.ftsPrefixQuery("a b")).isEqualTo("\"a\"* \"b\"*")
        assertThat(QueryNormalizer.ftsPrefixQuery("I ate 2 apples"))
            .isEqualTo("\"i\"* \"ate\"* \"2\"* \"apples\"*")
    }

    @Test
    fun `fts prefix query returns null for empty or punctuation-only input`() {
        assertThat(QueryNormalizer.ftsPrefixQuery("")).isNull()
        assertThat(QueryNormalizer.ftsPrefixQuery("   ")).isNull()
        assertThat(QueryNormalizer.ftsPrefixQuery("!?")).isNull()
        assertThat(QueryNormalizer.ftsPrefixQuery("ab")).isEqualTo("\"ab\"*")
    }
}
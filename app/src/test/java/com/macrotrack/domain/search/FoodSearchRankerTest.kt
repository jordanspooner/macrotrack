package com.macrotrack.domain.search

import com.google.common.truth.Truth.assertThat
import com.macrotrack.domain.model.FoodItem
import com.macrotrack.domain.model.Macros
import com.macrotrack.domain.model.Source
import org.junit.Test

class FoodSearchRankerTest {

    private val ranker = FoodSearchRanker()

    private fun food(id: Long, name: String, brand: String? = null) = FoodItem(
        id = id,
        source = Source.OPEN_FOOD_FACTS,
        brand = brand,
        name = name,
        macroPer100g = Macros(100f, 10f, 10f, 5f)
    )

    private fun rankIds(
        query: String,
        foods: List<FoodItem>,
        fuzzy: List<FoodItem> = emptyList(),
    ) = ranker.rank(query, foods, fuzzy).map { it.id }

    @Test
    fun `empty or blank query returns no results`() {
        assertThat(rankIds("", listOf(food(1, "Cheese")))).isEmpty()
        assertThat(rankIds("   ", listOf(food(1, "Cheese")))).isEmpty()
    }

    @Test
    fun `exact ranks above prefix above token above typo above brand-only`() {
        val exact = food(1, "Cheese")
        val prefix = food(2, "Cheesecake")
        val token = food(3, "Cream Cheese")
        val typo = food(4, "Chese Crackers")
        val brandOnly = food(5, "Vintage Cheddar", brand = "Cheese Co")
        val unrelated = food(6, "Apple Pie")

        assertThat(rankIds("cheese", listOf(unrelated, brandOnly, typo, token, prefix, exact)))
            .containsExactly(1L, 2L, 3L, 4L, 5L).inOrder()
    }

    @Test
    fun `substring matches rank below prefixes and above fuzzy`() {
        val prefix = food(1, "Cheese")
        val substring = food(2, "Macaroni Cheese")
        val typo = food(3, "Chese")

        assertThat(rankIds("chee", listOf(typo, substring, prefix)))
            .containsExactly(1L, 2L, 3L).inOrder()
    }

    @Test
    fun `name matches always outrank brand-only matches`() {
        val nameExact = food(1, "Apple", brand = "Acme")
        val namePrefix = food(2, "Applesauce", brand = "Acme")
        val nameFuzzy = food(3, "Aple Crisp", brand = "Acme")
        val brandExact = food(4, "Pie Filling", brand = "Apple")
        val brandFuzzy = food(5, "Pie Filling", brand = "Aple")

        assertThat(rankIds("apple", listOf(nameExact, namePrefix, nameFuzzy, brandExact, brandFuzzy)))
            .containsExactly(1L, 2L, 3L, 4L, 5L).inOrder()
    }

    @Test
    fun `multi token queries favour full coverage over partial`() {
        val exact = food(1, "Cheese Cracker")
        val bothPrefix = food(2, "Cheese Cracker Snacks")
        val oneToken = food(3, "Cheese Sandwich")

        assertThat(rankIds("cheese cracker", listOf(oneToken, bothPrefix, exact)))
            .containsExactly(1L, 2L, 3L).inOrder()
    }

    @Test
    fun `partial token coverage ranks by number of matched tokens`() {
        val twoTokens = food(1, "Cheese Cracker Bites")
        val oneToken = food(2, "Cheese Dip")

        assertThat(rankIds("crack chee", listOf(oneToken, twoTokens)))
            .containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `multi token queries with a typo still rank by coverage`() {
        val cracker = food(1, "Cheese Cracker")
        val sandwich = food(2, "Cheese Sandwich")

        assertThat(rankIds("chese crack", listOf(sandwich, cracker)))
            .containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `one and two character tokens never fuzzy match`() {
        assertThat(rankIds("cs", listOf(food(1, "Cheese")))).isEmpty()
        assertThat(rankIds("chs", listOf(food(1, "Cheese")))).isEmpty()
    }

    @Test
    fun `transposition and one edit typos fuzzy match`() {
        assertThat(rankIds("bannaa", listOf(food(1L, "Banana")))).containsExactly(1L)
        assertThat(rankIds("chese", listOf(food(2L, "Cheese")))).containsExactly(2L)
    }

    @Test
    fun `two edits are rejected without trigram overlap`() {
        assertThat(rankIds("chse", listOf(food(1, "Cheese")))).isEmpty()
    }

    @Test
    fun `fuzzy candidates never outrank exact or prefix results`() {
        val exact = food(1, "Cheese")
        val prefix = food(2, "Cheesecake")
        val fuzzy = food(3, "Chese Bites")

        assertThat(rankIds("cheese", listOf(exact, prefix), fuzzy = listOf(fuzzy)))
            .containsExactly(1L, 2L, 3L).inOrder()
    }

    @Test
    fun `separate fuzzy candidate list is merged and deduplicated by id`() {
        val viaFts = food(1, "Cheese")
        val fuzzy = food(2, "Chese")

        assertThat(rankIds("cheese", listOf(viaFts), fuzzy = listOf(fuzzy, viaFts)))
            .containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `logged ids break ties within equal relevance only`() {
        val a = food(1, "Cheese")
        val b = food(2, "Cheese")
        val prefix = food(3, "Cheesecake")

        assertThat(ranker.rank("cheese", listOf(a, b, prefix), loggedIds = setOf(2)).map { it.id })
            .containsExactly(2L, 1L, 3L).inOrder()
        assertThat(ranker.rank("cheese", listOf(prefix, b, a)).map { it.id })
            .containsExactly(1L, 2L, 3L).inOrder()
    }

    @Test
    fun `usage scores break ties within equal relevance only`() {
        val a = food(1, "Cheese")
        val b = food(2, "Cheese")
        val prefix = food(3, "Cheesecake")

        val ids = ranker.rank(
            "cheese",
            listOf(a, b, prefix),
            usageScores = mapOf(2L to 0.9, 1L to 0.1),
        ).map { it.id }
        assertThat(ids).containsExactly(2L, 1L, 3L).inOrder()
    }

    @Test
    fun `usage scores take precedence over logged ids`() {
        val a = food(1, "Cheese")
        val b = food(2, "Cheese")

        val ids = ranker.rank(
            "cheese",
            listOf(a, b),
            loggedIds = setOf(1L),
            usageScores = mapOf(2L to 0.8, 1L to 0.2),
        ).map { it.id }
        assertThat(ids).containsExactly(2L, 1L).inOrder()
    }

    @Test
    fun `heavily used fuzzy match never outranks a strong exact match`() {
        val exact = food(1, "Cheese")
        val fuzzy = food(2, "Chese Bites")

        val ids = ranker.rank(
            "cheese",
            listOf(exact),
            fuzzyCandidates = listOf(fuzzy),
            usageScores = mapOf(2L to 1.0),
        ).map { it.id }
        assertThat(ids).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `usage scores never cross tiers even when maxed`() {
        val exact = food(1, "Cheese")
        val prefix = food(2, "Cheesecake")
        val token = food(3, "Macaroni Cheese")
        val fuzzy = food(4, "Chese")

        val ids = ranker.rank(
            "cheese",
            listOf(exact, prefix, token, fuzzy),
            usageScores = mapOf(4L to 1.0, 3L to 1.0, 2L to 1.0),
        ).map { it.id }
        assertThat(ids).containsExactly(1L, 2L, 3L, 4L).inOrder()
    }

    @Test
    fun `shorter names win within equal relevance then id breaks final ties`() {
        val cheddar = food(1, "Cheddar Cheese")
        val cream = food(2, "Cream Cheese")
        val creamDup = food(3, "Cream Cheese")

        assertThat(rankIds("cheese", listOf(cheddar, cream))).containsExactly(2L, 1L).inOrder()
        assertThat(rankIds("cheese", listOf(cheddar, cream, creamDup)))
            .containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test
    fun `candidate names are normalized before matching`() {
        assertThat(rankIds("cafe au lait", listOf(food(1L, "Café au Lait")))).containsExactly(1L)
        assertThat(rankIds("low fat", listOf(food(2L, "Low-Fat Yogurt")))).containsExactly(2L)
        assertThat(rankIds("yogurt", listOf(food(3, "Yogurth")), fuzzy = listOf(food(4, "Yoğurt"))))
            .containsExactly(4L, 3L).inOrder()
    }

    @Test
    fun `ranking is pure and deterministic`() {
        val foods = listOf(
            food(4, "Cheesecake"),
            food(1, "Cheese"),
            food(3, "Cream Cheese"),
            food(2, "Chese"),
        )
        val first = rankIds("cheese", foods)
        val second = rankIds("cheese", foods.shuffled())
        assertThat(first).containsExactlyElementsIn(second).inOrder()
    }

    @Test
    fun `result set is capped at MAX_RESULTS even with more matching candidates`() {
        val foods = (1L..120L).map { food(it, "Cheese") }
        val results = ranker.rank("cheese", foods.shuffled()).map { it.id }
        assertThat(results).hasSize(FoodSearchRanker.MAX_RESULTS)
        assertThat(results).containsExactlyElementsIn(1L..FoodSearchRanker.MAX_RESULTS).inOrder()
    }
}
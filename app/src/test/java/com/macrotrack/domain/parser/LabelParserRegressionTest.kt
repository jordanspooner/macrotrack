package com.macrotrack.domain.parser

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Regression gate: feed real OCR elements through the structured parser and
 * assert that every label which currently parses correctly continues to do so.
 *
 * If any previously-passing label regresses, this test fails. To add a
 * newly-fixed label, add it to [passingLabels] with the expected per-100g values.
 *
 * Tolerance matches the analysis test: kcal ±15% (or ±2 for small values),
 * macros ±20% (or ±1 for small values).
 */
class LabelParserRegressionTest {

    private val parser = LabelParser()

    data class Expected(
        val filename: String,
        val kcal: Float,
        val fat: Float,
        val carbs: Float,
        val protein: Float,
    )

    /**
     * All 9 labels that currently parse fully correctly (all 4 macros match
     * ground truth within tolerance). If you fix a new label, add it here.
     */
    private val passingLabels = listOf(
        Expected("leerdammer-light.jpg", 262f, 16f, 0.1f, 30f),
        Expected("sabra-houmous-extra.jpg", 367f, 33f, 9f, 7f),
        Expected("tesco-semi-skimmed-milk.jpg", 50f, 1.8f, 4.8f, 3.6f),
        Expected("lurpak-spreadable.jpg", 679f, 75f, 0.6f, 0.5f),
        Expected("goodness-shakes-chocolate.jpg", 66f, 0.3f, 5f, 10.6f),
        Expected("roses-lime-cordial.jpg", 21f, 0f, 4.9f, 0f),
        Expected("skipjack-tuna-brine.jpg", 109f, 1f, 0f, 24.9f),
        Expected("mands-cheese-twists.jpg", 527f, 28.7f, 53.5f, 12.1f),
        Expected("lidl-halva.jpg", 569f, 38.4f, 40f, 13.5f),
    )

    private fun assertKcalClose(actual: Float?, expected: Float) {
        assertTrue("kcal: expected non-null", actual != null)
        val tolerance = if (expected < 5f) 2f else expected * 0.15f
        assertTrue(
            "kcal: parsed $actual not within tolerance of $expected (±$tolerance)",
            abs(actual!! - expected) <= tolerance
        )
    }

    private fun assertMacroClose(field: String, actual: Float?, expected: Float) {
        assertTrue("$field: expected non-null", actual != null)
        val tolerance = if (expected < 1f) 1f else expected * 0.20f
        assertTrue(
            "$field: parsed $actual not within tolerance of $expected (±$tolerance)",
            abs(actual!! - expected) <= tolerance
        )
    }

    @Test
    fun `all passing labels parse correctly`() {
        val failures = mutableListOf<String>()

        for (label in passingLabels) {
            val elements = realOcrElements[label.filename]
                ?: throw AssertionError("No OCR elements for ${label.filename}")

            val result = parser.parseStructured(elements)
            val per100 = result.per100

            val errors = mutableListOf<String>()

            try { assertKcalClose(per100?.kcal, label.kcal) }
            catch (e: AssertionError) { errors.add(e.message!!) }

            try { assertMacroClose("fat", per100?.fat, label.fat) }
            catch (e: AssertionError) { errors.add(e.message!!) }

            try { assertMacroClose("carbs", per100?.carbs, label.carbs) }
            catch (e: AssertionError) { errors.add(e.message!!) }

            try { assertMacroClose("protein", per100?.protein, label.protein) }
            catch (e: AssertionError) { errors.add(e.message!!) }

            if (errors.isNotEmpty()) {
                failures.add("${label.filename}:\n  ${errors.joinToString("\n  ")}")
            }
        }

        assertTrue(
            "Regression detected — the following labels no longer parse correctly:\n\n${failures.joinToString("\n\n")}",
            failures.isEmpty()
        )
    }
}

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
     * All 11 labels that currently parse fully correctly (all 4 macros match
     * ground truth within tolerance). If you fix a new label, add it here.
     */
    private val passingLabels = listOf(
        Expected("leerdammer-light.jpg", 262f, 16f, 0.1f, 30f),
        Expected("sabra-houmous-extra.jpg", 367f, 33f, 9f, 7f),
        Expected("tesco-semi-skimmed-milk.jpg", 50f, 1.8f, 4.8f, 3.6f),
        Expected("lurpak-spreadable.jpg", 679f, 75f, 0.6f, 0.5f),
        Expected("morrisons-lemon-juice.jpg", 34f, 0.5f, 7.1f, 0.4f),
        Expected("goodness-shakes-chocolate.jpg", 66f, 0.3f, 5f, 10.6f),
        Expected("roses-lime-cordial.jpg", 21f, 0f, 4.9f, 0f),
        Expected("skipjack-tuna-brine.jpg", 109f, 1f, 0f, 24.9f),
        Expected("mands-cheese-twists.jpg", 527f, 28.7f, 53.5f, 12.1f),
        Expected("pasta.jpg", 359f, 2f, 71f, 13f),
        Expected("lidl-halva.jpg", 569f, 38.4f, 40f, 13.5f),
    )

    /**
     * Labels that don't yet fully parse, but whose individual macros below
     * DO parse correctly (within tolerance) today. We assert exactly those
     * macros so a future change can't regress the values we already get
     * right, without over-claiming the not-yet-working ones.
     *
     * A null field means "not yet parsed correctly — don't assert it".
     */
    private val partialLabels = listOf(
        PartialExpected("sainsburys-soured-cream.jpg", protein = 2.3f),
        PartialExpected("british-chicken-breast.jpg", kcal = 156f, fat = 3.5f, protein = 30.8f),
        PartialExpected("pepsi-max-cherry.jpg", kcal = 0.6f, fat = 0f, carbs = 0f),
        PartialExpected("gochujang.jpg", kcal = 227f, fat = 2.8f, protein = 6.4f),
        PartialExpected("sainsburys-lemon-curd.jpg", carbs = 60.3f, protein = 0.8f),
        PartialExpected("frenchs-yellow-mustard.jpg", kcal = 85f),
        PartialExpected("lidl-cooking-oil-spray.jpg", kcal = 464f, fat = 50.9f, carbs = 1.4f),
    )

    data class PartialExpected(
        val filename: String,
        val kcal: Float? = null,
        val fat: Float? = null,
        val carbs: Float? = null,
        val protein: Float? = null,
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

    @Test
    fun `partially parsed labels keep their correct values`() {
        val failures = mutableListOf<String>()

        for (label in partialLabels) {
            val elements = realOcrElements[label.filename]
                ?: throw AssertionError("No OCR elements for ${label.filename}")

            val result = parser.parseStructured(elements)
            val per100 = result.per100

            val errors = mutableListOf<String>()

            label.kcal?.let {
                try { assertKcalClose(per100?.kcal, it) }
                catch (e: AssertionError) { errors.add(e.message!!) }
            }
            label.fat?.let {
                try { assertMacroClose("fat", per100?.fat, it) }
                catch (e: AssertionError) { errors.add(e.message!!) }
            }
            label.carbs?.let {
                try { assertMacroClose("carbs", per100?.carbs, it) }
                catch (e: AssertionError) { errors.add(e.message!!) }
            }
            label.protein?.let {
                try { assertMacroClose("protein", per100?.protein, it) }
                catch (e: AssertionError) { errors.add(e.message!!) }
            }

            if (errors.isNotEmpty()) {
                failures.add("${label.filename}:\n  ${errors.joinToString("\n  ")}")
            }
        }

        assertTrue(
            "Regression detected — the following partially-parsed labels lost values they used to get right:\n\n${failures.joinToString("\n\n")}",
            failures.isEmpty()
        )
    }
}

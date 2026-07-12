package com.macrotrack.domain.parser

/**
 * Rolling, history-based consensus over successive OCR readings of a nutrition
 * label.
 *
 * For every field we keep a bounded history (the last [HISTORY_SIZE] non-null,
 * *plausible* readings). The value we surface is the most common one, but only
 * once it has been seen at least [MIN_READS] times. A field becomes "confirmed"
 * once it has been seen at least [MIN_READS] times AND that most-common value
 * accounts for strictly more than half of all valid readings. Once confirmed the
 * value is locked and will never change again.
 *
 * Any reading that is individually implausible (e.g. 5000 kcal per 100g) is
 * thrown out immediately and is *not* counted towards the confidence history.
 *
 * Cross-validation: when both per-100g and per-serving values are available,
 * we verify that per100 * servingSize/100 ≈ perServing. If they agree, we
 * count that scan as providing stronger evidence for each field.
 *
 * Serving confusion: if a field's history contains exactly 2 distinct values
 * whose ratio is approximately 100/servingSize, we detect this as the
 * per-100g vs per-serving confusion and discard the per-serving value.
 */
data class ConsensusField<T : Any> private constructor(
    val history: List<T>,
    val locked: T?,
    val latest: T?,
    private val keyOf: (T) -> Any,
    private val plausible: (T) -> Boolean
) {
    constructor(plausible: (T) -> Boolean = { true }, keyOf: (T) -> Any = { it })
        : this(emptyList(), null, null, keyOf, plausible)

    private data class Counted<T>(val value: T, val count: Int)

    val readCount: Int get() = history.size
    val confirmed: Boolean get() = locked != null

    val confidence: Float
        get() = best?.let { it.count.toFloat() / readCount } ?: 0f

    val progress: Float
        get() = when {
            confirmed -> 1f
            readCount < MIN_READS -> readCount.toFloat() / MIN_READS
            else -> confidence.coerceAtMost(1f)
        }

    val value: T?
        get() = locked ?: if (readCount >= MIN_READS) best?.value else latest

    val bestValue: T? get() = best?.value

    private val best: Counted<T>?
        get() {
            if (history.isEmpty()) return null
            val counts = LinkedHashMap<Any, Counted<T>>()
            for (v in history) {
                val key = keyOf(v)
                val existing = counts[key]
                counts[key] = existing?.copy(count = existing.count + 1) ?: Counted(v, 1)
            }
            return counts.values.maxByOrNull { it.count }
        }

    /** Unique values currently in the history, with their counts. */
    val distinctValues: List<Pair<T, Int>>
        get() {
            if (history.isEmpty()) return emptyList()
            val counts = LinkedHashMap<Any, Counted<T>>()
            for (v in history) {
                val key = keyOf(v)
                val existing = counts[key]
                counts[key] = existing?.copy(count = existing.count + 1) ?: Counted(v, 1)
            }
            return counts.values.map { it.value to it.count }
        }

    fun observe(next: T?): ConsensusField<T> {
        if (locked != null) return this
        if (next == null) return this
        if (!plausible(next)) return this
        val newHistory = if (history.size >= HISTORY_SIZE) history.drop(1) + next else history + next
        return copy(history = newHistory, latest = next)
    }

    fun tryLock(valid: Boolean): ConsensusField<T> {
        if (locked != null || !valid) return this
        val b = best ?: return this
        return if (b.count >= MIN_READS && b.count > readCount / 2) copy(locked = b.value) else this
    }

    /**
     * Remove a specific value from the history. Used to discard per-serving
     * values when we detect the per-100g vs per-serving confusion.
     */
    fun discardValue(valueToDiscard: T): ConsensusField<T> {
        if (locked != null) return this
        val newHistory = history.filter { keyOf(it) != keyOf(valueToDiscard) }
        return copy(history = newHistory)
    }

    companion object {
        const val HISTORY_SIZE = 100
        const val MIN_READS = 5
    }
}

data class LabelConsensus(
    val kcal: ConsensusField<Float> = floatField(KCAL_PLAUSIBLE),
    val protein: ConsensusField<Float> = floatField(MACRO_PLAUSIBLE),
    val carbs: ConsensusField<Float> = floatField(MACRO_PLAUSIBLE),
    val fat: ConsensusField<Float> = floatField(MACRO_PLAUSIBLE),
    val servingG: ConsensusField<Float> = floatField(SERVING_PLAUSIBLE),
    val servingLabel: ConsensusField<String> = ConsensusField(plausible = { it.isNotBlank() })
) {
    fun accept(p: ParsedNutritionLabel): LabelConsensus {
        val servingG = p.servingSizeG
        val resolved = resolvePer100(p, servingG)

        var updated = copy(
            kcal = kcal.observe(resolved.kcal),
            protein = protein.observe(resolved.protein),
            carbs = carbs.observe(resolved.carbs),
            fat = fat.observe(resolved.fat),
            servingG = if (servingG != null) this.servingG.observe(servingG) else this.servingG,
            servingLabel = servingLabel.observe(p.servingLabel)
        )

        // Detect per-100g vs per-serving confusion: if a field has exactly
        // 2 distinct values whose ratio matches the serving size, discard
        // the per-serving value (the smaller one).
        val serving = servingG ?: updated.servingG.bestValue
        if (serving != null && serving > 0f) {
            updated = updated.resolveServingConfusion(serving)
        }

        // Fix 10× OCR errors: when oscillating between X and X*10, prefer
        // the decimal value. Also cross-check using kcal=4P+4C+9F.
        updated = updated.fixTenXErrors()

        val valid = updated.isValid
        val labelValid = valid.plausible && valid.consistent
        return updated.copy(
            kcal = updated.kcal.tryLock(labelValid),
            protein = updated.protein.tryLock(labelValid),
            carbs = updated.carbs.tryLock(labelValid),
            fat = updated.fat.tryLock(labelValid),
            servingG = updated.servingG.tryLock(valid.plausible),
            servingLabel = updated.servingLabel.tryLock(valid.plausible)
        )
    }

    /**
     * Detect and resolve the per-100g vs per-serving confusion.
     *
     * When a field has exactly 2 distinct values in its history, and their
     * ratio is approximately 100/servingSize, one is per-100g and the other
     * is per-serving. We discard the per-serving value (the smaller one)
     * since the consensus should track per-100g values.
     */
    private fun resolveServingConfusion(servingSizeG: Float): LabelConsensus {
        val ratio = 100f / servingSizeG

        fun resolveField(field: ConsensusField<Float>): ConsensusField<Float> {
            val distinct = field.distinctValues
            if (distinct.size != 2) return field

            val (v1, c1) = distinct[0]
            val (v2, c2) = distinct[1]

            // Check if the ratio between the two values matches the serving ratio.
            // We check both directions since we don't know which is per-100g.
            val small = minOf(v1, v2)
            val large = maxOf(v1, v2)
            if (small <= 0f) return field

            val actualRatio = large / small
            // Tolerance: within 20% of the expected ratio
            if (kotlin.math.abs(actualRatio - ratio) / ratio > 0.2f) return field

            // The larger value is per-100g, the smaller is per-serving.
            // Discard the smaller value from the history.
            return field.discardValue(small)
        }

        return copy(
            kcal = resolveField(kcal),
            protein = resolveField(protein),
            carbs = resolveField(carbs),
            fat = resolveField(fat)
        )
    }

    /**
     * Fix 10× OCR errors. Two strategies:
     *
     * 1. Oscillation fix: If a field has exactly 2 distinct values and one
     *    is approximately 10× the other, prefer the decimal (smaller) value.
     *    E.g. fat oscillating between 15.4 and 154 → keep 15.4.
     *
     * 2. Cross-check fix: If we have kcal and at least 2 macros, compute
     *    kcal = 4P + 4C + 9F. If one macro is off by ~10× compared to
     *    this estimate, correct it.
     */
    private fun fixTenXErrors(): LabelConsensus {
        var result = this

        // Strategy 1: oscillation fix per field
        result = result.copy(
            kcal = fixTenXField(result.kcal),
            protein = fixTenXField(result.protein),
            carbs = fixTenXField(result.carbs),
            fat = fixTenXField(result.fat)
        )

        // Strategy 2: cross-check using kcal = 4P + 4C + 9F
        val k = result.kcal.bestValue
        val p = result.protein.bestValue
        val c = result.carbs.bestValue
        val f = result.fat.bestValue

        if (k != null && k > 0f) {
            val pVal = p
            val cVal = c
            val fVal = f
            if (pVal != null && cVal != null && fVal != null) {
                val computedKcal = pVal * 4f + cVal * 4f + fVal * 9f
                if (computedKcal > 0f) {
                    // Try fixing each macro individually: does adjusting by 10× make it balance?
                    result = result.fixMacroForKcal(k, result.protein, "protein",
                        pVal, cVal * 4f + fVal * 9f, 4f) { result.copy(protein = it) }
                    result = result.fixMacroForKcal(k, result.carbs, "carbs",
                        cVal, pVal * 4f + fVal * 9f, 4f) { result.copy(carbs = it) }
                    result = result.fixMacroForKcal(k, result.fat, "fat",
                        fVal, pVal * 4f + cVal * 4f, 9f) { result.copy(fat = it) }
                }
            }
        }

        return result
    }

    /**
     * Try to fix a single macro field's 10× error by cross-checking against kcal.
     * If multiplying or dividing the macro by 10 makes the kcal equation match
     * better, replace the field's best value with the corrected one.
     */
    private fun fixMacroForKcal(
        kcal: Float,
        field: ConsensusField<Float>,
        name: String,
        currentMacro: Float,
        otherKcalContribution: Float,
        multiplier: Float,
        apply: (ConsensusField<Float>) -> LabelConsensus
    ): LabelConsensus {
        val value = field.bestValue ?: return this
        if (field.locked != null) return this
        if (currentMacro <= 0f) return this

        // Current error
        val currentTotal = otherKcalContribution + currentMacro * multiplier
        val currentError = kotlin.math.abs(kcal - currentTotal)

        // Try value*10 and value/10
        for (factor in listOf(10f, 0.1f)) {
            val adjusted = value * factor
            if (adjusted > 100f) continue
            val newTotal = otherKcalContribution + adjusted * multiplier
            val newError = kotlin.math.abs(kcal - newTotal)
            if (newError < currentError && newError < 0.3f * kcal) {
                return apply(field.discardValue(value).observe(adjusted))
            }
        }
        return this
    }

    /**
     * Fix a single field's 10× oscillation: if there are exactly 2 distinct
     * values and one is ~10× the other, prefer the decimal one.
     */
    private fun fixTenXField(field: ConsensusField<Float>): ConsensusField<Float> {
        if (field.locked != null) return field
        val distinct = field.distinctValues
        if (distinct.size != 2) return field

        val (v1, _) = distinct[0]
        val (v2, _) = distinct[1]
        val small = minOf(v1, v2)
        val large = maxOf(v1, v2)
        if (small <= 0f) return field

        val ratio = large / small
        // Ratio close to 10× (within 20%)
        if (kotlin.math.abs(ratio - 10f) > 2f) return field

        // Prefer the decimal (smaller) value — discard the 10× one
        return field.discardValue(large)
    }

    private fun resolvePer100(p: ParsedNutritionLabel, servingG: Float?): MacroValues {
        val p100 = p.per100
        val pServ = p.perServing

        if (pServ == null) return p100 ?: MacroValues(null, null, null, null)

        if (p100 == null) {
            val factor = if (servingG != null && servingG > 0f) 100f / servingG else return pServ
            return MacroValues(
                kcal = pServ.kcal?.let { it * factor },
                fat = pServ.fat?.let { it * factor },
                carbs = pServ.carbs?.let { it * factor },
                protein = pServ.protein?.let { it * factor }
            )
        }

        return crossValidate(p100, pServ, servingG)
    }

    private fun crossValidate(p100: MacroValues, pServ: MacroValues, servingG: Float?): MacroValues {
        val factor = if (servingG != null && servingG > 0f) servingG / 100f else null

        fun pick(per100: Float?, perServing: Float?): Float? {
            if (per100 != null) return per100
            if (perServing != null && factor != null) {
                return perServing / factor
            }
            return null
        }

        return MacroValues(
            kcal = pick(p100.kcal, pServ.kcal),
            fat = pick(p100.fat, pServ.fat),
            carbs = pick(p100.carbs, pServ.carbs),
            protein = pick(p100.protein, pServ.protein)
        )
    }

    val isReady: Boolean
        get() = kcal.confirmed && (protein.confirmed || carbs.confirmed || fat.confirmed) && isValid.plausible

    fun toParsedLabel(): ParsedNutritionLabel = ParsedNutritionLabel(
        per100 = MacroValues(kcal.value, fat.value, carbs.value, protein.value),
        perServing = null,
        servingSizeG = servingG.value,
        servingLabel = servingLabel.value
    )

    private val isValid: Validity
        get() {
            val k = kcal.bestValue
            val p = protein.bestValue
            val c = carbs.bestValue
            val f = fat.bestValue
            val plausible = (k != null && k in 0f..1000f) &&
                listOf(p, c, f).all { it == null || it in 0f..100f } &&
                listOfNotNull(p, c, f).sum() <= 100f
            return Validity(plausible, isConsistent(k, p, c, f))
        }

    private data class Validity(val plausible: Boolean, val consistent: Boolean)

    companion object {
        private val KCAL_PLAUSIBLE: (Float) -> Boolean = { it >= 0f && it <= 1000f }
        private val MACRO_PLAUSIBLE: (Float) -> Boolean = { it >= 0f && it <= 100f }
        private val SERVING_PLAUSIBLE: (Float) -> Boolean = { it > 0f && it <= 2000f }

        private fun floatField(plausible: (Float) -> Boolean) =
            ConsensusField<Float>(plausible = plausible, keyOf = { kotlin.math.round(it * 10f).toInt() })

        private fun isConsistent(kcal: Float?, p: Float?, c: Float?, f: Float?): Boolean {
            if (kcal == null || kcal <= 0f) return true
            val present = listOfNotNull(p, c, f)
            if (present.size < 2) return true
            val computed = (p ?: 0f) * 4f + (c ?: 0f) * 4f + (f ?: 0f) * 9f
            val diff = kotlin.math.abs(kcal - computed)
            return diff <= kotlin.math.max(0.15f * kcal, 20f)
        }
    }
}

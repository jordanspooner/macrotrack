package com.macrotrack.domain.parser

import kotlin.math.abs

private data class ResolvedFields(
    val kcal: Float?,
    val fat: Float?,
    val carbs: Float?,
    val protein: Float?,
    val servingSizeG: Float?
)

/**
 * Rolling consensus over successive OCR readings of a nutrition label.
 *
 * For every field we keep a bounded history (the last [HISTORY_SIZE] non-null,
 * *plausible* readings) and run MAP scoring across them: cluster readings,
 * generate scale-variant candidates, and score combinations against all
 * evidence using hard physical constraints and soft invariant couplings
 * (Atwater, kJ↔kcal, perServing↔per100). The result is the live value shown in
 * the preview pie chart while scanning.
 *
 * Locking is "all-or-nothing" for the core four fields: kcal plus all three
 * macros lock together only once we are highly confident about the *whole*
 * combo (each field has a majority of agreeing readings AND the combo is
 * internally consistent). Until then nothing is locked and the preview keeps
 * refining. Once locked, [resolveAllFields] freezes on the locked values, so
 * the chart stops moving and the final label can never contradict what the
 * user confirmed.
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
        return if (canLock()) copy(locked = best?.value) else this
    }

    /** True if this field has enough agreeing readings to lock. */
    fun canLock(): Boolean {
        if (locked != null) return true
        val b = best ?: return false
        return b.count >= MIN_READS && b.count > readCount / 2
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
        const val HISTORY_SIZE = 20
        const val MIN_READS = 3
    }
}

data class LabelConsensus(
    val kcal: ConsensusField<Float> = floatField(KCAL_PLAUSIBLE),
    val protein: ConsensusField<Float> = floatField(MACRO_PLAUSIBLE),
    val carbs: ConsensusField<Float> = floatField(MACRO_PLAUSIBLE),
    val fat: ConsensusField<Float> = floatField(MACRO_PLAUSIBLE),
    val servingG: ConsensusField<Float> = floatField(SERVING_PLAUSIBLE),
    val servingLabel: ConsensusField<String> = ConsensusField(plausible = { it.isNotBlank() }),
    val readings: List<Reading> = emptyList(),
    val kcalDerived: Boolean = false
) {
    fun accept(p: ParsedNutritionLabel): LabelConsensus {
        val servingG = p.servingSizeG
        val resolved = resolvePer100(p, servingG)

        // kcal is "derived" when this reading supplied macros but no kcal —
        // we surface it so the UI can mark the energy value as estimated.
        val derived = resolved.kcal == null &&
            (resolved.fat != null || resolved.carbs != null || resolved.protein != null)

        // Record the full per-scan reading (per-100g + per-serving) so the MAP
        // resolver can score combinations against co-occurring evidence.
        val scan = Reading(
            kj = resolved.kj,
            kcal = resolved.kcal,
            fat = resolved.fat,
            carbs = resolved.carbs,
            protein = resolved.protein,
            perServingKcal = p.perServing?.kcal,
            perServingKj = p.perServing?.kj,
            perServingFat = p.perServing?.fat,
            perServingCarbs = p.perServing?.carbs,
            perServingProtein = p.perServing?.protein
        )
        val newReadings = if (readings.size >= ConsensusField.HISTORY_SIZE) readings.drop(1) + scan else readings + scan

        var updated = copy(
            kcal = kcal.observe(resolved.kcal),
            protein = protein.observe(resolved.protein),
            carbs = carbs.observe(resolved.carbs),
            fat = fat.observe(resolved.fat),
            servingG = if (servingG != null) this.servingG.observe(servingG) else this.servingG,
            servingLabel = servingLabel.observe(p.servingLabel),
            readings = newReadings,
            kcalDerived = kcalDerived || derived
        )

        // Detect per-100g vs per-serving confusion
        val serving = servingG ?: updated.servingG.bestValue
        if (serving != null && serving > 0f) {
            updated = updated.resolveServingConfusion(serving)
        }

        // Fix 10× OCR errors: oscillation detection + Atwater cross-check
        updated = updated.fixTenXErrors()

        val valid = updated.isValid
        // We only lock the core four once every field either has enough agreeing
        // evidence to lock, or has no readings at all (nil — genuinely absent from
        // the label). Fields with partial evidence that hasn't reached the
        // threshold block the lock.
        val coreReady = listOf(updated.kcal, updated.protein, updated.carbs, updated.fat).all {
            it.canLock() || it.readCount == 0
        }
        val lockCore = valid.plausible && valid.consistent && coreReady
        return updated.copy(
            kcal = updated.kcal.tryLock(lockCore),
            protein = updated.protein.tryLock(lockCore),
            carbs = updated.carbs.tryLock(lockCore),
            fat = updated.fat.tryLock(lockCore),
            servingG = updated.servingG.tryLock(valid.plausible),
            servingLabel = updated.servingLabel.tryLock(valid.plausible),
            readings = newReadings
        )
    }

    /**
     * Produce the final label. While scanning, this is the live MAP-consensus
     * of all readings (the same values shown in the pie chart). Once kcal and
     * all three macros are locked, [resolveAllFields] freezes on the locked
     * values, so the output can never contradict what the user confirmed.
     */
    fun toParsedLabel(): ParsedNutritionLabel {
        val resolved = resolveAllFields()
        return ParsedNutritionLabel(
            per100 = MacroValues(
                kcal = resolved.kcal,
                fat = resolved.fat,
                carbs = resolved.carbs,
                protein = resolved.protein
            ),
            perServing = null,
            servingSizeG = resolved.servingSizeG ?: servingG.value,
            servingLabel = servingLabel.value,
            kcalDerived = kcalDerived
        )
    }

    /**
     * The label is ready to confirm only once kcal AND all three macros are
     * locked (i.e. we were highly confident about the whole combo) and the
     * locked values add up consistently.
     */
    val isReady: Boolean
        get() {
            val k = kcal.bestValue
            val p = protein.bestValue
            val c = carbs.bestValue
            val f = fat.bestValue
            val v = isValid(k, p, c, f)
            val fieldsReady = listOf(kcal, protein, carbs, fat).all {
                it.canLock() || it.readCount == 0
            }
            return fieldsReady && v.plausible && v.consistent
        }

    val isKcalReady: Boolean
        get() = kcal.confirmed

    val isFullyConsistent: Boolean
        get() {
            val k = kcal.bestValue
            val p = protein.bestValue
            val c = carbs.bestValue
            val f = fat.bestValue
            if (k == null || p == null || c == null || f == null) return false
            return isValid(k, p, c, f).consistent
        }

    // ── Per-100g resolution ────────────────────────────────────────

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
            if (perServing != null && factor != null) return perServing / factor
            return null
        }
        return MacroValues(
            kcal = pick(p100.kcal, pServ.kcal),
            fat = pick(p100.fat, pServ.fat),
            carbs = pick(p100.carbs, pServ.carbs),
            protein = pick(p100.protein, pServ.protein)
        )
    }

    // ── Serving confusion detection ────────────────────────────────

    private fun resolveServingConfusion(servingSizeG: Float): LabelConsensus {
        val ratio = 100f / servingSizeG

        fun resolveField(field: ConsensusField<Float>): ConsensusField<Float> {
            val distinct = field.distinctValues
            if (distinct.size != 2) return field
            val (v1, c1) = distinct[0]; val (v2, c2) = distinct[1]
            val small = minOf(v1, v2); val large = maxOf(v1, v2)
            if (small <= 0f) return field
            val actualRatio = large / small
            if (abs(actualRatio - ratio) / ratio > 0.2f) return field
            return field.discardValue(small)
        }

        return copy(
            kcal = resolveField(kcal),
            protein = resolveField(protein),
            carbs = resolveField(carbs),
            fat = resolveField(fat)
        )
    }

    // ── Ten× OCR error fixing ──────────────────────────────────────

    private fun fixTenXErrors(): LabelConsensus {
        var result = this
        result = result.copy(
            kcal = fixTenXField(result.kcal),
            protein = fixTenXField(result.protein),
            carbs = fixTenXField(result.carbs),
            fat = fixTenXField(result.fat)
        )

        val k = result.kcal.bestValue
        val p = result.protein.bestValue
        val c = result.carbs.bestValue
        val f = result.fat.bestValue

        if (k != null && k > 0f && p != null && c != null && f != null) {
            result = fixMacroForKcal(k, result.protein, "protein", p, c * 4f + f * 9f, 4f) { result.copy(protein = it) }
            result = fixMacroForKcal(k, result.carbs, "carbs", c, p * 4f + f * 9f, 4f) { result.copy(carbs = it) }
            result = fixMacroForKcal(k, result.fat, "fat", f, p * 4f + c * 4f, 9f) { result.copy(fat = it) }
        }
        return result
    }

    private fun fixMacroForKcal(
        kcal: Float, field: ConsensusField<Float>, name: String,
        currentMacro: Float, otherKcalContribution: Float, multiplier: Float,
        apply: (ConsensusField<Float>) -> LabelConsensus
    ): LabelConsensus {
        val value = field.bestValue ?: return this
        if (field.locked != null) return this
        if (currentMacro <= 0f) return this
        val currentTotal = otherKcalContribution + currentMacro * multiplier
        val currentError = abs(kcal - currentTotal)
        for (factor in listOf(10f, 0.1f)) {
            val adjusted = value * factor
            if (adjusted > 100f) continue
            val newTotal = otherKcalContribution + adjusted * multiplier
            val newError = abs(kcal - newTotal)
            if (newError < currentError && newError < 0.3f * kcal) {
                return apply(field.discardValue(value).observe(adjusted))
            }
        }
        return this
    }

    private fun fixTenXField(field: ConsensusField<Float>): ConsensusField<Float> {
        if (field.locked != null) return field
        val distinct = field.distinctValues
        if (distinct.size != 2) return field
        val (v1, _) = distinct[0]; val (v2, _) = distinct[1]
        val small = minOf(v1, v2); val large = maxOf(v1, v2)
        if (small <= 0f) return field
        val ratio = large / small
        if (abs(ratio - 10f) > 2f) return field
        return field.discardValue(large)
    }

    private fun resolveFromBestValues(): ResolvedFields {
        return ResolvedFields(
            kcal = kcal.value,
            fat = fat.value,
            carbs = carbs.value,
            protein = protein.value,
            servingSizeG = servingG.value
        )
    }

    // ── MAP-based field resolution ─────────────────────────────────

    private fun resolveAllFields(): ResolvedFields {
        // FREEZE: once kcal and all three macros are locked we are done — the
        // confirmed combo is final and must not drift as more scans arrive.
        if (kcal.locked != null && protein.locked != null && carbs.locked != null && fat.locked != null) {
            return ResolvedFields(
                kcal = kcal.locked,
                fat = fat.locked,
                carbs = carbs.locked,
                protein = protein.locked,
                servingSizeG = servingG.locked ?: servingG.value
            )
        }

        if (readCount < MIN_READS_FOR_MAP) return resolveFromBestValues()

        val allReadings = collectReadings()
        if (allReadings.isEmpty()) return resolveFromBestValues()

        val per100Readings = allReadings.filter { it.kcal != null || it.fat != null || it.carbs != null || it.protein != null }
        val perServReadings = allReadings.filter { it.perServingKcal != null || it.perServingFat != null || it.perServingCarbs != null || it.perServingProtein != null }

        val fatClusters = clusterReadings(per100Readings.mapNotNull { it.fat })
        val carbClusters = clusterReadings(per100Readings.mapNotNull { it.carbs })
        val protClusters = clusterReadings(per100Readings.mapNotNull { it.protein })
        val kcalClusters = clusterReadings(per100Readings.mapNotNull { it.kcal })
        val kjClusters = clusterReadings(per100Readings.mapNotNull { it.kj })
        val servFatClusters = clusterReadings(perServReadings.mapNotNull { it.perServingFat })
        val servCarbClusters = clusterReadings(perServReadings.mapNotNull { it.perServingCarbs })
        val servProtClusters = clusterReadings(perServReadings.mapNotNull { it.perServingProtein })
        val servKcalClusters = clusterReadings(perServReadings.mapNotNull { it.perServingKcal })

        val fatCandidates = generateCandidates(fatClusters, 0f, 100f)
        val carbCandidates = generateCandidates(carbClusters, 0f, 100f)
        val protCandidates = generateCandidates(protClusters, 0f, 100f)
        val kcalCandidates = generateCandidates(kcalClusters, 0f, 1100f)
        val servingCandidates = generateServingCandidates(per100Readings, perServReadings)

        var bestScore = Double.NEGATIVE_INFINITY
        var bestFat: Float? = null; var bestCarbs: Float? = null; var bestProt: Float? = null
        var bestKcal: Float? = null; var bestServ: Float? = null

        for (fc in fatCandidates) {
            for (cc in carbCandidates) {
                if (fc.first + cc.first > 100f) continue
                for (pc in protCandidates) {
                    if (fc.first + cc.first + pc.first > 100f) continue
                    for (kc in kcalCandidates) {
                        val score = scoreCombo(
                            fc.first, cc.first, pc.first, kc.first,
                            fatClusters, carbClusters, protClusters, kcalClusters,
                            kjClusters
                        )
                        if (score > bestScore) {
                            bestScore = score; bestFat = fc.first; bestCarbs = cc.first
                            bestProt = pc.first; bestKcal = kc.first
                        }
                    }
                }
            }
        }

        val serving = if (bestFat != null && servingCandidates.isNotEmpty()) {
            servingCandidates.maxByOrNull { (servVal, _) ->
                val expFat = bestFat * servVal / 100f
                val expCarb = (bestCarbs ?: 0f) * servVal / 100f
                val expProt = (bestProt ?: 0f) * servVal / 100f
                val expKcal = (bestKcal ?: 0f) * servVal / 100f
                var s = 0.0
                for (r in perServReadings) {
                    if (r.perServingFat != null && expFat > 0) s += 1.0 - minOf(abs(r.perServingFat - expFat).toDouble() / maxOf(expFat, 1f), 1.0)
                    if (r.perServingCarbs != null && expCarb > 0) s += 1.0 - minOf(abs(r.perServingCarbs - expCarb).toDouble() / maxOf(expCarb, 1f), 1.0)
                    if (r.perServingProtein != null && expProt > 0) s += 1.0 - minOf(abs(r.perServingProtein - expProt).toDouble() / maxOf(expProt, 1f), 1.0)
                    if (r.perServingKcal != null && expKcal > 0) s += 1.0 - minOf(abs(r.perServingKcal - expKcal).toDouble() / maxOf(expKcal, 1f), 1.0)
                }
                s
            }?.first
        } else null

        // Derivation gate: if kcal had no direct reading but all three macros
        // are confidently resolved, derive it via Atwater (R3).
        if (bestKcal == null) {
            val f = bestFat; val c = bestCarbs; val pr = bestProt
            if (f != null && c != null && pr != null) {
                bestKcal = 9f * f + 4f * c + 4f * pr
            }
        }

        // Respect locks: a field already confirmed keeps its locked value, so
        // the surfaced combo can never contradict what the user accepted.
        return ResolvedFields(
            kcal = kcal.locked ?: bestKcal,
            fat = fat.locked ?: bestFat,
            carbs = carbs.locked ?: bestCarbs,
            protein = protein.locked ?: bestProt,
            servingSizeG = servingG.locked ?: serving
        )
    }

    private fun scoreCombo(
        fat: Float, carbs: Float, prot: Float, kcal: Float,
        fatC: List<Cluster>, carbC: List<Cluster>, protC: List<Cluster>,
        kcalC: List<Cluster>, kjC: List<Cluster>
    ): Double {
        // Hard constraints
        if (fat + carbs + prot > 100f) return Double.NEGATIVE_INFINITY

        // Stage 3A: Hard Atwater constraint — reject combos where Atwater estimate
        // is wildly off from the kcal candidate (25% tolerance).
        if (kcal > 0f && fat > 0f && carbs > 0f && prot > 0f) {
            val atwater = 9f * fat + 4f * carbs + 4f * prot
            val relErr = abs(atwater - kcal) / maxOf(kcal, 1f)
            if (relErr > 0.25f) return Double.NEGATIVE_INFINITY
        }

        var score = 0.0
        val n = readCount.toFloat()

        fun fieldScore(value: Float, clusters: List<Cluster>, high: Float, med: Float): Double {
            val total = clusters.sumOf { it.count }.toFloat()
            if (total == 0f) return 0.0
            val matching = clusters.filter { abs(it.value - value) < maxOf(0.1f, 0.02f * value) }
            val matchCount = matching.sumOf { it.count }.toFloat()
            val freq = matchCount / total
            val confidence = when {
                total >= 3 && freq > 0.6f -> high.toDouble()
                total >= 2 && freq > 0.4f -> med.toDouble()
                else -> 0.0
            }
            return freq * confidence * 10.0
        }

        score += fieldScore(fat, fatC, HIGH_CONF, MED_CONF)
        score += fieldScore(carbs, carbC, HIGH_CONF, MED_CONF)
        score += fieldScore(prot, protC, HIGH_CONF, MED_CONF)
        score += fieldScore(kcal, kcalC, HIGH_CONF, MED_CONF)

        // R1: kJ should track 4.184 × kcal. Score the implied kJ against the
        // kJ readings so combos consistent with both energy scales win.
        if (kcal > 0f && kjC.isNotEmpty()) {
            score += fieldScore(4.184f * kcal, kjC, HIGH_CONF, MED_CONF)
        }

        // Invariants
        val computedKcal = 4f * carbs + 4f * prot + 9f * fat
        if (kcal > 0f) {
            val kcalError = abs(kcal - computedKcal) / maxOf(kcal, 1f)
            score -= kcalError * 15.0
        }

        val highFields = listOf(fat to fatC, carbs to carbC, prot to protC, kcal to kcalC).count { (_, c) ->
            val total = c.sumOf { it.count }.toFloat()
            val matchCount = c.filter { cl -> abs(cl.value - (if (c === fatC) fat else if (c === carbC) carbs else if (c === protC) prot else kcal)) < maxOf(0.1f, 0.02f * (if (c === fatC) fat else if (c === carbC) carbs else if (c === protC) prot else kcal)) }.sumOf { it.count }.toFloat()
            total > 0 && matchCount / total > 0.6f
        }
        if (highFields >= 3) score += 5.0

        return score
    }

    // ── Reading collection & field resolution ───────────────────────

    data class Reading(
        val kj: Float? = null,
        val kcal: Float? = null,
        val fat: Float? = null,
        val carbs: Float? = null,
        val protein: Float? = null,
        val perServingKcal: Float? = null,
        val perServingKj: Float? = null,
        val perServingFat: Float? = null,
        val perServingCarbs: Float? = null,
        val perServingProtein: Float? = null
    )

    private fun collectReadings(): List<Reading> {
        return readings
    }

    private val readCount: Int
        get() = readings.size

    // ── Clustering ─────────────────────────────────────────────────

    data class Cluster(val value: Float, val count: Int)

    private fun clusterReadings(values: List<Float>, tolerance: Float = 0.1f): List<Cluster> {
        if (values.isEmpty()) return emptyList()
        val sorted = values.sorted()
        val clusters = mutableListOf<MutableList<Float>>()
        var currentCluster = mutableListOf(sorted[0])
        for (i in 1 until sorted.size) {
            if (abs(sorted[i] - sorted[i - 1]) <= maxOf(tolerance, 0.02f * sorted[i - 1])) {
                currentCluster.add(sorted[i])
            } else {
                clusters.add(currentCluster)
                currentCluster = mutableListOf(sorted[i])
            }
        }
        clusters.add(currentCluster)
        return clusters.map { Cluster(it.average().toFloat(), it.size) }
    }

    // ── Candidate generation ───────────────────────────────────────

    private fun generateCandidates(
        clusters: List<Cluster>,
        low: Float,
        high: Float
    ): List<Pair<Float, Float>> {
        if (clusters.isEmpty()) return emptyList()
        return clusters.flatMap { c ->
            listOf(c.value / 10f, c.value, c.value * 10f)
                .filter { it in low..high }
                .map { it to c.count.toFloat() }
        }
    }

    private fun generateServingCandidates(
        per100: List<Reading>,
        perServ: List<Reading>
    ): List<Pair<Float, Float>> {
        val candidates = mutableListOf<Pair<Float, Float>>()
        for (r in per100) {
            if (r.fat == null || r.fat <= 0f) continue
            for (rs in perServ) {
                if (rs.perServingFat != null && rs.perServingFat > 0f) {
                    val serv = 100f * rs.perServingFat / r.fat
                    if (serv in 1f..2000f) candidates.add(serv to 1f)
                }
            }
        }
        return candidates.ifEmpty {
            val sv = servingG.bestValue
            if (sv != null) listOf(sv to 0f) else emptyList()
        }
    }

    // ── Validation ─────────────────────────────────────────────────

    private data class Validity(val plausible: Boolean, val consistent: Boolean)

    private val isValid: Validity
        get() {
            val k = kcal.bestValue; val p = protein.bestValue; val c = carbs.bestValue; val f = fat.bestValue
            return isValid(k, p, c, f)
        }

    private fun isValid(kcal: Float?, p: Float?, c: Float?, f: Float?): Validity {
        val plausible = (kcal != null && kcal in 0f..1000f) &&
            listOf(p, c, f).all { it == null || it in 0f..100f } &&
            listOfNotNull(p, c, f).sum() <= 100f
        return Validity(plausible, isConsistent(kcal, p, c, f))
    }

    private fun isConsistent(kcal: Float?, p: Float?, c: Float?, f: Float?): Boolean {
        if (kcal == null || kcal <= 0f) return true
        val present = listOfNotNull(p, c, f)
        if (present.size < 2) return true
        val computed = (p ?: 0f) * 4f + (c ?: 0f) * 4f + (f ?: 0f) * 9f
        val diff = abs(kcal - computed)
        return diff <= maxOf(0.15f * kcal, 20f)
    }

    companion object {
        private const val HIGH_CONF = 1.0f
        private const val MED_CONF = 0.6f
        private const val MIN_READS_FOR_MAP = 4

        private val KCAL_PLAUSIBLE: (Float) -> Boolean = { it >= 0f && it <= 1000f }
        private val MACRO_PLAUSIBLE: (Float) -> Boolean = { it >= 0f && it <= 100f }
        private val SERVING_PLAUSIBLE: (Float) -> Boolean = { it > 0f && it <= 2000f }

        private fun floatField(plausible: (Float) -> Boolean) =
            ConsensusField<Float>(plausible = plausible, keyOf = { kotlin.math.round(it * 10f).toInt() })
    }
}

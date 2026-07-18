package com.macrotrack.domain.parser

import javax.inject.Inject
import kotlin.math.abs

/**
 * Single OCR reading — the output of Stage 2 (extraction).
 * All values are per-100g unless they are specifically per-serving.
 * null means "could not extract from this scan."
 */
data class ParsedReading(
    val servingSizeG: Float?,
    val kj: Float?,
    val kcal: Float?,
    val fat: Float?,
    val carbs: Float?,
    val protein: Float?,
    val kjPerServing: Float? = null,
    val kcalPerServing: Float? = null
)

data class ParsedNutritionLabel(
    val per100: MacroValues?,
    val perServing: MacroValues?,
    val servingSizeG: Float?,
    val servingLabel: String?,
    val kcalDerived: Boolean = false
)

data class MacroValues(
    val kcal: Float?,
    val fat: Float?,
    val carbs: Float?,
    val protein: Float?,
    val kj: Float? = null
)

class LabelParser @Inject constructor(
    private val lineReconstructor: OcrLineReconstructor = OcrLineReconstructor()
) {

    fun parseStructured(elements: List<OcrElement>): ParsedNutritionLabel {
        if (elements.isEmpty()) return ParsedNutritionLabel(null, null, null, null)
        val text = reconstructLines(elements)
        return extractFromText(text)
    }

    fun parse(rawText: String): ParsedNutritionLabel {
        val text = normalizeText(rawText)
        return extractFromText(text)
    }

    // ── Stage 2: extraction from text ──────────────────────────────

    private fun extractFromText(rawText: String): ParsedNutritionLabel {
        val text = normalizeText(rawText)
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val sections = splitSections(lines)

        var per100 = sections.per100?.let { extractReading(it.joinToString("\n")) }
        var perServing = sections.perServing?.let { extractReading(it.joinToString("\n")) }

        if (per100 == null && perServing == null) {
            per100 = extractReading(text)
        }

        val serving = per100?.servingSizeG ?: perServing?.servingSizeG

        return ParsedNutritionLabel(
            per100 = per100?.let { MacroValues(kcal = it.kcal, fat = it.fat, carbs = it.carbs, protein = it.protein, kj = it.kj) },
            perServing = perServing?.let { MacroValues(kcal = it.kcalPerServing, fat = it.fat, carbs = it.carbs, protein = it.protein, kj = it.kjPerServing) },
            servingSizeG = serving,
            servingLabel = null
        )
    }

    private data class Sections(val per100: List<String>?, val perServing: List<String>?)

    private fun splitSections(lines: List<String>): Sections {
        val per100Start = lines.indexOfFirst { line ->
            val lower = line.lowercase()
            lower.contains("per 100") || Regex("\\b100\\s*g\\b").containsMatchIn(lower)
        }
        val perServingStart = lines.indexOfFirst { line ->
            val lower = line.lowercase()
            lower.contains("per ") && !lower.contains("per 100") &&
                (lower.contains("serving") || lower.contains("portion") || lower.contains("slice"))
        }
        val per100Lines = if (per100Start >= 0) {
            val end = if (perServingStart > per100Start) perServingStart else lines.size
            lines.subList(per100Start, end)
        } else null
        val perServingLines = if (perServingStart >= 0) {
            lines.subList(perServingStart, lines.size)
        } else null
        return Sections(per100Lines, perServingLines)
    }

    private fun extractReading(rawText: String): ParsedReading {
        val normed = normalizeText(rawText)
        val tokens = tokenize(normed)
        val positions = findKeywordPositions(tokens)

        val (kj100, kcal100, kjServ, kcalServ) = extractEnergy(tokens, positions)
        val (fat100, _) = extractMacro(tokens, positions, "fat", 100f)
        val (carb100, _) = extractMacro(tokens, positions, "carbohydrate", 100f)
        val (prot100, _) = extractMacro(tokens, positions, "protein", 100f)
        val serving = extractServingSize(normed)

        val (fatR, carbR, protR) = atwaterRepair(fat100, carb100, prot100, kcal100)

        return ParsedReading(
            servingSizeG = serving,
            kj = kj100,
            kcal = kcal100,
            fat = fatR,
            carbs = carbR,
            protein = protR,
            kjPerServing = kjServ,
            kcalPerServing = kcalServ
        )
    }

    // ── Text normalization ─────────────────────────────────────────

    private fun normalizeText(text: String): String {
        var t = text.lowercase()
        t = t.replace(Regex("[kK][iI1j3]"), " kj ")
        t = t.replace(Regex("[kK]/"), " kj ")
        // OCR often misreads the "j" in "kj" as a bracket: k] k} k)
        t = t.replace(Regex("[kK][\\]\\}\\)]"), " kj ")
        t = t.replace(Regex("[kK][cC][aAeE][lLkK]"), " kcal ")
        t = t.replace(',', '.')
        for ((a, b) in listOf("ı" to "i", "ä" to "a", "ö" to "o", "ü" to "u", "ç" to "c", "&" to " ", "ô" to "o")) {
            t = t.replace(a, b)
        }
        return t
    }

    private fun tokenize(text: String): List<String> {
        val out = mutableListOf<String>()
        for (tok in text.split(Regex("\\s+"))) {
            for (p in tok.split(Regex("[:|/(){}\\[\\]=]"))) {
                val s = p.trim()
                if (s.isNotEmpty()) out.add(s)
            }
        }
        return out
    }

    // ── Keyword classification ─────────────────────────────────────

    private val KEYWORDS = mapOf(
        "energy" to listOf("energy"),
        "fat" to listOf("fat", "lipides", "lipide", "fett", "gras"),
        "saturates" to listOf("saturates", "saturated", "satur"),
        "carbohydrate" to listOf("carbohydrate", "carbs"),
        "sugar" to listOf("sugars", "sugar"),
        "protein" to listOf("protein"),
        "fibre" to listOf("fibre", "fiber", "fibres"),
        "salt" to listOf("salt", "sel"),
        "kj" to listOf("kj"),
        "kcal" to listOf("kcal"),
        "serving" to listOf("serving", "portion", "slice", "tablespoon", "teaspoon"),
        "reference" to listOf("reference", "intake"),
        "per" to listOf("per")
    )

    private val STOP_KEYWORDS = setOf(
        "energy", "fat", "saturates", "carbohydrate", "sugar", "protein",
        "fibre", "salt", "kj", "kcal", "serving", "reference", "of", "which", "from", "per"
    )

    private val FILLER_KEYWORDS = setOf("of", "which", "from")

    private fun classifyToken(tok: String): Pair<String?, Int> {
        val t = tok.lowercase().trim('.', ',', ';', ':', '-')
        if (t.isEmpty()) return null to -1
        var best: String? = null
        var bestS = -1
        for ((canon, variants) in KEYWORDS) {
            for (v in variants) {
                val s = if (v in t) {
                    10 + v.length
                } else if (t.length >= 5 && v.length >= 5 && editDistance(t, v) <= 2) {
                    v.length
                } else {
                    continue
                }
                if (s > bestS) {
                    bestS = s
                    best = canon
                }
            }
        }
        return best to bestS
    }

    private fun editDistance(a: String, b: String): Int {
        val m = a.length; val n = b.length
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = curr; curr = IntArray(n + 1)
        }
        return prev[n]
    }

    // ── Number cleaning ────────────────────────────────────────────

    private val UNIT_LETTERS = setOf('a', 'g', 'q', 'ç', 'k', 'j', 'c', 'r', 's', 'x', 'z', 'o')

    private val LETTER_NUMBERS = mapOf(
        "ia" to 1.0f, "l0" to 1.0f, "z0" to 7.0f, "z" to 7.0f,
        "og" to 0.0f, "o" to 0.0f, "0g" to 0.0f, "0" to 0.0f,
        "nil" to 0.0f, "trace" to 0.0f, "n/a" to 0.0f, "na" to 0.0f, "none" to 0.0f, "-" to 0.0f,
        "dg" to 0.0f
    )

    private val CONFUSABLE = mapOf('z' to '7', 'o' to '0', 'l' to '1', 'i' to '1', '|' to '1')

    private fun cleanNumber(tok: String): Triple<Float?, Boolean, Boolean> {
        val raw = tok
        val low = raw.lowercase().trim('.', ',', ';', ':', '-')

        if (low in LETTER_NUMBERS) {
            val v = LETTER_NUMBERS[low]!!
            return Triple(v, v != v.toInt().toFloat(), v == 0f)
        }

        val hasDigit = raw.any { it.isDigit() }
        val digits = StringBuilder()
        var sawDot = false
        for (ch in raw) {
            when {
                ch.isDigit() -> digits.append(ch)
                ch == '.' || ch == ',' -> {
                    if (!sawDot) { digits.append('.'); sawDot = true }
                }
                hasDigit && ch.lowercaseChar() in CONFUSABLE && adjacentToDigit(raw, ch) -> {
                    digits.append(CONFUSABLE[ch.lowercaseChar()])
                }
                ch.lowercaseChar() in UNIT_LETTERS -> { /* skip */ }
            }
        }
        if (digits.isEmpty()) return Triple(null, false, false)

        val numStr = digits.toString().let { s ->
            val first = s.indexOf('.')
            if (first >= 0) s.substring(0, first + 1) + s.substring(first + 1).replace(".", "")
            else s
        }
        val v = numStr.toFloatOrNull() ?: return Triple(null, false, false)
        return Triple(v, sawDot, false)
    }

    private fun adjacentToDigit(s: String, ch: Char): Boolean {
        val i = s.indexOf(ch)
        if (i < 0) return false
        return (i > 0 && s[i - 1].isDigit()) || (i + 1 < s.length && s[i + 1].isDigit())
    }

    private fun repairValue(value: Float?, hadDecimal: Boolean, bound: Float, originalTok: String): Float? {
        if (value == null) return null
        val stripped = originalTok.lowercase().trimStart('<', '>', '=', '~')
        if (!hadDecimal && Regex("^0\\d").containsMatchIn(stripped)) return value / 10f
        if (!hadDecimal && value > bound) {
            var cand = value
            repeat(3) { cand /= 10f; if (cand <= bound) return cand }
            return cand
        }
        return value
    }

    // ── Keyword position scanning ──────────────────────────────────

    private fun findKeywordPositions(tokens: List<String>): Map<String, MutableList<Pair<Int, Int>>> {
        val pos = KEYWORDS.keys.associateWith { mutableListOf<Pair<Int, Int>>() }.toMutableMap()
        for ((i, tok) in tokens.withIndex()) {
            val (c, s) = classifyToken(tok)
            if (c != null) pos.getOrPut(c) { mutableListOf() }.add(i to s)
        }
        return pos
    }

    private fun scanNumbersAfter(tokens: List<String>, startIdx: Int, maxNums: Int = 6): List<Triple<Float, Boolean, String>> {
        val nums = mutableListOf<Triple<Float, Boolean, String>>()
        var i = startIdx + 1
        var foundFirstNumber = false
        while (i < tokens.size && nums.size < maxNums) {
            val tok = tokens[i]
            val cls = classifyToken(tok).first
            if (cls != null && cls in STOP_KEYWORDS) {
                // Before the first number, skip filler words ("of which …")
                // so we can cross line breaks without losing the value.
                // Field/section keywords ("per", "carbohydrate", …) always stop.
                if (foundFirstNumber || cls !in FILLER_KEYWORDS) break
                i++; continue
            }
            val (v, hd, iz) = cleanNumber(tok)
            i++
            // A bare "9" with no decimal is almost always the unit letter
            // ("g") misread by OCR, not a real macro reading. Skip it so it
            // can't be picked up as fat/carbs/protein.
            if (v != null && !(v == 9.0f && !hd)) {
                nums.add(Triple(v, hd, tok))
                foundFirstNumber = true
            }
        }
        return nums
    }

    // ── Energy extraction ──────────────────────────────────────────

    private fun extractEnergy(
        tokens: List<String>,
        pos: Map<String, List<Pair<Int, Int>>>
    ): Quadruple<Float?, Float?, Float?, Float?> {
        val kjIdx = pos["kj"]?.map { it.first } ?: emptyList()
        val kcalIdx = pos["kcal"]?.map { it.first } ?: emptyList()
        val used = mutableSetOf<Int>()
        val allKj = mutableListOf<Float>()
        val allKcal = mutableListOf<Float>()

        // Value immediately adjacent (±4 tokens) to a unit token, on either
        // side. Stops at a field keyword, but skips the "energy"/"energie"
        // label word (it is part of the "Energy kJ/Energie" unit, not a
        // separate field) so the real value just beyond it is still found.
        fun numberNear(idx: Int): Float? {
            for (d in listOf(-1, 1)) {
                var k = idx + d
                var steps = 0
                while (k in tokens.indices && steps < 4) {
                    val cls = classifyToken(tokens[k]).first
                    if (cls != null && cls != "energy") break
                    val (v, _, _) = cleanNumber(tokens[k])
                    if (v != null) return v
                    k += d; steps++
                }
            }
            return null
        }

        // The n-th number following a token (skipping non-numbers), within a
        // small window so we don't reach into a distant field.
        fun numberAfter(idx: Int, n: Int): Float? {
            var count = 0
            var k = idx + 1
            var steps = 0
            while (k < tokens.size && steps < 6) {
                val (v, _, _) = cleanNumber(tokens[k])
                if (v != null) {
                    count++
                    if (count == n) return v
                }
                k++; steps++
            }
            return null
        }

        fun findHeaderPairs(): List<Quadruple<Float, Float, Int, Int>> {
            val pairs = mutableListOf<Quadruple<Float, Float, Int, Int>>()
            val energyIdx = pos["energy"]?.map { it.first } ?: emptyList()

            // Pattern 1: "energy kJ <num>" / "energy kcal <num>" on adjacent rows.
            // Values follow their unit token (skipping the foreign "energie"
            // word), so use numberAfter (forward-only) to avoid picking up the
            // sibling value that sits between the two units.
            for (ei in energyIdx) {
                val nearKj = kjIdx.firstOrNull { ei < it && it <= ei + 3 }
                val nearKcal = kcalIdx.firstOrNull { ei < it && it <= ei + 3 }
                if (nearKj != null && nearKcal != null) continue
                if (nearKj != null) {
                    val nearKcal2 = kcalIdx.firstOrNull { it > nearKj && it <= nearKj + 10 } ?: continue
                    val kjV = numberNear(nearKj) ?: continue
                    val kcalV = numberNear(nearKcal2) ?: continue
                    if (kcalV > 0 && kjV / kcalV in 2.0..8.0) {
                        pairs.add(Quadruple(kjV, kcalV, nearKj, nearKcal2))
                    }
                    continue
                }
                if (nearKcal != null) {
                    val nearKj2 = kjIdx.firstOrNull { it < nearKcal && it >= nearKcal - 10 } ?: continue
                    val kjV = numberNear(nearKj2) ?: continue
                    val kcalV = numberNear(nearKcal) ?: continue
                    if (kcalV > 0 && kjV / kcalV in 2.0..8.0) {
                        pairs.add(Quadruple(kjV, kcalV, nearKj2, nearKcal))
                    }
                    continue
                }
            }

            // Pattern 2: pair each kcal unit with the best-ratio kJ unit to its
            // left. Robust to single-line and interleaved layouts (e.g.
            // "2368 kJ 758 kJ 569 kcal" where a per-serving kJ sits between).
            for (ci in kcalIdx) {
                val kcalV = numberNear(ci) ?: continue
                if (kcalV <= 0) continue
                var bestKj: Float? = null
                var bestErr = Float.MAX_VALUE
                var bestKi = -1
                for (ki in kjIdx) {
                    if (ki >= ci) continue
                    val kjV = numberNear(ki) ?: continue
                    if (kjV <= 0) continue
                    val r = kjV / kcalV
                    if (r in 2.0..8.0) {
                        val err = abs(r - 4.184f)
                        if (err < bestErr) { bestErr = err; bestKj = kjV; bestKi = ki }
                    }
                }
                if (bestKj != null) pairs.add(Quadruple(bestKj, kcalV, bestKi, ci))
            }

            // Pattern 3: no explicit unit — "energy <num> <num>" with kJ:kcal ≈ 4.184.
            if (kjIdx.isEmpty() && kcalIdx.isEmpty()) {
                for (ei in energyIdx) {
                    val n1 = numberAfter(ei, 1) ?: continue
                    val n2 = numberAfter(ei, 2) ?: continue
                    val r = n1 / n2
                    when {
                        r in 2.0..8.0 -> pairs.add(Quadruple(n1, n2, ei, ei))
                        (n2 / n1) in 2.0..8.0 -> pairs.add(Quadruple(n2, n1, ei, ei))
                    }
                }
            }

            return pairs
        }

        // Sort by kcal descending so the per-100g value (always the largest)
        // is considered first; drop reference-intake pairs (kcal ~2000).
        var pairs = findHeaderPairs()
            .filter { it.two < 1100f }
            // The per-100g column always appears first (leftmost) in the token
            // stream, so sort by the kj-token position rather than by kcal
            // magnitude (which fails when the serving is larger than 100g).
            .sortedBy { it.three }

        // Pattern 1c: fallback when no header-based pair survived filtering.
        // When kj and kcal tokens are close in the token stream, the real
        // values may be scattered between (or before) them.  Collect all
        // numbers in the window and pick the pair whose kJ value appears
        // earliest (leftmost) in the stream — the per-100g column always
        // comes first; tie-break on the tightest ratio to 4.184.
        if (pairs.isEmpty()) {
            for (ki in kjIdx) {
                for (ci in kcalIdx) {
                    if (abs(ki - ci) > 12) continue
                    val lo = maxOf(0, minOf(ki, ci) - 4)
                    val hi = minOf(tokens.size, maxOf(ki, ci) + 5)
                    val nums = mutableListOf<Pair<Float, Int>>()
                    for (k in lo until hi) {
                        if (k == ki || k == ci) continue
                        val raw = tokens[k]
                        // Skip numbers carrying a mass/volume unit (e.g. "330ml",
                        // "100g") — these are serving sizes, not energy values.
                        if (raw.any { it.isLetter() && it.lowercaseChar() in setOf('g', 'm', 'l') }) continue
                        val (v, _, _) = cleanNumber(raw)
                        if (v != null && v > 0f) nums.add(v to k)
                    }
                    var bestPair: Pair<Float, Float>? = null
                    var bestPos = Int.MAX_VALUE
                    var bestErr = Float.MAX_VALUE
                    for (a in nums.indices) {
                        for (b in a + 1 until nums.size) {
                            val (va, pa) = nums[a]
                            val (vb, pb) = nums[b]
                            val (kjv, kcalv, kjPos) = if (va >= vb) Triple(va, vb, pa) else Triple(vb, va, pb)
                            val r = kjv / kcalv
                            if (r !in 2.0..8.0) continue
                            val err = abs(r - 4.184f)
                            if (err < 1.0f && (kjPos < bestPos ||
                                (kjPos == bestPos && err < bestErr))
                            ) {
                                bestPos = kjPos
                                bestErr = err
                                bestPair = kjv to kcalv
                            }
                        }
                    }
                    if (bestPair != null) {
                        val (bj, bcal) = bestPair
                        pairs = (pairs + Quadruple(bj, bcal, ki, ci)).sortedBy { it.three }
                    }
                }
            }
        }
        if (pairs.isNotEmpty()) {
            for (pair in pairs) {
                val (kjV, kcalV, kjI, kcalI) = pair
                if (kjV > 4200f || kcalV > 1100f) continue
                if (allKj.isEmpty() && allKcal.isEmpty()) {
                    allKj.add(kjV); allKcal.add(kcalV); used.add(kjI); used.add(kcalI)
                } else if (allKj.size == 1 && allKcal.size == 1) {
                    // Skip a duplicate of the per-100g pair (e.g. found by two
                    // patterns) so it isn't misassigned to the per-serving slot.
                    if (kjV == allKj[0] && kcalV == allKcal[0]) continue
                    allKj.add(kjV); allKcal.add(kcalV); used.add(kjI); used.add(kcalI)
                }
            }
        }

        if (allKj.isEmpty() && allKcal.isEmpty()) {
            fun tryAttach(idx: Int): Float? {
                if (idx - 1 >= 0 && (idx - 1) !in used) {
                    val (v, _, _) = cleanNumber(tokens[idx - 1])
                    if (v != null && classifyToken(tokens[idx - 1]).first == null) { used.add(idx - 1); return v }
                }
                var j = idx + 1
                while (j < tokens.size && j !in used) {
                    if (classifyToken(tokens[j]).first in setOf("kj", "kcal")) break
                    val (v, _, _) = cleanNumber(tokens[j])
                    if (v != null && classifyToken(tokens[j]).first == null) { used.add(j); return v }
                    if (classifyToken(tokens[j]).first != null) break
                    j++
                }
                return null
            }
            for (idx in kjIdx) { tryAttach(idx)?.let { allKj.add(it) } }
            for (idx in kcalIdx) { tryAttach(idx)?.let { allKcal.add(it) } }
        }

        // A value attached to "kcal" that exceeds the plausible per-100g
        // maximum is really a kJ figure (e.g. "1521 359" with a stray unit).
        // Move it to the kJ slot; otherwise discard out-of-range readings.
        if (allKcal.isNotEmpty() && allKcal[0] > 1100f) {
            allKj.add(0, allKcal.removeAt(0))
        }
        if (allKj.isNotEmpty() && allKj[0] > 4200f) allKj.clear()
        if (allKcal.isNotEmpty() && allKcal[0] > 1100f) allKcal.clear()

        var kj100 = allKj.firstOrNull()
        var kcal100 = allKcal.firstOrNull()
        if (kj100 != null && kcal100 != null) {
            if (kj100 / 4.184f > kcal100 * 6) kj100 = kcal100 * 4.184f
            else if (kcal100 / (kj100 / 4.184f) > 6) kcal100 = kj100 / 4.184f
        }
        if (kj100 == null && kcal100 != null) kj100 = kcal100 * 4.184f
        if (kcal100 == null && kj100 != null) kcal100 = kj100 / 4.184f

        return Quadruple(kj100, kcal100, allKj.getOrNull(1), allKcal.getOrNull(1))
    }

    // ── Macro extraction ───────────────────────────────────────────

    private fun extractMacro(
        tokens: List<String>,
        pos: Map<String, List<Pair<Int, Int>>>,
        key: String,
        bound: Float
    ): Pair<Float?, Float?> {
        val energyIdx = pos["energy"]?.firstOrNull()?.first
        val occ = (pos[key] ?: emptyList()).map { (i, s) ->
            val nums = scanNumbersAfter(tokens, i)
            val afterEnergy = if (energyIdx != null && i > energyIdx) 1 else 0
            Quadruple(i, s, nums.size >= 2, afterEnergy)
        }.sortedWith(compareByDescending<Quadruple<Int, Int, Boolean, Int>> { it.four }
            .thenByDescending { if (it.three) 1 else 0 }
            .thenByDescending { it.two }
            .thenBy { it.one })

        val per100 = mutableListOf<Float?>()
        val perServ = mutableListOf<Float?>()
        for (q in occ) {
            val nums = scanNumbersAfter(tokens, q.one)
            if (nums.isEmpty()) continue
            per100.add(repairValue(nums[0].first, nums[0].second, bound, nums[0].third))
            if (nums.size > 1) perServ.add(repairValue(nums[1].first, nums[1].second, bound, nums[1].third))
        }
        if (perServ.isEmpty() && occ.size > 1) {
            val nums2 = scanNumbersAfter(tokens, occ[1].one)
            if (nums2.isNotEmpty()) perServ.add(repairValue(nums2[0].first, nums2[0].second, bound, nums2[0].third))
        }
        return (per100.firstOrNull()) to (perServ.firstOrNull())
    }

    // ── Serving size extraction ────────────────────────────────────

    private fun extractServingSize(text: String): Float? {
        val low = text.lowercase()
        for (m in Regex("per\\s*(\\d+(?:\\.\\d+)?)\\s*(g|ml)").findAll(low)) {
            val v = m.groupValues[1].toFloatOrNull() ?: continue
            if (abs(v - 100) > 0.001f) return v
        }
        Regex("(?:serving|slice|portion|tablespoon|teaspoon|spray).{0,15}?(\\d+(?:\\.\\d+)?)\\s*g")
            .find(low)?.let { return it.groupValues[1].toFloatOrNull() }
        Regex("(\\d+(?:\\.\\d+)?)\\s*g\\s*(?:serving|slice|portion)")
            .find(low)?.let { return it.groupValues[1].toFloatOrNull() }
        return null
    }

    // ── Atwater repair ─────────────────────────────────────────────

    private fun atwaterRepair(fat: Float?, carb: Float?, protein: Float?, kcal: Float?): Triple<Float?, Float?, Float?> {
        if (kcal == null || fat == null || carb == null || protein == null) return Triple(fat, carb, protein)
        val vals = mutableMapOf("fat" to fat, "carbs" to carb, "protein" to protein)
        repeat(4) {
            val atm = 9 * vals["fat"]!! + 4 * vals["carbs"]!! + 4 * vals["protein"]!!
            if (atm <= kcal * 1.5f) return@repeat
            val contribs = mapOf("fat" to 9 * vals["fat"]!!, "carbs" to 4 * vals["carbs"]!!, "protein" to 4 * vals["protein"]!!)
            val worst = contribs.maxByOrNull { it.value }?.key ?: return@repeat
            if (contribs[worst]!! < 1f) return@repeat
            vals[worst] = vals[worst]!! / 10f
        }
        return Triple(vals["fat"], vals["carbs"], vals["protein"])
    }

    // ── Stage 1: OCR bounding box → text-line reconstruction ───────
    // Delegates to [OcrLineReconstructor]: a global-skew, ry-based line
    // builder that groups a label with its value across any number of
    // columns regardless of slight image slant.

    /** Reconstruct text lines from OCR bounding boxes. */
    fun reconstructLines(elements: List<OcrElement>): String =
        lineReconstructor.reconstructLines(elements)

    /**
     * Cluster OCR elements into visual rows (delegates to [OcrLineReconstructor]).
     * The [tolerance] parameter is accepted for API compatibility but the
     * skew-aware reconstructor derives its own tolerance from the median box
     * height.
     */
    fun clusterIntoRows(elements: List<OcrElement>, tolerance: Int = 15): List<OcrRow> =
        lineReconstructor.toRows(elements)

    private data class Quadruple<A, B, C, D>(val one: A, val two: B, val three: C, val four: D)
}

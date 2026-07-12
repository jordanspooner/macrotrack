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
    val protein: Float?
)

data class ParsedNutritionLabel(
    val per100: MacroValues?,
    val perServing: MacroValues?,
    val servingSizeG: Float?,
    val servingLabel: String?
)

data class MacroValues(
    val kcal: Float?,
    val fat: Float?,
    val carbs: Float?,
    val protein: Float?,
    val kj: Float? = null
)

class LabelParser @Inject constructor() {

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
            perServing = perServing?.let { MacroValues(kcal = it.kcal, fat = it.fat, carbs = it.carbs, protein = it.protein, kj = it.kj) },
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

        val (kj100, kcal100, _, _) = extractEnergy(tokens, positions)
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
            protein = protR
        )
    }

    // ── Text normalization ─────────────────────────────────────────

    private fun normalizeText(text: String): String {
        var t = text.lowercase()
        t = t.replace(Regex("[kK][iI1j3]"), " kj ")
        t = t.replace(Regex("[kK]/"), " kj ")
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
        "reference" to listOf("reference", "intake")
    )

    private val STOP_KEYWORDS = setOf(
        "energy", "fat", "saturates", "carbohydrate", "sugar", "protein",
        "fibre", "salt", "kj", "kcal", "serving", "reference", "of", "which", "from", "per"
    )

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
        "nil" to 0.0f, "trace" to 0.0f, "n/a" to 0.0f, "na" to 0.0f, "none" to 0.0f, "-" to 0.0f
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
        while (i < tokens.size && nums.size < maxNums) {
            val tok = tokens[i]
            if (classifyToken(tok).first in STOP_KEYWORDS) break
            val (v, hd, iz) = cleanNumber(tok)
            i++
            if (v != null) nums.add(Triple(v, hd, tok))
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

        fun findHeaderPairs(): List<Quadruple<Float, Float, Int, Int>> {
            val pairs = mutableListOf<Quadruple<Float, Float, Int, Int>>()
            val energyIdx = pos["energy"]?.map { it.first } ?: emptyList()

            // Pattern 1: two-row "energy kj num1 num2" / "energy kcal num3 num4"
            for (ei in energyIdx) {
                val nearKj = kjIdx.firstOrNull { ei < it && it <= ei + 3 }
                val nearKcal = kcalIdx.firstOrNull { ei < it && it <= ei + 3 }
                if (nearKj != null && nearKcal != null) continue

                if (nearKj != null) {
                    for (ei2 in energyIdx) {
                        if (ei2 <= nearKj) continue
                        val nearKcal2 = kcalIdx.firstOrNull { ei2 < it && it <= ei2 + 3 } ?: continue
                        val numsKj = mutableListOf<Pair<Float, Int>>()
                        var k = nearKj + 1
                        while (k < tokens.size && numsKj.size < 4) {
                            if (classifyToken(tokens[k]).first in setOf("kj", "kcal", "fat", "carbohydrate", "protein", "energy")) break
                            val (v, _, _) = cleanNumber(tokens[k])
                            if (v != null) numsKj.add(v to k)
                            k++
                        }
                        val numsKcal = mutableListOf<Pair<Float, Int>>()
                        k = nearKcal2 + 1
                        while (k < tokens.size && numsKcal.size < 4) {
                            if (classifyToken(tokens[k]).first in setOf("kj", "kcal", "fat", "carbohydrate", "protein", "energy")) break
                            val (v, _, _) = cleanNumber(tokens[k])
                            if (v != null) numsKcal.add(v to k)
                            k++
                        }
                        for (j in 0 until minOf(numsKj.size, numsKcal.size)) {
                            val kjV = numsKj[j].first; val kcalV = numsKcal[j].first
                            if (kcalV > 0 && kjV / kcalV in 2.0..8.0) {
                                pairs.add(Quadruple(kjV, kcalV, numsKj[j].second, numsKcal[j].second))
                            }
                        }
                        break
                    }
                    continue
                }

            }

            // Pattern 2: kj+kcal close together (units before numbers, or
            // interleaved). Runs over every kJ index, independent of any
            // "energy" keyword — this is the common single-line layout and
            // must not be nested inside the energy loop above.
            for (ki in kjIdx) {
                for (ci in kcalIdx) {
                    if (ci !in (ki + 1)..(ki + 5)) continue
                    val numsBetween = mutableListOf<Pair<Float, Int>>()
                    for (k2 in (ki + 1) until ci) {
                        val (v, _, _) = cleanNumber(tokens[k2])
                        if (v != null) numsBetween.add(v to k2)
                    }
                    if (numsBetween.isNotEmpty()) {
                        val kcalV = numsBetween[0].first
                        if (ki > 0 && (ki - 1) !in used) {
                            val (v, _, _) = cleanNumber(tokens[ki - 1])
                            if (v != null && classifyToken(tokens[ki - 1]).first == null && kcalV > 0 && v / kcalV in 2.0..8.0) {
                                pairs.add(Quadruple(v, kcalV, ki - 1, numsBetween[0].second))
                            }
                        }
                    } else {
                        val start = maxOf(ki, ci) + 1
                        val nums = mutableListOf<Pair<Float, Int>>()
                        var k2 = start
                        while (k2 < tokens.size && nums.size < 10) {
                            if (classifyToken(tokens[k2]).first in setOf("kj", "kcal", "fat", "carbohydrate", "protein")) break
                            val (v, _, _) = cleanNumber(tokens[k2])
                            if (v != null) nums.add(v to k2)
                            k2++
                        }
                        for (j in 0 until nums.size - 1 step 2) {
                            val kjV = nums[j].first; val kcalV = nums[j + 1].first
                            if (kcalV > 0 && kjV / kcalV in 2.0..8.0) {
                                pairs.add(Quadruple(kjV, kcalV, nums[j].second, nums[j + 1].second))
                            } else if (kjV > 0 && kcalV > 0 && 0.2 < kjV / kcalV && kjV / kcalV < 0.5) {
                                // values possibly swapped
                                pairs.add(Quadruple(kcalV, kjV, nums[j].second, nums[j + 1].second))
                            }
                        }
                        if (pairs.isEmpty() && ki > 0 && (ki - 1) !in used) {
                            val (v, _, _) = cleanNumber(tokens[ki - 1])
                            if (v != null && classifyToken(tokens[ki - 1]).first == null) {
                                pairs.add(Quadruple(v, v / 4.184f, ki - 1, ki))
                            }
                        }
                    }
                    break
                }
            }

            return pairs
        }

        val pairs = findHeaderPairs()
        if (pairs.isNotEmpty()) {
            for (pair in pairs) {
                val (kjV, kcalV, kjI, kcalI) = pair
                if (kjV > 4200f || kcalV > 1100f) continue
                if (allKj.isEmpty() && allKcal.isEmpty()) {
                    allKj.add(kjV); allKcal.add(kcalV); used.add(kjI); used.add(kcalI)
                } else if (allKj.size == 1 && allKcal.size == 1) {
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

    /**
     * Reconstruct text lines from OCR bounding boxes.
     * Port of ocr_to_text.py's [to_text] algorithm.
     * Geometry-first: walk right/left from seed boxes using edge proximity
     * and EMA-tracked skew to handle rotated/tightly-packed labels.
     */
    fun reconstructLines(elements: List<OcrElement>): String {
        val n = elements.size
        if (n == 0) return ""

        val cx = DoubleArray(n) { (elements[it].left + elements[it].right) / 2.0 }
        val cy = DoubleArray(n) { (elements[it].top + elements[it].bottom) / 2.0 }
        val used = BooleanArray(n)
        val medh = elements.map { (it.bottom - it.top).toDouble() }.sorted().let { it[it.size / 2] }
        val overlapTol = 10.0
        val yTol = maxOf(25.0, 0.5 * medh)

        fun findNext(cur: Int, direction: String, slope: Double): Int? {
            var best: Int? = null; var bestGap = Double.MAX_VALUE; var bestYd = Double.MAX_VALUE
            val cl = elements[cur].left.toDouble(); val cr = elements[cur].right.toDouble()
            val ccy = cy[cur]; val ccx = cx[cur]
            for (j in 0 until n) {
                if (used[j] || j == cur) continue
                val jl = elements[j].left.toDouble(); val jr = elements[j].right.toDouble()
                if (direction == "right") {
                    if (jl < cr - overlapTol) continue
                    val gap = jl - cr; val expY = ccy + slope * (cx[j] - ccx); val yd = abs(cy[j] - expY)
                    if (yd > yTol) continue
                    if (best == null || gap < bestGap || (abs(gap - bestGap) < 1 && yd < bestYd)) {
                        best = j; bestGap = gap; bestYd = yd
                    }
                } else {
                    if (jr > cl + overlapTol) continue
                    val gap = cl - jr; val expY = ccy + slope * (cx[j] - ccx); val yd = abs(cy[j] - expY)
                    if (yd > yTol) continue
                    if (best == null || gap < bestGap || (abs(gap - bestGap) < 1 && yd < bestYd)) {
                        best = j; bestGap = gap; bestYd = yd
                    }
                }
            }
            return best
        }

        val lines = mutableListOf<List<Int>>()
        while (used.any { !it }) {
            val start = (0 until n).filter { !used[it] }.minByOrNull { cy[it] }!!
            used[start] = true
            val line = mutableListOf(start)
            var slope = 0.0; var nslopes = 0

            var cur = start
            while (true) {
                val nxt = findNext(cur, "right", slope) ?: break
                used[nxt] = true
                if (abs(cx[nxt] - cx[cur]) > 1) {
                    val s = (cy[nxt] - cy[cur]) / (cx[nxt] - cx[cur])
                    slope = if (nslopes == 0) s else 0.7 * slope + 0.3 * s
                    nslopes++
                }
                line.add(nxt); cur = nxt
            }
            cur = start
            while (true) {
                val nxt = findNext(cur, "left", slope) ?: break
                used[nxt] = true
                if (abs(cx[nxt] - cx[cur]) > 1) {
                    val s = (cy[nxt] - cy[cur]) / (cx[nxt] - cx[cur])
                    slope = if (nslopes == 0) s else 0.7 * slope + 0.3 * s
                    nslopes++
                }
                line.add(0, nxt); cur = nxt
            }
            lines.add(line)
        }

        lines.sortBy { ln -> ln.minOf { cy[it] } }
        return lines.joinToString("\n") { ln ->
            ln.sortedBy { cx[it] }.joinToString(" ") { elements[it].text }
        }
    }

    // ── Legacy helpers (kept for backward compatibility) ────────────

    fun clusterIntoRows(elements: List<OcrElement>, tolerance: Int = 15): List<OcrRow> {
        if (elements.isEmpty()) return emptyList()
        val sorted = elements.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<OcrElement>>()
        var currentRow = mutableListOf(sorted[0])
        for (i in 1 until sorted.size) {
            val elem = sorted[i]
            val rowAvgY = currentRow.map { it.centerY }.average()
            val rowAvgX = currentRow.map { it.centerX }.average()
            val xDist = abs(elem.centerX - rowAvgX.toInt())
            val effectiveTolerance = minOf((tolerance + xDist * 0.02).toInt(), tolerance * 3)
            if (abs(elem.centerY - rowAvgY.toInt()) <= effectiveTolerance) {
                currentRow.add(elem)
            } else {
                rows.add(currentRow)
                currentRow = mutableListOf(elem)
            }
        }
        rows.add(currentRow)
        return rows.map { row ->
            val sortedRow = row.sortedBy { it.left }
            OcrRow(sortedRow, sortedRow.map { it.centerY }.average().toInt())
        }
    }

    private data class Quadruple<A, B, C, D>(val one: A, val two: B, val three: C, val four: D)
}

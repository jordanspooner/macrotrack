package com.macrotrack.domain.parser

import javax.inject.Inject
import kotlin.math.abs

/**
 * Reconstructs text lines from raw OCR bounding boxes.
 *
 * Port of the skew-aware algorithm used by the offline [ocr_to_text] tool:
 * estimate the global image skew `s` (the slight rotation that makes a label
 * and its value sit at different raw Y positions across columns), then use a
 * skew-corrected row coordinate
 *     ry = cy - s * cx
 * so that every box on the same visual line shares `ry`, regardless of how
 * many columns the table has or whether the slant goes up or down.
 *
 * Boxes are linked into chains using `ry` proximity only; chains are atomic
 * (never split, only appended whole), so no text is ever dropped.
 */
class OcrLineReconstructor @Inject constructor() {

    /** Join the reconstructed rows into a single newline-separated text. */
    fun reconstructLines(elements: List<OcrElement>): String =
        toRows(elements).joinToString("\n") { it.text }

    /** Reconstruct the visual rows, each sorted left-to-right. */
    fun toRows(elements: List<OcrElement>): List<OcrRow> {
        val n = elements.size
        if (n == 0) return emptyList()

        val cx = DoubleArray(n) { (elements[it].left + elements[it].right) / 2.0 }
        val cy = DoubleArray(n) { (elements[it].top + elements[it].bottom) / 2.0 }
        val left = DoubleArray(n) { elements[it].left.toDouble() }
        val right = DoubleArray(n) { elements[it].right.toDouble() }

        val heights = elements.map { (it.bottom - it.top).toDouble() }.sorted()
        val medh = heights[heights.size / 2]
        val yTol = maxOf(25.0, 0.5 * medh)
        val overlapTol = 0.5 * medh
        val imgWidth = right.maxOrNull()!! - left.minOrNull()!!

        /** Global skew: median slope of each box's far-away nearest-in-Y neighbour. */
        fun estimateSkew(): Double {
            val d = 0.3 * imgWidth
            val slopes = mutableListOf<Double>()
            for (i in 0 until n) {
                var bestD = Double.MAX_VALUE
                var bestS = 0.0
                for (j in 0 until n) {
                    if (i == j) continue
                    val dx = abs(cx[j] - cx[i])
                    if (dx < d) continue
                    val dy = abs(cy[j] - cy[i])
                    if (dy < bestD) {
                        bestD = dy
                        bestS = if (dx > 0) (cy[j] - cy[i]) / dx else 0.0
                    }
                }
                if (bestD < Double.MAX_VALUE) slopes.add(bestS)
            }
            return median(slopes)
        }

        /** Link boxes into chains assuming a given skew. */
        fun buildRows(s: Double): List<List<Int>> {
            val ry = DoubleArray(n) { cy[it] - s * cx[it] }
            val leftOf = IntArray(n) { -1 }
            val rightOf = IntArray(n) { -1 }
            for (i in 0 until n) {
                var bl = -1; var blg = Double.MAX_VALUE
                for (j in 0 until n) {
                    if (i == j) continue
                    if (right[j] > left[i] + overlapTol) continue
                    if (abs(ry[j] - ry[i]) > yTol) continue
                    val gap = maxOf(0.0, left[i] - right[j])
                    if (bl == -1 || gap < blg) { bl = j; blg = gap }
                }
                leftOf[i] = bl
            }
            for (i in 0 until n) {
                var br = -1; var brg = Double.MAX_VALUE
                for (j in 0 until n) {
                    if (i == j) continue
                    if (left[j] < right[i] - overlapTol) continue
                    if (abs(ry[j] - ry[i]) > yTol) continue
                    val gap = maxOf(0.0, left[j] - right[i])
                    if (br == -1 || gap < brg) { br = j; brg = gap }
                }
                rightOf[i] = br
            }
            val visited = BooleanArray(n)
            val chains = mutableListOf<List<Int>>()
            for (i in 0 until n) {
                if (visited[i]) continue
                var start = i
                while (leftOf[start] != -1 && !visited[leftOf[start]]) start = leftOf[start]
                val chain = mutableListOf<Int>()
                var cur = start
                while (cur != -1 && !visited[cur]) {
                    visited[cur] = true
                    chain.add(cur)
                    cur = rightOf[cur]
                }
                chains.add(chain)
            }
            return chains
        }

        var s = estimateSkew()
        var chains = buildRows(s)

        // Refine the skew from the long rows (each is itself a line), then rebuild.
        val refined = mutableListOf<Double>()

        for (ch in chains) {
            if (ch.size < 3) continue
            val xs = ch.map { cx[it] }
            if (xs.maxOrNull()!! - xs.minOrNull()!! < 0.2 * imgWidth) continue
            val ys = ch.map { cy[it] }
            val mx = xs.average(); val my = ys.average()
            var num = 0.0; var den = 0.0
            for (k in ch.indices) {
                num += (xs[k] - mx) * (ys[k] - my)
                den += (xs[k] - mx) * (xs[k] - mx)
            }
            if (den > 0) refined.add(num / den)
        }
        if (refined.isNotEmpty()) {
            s = median(refined)
            chains = buildRows(s)
        }

        val ry = DoubleArray(n) { cy[it] - s * cx[it] }
        val ordered = chains.map { ch -> ch to ch.map { ry[it] }.average() }
            .sortedBy { it.second }
            .map { it.first }

        return ordered.map { chain ->
            val sorted = chain.sortedBy { cx[it] }
            OcrRow(
                sorted.map { elements[it] },
                sorted.map { elements[it].centerY }.average().toInt()
            )
        }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
    }
}

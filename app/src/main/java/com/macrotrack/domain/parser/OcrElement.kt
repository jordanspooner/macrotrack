package com.macrotrack.domain.parser

/**
 * A single OCR element (word) with its position and confidence.
 * Extracted from ML Kit's Text.Element bounding box.
 */
data class OcrElement(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float = 1f
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * A row of OCR elements clustered by Y-position proximity.
 * Elements in the same row are sorted by X-position (left to right).
 */
data class OcrRow(
    val elements: List<OcrElement>,
    val avgY: Int
) {
    val left: Int get() = elements.minOf { it.left }
    val right: Int get() = elements.maxOf { it.right }
    val text: String get() = elements.joinToString(" ") { it.text }
}

package com.gallery.core.ai

import com.gallery.core.model.NormalizedRect

/**
 * A horizontal band of the image that contains text-like ink.
 * NOTE: this is a *layout* descriptor, not transcribed text.
 */
data class TextLineRegion(
    val boundingBox: NormalizedRect,
    val inkDensity: Float,
    val confidence: Float
)

enum class DocumentLayout {
    NONE,
    DOCUMENT,
    RECEIPT_LIKE,
    SCREEN_LIKE
}

data class DocumentAnalysis(
    val isDocument: Boolean,
    val layout: DocumentLayout,
    val lineRegions: List<TextLineRegion>,
    /** Fraction of the frame occupied by light, low-chroma background. */
    val paperCoverage: Float,
    /** Fraction of the frame occupied by dark ink pixels. */
    val inkCoverage: Float,
    /** Mean local contrast along horizontal scan lines, 0..1. */
    val horizontalContrast: Float
) {
    val textRegionCount: Int get() = lineRegions.size

    val confidence: Float
        get() = when {
            !isDocument -> 0f
            else -> (0.45f + paperCoverage * 0.3f + (horizontalContrast * 2.5f).coerceAtMost(0.25f))
                .coerceIn(0f, 0.97f)
        }

    companion object {
        val EMPTY = DocumentAnalysis(
            isDocument = false,
            layout = DocumentLayout.NONE,
            lineRegions = emptyList(),
            paperCoverage = 0f,
            inkCoverage = 0f,
            horizontalContrast = 0f
        )
    }
}

/**
 * Offline document *layout* analyzer.
 *
 * It answers "does this look like a page of text, and roughly where are the text
 * bands?" using cheap luminance statistics. It deliberately does **not** claim to
 * read the text: this project ships no OCR model, and inventing plausible strings
 * would be worse than returning nothing. Callers must surface the result as
 * "text regions detected", never as transcribed text.
 *
 * Input should be at least 320px on the long edge - at 64px a line of body text
 * is under one pixel tall and the statistics are meaningless.
 */
object DocumentLayoutAnalyzer {

    private const val MIN_USEFUL_EDGE = 96

    fun analyze(
        rgbPixels: IntArray,
        width: Int,
        height: Int,
        filename: String = ""
    ): DocumentAnalysis {
        if (rgbPixels.isEmpty() || width < MIN_USEFUL_EDGE || height < MIN_USEFUL_EDGE) {
            return DocumentAnalysis.EMPTY
        }

        val totalPixels = width * height
        var paperPixels = 0
        var inkPixels = 0

        val rowInk = IntArray(height)
        val rowEdges = IntArray(height)

        for (y in 0 until height) {
            var rowInkCount = 0
            var rowEdgeCount = 0
            val rowOffset = y * width

            for (x in 0 until width) {
                val pixel = rgbPixels[rowOffset + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000

                val chromaSpread = maxOf(r, g, b) - minOf(r, g, b)
                if (lum > 200 && chromaSpread < 28) {
                    paperPixels++
                } else if (lum < 90) {
                    rowInkCount++
                    inkPixels++
                }

                if (x < width - 1) {
                    val next = rgbPixels[rowOffset + x + 1]
                    val nextLum = (((next shr 16) and 0xFF) * 299 +
                            ((next shr 8) and 0xFF) * 587 +
                            (next and 0xFF) * 114) / 1000
                    if (kotlin.math.abs(lum - nextLum) > 60) rowEdgeCount++
                }
            }

            rowInk[y] = rowInkCount
            rowEdges[y] = rowEdgeCount
        }

        val paperCoverage = paperPixels.toFloat() / totalPixels
        val inkCoverage = inkPixels.toFloat() / totalPixels

        // A text line needs both dark ink and high horizontal contrast.
        val minInkPerRow = maxOf(2, (width * 0.015f).toInt())
        val minEdgesPerRow = maxOf(3, width / 24)

        val regions = mutableListOf<TextLineRegion>()
        var bandStart = -1
        var bandInk = 0
        var bandEdges = 0

        fun closeBand(endExclusive: Int) {
            if (bandStart < 0) return
            val bandHeight = endExclusive - bandStart
            val maxBandHeight = maxOf(6, height / 6)
            if (bandHeight in 3..maxBandHeight) {
                val bandPixels = maxOf(1, bandHeight * width)
                regions.add(
                    TextLineRegion(
                        boundingBox = NormalizedRect(
                            left = 0.04f,
                            top = bandStart.toFloat() / height,
                            right = 0.96f,
                            bottom = endExclusive.toFloat() / height
                        ),
                        inkDensity = (bandInk.toFloat() / bandPixels).coerceIn(0f, 1f),
                        confidence = (0.45f + paperCoverage * 0.4f).coerceIn(0f, 0.95f)
                    )
                )
            }
            bandStart = -1
            bandInk = 0
            bandEdges = 0
        }

        for (y in 0 until height) {
            val hasText = rowInk[y] >= minInkPerRow && rowEdges[y] >= minEdgesPerRow
            if (hasText) {
                if (bandStart < 0) {
                    bandStart = y
                    bandInk = 0
                    bandEdges = 0
                }
                bandInk += rowInk[y]
                bandEdges += rowEdges[y]
            } else {
                closeBand(y)
            }
        }
        closeBand(height)

        val horizontalContrast = rowEdges.sum().toFloat() / (totalPixels.toFloat() * 0.25f)
        val lowerName = filename.lowercase()

        val nameHintsDocument = lowerName.contains("receipt") ||
                lowerName.contains("invoice") ||
                lowerName.contains("document") ||
                lowerName.contains("doc_") ||
                lowerName.contains("scan") ||
                lowerName.contains("bill")

        // Requirements: light page background, ink, and at least a couple of bands.
        val looksLikePage = paperCoverage > 0.28f &&
                inkCoverage > 0.004f &&
                (regions.size >= 2 || (regions.size == 1 && paperCoverage > 0.5f))

        val isDocument = looksLikePage || (nameHintsDocument && paperCoverage > 0.15f)

        val layout = when {
            !isDocument -> DocumentLayout.NONE
            nameHintsDocument || (regions.size >= 5 && inkCoverage < 0.10f) -> DocumentLayout.RECEIPT_LIKE
            regions.size >= 3 -> DocumentLayout.DOCUMENT
            else -> DocumentLayout.SCREEN_LIKE
        }

        return DocumentAnalysis(
            isDocument = isDocument,
            layout = layout,
            lineRegions = if (isDocument) regions else emptyList(),
            paperCoverage = paperCoverage,
            inkCoverage = inkCoverage,
            horizontalContrast = horizontalContrast
        )
    }
}

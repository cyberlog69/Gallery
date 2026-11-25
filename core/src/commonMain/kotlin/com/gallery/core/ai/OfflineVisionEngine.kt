package com.gallery.core.ai

import com.gallery.core.model.AiCategory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Result of the offline vision pass.
 */
data class VisionResult(
    val category: AiCategory,
    val confidence: Float,
    val tags: List<String>,
    /** 256-d L2-normalized hand-crafted descriptor (see [OfflineVisionEngine]). */
    val embedding: FloatArray,
    /** INT8 version of [embedding] - 256 bytes on disk/in memory. */
    val quantizedEmbedding: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VisionResult) return false
        return category == other.category &&
                confidence == other.confidence &&
                tags == other.tags &&
                quantizedEmbedding.contentEquals(other.quantizedEmbedding)
    }

    override fun hashCode(): Int {
        var result = category.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + quantizedEmbedding.contentHashCode()
        return result
    }
}

/**
 * On-device visual descriptor and rule-based scene classifier.
 *
 * IMPORTANT: this is **not** a neural network. The project intentionally ships no
 * model binary; the descriptor below is hand-crafted and the category decision is
 * a decision tree over colour/edge statistics. It is cheap, deterministic, fully
 * offline, and honest about what it can do:
 *
 * - 0..63:   4x4 spatial grid, mean R/G/B/luma            (colour composition)
 * - 64..95:  global colour, saturation, warmth, contrast, semantic ratios
 * - 96..159: 4x4 grid edge energy + orientation mix       (texture/structure)
 * - 160..223: 64-bin hue histogram weighted by saturation (colour histogram)
 * - 224..255: 32-bin luminance histogram                  (tone distribution)
 *
 * Cosine similarity over this vector is a meaningful "looks similar" measure for
 * things like near-duplicates and colour/tone searches. It is not a CLIP-style
 * semantic embedding and the UI copy must never call it one.
 */
object OfflineVisionEngine {

    const val EMBEDDING_DIM = 256

    fun quantizeToInt8(floatVector: FloatArray): ByteArray {
        val quantized = ByteArray(floatVector.size)
        for (i in floatVector.indices) {
            val v = (floatVector[i] * 127.0f).toInt().coerceIn(-128, 127)
            quantized[i] = v.toByte()
        }
        return quantized
    }

    fun dequantizeFromInt8(byteVector: ByteArray): FloatArray {
        val floats = FloatArray(byteVector.size)
        for (i in byteVector.indices) {
            floats[i] = byteVector[i].toFloat() / 127.0f
        }
        return floats
    }

    /** Cosine similarity between two INT8 vectors, output in [-1..1]. */
    fun computeQuantizedSimilarity(vecA: ByteArray, vecB: ByteArray): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0f
        var dot = 0
        var normASq = 0
        var normBSq = 0
        for (i in vecA.indices) {
            val a = vecA[i].toInt()
            val b = vecB[i].toInt()
            dot += a * b
            normASq += a * a
            normBSq += b * b
        }
        val denom = sqrt(normASq.toDouble() * normBSq.toDouble()).toFloat()
        if (denom < 1e-6f) return 0f
        return (dot.toFloat() / denom).coerceIn(-1.0f, 1.0f)
    }

    /** Cosine similarity between two L2-normalized float vectors. */
    fun computeCosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0f
        var dot = 0f
        for (i in vecA.indices) {
            dot += vecA[i] * vecB[i]
        }
        return dot.coerceIn(-1.0f, 1.0f)
    }

    /**
     * Describes and categorises a downscaled frame.
     *
     * @param rgbPixels ARGB pixels, row-major
     * @param width at least 32px; 256px is the sweet spot
     * @param height at least 32px
     * @param filename used only as an explicit hint (screenshot / receipt names)
     */
    fun processImage(
        rgbPixels: IntArray,
        width: Int,
        height: Int,
        filename: String = ""
    ): VisionResult {
        if (rgbPixels.isEmpty() || width <= 0 || height <= 0 ||
            rgbPixels.size < width * height
        ) {
            return VisionResult(
                category = AiCategory.UNCATEGORIZED,
                confidence = 0f,
                tags = emptyList(),
                embedding = FloatArray(EMBEDDING_DIM),
                quantizedEmbedding = ByteArray(EMBEDDING_DIM)
            )
        }

        val totalPixels = width * height
        val lowerName = filename.lowercase()

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var sumLum = 0.0
        var sumSat = 0.0

        var greenCount = 0
        var skyCount = 0
        var skinCount = 0
        var warmCount = 0
        var paperCount = 0
        var darkCount = 0

        var sumHorizEdge = 0.0
        var sumVertEdge = 0.0
        var sumDiagEdge = 0.0

        val cellCount = 16
        val cellLum = DoubleArray(cellCount)
        val cellR = DoubleArray(cellCount)
        val cellG = DoubleArray(cellCount)
        val cellB = DoubleArray(cellCount)
        val cellPixels = IntArray(cellCount)
        val cellEdgeH = DoubleArray(cellCount)
        val cellEdgeV = DoubleArray(cellCount)
        val cellEdgeD = DoubleArray(cellCount)

        val hueHistogram = DoubleArray(64)
        val lumaHistogram = DoubleArray(32)

        val cellW = max(1, width / 4)
        val cellH = max(1, height / 4)

        for (y in 0 until height) {
            val cellY = min(3, y / cellH)
            val rowOffset = y * width
            for (x in 0 until width) {
                val cellX = min(3, x / cellW)
                val cIdx = cellY * 4 + cellX

                val pixel = rgbPixels[rowOffset + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000.0
                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                val chroma = mx - mn
                val sat = if (mx == 0) 0.0 else chroma / mx.toDouble()

                sumR += r
                sumG += g
                sumB += b
                sumLum += lum
                sumSat += sat

                cellR[cIdx] += r
                cellG[cIdx] += g
                cellB[cIdx] += b
                cellLum[cIdx] += lum
                cellPixels[cIdx]++

                if (lum < 35) darkCount++
                if (lum > 210 && chroma < 24) paperCount++

                if (g > r * 1.15 && g > b * 1.1 && g > 60) greenCount++
                if (b > r * 1.25 && b > g * 1.05 && b > 80 && y < height * 0.6) skyCount++
                if (r > 95 && g > 40 && b > 20 && (r - g) > 15 && r > b && chroma > 15) skinCount++
                if (r > 120 && g in 50..180 && b < 100 && (r - b) > 40) warmCount++

                // Hue (0..360) weighted by saturation so grey pixels barely vote.
                if (chroma > 12) {
                    val hue = when (mx) {
                        r -> 60.0 * (((g - b).toDouble() / chroma) % 6.0)
                        g -> 60.0 * (((b - r).toDouble() / chroma) + 2.0)
                        else -> 60.0 * (((r - g).toDouble() / chroma) + 4.0)
                    }.let { if (it < 0) it + 360.0 else it }
                    val bin = ((hue / 360.0) * 64).toInt().coerceIn(0, 63)
                    hueHistogram[bin] += sat
                }
                lumaHistogram[((lum / 256.0) * 32).toInt().coerceIn(0, 31)] += 1.0

                if (x < width - 1) {
                    val right = rgbPixels[rowOffset + x + 1]
                    val rightLum = lumaOf(right)
                    val d = abs(lum - rightLum)
                    cellEdgeH[cIdx] += d
                    sumHorizEdge += d
                }
                if (y < height - 1) {
                    val down = rgbPixels[rowOffset + width + x]
                    val downLum = lumaOf(down)
                    val d = abs(lum - downLum)
                    cellEdgeV[cIdx] += d
                    sumVertEdge += d
                }
                if (x < width - 1 && y < height - 1) {
                    val diag = rgbPixels[rowOffset + width + x + 1]
                    val d = abs(lum - lumaOf(diag))
                    cellEdgeD[cIdx] += d
                    sumDiagEdge += d
                }
            }
        }

        val avgR = (sumR / totalPixels).toFloat()
        val avgG = (sumG / totalPixels).toFloat()
        val avgB = (sumB / totalPixels).toFloat()
        val avgLum = (sumLum / totalPixels).toFloat()
        val avgSat = (sumSat / totalPixels).toFloat()

        val greenRatio = greenCount.toFloat() / totalPixels
        val skyRatio = skyCount.toFloat() / totalPixels
        val skinRatio = skinCount.toFloat() / totalPixels
        val warmRatio = warmCount.toFloat() / totalPixels
        val paperRatio = paperCount.toFloat() / totalPixels
        val darkRatio = darkCount.toFloat() / totalPixels
        val totalEdgeEnergy = ((sumHorizEdge + sumVertEdge + sumDiagEdge) / (totalPixels * 255.0)).toFloat()

        val vector = FloatArray(EMBEDDING_DIM)

        // 0..63 - 4x4 grid colour/luma.
        for (i in 0 until 16) {
            val count = max(1, cellPixels[i]).toDouble()
            vector[i * 4] = (cellR[i] / (count * 255.0)).toFloat()
            vector[i * 4 + 1] = (cellG[i] / (count * 255.0)).toFloat()
            vector[i * 4 + 2] = (cellB[i] / (count * 255.0)).toFloat()
            vector[i * 4 + 3] = (cellLum[i] / (count * 255.0)).toFloat()
        }

        // 64..95 - global statistics.
        vector[64] = avgR / 255f
        vector[65] = avgG / 255f
        vector[66] = avgB / 255f
        vector[67] = avgLum / 255f
        vector[68] = greenRatio
        vector[69] = skyRatio
        vector[70] = skinRatio
        vector[71] = warmRatio
        vector[72] = paperRatio
        vector[73] = darkRatio
        vector[74] = (sumHorizEdge / (totalPixels * 255.0)).toFloat()
        vector[75] = (sumVertEdge / (totalPixels * 255.0)).toFloat()
        vector[76] = (sumDiagEdge / (totalPixels * 255.0)).toFloat()
        vector[77] = totalEdgeEnergy
        vector[78] = avgSat
        vector[79] = ((avgR - avgB) / 255f).coerceIn(-1f, 1f) // warmth
        vector[80] = ((avgLum / 255f) * (1f - avgSat)).coerceIn(0f, 1f)
        vector[81] = (paperRatio * (1f - totalEdgeEnergy * 4f)).coerceIn(0f, 1f)

        // 96..159 - per-cell texture/orientation.
        for (i in 0 until 16) {
            val count = max(1, cellPixels[i]).toDouble()
            val h = cellEdgeH[i] / (count * 255.0)
            val v = cellEdgeV[i] / (count * 255.0)
            val d = cellEdgeD[i] / (count * 255.0)
            val energy = (h + v + d) / 3.0
            val denom = (h + v + d).coerceAtLeast(1e-6)
            vector[96 + i * 4] = energy.toFloat()
            vector[96 + i * 4 + 1] = (h / denom).toFloat()
            vector[96 + i * 4 + 2] = (v / denom).toFloat()
            vector[96 + i * 4 + 3] = ((d / denom) * energy).toFloat()
        }

        // 160..223 - saturation-weighted hue histogram.
        val hueTotal = hueHistogram.sum().coerceAtLeast(1e-6)
        for (i in 0 until 64) {
            vector[160 + i] = (hueHistogram[i] / hueTotal).toFloat()
        }

        // 224..255 - luma histogram.
        val lumaTotal = lumaHistogram.sum().coerceAtLeast(1e-6)
        for (i in 0 until 32) {
            vector[224 + i] = (lumaHistogram[i] / lumaTotal).toFloat()
        }

        var normSq = 0f
        for (v in vector) normSq += v * v
        val norm = sqrt(normSq).coerceAtLeast(1e-6f)
        for (i in vector.indices) vector[i] /= norm

        val (category, confidence, tags) = classify(
            lowerName = lowerName,
            greenRatio = greenRatio,
            skyRatio = skyRatio,
            skinRatio = skinRatio,
            warmRatio = warmRatio,
            paperRatio = paperRatio,
            darkRatio = darkRatio,
            totalEdgeEnergy = totalEdgeEnergy,
            avgLum = avgLum
        )

        return VisionResult(
            category = category,
            confidence = confidence,
            tags = tags,
            embedding = vector,
            quantizedEmbedding = quantizeToInt8(vector)
        )
    }

    private fun lumaOf(pixel: Int): Double {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000.0
    }

    private data class Decision(
        val category: AiCategory,
        val confidence: Float,
        val tags: List<String>
    )

    private fun classify(
        lowerName: String,
        greenRatio: Float,
        skyRatio: Float,
        skinRatio: Float,
        warmRatio: Float,
        paperRatio: Float,
        darkRatio: Float,
        totalEdgeEnergy: Float,
        avgLum: Float
    ): Decision = when {
        lowerName.contains("screenshot") || lowerName.contains("screen_shot") ||
                lowerName.contains("screencap") ->
            Decision(AiCategory.SCREENSHOTS, 0.97f, listOf("screenshot", "ui", "screen"))

        lowerName.contains("receipt") || lowerName.contains("invoice") ||
                lowerName.contains("scan") || lowerName.contains("document") ->
            Decision(AiCategory.DOCUMENTS, 0.92f, listOf("document", "text", "paper"))

        paperRatio > 0.45f && totalEdgeEnergy > 0.06f ->
            Decision(
                AiCategory.DOCUMENTS,
                min(0.94f, 0.70f + paperRatio * 0.25f),
                listOf("document", "text", "paper", "scan")
            )

        greenRatio + skyRatio > 0.30f -> {
            val tags = mutableListOf("nature", "landscape", "outdoors")
            if (greenRatio > 0.18f) tags += listOf("greenery", "plants")
            if (skyRatio > 0.12f) tags += listOf("sky", "blue sky")
            if (avgLum < 110f && skyRatio > 0.08f) tags += "sunset"
            Decision(AiCategory.NATURE, min(0.93f, 0.62f + (greenRatio + skyRatio) * 0.4f), tags)
        }

        skinRatio > 0.20f -> {
            val tags = mutableListOf("people", "person", "portrait")
            if (skinRatio > 0.38f) tags += "selfie"
            Decision(AiCategory.PEOPLE, min(0.92f, 0.60f + skinRatio * 0.45f), tags)
        }

        warmRatio > 0.24f -> {
            val tags = mutableListOf("food", "meal", "dining")
            if (avgLum < 120f) tags += "warm tones"
            Decision(AiCategory.FOOD, min(0.90f, 0.62f + warmRatio * 0.4f), tags)
        }

        totalEdgeEnergy > 0.16f && darkRatio < 0.45f ->
            Decision(AiCategory.CITY, 0.80f, listOf("city", "architecture", "structure"))

        greenRatio > 0.14f ->
            Decision(AiCategory.NATURE, 0.70f, listOf("nature", "outdoors"))

        skinRatio > 0.10f ->
            Decision(AiCategory.PEOPLE, 0.66f, listOf("people"))

        else ->
            Decision(AiCategory.UNCATEGORIZED, 0.45f, listOf("photo"))
    }
}

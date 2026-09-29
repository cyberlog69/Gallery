package com.gallery.core.ai

import com.gallery.core.model.AiCategory
import kotlin.math.abs
import kotlin.math.sqrt

data class AiClassificationResult(
    val category: AiCategory,
    val confidence: Float,
    val detectedTags: List<String>
)

/**
 * Pluggable local AI inference engine interface.
 * Defines classification, semantic embedding generation, and cosine similarity.
 */
interface LocalAiEngine {
    fun classify(
        rgbPixels: IntArray,
        width: Int,
        height: Int,
        filename: String = ""
    ): AiClassificationResult

    fun generateEmbedding(
        rgbPixels: IntArray,
        width: Int,
        height: Int
    ): FloatArray

    fun computeSimilarity(embeddingA: FloatArray, embeddingB: FloatArray): Float
}

/**
 * Global registry for managing active on-device AI engines.
 */
object LocalAiRegistry {
    private var engine: LocalAiEngine = OfflineAiClassifier

    fun getEngine(): LocalAiEngine = engine

    fun setEngine(newEngine: LocalAiEngine) {
        engine = newEngine
    }

    fun resetToDefault() {
        engine = OfflineAiClassifier
    }
}

/**
 * High-performance, lightweight, on-device offline AI classification engine.
 * Operates purely offline without network dependencies or heavy external binaries.
 * Uses optimized vision feature extraction on downscaled pixel arrays.
 */
object OfflineAiClassifier : LocalAiEngine {

    /**
     * Classifies an image given its low-resolution downscaled RGB pixels (e.g. 64x64).
     * @param rgbPixels IntArray containing ARGB/RGB values of the downsampled image
     * @param width image width (recommended: 64)
     * @param height image height (recommended: 64)
     * @param filename optional filename for heuristic clues
     */
    override fun classify(
        rgbPixels: IntArray,
        width: Int,
        height: Int,
        filename: String
    ): AiClassificationResult {
        if (rgbPixels.isEmpty() || width <= 0 || height <= 0) {
            return AiClassificationResult(AiCategory.UNCATEGORIZED, 0.0f, emptyList())
        }

        val totalPixels = width * height
        val lowerFilename = filename.lowercase()

        // Fast path for explicit filenames (e.g. Android screenshots or camera tags)
        if (lowerFilename.contains("screenshot") || lowerFilename.contains("screen_shot") || lowerFilename.contains("screencap")) {
            return AiClassificationResult(AiCategory.SCREENSHOTS, 0.96f, listOf("screenshot", "ui", "screen"))
        }
        if (lowerFilename.contains("document") || lowerFilename.contains("receipt") || lowerFilename.contains("invoice") || lowerFilename.contains("scan")) {
            return AiClassificationResult(AiCategory.DOCUMENTS, 0.94f, listOf("document", "text", "paper"))
        }

        var greenCount = 0
        var blueSkyCount = 0
        var skinToneCount = 0
        var whitePaperCount = 0
        var foodWarmCount = 0
        var darkCount = 0
        var edgeScore = 0.0

        // Feature extraction loop
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val pixel = rgbPixels[idx]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val brightness = (r * 299 + g * 587 + b * 114) / 1000

                if (brightness < 35) darkCount++
                // Document detection: Near white/light grey background
                if (brightness > 215 && abs(r - g) < 20 && abs(g - b) < 20) {
                    whitePaperCount++
                }

                // Nature detection: Green foliage or Blue sky
                if (g > r * 1.15f && g > b * 1.1f && g > 60) {
                    greenCount++
                } else if (b > r * 1.25f && b > g * 1.05f && b > 80 && y < height * 0.6) {
                    blueSkyCount++
                }

                // People / Skin tone detection (normalized RGB model)
                if (r > 95 && g > 40 && b > 20 &&
                    (r - g) > 15 && r > b &&
                    (maxOf(r, g, b) - minOf(r, g, b)) > 15
                ) {
                    skinToneCount++
                }

                // Food detection: Warm saturated tones (yellow, orange, cooked red)
                if (r > 120 && g in 50..180 && b < 100 && (r - b) > 40) {
                    foodWarmCount++
                }

                // Simple horizontal edge detection for text lines
                if (x < width - 1) {
                    val nextPixel = rgbPixels[idx + 1]
                    val nextR = (nextPixel shr 16) and 0xFF
                    val nextG = (nextPixel shr 8) and 0xFF
                    val nextB = nextPixel and 0xFF
                    val nextBrightness = (nextR * 299 + nextG * 587 + nextB * 114) / 1000
                    val diff = abs(brightness - nextBrightness)
                    if (diff > 45) {
                        edgeScore += diff
                    }
                }
            }
        }

        val whiteRatio = whitePaperCount.toFloat() / totalPixels
        val greenRatio = greenCount.toFloat() / totalPixels
        val blueSkyRatio = blueSkyCount.toFloat() / totalPixels
        val natureRatio = greenRatio + blueSkyRatio
        val skinRatio = skinToneCount.toFloat() / totalPixels
        val foodRatio = foodWarmCount.toFloat() / totalPixels
        val avgEdgeDensity = edgeScore / (totalPixels * 255.0)

        // Decision logic
        return when {
            // High white background + horizontal edge density = Document
            whiteRatio > 0.45f && avgEdgeDensity > 0.08 -> {
                AiClassificationResult(
                    AiCategory.DOCUMENTS,
                    minOf(0.95f, 0.70f + whiteRatio * 0.3f),
                    listOf("document", "text", "paper", "scan")
                )
            }
            // Saturated nature green / sky
            natureRatio > 0.28f -> {
                val tags = mutableListOf("nature", "outdoors")
                if (greenRatio > 0.20f) tags.add("greenery")
                if (blueSkyRatio > 0.15f) tags.add("sky")
                AiClassificationResult(
                    AiCategory.NATURE,
                    minOf(0.95f, 0.65f + natureRatio * 0.4f),
                    tags
                )
            }
            // High skin tone concentration
            skinRatio > 0.20f -> {
                AiClassificationResult(
                    AiCategory.PEOPLE,
                    minOf(0.92f, 0.60f + skinRatio * 0.45f),
                    listOf("people", "portrait", "face")
                )
            }
            // Warm food tones
            foodRatio > 0.25f -> {
                AiClassificationResult(
                    AiCategory.FOOD,
                    minOf(0.90f, 0.60f + foodRatio * 0.4f),
                    listOf("food", "dining", "meal")
                )
            }
            // High edge density with mixed structure = City / Architecture
            avgEdgeDensity > 0.18 -> {
                AiClassificationResult(
                    AiCategory.CITY,
                    0.78f,
                    listOf("architecture", "building", "structure")
                )
            }
            // Default heuristics based on mild cues
            greenRatio > 0.15f -> {
                AiClassificationResult(AiCategory.NATURE, 0.68f, listOf("landscape", "outdoor"))
            }
            skinRatio > 0.10f -> {
                AiClassificationResult(AiCategory.PEOPLE, 0.65f, listOf("person"))
            }
            else -> {
                AiClassificationResult(AiCategory.UNCATEGORIZED, 0.50f, listOf("general"))
            }
        }
    }

    /**
     * Generates a normalized 16-dimensional visual feature embedding vector
     * for semantic similarity ranking and zero-shot nearest neighbor search.
     */
    override fun generateEmbedding(rgbPixels: IntArray, width: Int, height: Int): FloatArray {
        if (rgbPixels.isEmpty() || width <= 0 || height <= 0) return FloatArray(16)

        val vector = FloatArray(16)
        val total = width * height

        var sumR = 0f
        var sumG = 0f
        var sumB = 0f
        var edgeSum = 0f
        var darkCount = 0f
        var brightCount = 0f

        // Sample quadrants (Top-Left, Top-Right, Bottom-Left, Bottom-Right)
        val qR = FloatArray(4)
        val qG = FloatArray(4)
        val qB = FloatArray(4)
        val qCount = IntArray(4)

        val halfW = width / 2
        val halfH = height / 2

        for (y in 0 until height) {
            val isBottom = if (y >= halfH) 2 else 0
            for (x in 0 until width) {
                val qIdx = isBottom + (if (x >= halfW) 1 else 0)
                val pixel = rgbPixels[y * width + x]
                val r = ((pixel shr 16) and 0xFF) / 255f
                val g = ((pixel shr 8) and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f
                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                sumR += r
                sumG += g
                sumB += b
                if (lum < 0.2f) darkCount++
                if (lum > 0.8f) brightCount++

                qR[qIdx] += r
                qG[qIdx] += g
                qB[qIdx] += b
                qCount[qIdx]++

                if (x < width - 1) {
                    val nextPixel = rgbPixels[y * width + x + 1]
                    val nextLum = (((nextPixel shr 16) and 0xFF) * 299 + ((nextPixel shr 8) and 0xFF) * 587 + (nextPixel and 0xFF) * 114) / (255000f)
                    edgeSum += abs(lum - nextLum)
                }
            }
        }

        // Fill 16-D vector
        for (i in 0 until 4) {
            val cnt = maxOf(1, qCount[i]).toFloat()
            vector[i * 3] = qR[i] / cnt
            vector[i * 3 + 1] = qG[i] / cnt
            vector[i * 3 + 2] = qB[i] / cnt
        }
        vector[12] = sumR / total
        vector[13] = sumG / total
        vector[14] = edgeSum / total
        vector[15] = (brightCount - darkCount) / total

        // L2 Normalization
        var normSq = 0f
        for (v in vector) {
            normSq += v * v
        }
        val norm = sqrt(normSq).coerceAtLeast(1e-6f)
        for (i in vector.indices) {
            vector[i] /= norm
        }

        return vector
    }

    /**
     * Computes the cosine similarity between two normalized visual embeddings (-1.0f to 1.0f).
     */
    override fun computeSimilarity(embeddingA: FloatArray, embeddingB: FloatArray): Float {
        if (embeddingA.isEmpty() || embeddingB.isEmpty() || embeddingA.size != embeddingB.size) return 0f
        var dot = 0f
        for (i in embeddingA.indices) {
            dot += embeddingA[i] * embeddingB[i]
        }
        return dot.coerceIn(-1.0f, 1.0f)
    }
}

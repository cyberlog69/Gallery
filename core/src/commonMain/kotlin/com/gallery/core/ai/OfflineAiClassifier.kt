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
 * High-performance, lightweight, on-device offline AI classification engine.
 * Operates purely offline without network dependencies or heavy external binaries.
 * Uses optimized vision feature extraction (Color Chromaticity, Gradient Density,
 * Text/Edge Frequency, and Structural Metadata) on small 64x64 downsampled pixel arrays.
 */
object OfflineAiClassifier {

    /**
     * Classifies an image given its low-resolution downscaled RGB pixels (e.g. 64x64).
     * @param rgbPixels IntArray containing ARGB/RGB values of the downsampled image
     * @param width image width (recommended: 64)
     * @param height image height (recommended: 64)
     * @param filename optional filename for heuristic clues
     */
    fun classify(
        rgbPixels: IntArray,
        width: Int,
        height: Int,
        filename: String = ""
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
}

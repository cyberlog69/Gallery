package com.gallery.core.ai

import com.gallery.core.model.AiCategory

data class AiClassificationResult(
    val category: AiCategory,
    val confidence: Float,
    val detectedTags: List<String>
)

/**
 * Pluggable classification surface, kept so a real model (TFLite/ML Kit) can be
 * dropped in later without touching call sites.
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
 * Adapter over [OfflineVisionEngine].
 *
 * Retained for API compatibility; the actual work happens in one place so the
 * classifier, the descriptor and the search index can never drift apart.
 */
object OfflineAiClassifier : LocalAiEngine {

    override fun classify(
        rgbPixels: IntArray,
        width: Int,
        height: Int,
        filename: String
    ): AiClassificationResult {
        val result = OfflineVisionEngine.processImage(rgbPixels, width, height, filename)
        return AiClassificationResult(result.category, result.confidence, result.tags)
    }

    override fun generateEmbedding(rgbPixels: IntArray, width: Int, height: Int): FloatArray =
        OfflineVisionEngine.processImage(rgbPixels, width, height).embedding

    override fun computeSimilarity(embeddingA: FloatArray, embeddingB: FloatArray): Float =
        OfflineVisionEngine.computeCosineSimilarity(embeddingA, embeddingB)
}

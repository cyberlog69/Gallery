package com.gallery.core.ai

import com.gallery.core.model.NormalizedRect

/**
 * Compact, persistable face record. The 64-d appearance vector is INT8-quantized so
 * a library-wide "People" shelf can be rebuilt on startup without decoding a single
 * image again (~68 bytes per face).
 */
data class FaceSignature(
    val mediaItemId: String,
    val quantizedEmbedding: ByteArray,
    val box: NormalizedRect,
    val confidence: Float = 0.75f
) {
    fun toDetectedFace(index: Int): DetectedFace = DetectedFace(
        faceId = "${mediaItemId}_face_${index + 1}",
        mediaItemId = mediaItemId,
        boundingBox = box,
        confidence = confidence,
        faceEmbedding = OfflineVisionEngine.dequantizeFromInt8(quantizedEmbedding)
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceSignature) return false
        return mediaItemId == other.mediaItemId &&
                box == other.box &&
                quantizedEmbedding.contentEquals(other.quantizedEmbedding)
    }

    override fun hashCode(): Int =
        31 * (31 * mediaItemId.hashCode() + box.hashCode()) + quantizedEmbedding.contentHashCode()

    companion object {
        fun from(face: DetectedFace): FaceSignature = FaceSignature(
            mediaItemId = face.mediaItemId,
            quantizedEmbedding = OfflineVisionEngine.quantizeToInt8(face.faceEmbedding),
            box = face.boundingBox,
            confidence = face.confidence
        )
    }
}

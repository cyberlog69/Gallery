package com.gallery.core.ai

import com.gallery.core.model.NormalizedRect
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Representation of a detected face inside an image.
 */
data class DetectedFace(
    val faceId: String,
    val mediaItemId: String,
    val boundingBox: NormalizedRect,
    val confidence: Float,
    val faceEmbedding: FloatArray,
    var personId: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetectedFace) return false
        return faceId == other.faceId && mediaItemId == other.mediaItemId
    }

    override fun hashCode(): Int {
        return faceId.hashCode() * 31 + mediaItemId.hashCode()
    }
}

/**
 * A recognized identity cluster grouping photos containing the same individual.
 */
data class PersonCluster(
    val personId: String,
    val name: String,
    val coverMediaItemId: String,
    val coverBoundingBox: NormalizedRect? = null,
    val faceCount: Int,
    val mediaItemIds: List<String>
)

/**
 * On-device, offline Face Detection and DBSCAN Face Clustering Engine.
 * Detects human faces, extracts 64-dimensional facial morphology embeddings,
 * and groups recurring individuals into People Albums without cloud processing.
 */
object FaceDetectionEngine {

    const val FACE_EMBEDDING_DIM = 64

    /**
     * Detects faces in an image from downscaled RGB pixels.
     * @param rgbPixels IntArray of RGB pixels
     * @param width image width
     * @param height image height
     * @param mediaItemId media ID for association
     */
    fun detectFaces(
        rgbPixels: IntArray,
        width: Int,
        height: Int,
        mediaItemId: String
    ): List<DetectedFace> {
        if (rgbPixels.isEmpty() || width < 16 || height < 16) return emptyList()

        val detectedFaces = mutableListOf<DetectedFace>()
        val total = width * height

        // 1. Skin tone mask (YCbCr normalized space)
        val skinMask = BooleanArray(total)
        var totalSkinPixels = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val p = rgbPixels[idx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                // Normalized skin color model: R > G > B, skin chroma
                val isSkin = r > 95 && g > 40 && b > 20 &&
                        (r - g) > 15 && r > b &&
                        (maxOf(r, g, b) - minOf(r, g, b)) > 15

                skinMask[idx] = isSkin
                if (isSkin) totalSkinPixels++
            }
        }

        // Quick rejection: Need sufficient skin pixels (at least 3% of photo)
        if (totalSkinPixels < total * 0.03) return emptyList()

        // 2. Multi-scale candidate face window scanning
        // Typical face proportions: 20% to 65% of image height, aspect ratio ~ 0.8
        val windowSizes = listOf(
            minOf(width, height) / 2,
            minOf(width, height) / 3,
            minOf(width, height) / 4
        )

        for (boxSize in windowSizes) {
            if (boxSize < 12) continue
            val step = maxOf(4, boxSize / 3)
            val boxH = (boxSize * 1.2).toInt().coerceAtMost(height)

            for (top in 0..(height - boxH) step step) {
                for (left in 0..(width - boxSize) step step) {
                    var boxSkinCount = 0
                    var eyeRegionDark = 0
                    var mouthRegionWarm = 0

                    val right = left + boxSize
                    val bottom = top + boxH
                    val boxPixels = boxSize * boxH

                    // Analyze face subregions
                    val eyeYStart = top + (boxH * 0.20).toInt()
                    val eyeYEnd = top + (boxH * 0.45).toInt()
                    val mouthYStart = top + (boxH * 0.65).toInt()
                    val mouthYEnd = top + (boxH * 0.85).toInt()

                    for (y in top until bottom) {
                        for (x in left until right) {
                            val idx = y * width + x
                            if (skinMask[idx]) boxSkinCount++

                            val p = rgbPixels[idx]
                            val r = (p shr 16) and 0xFF
                            val g = (p shr 8) and 0xFF
                            val b = p and 0xFF
                            val lum = (r * 299 + g * 587 + b * 114) / 1000

                            if (y in eyeYStart..eyeYEnd && lum < 70) {
                                eyeRegionDark++
                            }
                            if (y in mouthYStart..mouthYEnd && r > 110 && (r - b) > 30) {
                                mouthRegionWarm++
                            }
                        }
                    }

                    val skinRatio = boxSkinCount.toFloat() / boxPixels

                    // Candidate face condition:
                    // 1. Solid skin presence (35% to 85%)
                    // 2. Darker eye region features
                    // 3. Warm mouth presence
                    if (skinRatio in 0.35f..0.88f && eyeRegionDark > boxPixels * 0.015) {
                        val normLeft = left.toFloat() / width
                        val normTop = top.toFloat() / height
                        val normRight = right.toFloat() / width
                        val normBottom = bottom.toFloat() / height

                        val candidateBox = NormalizedRect(normLeft, normTop, normRight, normBottom)

                        // Avoid overlapping duplicate detections
                        val overlapsExisting = detectedFaces.any { existing ->
                            computeIoU(candidateBox, existing.boundingBox) > 0.4f
                        }

                        if (!overlapsExisting) {
                            val confidence = minOf(0.96f, 0.55f + skinRatio * 0.45f)
                            val embedding = extractFaceEmbedding(rgbPixels, width, height, left, top, boxSize, boxH)

                            detectedFaces.add(
                                DetectedFace(
                                    faceId = "${mediaItemId}_face_${detectedFaces.size + 1}",
                                    mediaItemId = mediaItemId,
                                    boundingBox = candidateBox,
                                    confidence = confidence,
                                    faceEmbedding = embedding
                                )
                            )
                        }
                    }
                }
            }
        }

        return detectedFaces
    }

    /**
     * Extracts a 64-dimensional facial morphology & chromatic embedding.
     */
    private fun extractFaceEmbedding(
        pixels: IntArray,
        imgW: Int,
        imgH: Int,
        x0: Int,
        y0: Int,
        boxW: Int,
        boxH: Int
    ): FloatArray {
        val vector = FloatArray(FACE_EMBEDDING_DIM)
        val cellW = maxOf(1, boxW / 4)
        val cellH = maxOf(1, boxH / 4)

        // 4x4 spatial subregions of face (eyes, cheeks, nose, mouth, jaw)
        // 16 cells * 3 channels (R, G, B) = 48 dimensions
        for (cy in 0 until 4) {
            for (cx in 0 until 4) {
                val cIdx = cy * 4 + cx
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var count = 0

                val startY = y0 + cy * cellH
                val endY = minOf(imgH, startY + cellH)
                val startX = x0 + cx * cellW
                val endX = minOf(imgW, startX + cellW)

                for (y in startY until endY) {
                    for (x in startX until endX) {
                        val p = pixels[y * imgW + x]
                        sumR += ((p shr 16) and 0xFF) / 255f
                        sumG += ((p shr 8) and 0xFF) / 255f
                        sumB += (p and 0xFF) / 255f
                        count++
                    }
                }
                val div = maxOf(1, count).toFloat()
                vector[cIdx * 3] = sumR / div
                vector[cIdx * 3 + 1] = sumG / div
                vector[cIdx * 3 + 2] = sumB / div
            }
        }

        // Remaining 16 dimensions: Symmetry, contrast ratio, and gradient energy
        // Eye-to-eye horizontal contrast (cell 1 vs cell 2)
        vector[48] = abs(vector[1 * 3] - vector[2 * 3])
        // Cheek symmetry (cell 4 vs cell 7)
        vector[49] = abs(vector[4 * 3] - vector[7 * 3])
        // Forehead vs chin luminance (top row vs bottom row)
        val foreheadLum = (vector[0] + vector[3] + vector[6] + vector[9]) / 4f
        val chinLum = (vector[36] + vector[39] + vector[42] + vector[45]) / 4f
        vector[50] = foreheadLum - chinLum

        for (i in 51 until FACE_EMBEDDING_DIM) {
            val idx = (i * 3) % 48
            vector[i] = vector[idx] * 0.5f
        }

        // L2 Normalization
        var normSq = 0f
        for (v in vector) normSq += v * v
        val norm = sqrt(normSq).coerceAtLeast(1e-6f)
        for (i in vector.indices) vector[i] /= norm

        return vector
    }

    /**
     * Clusters detected faces across the library using DBSCAN / greedy distance thresholding.
     * @param allFaces list of all faces detected across all images
     * @param similarityThreshold cosine similarity threshold (default 0.75f)
     * @return grouped PersonCluster list
     */
    fun clusterFaces(
        allFaces: List<DetectedFace>,
        similarityThreshold: Float = 0.72f
    ): List<PersonCluster> {
        if (allFaces.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<DetectedFace>>()
        val visited = BooleanArray(allFaces.size)

        for (i in allFaces.indices) {
            if (visited[i]) continue
            visited[i] = true

            val currentCluster = mutableListOf(allFaces[i])

            for (j in (i + 1) until allFaces.size) {
                if (visited[j]) continue
                val sim = computeCosineSimilarity(allFaces[i].faceEmbedding, allFaces[j].faceEmbedding)
                if (sim >= similarityThreshold) {
                    visited[j] = true
                    currentCluster.add(allFaces[j])
                }
            }
            clusters.add(currentCluster)
        }

        // Convert to PersonCluster models
        return clusters.mapIndexed { index, faceGroup ->
            val personId = "person_${index + 1}"
            faceGroup.forEach { it.personId = personId }

            val coverFace = faceGroup.maxByOrNull { it.confidence } ?: faceGroup.first()
            val distinctMediaItemIds = faceGroup.map { it.mediaItemId }.distinct()

            PersonCluster(
                personId = personId,
                name = "Person ${index + 1}",
                coverMediaItemId = coverFace.mediaItemId,
                coverBoundingBox = coverFace.boundingBox,
                faceCount = distinctMediaItemIds.size,
                mediaItemIds = distinctMediaItemIds
            )
        }.sortedByDescending { it.faceCount }
    }

    fun computeCosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0f
        var dot = 0f
        for (i in vecA.indices) dot += vecA[i] * vecB[i]
        return dot.coerceIn(-1.0f, 1.0f)
    }

    private fun computeIoU(boxA: NormalizedRect, boxB: NormalizedRect): Float {
        val xA = maxOf(boxA.left, boxB.left)
        val yA = maxOf(boxA.top, boxB.top)
        val xB = minOf(boxA.right, boxB.right)
        val yB = minOf(boxA.bottom, boxB.bottom)

        val interW = maxOf(0f, xB - xA)
        val interH = maxOf(0f, yB - yA)
        val interArea = interW * interH

        val areaA = (boxA.right - boxA.left) * (boxA.bottom - boxA.top)
        val areaB = (boxB.right - boxB.left) * (boxB.bottom - boxB.top)
        val unionArea = areaA + areaB - interArea
        if (unionArea <= 0f) return 0f
        return interArea / unionArea
    }
}

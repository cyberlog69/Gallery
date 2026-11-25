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
 * On-device, offline face *candidate* detection and appearance clustering.
 *
 * Honest scope: this is chroma-based blob detection with sub-region heuristics, not
 * a trained face detector. It finds skin-toned regions that look roughly face-shaped
 * and groups them by colour/structure similarity, which is enough for a "People"
 * shelf but will miss profile faces, dark skin under poor light, and small faces in
 * group shots. The UI must present it as "grouped by appearance", never as
 * identity recognition.
 *
 * Input should be at least 200px on the long edge so a face is >= 24px.
 */
object FaceDetectionEngine {

    const val FACE_EMBEDDING_DIM = 64
    const val MIN_FACE_BOX_PX = 24
    private const val MAX_FACES_PER_IMAGE = 3
    private const val MAX_PEOPLE_GROUPS = 60
    private const val MIN_FACE_CONFIDENCE = 0.65f

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

        // 2. Multi-scale candidate window scanning.
        // Windows are fractions of the short edge so this scales with input size.
        val shortEdge = minOf(width, height)
        val windowSizes = listOf(
            shortEdge / 2,
            shortEdge / 3,
            shortEdge / 4,
            shortEdge / 6
        ).filter { it >= MIN_FACE_BOX_PX }.distinct()

        for (boxSize in windowSizes) {
            val step = maxOf(6, boxSize / 4)
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
                            val confidence = minOf(0.90f, 0.45f + skinRatio * 0.35f)
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
                            if (detectedFaces.size >= MAX_FACES_PER_IMAGE) return detectedFaces
                        }
                    }
                }
            }
        }

        return detectedFaces
    }

    /**
     * Extracts a 64-dimensional appearance descriptor from a face box.
     *
     * Deliberately structure-dominant: absolute skin colour is the *least* useful
     * signal (it collapses every face into one group), so each 4x4 cell stores
     * luma relative to the box mean, two chroma differences, and gradient energy.
     * This is still a hand-crafted descriptor, not a learned face embedding; it
     * groups similar-looking faces, it does not identify people.
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

        val cellLuma = FloatArray(16)
        val cellChromaRg = FloatArray(16)
        val cellChromaRb = FloatArray(16)
        val cellEnergy = FloatArray(16)
        val cellCount = IntArray(16)

        for (cy in 0 until 4) {
            for (cx in 0 until 4) {
                val cIdx = cy * 4 + cx
                val startY = y0 + cy * cellH
                val endY = minOf(imgH, startY + cellH)
                val startX = x0 + cx * cellW
                val endX = minOf(imgW, startX + cellW)

                for (y in startY until endY) {
                    val row = y * imgW
                    for (x in startX until endX) {
                        val p = pixels[row + x]
                        val r = ((p shr 16) and 0xFF) / 255f
                        val g = ((p shr 8) and 0xFF) / 255f
                        val b = (p and 0xFF) / 255f
                        val lum = 0.299f * r + 0.587f * g + 0.114f * b

                        cellLuma[cIdx] += lum
                        cellChromaRg[cIdx] += (r - g)
                        cellChromaRb[cIdx] += (r - b)
                        cellCount[cIdx]++

                        // Horizontal + vertical gradient energy inside the cell.
                        if (x + 1 < endX) {
                            val q = pixels[row + x + 1]
                            val qLum = 0.299f * ((q shr 16) and 0xFF) / 255f +
                                    0.587f * ((q shr 8) and 0xFF) / 255f +
                                    0.114f * (q and 0xFF) / 255f
                            cellEnergy[cIdx] += kotlin.math.abs(lum - qLum)
                        }
                        if (y + 1 < endY) {
                            val q = pixels[(y + 1) * imgW + x]
                            val qLum = 0.299f * ((q shr 16) and 0xFF) / 255f +
                                    0.587f * ((q shr 8) and 0xFF) / 255f +
                                    0.114f * (q and 0xFF) / 255f
                            cellEnergy[cIdx] += kotlin.math.abs(lum - qLum)
                        }
                    }
                }
            }
        }

        var boxLuma = 0f
        var boxCount = 0
        for (i in 0 until 16) {
            boxLuma += cellLuma[i]
            boxCount += cellCount[i]
        }
        val meanLuma = if (boxCount == 0) 0f else boxLuma / boxCount

        for (i in 0 until 16) {
            val div = maxOf(1, cellCount[i]).toFloat()
            vector[i * 4] = (cellLuma[i] / div) - meanLuma
            vector[i * 4 + 1] = cellChromaRg[i] / div
            vector[i * 4 + 2] = cellChromaRb[i] / div
            vector[i * 4 + 3] = cellEnergy[i] / div
        }

        normalizeInPlace(vector)
        return vector
    }

    /**
     * Groups detected faces by appearance similarity.
     *
     * Greedy nearest-centroid assignment: deterministic (input is sorted first), O(n·k)
     * instead of O(n²), and stable across re-scans because cluster ids derive from the
     * cover face rather than from array position.
     *
     * @param similarityThreshold cosine similarity required to join an existing group
     */
    fun clusterFaces(
        allFaces: List<DetectedFace>,
        similarityThreshold: Float = 0.74f,
        minDistinctMedia: Int = 3
    ): List<PersonCluster> {
        if (allFaces.isEmpty()) return emptyList()

        data class Group(
            val faces: MutableList<DetectedFace>,
            var centroid: FloatArray
        )

        // Chrome-style heuristic detection produces several overlapping candidates
        // per photo. Keep only the strongest three per item so one photo cannot
        // dominate a cluster (and so the shelf is not flooded with junk groups).
        val sorted = allFaces
            .filter { it.confidence >= MIN_FACE_CONFIDENCE }
            .groupBy { it.mediaItemId }
            .values
            .flatMap { faces ->
                faces.sortedWith(
                    compareByDescending<DetectedFace> { it.confidence }
                        .thenBy { it.boundingBox.top }
                        .thenBy { it.boundingBox.left }
                ).take(MAX_FACES_PER_IMAGE)
            }
            .sortedWith(
                compareBy<DetectedFace> { it.mediaItemId }
                    .thenBy { it.boundingBox.top }
                    .thenBy { it.boundingBox.left }
            )

        val groups = mutableListOf<Group>()

        for (face in sorted) {
            var bestIndex = -1
            var bestSimilarity = -1f
            for (i in groups.indices) {
                val similarity = computeCosineSimilarity(groups[i].centroid, face.faceEmbedding)
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestIndex = i
                }
            }

            if (bestIndex >= 0 && bestSimilarity >= similarityThreshold) {
                val group = groups[bestIndex]
                group.faces += face
                // Incremental centroid update (keeps the mean L2-normalized).
                val count = group.faces.size.toFloat()
                val updated = FloatArray(FACE_EMBEDDING_DIM)
                for (d in 0 until FACE_EMBEDDING_DIM) {
                    updated[d] = group.centroid[d] + (face.faceEmbedding[d] - group.centroid[d]) / count
                }
                normalizeInPlace(updated)
                group.centroid = updated
            } else {
                groups += Group(
                    faces = mutableListOf(face),
                    centroid = face.faceEmbedding.copyOf()
                )
            }
        }

        return groups
            .filter { group -> group.faces.map { it.mediaItemId }.distinct().size >= minDistinctMedia }
            .sortedByDescending { group -> group.faces.map { f -> f.mediaItemId }.distinct().size }
            .take(MAX_PEOPLE_GROUPS)
            .mapIndexed { index, group ->
                val coverFace = group.faces.maxByOrNull { it.confidence } ?: group.faces.first()
                val distinctMedia = group.faces.map { it.mediaItemId }.distinct()
                val personId = "person_${coverFace.mediaItemId}_${(coverFace.boundingBox.left * 100).toInt()}_${(coverFace.boundingBox.top * 100).toInt()}"

                group.faces.forEach { it.personId = personId }

                PersonCluster(
                    personId = personId,
                    name = "Person ${index + 1}",
                    coverMediaItemId = coverFace.mediaItemId,
                    coverBoundingBox = coverFace.boundingBox,
                    faceCount = distinctMedia.size,
                    mediaItemIds = distinctMedia
                )
            }
    }

    private fun normalizeInPlace(vector: FloatArray) {
        var normSq = 0f
        for (v in vector) normSq += v * v
        val norm = sqrt(normSq).coerceAtLeast(1e-6f)
        for (i in vector.indices) vector[i] /= norm
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

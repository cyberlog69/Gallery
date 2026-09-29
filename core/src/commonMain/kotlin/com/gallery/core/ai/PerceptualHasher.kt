package com.gallery.core.ai

import com.gallery.core.model.MediaItem

data class DuplicateCluster(
    val primaryItem: MediaItem,
    val duplicateItems: List<MediaItem>,
    val averageDistance: Int
)

/**
 * High-performance, offline perceptual image hasher and duplicate detector.
 * Implements 64-bit difference hash (dHash) and average hash (aHash) in pure Kotlin.
 */
object PerceptualHasher {

    /**
     * Computes a 64-bit difference hash (dHash) from downscaled image pixels.
     * Expects pixels scaled to at least 9x8 resolution (or 64x64).
     */
    fun computeDHash(pixels: IntArray, width: Int, height: Int): ULong {
        if (pixels.isEmpty() || width < 9 || height < 8) return 0UL

        // Downscale to 9x8 grayscale
        val gray9x8 = IntArray(9 * 8)
        for (y in 0 until 8) {
            val srcY = (y * height) / 8
            for (x in 0 until 9) {
                val srcX = (x * width) / 9
                val pixel = pixels[srcY * width + srcX]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                gray9x8[y * 9 + x] = (r * 299 + g * 587 + b * 114) / 1000
            }
        }

        // Compare adjacent pixels horizontally: 8 rows of 8 comparisons = 64 bits
        var hash = 0UL
        var bitIndex = 0
        for (y in 0 until 8) {
            val rowOffset = y * 9
            for (x in 0 until 8) {
                val left = gray9x8[rowOffset + x]
                val right = gray9x8[rowOffset + x + 1]
                if (left > right) {
                    hash = hash or (1UL shl bitIndex)
                }
                bitIndex++
            }
        }
        return hash
    }

    /**
     * Computes the Hamming distance between two 64-bit perceptual hashes.
     * Represents the number of differing bits (0 to 64).
     */
    fun hammingDistance(hashA: ULong, hashB: ULong): Int {
        return (hashA xor hashB).countOneBits()
    }

    /**
     * Determines whether two images are duplicates or near-duplicates.
     * @param maxDistance Maximum Hamming distance threshold (default 4 for duplicates, 10 for burst shots)
     */
    fun isSimilar(hashA: ULong, hashB: ULong, maxDistance: Int = 4): Boolean {
        if (hashA == 0UL || hashB == 0UL) return false
        return hammingDistance(hashA, hashB) <= maxDistance
    }

    /**
     * Clusters a collection of hashed media items into groups of duplicates.
     */
    fun clusterDuplicates(
        itemsWithHashes: List<Pair<MediaItem, ULong>>,
        maxDistance: Int = 4
    ): List<DuplicateCluster> {
        val visited = mutableSetOf<String>()
        val clusters = mutableListOf<DuplicateCluster>()

        for (i in itemsWithHashes.indices) {
            val (itemA, hashA) = itemsWithHashes[i]
            if (visited.contains(itemA.id) || hashA == 0UL) continue

            val duplicates = mutableListOf<MediaItem>()
            var totalDistance = 0

            for (j in (i + 1) until itemsWithHashes.size) {
                val (itemB, hashB) = itemsWithHashes[j]
                if (visited.contains(itemB.id) || hashB == 0UL) continue

                val distance = hammingDistance(hashA, hashB)
                if (distance <= maxDistance) {
                    duplicates.add(itemB)
                    visited.add(itemB.id)
                    totalDistance += distance
                }
            }

            if (duplicates.isNotEmpty()) {
                visited.add(itemA.id)
                val avgDist = totalDistance / duplicates.size
                clusters.add(DuplicateCluster(itemA, duplicates, avgDist))
            }
        }

        return clusters
    }
}

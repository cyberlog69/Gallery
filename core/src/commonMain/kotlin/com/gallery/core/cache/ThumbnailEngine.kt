package com.gallery.core.cache

import kotlin.math.max

/**
 * High performance, RAM-optimized caching and sub-sampling calculations.
 */
object ThumbnailEngine {

    /**
     * Calculates the best inSampleSize factor (power of 2) for subsampling large images.
     * Prevents loading multi-megapixel images directly into RAM.
     */
    fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    /**
     * Compute cache key from file path and last modified timestamp.
     */
    fun getCacheKey(path: String, lastModified: Long, sizePx: Int): String {
        val raw = "${path}_${lastModified}_$sizePx"
        return raw.hashCode().toString(16)
    }
}

/**
 * Thread-safe LRU Memory Cache for downscaled image thumbnails.
 * Capped by total bytes to guarantee low RAM footprint.
 */
class MemoryLruCache<K : Any, V : Any>(
    private val maxSizeBytes: Long = 32L * 1024L * 1024L, // 32MB default memory limit
    private val sizeOf: (key: K, value: V) -> Long
) {
    private val map = LinkedHashMap<K, V>(100, 0.75f, true)
    private var currentSizeBytes: Long = 0L
    private val lock = Any()

    fun get(key: K): V? {
        synchronized(lock) {
            return map[key]
        }
    }

    fun put(key: K, value: V) {
        val valueSize = sizeOf(key, value)
        synchronized(lock) {
            val previous = map.put(key, value)
            if (previous != null) {
                currentSizeBytes -= sizeOf(key, previous)
            }
            currentSizeBytes += valueSize

            trimToSize(maxSizeBytes)
        }
    }

    fun remove(key: K): V? {
        synchronized(lock) {
            val removed = map.remove(key)
            if (removed != null) {
                currentSizeBytes -= sizeOf(key, removed)
            }
            return removed
        }
    }

    fun clear() {
        synchronized(lock) {
            map.clear()
            currentSizeBytes = 0L
        }
    }

    fun currentSize(): Long = synchronized(lock) { currentSizeBytes }

    fun count(): Int = synchronized(lock) { map.size }

    private fun trimToSize(maxBytes: Long) {
        val iterator = map.entries.iterator()
        while (currentSizeBytes > maxBytes && iterator.hasNext()) {
            val entry = iterator.next()
            currentSizeBytes -= sizeOf(entry.key, entry.value)
            iterator.remove()
        }
    }
}

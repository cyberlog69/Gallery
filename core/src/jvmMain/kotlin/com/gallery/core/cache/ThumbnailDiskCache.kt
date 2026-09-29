package com.gallery.core.cache

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Metadata record for a disk-cached thumbnail.
 */
data class DiskCacheEntry(
    val key: String,
    val file: File,
    val sizeBytes: Long,
    var lastAccessTime: Long
)

/**
 * Thread-safe persistent disk cache with LRU eviction and indexed metadata tracking.
 * Prevents redundant subsampled bitmap decodes across application restarts.
 */
class ThumbnailDiskCache(
    val cacheDir: File = File(System.getProperty("user.home"), ".gallery_cache/thumbnails"),
    private val maxCapacityBytes: Long = 256L * 1024L * 1024L // 256MB default ceiling
) {
    private val index = ConcurrentHashMap<String, DiskCacheEntry>()
    private var totalSizeBytes = 0L
    private val lock = Any()

    init {
        buildIndex()
    }

    private fun buildIndex() {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            return
        }

        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.length() > 0) {
                val key = file.nameWithoutExtension
                val size = file.length()
                index[key] = DiskCacheEntry(
                    key = key,
                    file = file,
                    sizeBytes = size,
                    lastAccessTime = file.lastModified()
                )
                totalSizeBytes += size
            }
        }

        trimToLimit()
    }

    fun has(key: String): Boolean = index.containsKey(key) && index[key]?.file?.exists() == true

    fun get(key: String): ByteArray? {
        val entry = index[key] ?: return null
        if (!entry.file.exists()) {
            index.remove(key)
            return null
        }
        val now = System.currentTimeMillis()
        entry.lastAccessTime = now
        entry.file.setLastModified(now)

        return try {
            entry.file.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    fun put(key: String, data: ByteArray) {
        if (data.isEmpty()) return

        synchronized(lock) {
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val targetFile = File(cacheDir, "$key.thumb")
            try {
                targetFile.writeBytes(data)
                val size = data.size.toLong()
                val existing = index.put(key, DiskCacheEntry(key, targetFile, size, System.currentTimeMillis()))
                if (existing != null) {
                    totalSizeBytes -= existing.sizeBytes
                }
                totalSizeBytes += size
                trimToLimit()
            } catch (_: Exception) {
                // Non-critical cache write failure
            }
        }
    }

    fun remove(key: String): Boolean {
        synchronized(lock) {
            val entry = index.remove(key) ?: return false
            totalSizeBytes -= entry.sizeBytes
            return entry.file.delete()
        }
    }

    fun clear() {
        synchronized(lock) {
            index.values.forEach { it.file.delete() }
            index.clear()
            totalSizeBytes = 0L
        }
    }

    fun currentSize(): Long = synchronized(lock) { totalSizeBytes }

    fun entryCount(): Int = index.size

    private fun trimToLimit() {
        if (totalSizeBytes <= maxCapacityBytes) return

        val sortedEntries = index.values.sortedBy { it.lastAccessTime }
        for (entry in sortedEntries) {
            if (totalSizeBytes <= maxCapacityBytes) break
            if (entry.file.delete()) {
                index.remove(entry.key)
                totalSizeBytes -= entry.sizeBytes
            }
        }
    }
}

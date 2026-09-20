package com.gallery.core

import com.gallery.core.ai.OfflineAiClassifier
import com.gallery.core.cache.MemoryLruCache
import com.gallery.core.cache.ThumbnailEngine
import com.gallery.core.model.AiCategory
import com.gallery.core.model.MediaType
import com.gallery.core.util.MediaFormatDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {

    @Test
    fun testMediaFormatDetectorImageFormats() {
        val testImages = listOf(
            "vacation.jpg", "photo.jpeg", "screenshot.png", "sticker.webp",
            "animation.gif", "bitmap.bmp", "apple.heic", "live.heif",
            "modern.avif", "graphic.svg", "scan.tiff", "camera.dng",
            "canon.cr2", "nikon.nef", "sony.arw"
        )
        for (file in testImages) {
            assertTrue("Expected $file to be supported", MediaFormatDetector.isSupported(file))
            assertTrue("Expected $file to be recognized as image", MediaFormatDetector.isImage(file))
            assertEquals(MediaType.IMAGE, MediaFormatDetector.getMediaType(file))
        }
    }

    @Test
    fun testMediaFormatDetectorVideoFormats() {
        val testVideos = listOf(
            "movie.mp4", "clip.mkv", "web.webm", "legacy.avi",
            "quicktime.mov", "cellular.3gp", "stream.ts", "windows.wmv"
        )
        for (file in testVideos) {
            assertTrue("Expected $file to be supported", MediaFormatDetector.isSupported(file))
            assertTrue("Expected $file to be recognized as video", MediaFormatDetector.isVideo(file))
            assertEquals(MediaType.VIDEO, MediaFormatDetector.getMediaType(file))
        }
    }

    @Test
    fun testThumbnailEngineSubsampling() {
        // 4000x3000 down to 256x256
        val sampleSize = ThumbnailEngine.calculateInSampleSize(4000, 3000, 256, 256)
        assertTrue("Subsampling factor must be power of 2 and >= 8", sampleSize >= 8)

        // Equal or smaller image should not be subsampled
        val sampleSizeSmall = ThumbnailEngine.calculateInSampleSize(200, 200, 256, 256)
        assertEquals(1, sampleSizeSmall)
    }

    @Test
    fun testMemoryLruCacheEviction() {
        // Limit cache to 1000 bytes
        val cache = MemoryLruCache<String, ByteArray>(maxSizeBytes = 1000) { _, v -> v.size.toLong() }

        cache.put("item1", ByteArray(400))
        cache.put("item2", ByteArray(400))
        assertEquals(800L, cache.currentSize())

        // Adding 400 more bytes must evict item1 (oldest)
        cache.put("item3", ByteArray(400))
        assertTrue("Cache size must stay <= 1000 bytes", cache.currentSize() <= 1000)
        assertEquals(null, cache.get("item1"))
        assertEquals(2, cache.count())
    }

    @Test
    fun testOfflineAiClassifierScreenshotsAndDocuments() {
        val dummyPixels = IntArray(64 * 64) { 0xFFFFFFFF.toInt() }

        // Test screenshot filename heuristic
        val screenshotResult = OfflineAiClassifier.classify(dummyPixels, 64, 64, "Screenshot_20260920_142010.png")
        assertEquals(AiCategory.SCREENSHOTS, screenshotResult.category)
        assertTrue(screenshotResult.confidence > 0.9f)

        // Test document pixel feature classification (high white paper ratio + edges)
        val docPixels = IntArray(64 * 64) { idx ->
            // alternate white and black lines for text
            if ((idx / 64) % 4 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val docResult = OfflineAiClassifier.classify(docPixels, 64, 64, "scan.jpg")
        assertEquals(AiCategory.DOCUMENTS, docResult.category)
    }

    @Test
    fun testOfflineAiClassifierNature() {
        // Mostly lush green pixels
        val greenPixels = IntArray(64 * 64) { 0xFF2E8B57.toInt() } // SeaGreen
        val natureResult = OfflineAiClassifier.classify(greenPixels, 64, 64, "forest.jpg")
        assertEquals(AiCategory.NATURE, natureResult.category)
        assertTrue(natureResult.confidence > 0.7f)
    }
}

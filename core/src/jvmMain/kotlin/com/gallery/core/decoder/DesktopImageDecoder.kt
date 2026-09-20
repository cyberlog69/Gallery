package com.gallery.core.decoder

import com.gallery.core.ai.AiClassificationResult
import com.gallery.core.ai.OfflineAiClassifier
import com.gallery.core.cache.MemoryLruCache
import com.gallery.core.cache.ThumbnailEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.stream.ImageInputStream

object DesktopImageDecoder {

    // 32MB Memory LRU Cache for decoded thumbnails
    private val thumbnailCache = MemoryLruCache<String, BufferedImage>(
        maxSizeBytes = 32L * 1024L * 1024L
    ) { _, image ->
        (image.width * image.height * 4L)
    }

    /**
     * Reads image bounds (width & height) without decoding pixel data into RAM.
     */
    fun readImageBounds(file: File): Pair<Int, Int>? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            ImageIO.createImageInputStream(file).use { iis ->
                if (iis == null) return null
                val readers = ImageIO.getImageReaders(iis)
                if (readers.hasNext()) {
                    val reader = readers.next()
                    try {
                        reader.input = iis
                        val width = reader.getWidth(0)
                        val height = reader.getHeight(0)
                        Pair(width, height)
                    } finally {
                        reader.dispose()
                    }
                } else null
            }
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Loads a RAM-optimized subsampled thumbnail using ImageReadParam subsampling.
     * Prevents OutOfMemoryError on massive camera photos.
     */
    suspend fun loadThumbnail(file: File, targetSizePx: Int = 256): BufferedImage? = withContext(Dispatchers.IO) {
        val cacheKey = ThumbnailEngine.getCacheKey(file.absolutePath, file.lastModified(), targetSizePx)
        val cached = thumbnailCache.get(cacheKey)
        if (cached != null) return@withContext cached

        if (!file.exists() || file.length() == 0L) return@withContext null

        try {
            ImageIO.createImageInputStream(file).use { iis ->
                if (iis == null) return@withContext null
                val readers = ImageIO.getImageReaders(iis)
                if (!readers.hasNext()) return@withContext null

                val reader = readers.next() as ImageReader
                try {
                    reader.input = iis
                    val origWidth = reader.getWidth(0)
                    val origHeight = reader.getHeight(0)

                    val sampleSize = ThumbnailEngine.calculateInSampleSize(
                        origWidth, origHeight, targetSizePx, targetSizePx
                    )

                    val param = reader.defaultReadParam
                    param.setSourceSubsampling(sampleSize, sampleSize, 0, 0)

                    val subsampledImage = reader.read(0, param) ?: return@withContext null

                    // Resize smoothly to exact requested box while maintaining aspect ratio
                    val finalThumb = resizeToFit(subsampledImage, targetSizePx, targetSizePx)
                    thumbnailCache.put(cacheKey, finalThumb)
                    finalThumb
                } finally {
                    reader.dispose()
                }
            }
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Runs offline AI classification on a small 64x64 downscaled version of the image.
     */
    suspend fun classifyImage(file: File): AiClassificationResult = withContext(Dispatchers.Default) {
        val thumb = loadThumbnail(file, targetSizePx = 64)
            ?: return@withContext AiClassificationResult(com.gallery.core.model.AiCategory.UNCATEGORIZED, 0.0f, emptyList())

        val w = thumb.width
        val h = thumb.height
        val pixels = IntArray(w * h)
        thumb.getRGB(0, 0, w, h, pixels, 0, w)

        OfflineAiClassifier.classify(pixels, w, h, file.name)
    }

    /**
     * High quality bilinear downscaler for thumbnails.
     */
    fun resizeToFit(src: BufferedImage, maxWidth: Int, maxHeight: Int): BufferedImage {
        val srcWidth = src.width
        val srcHeight = src.height

        val ratio = minOf(maxWidth.toDouble() / srcWidth, maxHeight.toDouble() / srcHeight)
        val targetWidth = maxOf(1, (srcWidth * ratio).toInt())
        val targetHeight = maxOf(1, (srcHeight * ratio).toInt())

        val result = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d: Graphics2D = result.createGraphics()
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.drawImage(src, 0, 0, targetWidth, targetHeight, null)
        } finally {
            g2d.dispose()
        }
        return result
    }
}

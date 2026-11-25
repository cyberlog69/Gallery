package com.gallery.core.analyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.gallery.core.ai.DocumentLayoutAnalyzer
import com.gallery.core.ai.FaceDetectionEngine
import com.gallery.core.ai.FaceSignature
import com.gallery.core.ai.OfflineVisionEngine
import com.gallery.core.ai.PerceptualHasher
import com.gallery.core.cache.ThumbnailEngine
import com.gallery.core.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single entry point for the offline analysis pass.
 *
 * Everything runs on tiny, subsampled decodes: a 256px frame is enough for the
 * colour/edge descriptor and for face candidates, and document layout gets its own
 * 640px pass only when the first pass suggests a page of text.
 */
object MediaAnalyzer {

    /** Long-edge size for the main descriptor pass. 256px keeps faces >= 24px. */
    const val ANALYSIS_EDGE = 256

    /** Long-edge size used only for the document layout pass. */
    const val DOCUMENT_EDGE = 640

    data class Result(
        val category: com.gallery.core.model.AiCategory,
        val confidence: Float,
        val tags: List<String>,
        val quantizedEmbedding: ByteArray,
        val perceptualHash: ULong,
        val faceSignatures: List<FaceSignature>,
        val textRegionCount: Int
    )

    suspend fun analyze(
        context: Context,
        item: MediaItem,
        needsDocumentPass: Boolean = false
    ): Result? = withContext(Dispatchers.Default) {
        var frame: Bitmap? = null
        try {
            frame = decodeScaled(context, item.uri, ANALYSIS_EDGE) ?: return@withContext null
            val width = frame.width
            val height = frame.height
            val pixels = IntArray(width * height)
            frame.getPixels(pixels, 0, width, 0, 0, width, height)

            val vision = OfflineVisionEngine.processImage(pixels, width, height, item.displayName)
            val hash = PerceptualHasher.computeDHash(pixels, width, height)

            val faces = if (vision.category != com.gallery.core.model.AiCategory.SCREENSHOTS) {
                FaceDetectionEngine.detectFaces(pixels, width, height, item.id)
                    .map(FaceSignature::from)
            } else {
                emptyList()
            }

            val likelyDocument = needsDocumentPass ||
                    vision.category == com.gallery.core.model.AiCategory.DOCUMENTS ||
                    vision.tags.any { it == "document" }

            var textRegions = 0
            if (likelyDocument) {
                val page = decodeScaled(context, item.uri, DOCUMENT_EDGE)
                if (page != null) {
                    try {
                        val pagePixels = IntArray(page.width * page.height)
                        page.getPixels(pagePixels, 0, page.width, 0, 0, page.width, page.height)
                        textRegions = DocumentLayoutAnalyzer
                            .analyze(pagePixels, page.width, page.height, item.displayName)
                            .textRegionCount
                    } finally {
                        page.recycle()
                    }
                }
            }

            Result(
                category = vision.category,
                confidence = vision.confidence,
                tags = vision.tags,
                quantizedEmbedding = vision.quantizedEmbedding,
                perceptualHash = hash,
                faceSignatures = faces,
                textRegionCount = textRegions
            )
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Throwable) {
            null
        } finally {
            frame?.recycle()
        }
    }

    /**
     * Subsampled decode that never allocates the full-resolution bitmap.
     * Returns a bitmap whose long edge is <= [maxEdge].
     */
    private fun decodeScaled(context: Context, uriString: String, maxEdge: Int): Bitmap? {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = ThumbnailEngine.calculateInSampleSize(
            bounds.outWidth, bounds.outHeight, maxEdge, maxEdge
        )
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val longEdge = maxOf(decoded.width, decoded.height)
        if (longEdge <= maxEdge) return decoded

        val scale = maxEdge.toFloat() / longEdge
        val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
        val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}

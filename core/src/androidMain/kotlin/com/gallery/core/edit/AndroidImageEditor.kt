package com.gallery.core.edit

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.gallery.core.model.ImageEditState
import com.gallery.core.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android implementation for applying and exporting non-destructive image edits.
 * Handles high-resolution decoding, color/crop transformations, and MediaStore persistence.
 */
object AndroidImageEditor {

    /**
     * Loads and auto-rotates a Bitmap from URI or file path, constrained by maxDimension to prevent OOM.
     */
    suspend fun loadBitmap(
        context: Context,
        uriString: String,
        maxDimension: Int = 2048
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)

            // 1. Decode bounds
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            openInputStream(context, uri, uriString)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null

            // 2. Sample size calculation
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            // 3. Decode actual bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = openInputStream(context, uri, uriString)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            // 4. Handle EXIF orientation (rotation and mirrored variants)
            val transform = getExifTransform(context, uri, uriString)
            if (transform.rotation != 0f || transform.flipHorizontal) {
                val matrix = Matrix().apply {
                    if (transform.flipHorizontal) postScale(-1f, 1f)
                    if (transform.rotation != 0f) postRotate(transform.rotation)
                }
                val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (oriented !== decoded) decoded.recycle()
                oriented
            } else {
                decoded
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Applies ImageEditState to a Bitmap in memory using ImageEditEngine.
     */
    fun applyEdit(source: Bitmap, state: ImageEditState): Bitmap {
        if (!state.isEdited) return source

        val width = source.width
        val height = source.height
        val srcPixels = IntArray(width * height)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val edited = ImageEditEngine.applyEdits(srcPixels, width, height, state)
        val result = Bitmap.createBitmap(edited.pixels, edited.width, edited.height, Bitmap.Config.ARGB_8888)

        if (state.straightenAngle != 0f) {
            val matrix = Matrix().apply { postRotate(state.straightenAngle) }
            val straightened = Bitmap.createBitmap(result, 0, 0, result.width, result.height, matrix, true)
            if (straightened !== result) result.recycle()
            return straightened
        }

        return result
    }

    private fun compressFor(bitmap: Bitmap, mimeType: String, quality: Int): Pair<Bitmap.CompressFormat, Int> {
        val format = when {
            mimeType.contains("png", ignoreCase = true) -> Bitmap.CompressFormat.PNG
            mimeType.contains("webp", ignoreCase = true) ->
                if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS
                else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
        return format to if (format == Bitmap.CompressFormat.JPEG) quality else 100
    }

    /**
     * Saves the edited image as a new copy in Pictures/Gallery.
     * Uses transient high-resolution bitmap and explicitly recycles allocations to satisfy Google Play Low-RAM standards.
     */
    suspend fun saveAsCopy(
        context: Context,
        originalUri: String,
        sourceBitmap: Bitmap,
        state: ImageEditState,
        originalDisplayName: String? = null,
        quality: Int = 95
    ): Uri? = withContext(Dispatchers.IO) {
        var highResSource: Bitmap? = null
        var edited: Bitmap? = null
        try {
            // Load higher resolution for master export (up to 3840px), fallback to current bitmap
            highResSource = loadBitmap(context, originalUri, maxDimension = 3840) ?: sourceBitmap
            edited = applyEdit(highResSource, state)

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val baseName = originalDisplayName?.substringBeforeLast('.') ?: "IMG_$timeStamp"
            val newFileName = "${baseName}_edited_$timeStamp.jpg"

            val resolver = context.contentResolver
            val targetUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, newFileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Gallery")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues)
                    ?: return@withContext null

                resolver.openOutputStream(uri)?.use { out ->
                    edited.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                uri
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val galleryDir = File(picturesDir, "Gallery").apply { if (!exists()) mkdirs() }
                val targetFile = File(galleryDir, newFileName)

                FileOutputStream(targetFile).use { out ->
                    edited.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
                Uri.fromFile(targetFile)
            }

            targetUri
        } catch (_: Exception) {
            null
        } finally {
            if (highResSource != null && highResSource != sourceBitmap && !highResSource.isRecycled) {
                highResSource.recycle()
            }
            if (edited != null && edited != sourceBitmap && !edited.isRecycled) {
                edited.recycle()
            }
        }
    }

    /**
     * Saves edits in place, preserving original by creating a .bak file if possible.
     * Guarantees all transient bitmaps are recycled immediately.
     */
    suspend fun saveInPlace(
        context: Context,
        originalItem: MediaItem,
        sourceBitmap: Bitmap,
        state: ImageEditState,
        quality: Int = 95
    ): Boolean = withContext(Dispatchers.IO) {
        var highResSource: Bitmap? = null
        var edited: Bitmap? = null
        try {
            highResSource = loadBitmap(context, originalItem.uri, maxDimension = 3840) ?: sourceBitmap
            edited = applyEdit(highResSource, state)
            val file = File(originalItem.path)

            if (file.exists() && file.canWrite()) {
                val backupFile = File(file.parentFile, "${file.name}.bak")
                if (!backupFile.exists()) {
                    file.copyTo(backupFile, overwrite = false)
                }
                val (format, effectiveQuality) = compressFor(edited, originalItem.mimeType, quality)
                FileOutputStream(file).use { out ->
                    edited.compress(format, effectiveQuality, out)
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf(originalItem.mimeType),
                    null
                )
                true
            } else {
                // Try writing via ContentResolver
                val uri = Uri.parse(originalItem.uri)
                val (format, effectiveQuality) = compressFor(edited, originalItem.mimeType, quality)
                context.contentResolver.openOutputStream(uri, "rwt")?.use { out ->
                    edited.compress(format, effectiveQuality, out)
                    true
                } ?: false
            }
        } catch (_: Exception) {
            false
        } finally {
            if (highResSource != null && highResSource != sourceBitmap && !highResSource.isRecycled) {
                highResSource.recycle()
            }
            if (edited != null && edited != sourceBitmap && !edited.isRecycled) {
                edited.recycle()
            }
        }
    }

    private fun openInputStream(context: Context, uri: Uri, path: String): InputStream? {
        return try {
            if (uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)
            } else {
                File(path).inputStream()
            }
        } catch (_: Exception) {
            null
        }
    }

    private data class ExifTransform(val rotation: Float, val flipHorizontal: Boolean)

    private fun getExifTransform(context: Context, uri: Uri, path: String): ExifTransform {
        return try {
            val exif = if (uri.scheme == "content") {
                openInputStream(context, uri, path)?.use { ExifInterface(it) }
            } else {
                ExifInterface(path)
            } ?: return ExifTransform(0f, false)

            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> ExifTransform(90f, false)
                ExifInterface.ORIENTATION_ROTATE_180 -> ExifTransform(180f, false)
                ExifInterface.ORIENTATION_ROTATE_270 -> ExifTransform(270f, false)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifTransform(0f, true)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifTransform(180f, true)
                ExifInterface.ORIENTATION_TRANSPOSE -> ExifTransform(90f, true)
                ExifInterface.ORIENTATION_TRANSVERSE -> ExifTransform(270f, true)
                else -> ExifTransform(0f, false)
            }
        } catch (_: Exception) {
            ExifTransform(0f, false)
        }
    }
}

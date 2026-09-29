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

            // 4. Handle EXIF orientation
            val orientation = getExifOrientation(context, uri, uriString)
            if (orientation != 0) {
                val matrix = Matrix().apply { postRotate(orientation.toFloat()) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
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
        var result = Bitmap.createBitmap(edited.pixels, edited.width, edited.height, Bitmap.Config.ARGB_8888)

        if (state.straightenAngle != 0f) {
            val matrix = Matrix().apply {
                postRotate(state.straightenAngle)
            }
            result = Bitmap.createBitmap(result, 0, 0, result.width, result.height, matrix, true)
        }

        return result
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
                FileOutputStream(file).use { out ->
                    edited.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
                true
            } else {
                // Try writing via ContentResolver
                val uri = Uri.parse(originalItem.uri)
                context.contentResolver.openOutputStream(uri, "rwt")?.use { out ->
                    edited.compress(Bitmap.CompressFormat.JPEG, quality, out)
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

    private fun getExifOrientation(context: Context, uri: Uri, path: String): Int {
        return try {
            val exif = if (uri.scheme == "content") {
                openInputStream(context, uri, path)?.use { ExifInterface(it) }
            } else {
                ExifInterface(path)
            } ?: return 0

            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }
}

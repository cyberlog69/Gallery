package com.gallery.core.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.gallery.core.model.MediaItem
import com.gallery.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Read-only MediaStore scanner.
 *
 * Emits one merged, newest-first stream of images and videos. Trashed rows are
 * already excluded by MediaStore on Android 11+, so this is "what the user can see".
 */
object MediaStoreScanner {

    fun scanAllMedia(context: Context): Flow<MediaItem> = flow {
        val resolver = context.contentResolver
        val items = ArrayList<MediaItem>(512)
        items += queryImages(resolver)
        items += queryVideos(resolver)
        items.sortWith(
            compareByDescending<MediaItem> { displayTimestamp(it) }
                .thenByDescending { it.sizeBytes }
        )
        for (item in items) emit(item)
    }.flowOn(Dispatchers.IO)

    private fun displayTimestamp(item: MediaItem): Long =
        if (item.dateTakenSec > 0L) item.dateTakenSec else item.dateModifiedSec

    private fun imageCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private fun videoCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    private fun queryImages(resolver: ContentResolver): List<MediaItem> {
        val collection = imageCollection()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        val out = ArrayList<MediaItem>(256)

        resolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val takenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
            val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
            val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(collection, id)
                val name = cursor.getString(nameCol) ?: "Image_$id"
                val path = if (dataCol >= 0 && !cursor.isNull(dataCol)) {
                    cursor.getString(dataCol)
                } else {
                    contentUri.toString()
                }
                val dateModified = cursor.getLong(modCol)
                val dateTaken = if (takenCol >= 0 && !cursor.isNull(takenCol)) {
                    (cursor.getLong(takenCol) / 1000L).takeIf { it > 0L } ?: dateModified
                } else {
                    dateModified
                }

                out += MediaItem(
                    id = id.toString(),
                    path = path,
                    uri = contentUri.toString(),
                    displayName = name,
                    sizeBytes = cursor.getLong(sizeCol),
                    dateModifiedSec = dateModified,
                    dateTakenSec = dateTaken,
                    mimeType = cursor.getString(mimeCol) ?: "image/jpeg",
                    mediaType = MediaType.IMAGE,
                    width = if (widthCol >= 0 && !cursor.isNull(widthCol)) cursor.getInt(widthCol) else 0,
                    height = if (heightCol >= 0 && !cursor.isNull(heightCol)) cursor.getInt(heightCol) else 0,
                    albumName = resolveAlbum(cursor, bucketCol, path, "Pictures")
                )
            }
        }
        return out
    }

    private fun queryVideos(resolver: ContentResolver): List<MediaItem> {
        val collection = videoCollection()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        val out = ArrayList<MediaItem>(64)

        resolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val takenCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
            val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(collection, id)
                val name = cursor.getString(nameCol) ?: "Video_$id"
                val path = if (dataCol >= 0 && !cursor.isNull(dataCol)) {
                    cursor.getString(dataCol)
                } else {
                    contentUri.toString()
                }
                val dateModified = cursor.getLong(modCol)
                val dateTaken = if (takenCol >= 0 && !cursor.isNull(takenCol)) {
                    (cursor.getLong(takenCol) / 1000L).takeIf { it > 0L } ?: dateModified
                } else {
                    dateModified
                }

                out += MediaItem(
                    id = id.toString(),
                    path = path,
                    uri = contentUri.toString(),
                    displayName = name,
                    sizeBytes = cursor.getLong(sizeCol),
                    dateModifiedSec = dateModified,
                    dateTakenSec = dateTaken,
                    mimeType = cursor.getString(mimeCol) ?: "video/mp4",
                    mediaType = MediaType.VIDEO,
                    width = if (widthCol >= 0 && !cursor.isNull(widthCol)) cursor.getInt(widthCol) else 0,
                    height = if (heightCol >= 0 && !cursor.isNull(heightCol)) cursor.getInt(heightCol) else 0,
                    durationMs = if (durCol >= 0 && !cursor.isNull(durCol)) cursor.getLong(durCol) else 0L,
                    albumName = resolveAlbum(cursor, bucketCol, path, "Videos")
                )
            }
        }
        return out
    }

    private fun resolveAlbum(
        cursor: android.database.Cursor,
        bucketCol: Int,
        path: String,
        fallback: String
    ): String {
        val bucket = if (bucketCol >= 0 && !cursor.isNull(bucketCol)) cursor.getString(bucketCol) else null
        if (!bucket.isNullOrBlank()) return bucket
        val parent = path.substringBeforeLast('/', "").substringAfterLast('/')
        return parent.ifBlank { fallback }
    }
}

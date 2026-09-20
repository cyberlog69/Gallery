package com.gallery.core.scanner

import com.gallery.core.model.Album
import com.gallery.core.model.MediaItem
import com.gallery.core.util.ExifExtractor
import com.gallery.core.util.MediaFormatDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object DesktopFileScanner {

    fun getDefaultPicturesDirectory(): File {
        val userHome = System.getProperty("user.home")
        val picturesDir = File(userHome, "Pictures")
        return if (picturesDir.exists()) picturesDir else File(userHome)
    }

    /**
     * Streams media items from a directory using Java NIO DirectoryStream to minimize memory usage.
     * Large directories with 20,000+ files stream items incrementally without causing memory spikes.
     */
    fun scanDirectory(
        directory: File,
        recursive: Boolean = false,
        maxDepth: Int = 2
    ): Flow<MediaItem> = flow {
        if (!directory.exists() || !directory.isDirectory) return@flow

        val dirPath = directory.toPath()
        scanPath(dirPath, recursive, currentDepth = 0, maxDepth = maxDepth) { item ->
            emit(item)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun scanPath(
        dirPath: Path,
        recursive: Boolean,
        currentDepth: Int,
        maxDepth: Int,
        onItemFound: suspend (MediaItem) -> Unit
    ) {
        if (currentDepth > maxDepth) return

        try {
            Files.newDirectoryStream(dirPath).use { stream: DirectoryStream<Path> ->
                for (path in stream) {
                    val file = path.toFile()
                    if (file.isDirectory && recursive && !file.isHidden && !file.name.startsWith(".")) {
                        scanPath(path, recursive, currentDepth + 1, maxDepth, onItemFound)
                    } else if (file.isFile && MediaFormatDetector.isSupported(file.name)) {
                        val mediaItem = buildMediaItem(file)
                        onItemFound(mediaItem)
                    }
                }
            }
        } catch (e: Exception) {
            // Silently ignore access denied / security exceptions on Windows system folders
        }
    }

    fun buildMediaItem(file: File): MediaItem {
        val path = file.absolutePath
        val name = file.name
        val ext = MediaFormatDetector.getExtension(name)
        val isVideo = MediaFormatDetector.isVideo(name)
        val mediaType = MediaFormatDetector.getMediaType(name)
        val mimeType = MediaFormatDetector.getMimeType(name)
        val sizeBytes = file.length()
        val lastModifiedSec = file.lastModified() / 1000

        var width = 0
        var height = 0
        var dateTaken = lastModifiedSec
        var exif = if (!isVideo) ExifExtractor.extract(file) else null

        if (exif != null) {
            width = exif.width
            height = exif.height
        }

        val albumName = file.parentFile?.name ?: "Pictures"

        return MediaItem(
            id = path.hashCode().toString(),
            path = path,
            uri = file.toURI().toString(),
            displayName = name,
            sizeBytes = sizeBytes,
            dateModifiedSec = lastModifiedSec,
            dateTakenSec = dateTaken,
            mimeType = mimeType,
            mediaType = mediaType,
            width = width,
            height = height,
            albumName = albumName,
            exifData = exif
        )
    }

    /**
     * Discover albums/folders containing media in a parent directory.
     */
    suspend fun discoverAlbums(parentDir: File): List<Album> = withContext(Dispatchers.IO) {
        val albums = mutableListOf<Album>()
        if (!parentDir.exists() || !parentDir.isDirectory) return@withContext albums

        try {
            Files.newDirectoryStream(parentDir.toPath()).use { stream ->
                for (path in stream) {
                    val dir = path.toFile()
                    if (dir.isDirectory && !dir.isHidden && !dir.name.startsWith(".")) {
                        var count = 0
                        var firstItem: MediaItem? = null
                        try {
                            Files.newDirectoryStream(path).use { innerStream ->
                                for (innerPath in innerStream) {
                                    val innerFile = innerPath.toFile()
                                    if (innerFile.isFile && MediaFormatDetector.isSupported(innerFile.name)) {
                                        count++
                                        if (firstItem == null) {
                                            firstItem = buildMediaItem(innerFile)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) { }

                        if (count > 0) {
                            albums.add(
                                Album(
                                    id = dir.absolutePath.hashCode().toString(),
                                    name = dir.name,
                                    path = dir.absolutePath,
                                    coverItem = firstItem,
                                    itemCount = count
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) { }

        albums
    }
}

package com.gallery.core.util

import com.gallery.core.model.MediaType

object MediaFormatDetector {

    val SUPPORTED_IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif",
        "avif", "tif", "tiff", "svg", "ico",
        // RAW Formats
        "dng", "cr2", "nef", "arw", "rw2", "orf"
    )

    val SUPPORTED_VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "3gp", "wmv", "flv", "ts", "m4v"
    )

    fun isSupported(pathOrName: String): Boolean {
        val ext = getExtension(pathOrName)
        return ext in SUPPORTED_IMAGE_EXTENSIONS || ext in SUPPORTED_VIDEO_EXTENSIONS
    }

    fun isImage(pathOrName: String): Boolean {
        return getExtension(pathOrName) in SUPPORTED_IMAGE_EXTENSIONS
    }

    fun isVideo(pathOrName: String): Boolean {
        return getExtension(pathOrName) in SUPPORTED_VIDEO_EXTENSIONS
    }

    fun getMediaType(pathOrName: String): MediaType {
        return if (isVideo(pathOrName)) MediaType.VIDEO else MediaType.IMAGE
    }

    fun getExtension(pathOrName: String): String {
        val lastDot = pathOrName.lastIndexOf('.')
        if (lastDot == -1 || lastDot == pathOrName.length - 1) return ""
        return pathOrName.substring(lastDot + 1).lowercase()
    }

    fun getMimeType(pathOrName: String): String {
        return when (getExtension(pathOrName)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "avif" -> "image/avif"
            "tif", "tiff" -> "image/tiff"
            "svg" -> "image/svg+xml"
            "dng", "cr2", "nef", "arw" -> "image/x-raw"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "ts" -> "video/mp2t"
            "m4v" -> "video/x-m4v"
            else -> "application/octet-stream"
        }
    }
}

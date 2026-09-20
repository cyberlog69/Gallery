package com.gallery.core.model

enum class MediaType {
    IMAGE,
    VIDEO
}

enum class AiCategory(val displayName: String, val emoji: String) {
    NATURE("Nature & Landscapes", "🌿"),
    PEOPLE("People & Portraits", "👤"),
    FOOD("Food & Dining", "🍕"),
    ANIMALS("Pets & Animals", "🐾"),
    DOCUMENTS("Documents & Text", "📄"),
    CITY("City & Architecture", "🏙️"),
    VEHICLES("Vehicles & Travel", "🚗"),
    SCREENSHOTS("Screenshots", "📱"),
    UNCATEGORIZED("Uncategorized", "🖼️");

    companion object {
        fun fromString(name: String?): AiCategory {
            if (name == null) return UNCATEGORIZED
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: UNCATEGORIZED
        }
    }
}

data class ExifData(
    val make: String? = null,
    val model: String? = null,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null,
    val focalLength: String? = null,
    val flash: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val orientation: Int = 0,
    val dateTimeOriginal: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val resolutionString: String
        get() = if (width > 0 && height > 0) "${width} × ${height}" else "Unknown"

    val cameraString: String
        get() = when {
            make != null && model != null -> if (model.contains(make, ignoreCase = true)) model else "$make $model"
            model != null -> model
            make != null -> make
            else -> "Unknown Camera"
        }
}

data class MediaItem(
    val id: String,
    val path: String,
    val uri: String = path,
    val displayName: String,
    val sizeBytes: Long = 0L,
    val dateModifiedSec: Long = 0L,
    val dateTakenSec: Long = 0L,
    val mimeType: String = "image/jpeg",
    val mediaType: MediaType = MediaType.IMAGE,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val albumName: String = "Pictures",
    val aiCategory: AiCategory = AiCategory.UNCATEGORIZED,
    val aiConfidence: Float = 0.0f,
    val isFavorite: Boolean = false,
    val exifData: ExifData? = null
) {
    val isVideo: Boolean get() = mediaType == MediaType.VIDEO
    val isImage: Boolean get() = mediaType == MediaType.IMAGE

    val formattedDuration: String
        get() {
            if (durationMs <= 0) return ""
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return if (minutes >= 60) {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                "%d:%02d:%02d".format(hours, remainingMinutes, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 B"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> "%.1f GB".format(gb)
                mb >= 1.0 -> "%.1f MB".format(mb)
                kb >= 1.0 -> "%.1f KB".format(kb)
                else -> "$sizeBytes B"
            }
        }
}

data class Album(
    val id: String,
    val name: String,
    val path: String,
    val coverItem: MediaItem? = null,
    val itemCount: Int = 0
)

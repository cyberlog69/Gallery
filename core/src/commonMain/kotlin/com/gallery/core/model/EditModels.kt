package com.gallery.core.model

/**
 * Normalized rectangle with coordinates ranging from 0.0f to 1.0f.
 */
data class NormalizedRect(
    val left: Float = 0.0f,
    val top: Float = 0.0f,
    val right: Float = 1.0f,
    val bottom: Float = 1.0f
) {
    val width: Float get() = (right - left).coerceAtLeast(0.01f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.01f)

    init {
        require(left in 0.0f..1.0f && top in 0.0f..1.0f && right in 0.0f..1.0f && bottom in 0.0f..1.0f) {
            "NormalizedRect coordinates must be within [0.0, 1.0]"
        }
        require(right >= left && bottom >= top) {
            "right must be >= left and bottom must be >= top"
        }
    }
}

/**
 * Immutable state representing non-destructive photo transformations.
 * Original source media is never modified directly.
 */
data class ImageEditState(
    val cropRect: NormalizedRect? = null,
    val rotationDegrees: Float = 0f,      // 0°, 90°, 180°, 270° increments
    val straightenAngle: Float = 0f,      // -45° to +45° fine angle
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val exposure: Float = 0f,             // -2.0f (darker) to +2.0f (brighter)
    val contrast: Float = 1.0f,           // 0.5f (low) to 2.0f (high contrast)
    val saturation: Float = 1.0f,         // 0.0f (B&W monochrome) to 2.0f (vivid)
    val temperature: Float = 0f,          // -1.0f (cool blue) to +1.0f (warm amber)
    val tint: Float = 0f,                 // -1.0f (green) to +1.0f (magenta)
    val highlights: Float = 0f,           // -1.0f to +1.0f (suppress / boost highlights)
    val shadows: Float = 0f               // -1.0f to +1.0f (deepen / lift shadows)
) {
    val isEdited: Boolean
        get() = cropRect != null ||
                rotationDegrees != 0f ||
                straightenAngle != 0f ||
                flipHorizontal ||
                flipVertical ||
                exposure != 0f ||
                contrast != 1.0f ||
                saturation != 1.0f ||
                temperature != 0f ||
                tint != 0f ||
                highlights != 0f ||
                shadows != 0f

    /**
     * Serializes to a compact JSON string for sidecar files and persistence.
     */
    fun toJson(): String {
        val cropJson = if (cropRect != null) {
            """{"l":${cropRect.left},"t":${cropRect.top},"r":${cropRect.right},"b":${cropRect.bottom}}"""
        } else "null"

        return """{"crop":$cropJson,"rot":$rotationDegrees,"str":$straightenAngle,"flipH":$flipHorizontal,"flipV":$flipVertical,"exp":$exposure,"con":$contrast,"sat":$saturation,"temp":$temperature,"tint":$tint,"high":$highlights,"shad":$shadows}"""
    }

    companion object {
        val DEFAULT = ImageEditState()

        /**
         * Fast manual JSON parser without external dependencies.
         */
        fun fromJson(json: String?): ImageEditState {
            if (json.isNullOrBlank()) return DEFAULT
            return try {
                fun getFloat(key: String, default: Float): Float {
                    val pattern = "\"$key\":\\s*(-?[0-9]*\\.?[0-9]+)".toRegex()
                    return pattern.find(json)?.groupValues?.get(1)?.toFloatOrNull() ?: default
                }
                fun getBool(key: String, default: Boolean): Boolean {
                    val pattern = "\"$key\":\\s*(true|false)".toRegex()
                    return pattern.find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: default
                }

                var cropRect: NormalizedRect? = null
                val cropMatch = "\"crop\":\\s*\\{\"l\":\\s*([0-9.]+),\\s*\"t\":\\s*([0-9.]+),\\s*\"r\":\\s*([0-9.]+),\\s*\"b\":\\s*([0-9.]+)\\}".toRegex().find(json)
                if (cropMatch != null) {
                    val (l, t, r, b) = cropMatch.destructured
                    cropRect = NormalizedRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
                }

                ImageEditState(
                    cropRect = cropRect,
                    rotationDegrees = getFloat("rot", 0f),
                    straightenAngle = getFloat("str", 0f),
                    flipHorizontal = getBool("flipH", false),
                    flipVertical = getBool("flipV", false),
                    exposure = getFloat("exp", 0f),
                    contrast = getFloat("con", 1.0f),
                    saturation = getFloat("sat", 1.0f),
                    temperature = getFloat("temp", 0f),
                    tint = getFloat("tint", 0f),
                    highlights = getFloat("high", 0f),
                    shadows = getFloat("shad", 0f)
                )
            } catch (_: Exception) {
                DEFAULT
            }
        }
    }
}

/**
 * Common crop aspect ratio presets.
 */
enum class AspectRatioPreset(val displayName: String, val ratio: Float?) {
    FREEFORM("Freeform", null),
    ORIGINAL("Original", null),
    SQUARE("1:1 Square", 1.0f),
    PORTRAIT("4:5 Portrait", 4f / 5f),
    LANDSCAPE("16:9 Landscape", 16f / 9f),
    CLASSIC("3:2 Classic", 3f / 2f);

    /**
     * Computes a normalized crop rectangle centered on the given image dimensions.
     */
    fun calculateRect(imageWidth: Int, imageHeight: Int): NormalizedRect {
        if (ratio == null || imageWidth <= 0 || imageHeight <= 0) {
            return NormalizedRect(0f, 0f, 1f, 1f)
        }
        val targetRatio = ratio
        val currentRatio = imageWidth.toFloat() / imageHeight.toFloat()

        return if (currentRatio > targetRatio) {
            val cropWidthFraction = targetRatio / currentRatio
            val offset = ((1f - cropWidthFraction) / 2f).coerceIn(0f, 0.49f)
            NormalizedRect(left = offset, top = 0f, right = 1f - offset, bottom = 1f)
        } else {
            val cropHeightFraction = currentRatio / targetRatio
            val offset = ((1f - cropHeightFraction) / 2f).coerceIn(0f, 0.49f)
            NormalizedRect(left = 0f, top = offset, right = 1f, bottom = 1f - offset)
        }
    }
}

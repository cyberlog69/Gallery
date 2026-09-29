package com.gallery.core.edit

import com.gallery.core.model.ImageEditState
import com.gallery.core.model.NormalizedRect
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Result of an in-memory image editing operation.
 */
data class EditedPixelBuffer(
    val pixels: IntArray,
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EditedPixelBuffer) return false
        return width == other.width && height == other.height && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = pixels.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

/**
 * High-performance, memory-efficient non-destructive image editing engine.
 * Pure Kotlin multiplatform implementation for real-time slider updates and rendering.
 */
object ImageEditEngine {

    /**
     * Applies the complete non-destructive edit pipeline (color, crop, rotation, flips).
     */
    fun applyEdits(
        srcPixels: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        state: ImageEditState
    ): EditedPixelBuffer {
        if (!state.isEdited || srcPixels.isEmpty() || srcWidth <= 0 || srcHeight <= 0) {
            return EditedPixelBuffer(srcPixels, srcWidth, srcHeight)
        }

        var currentPixels = srcPixels
        var currentWidth = srcWidth
        var currentHeight = srcHeight

        // 1. Color adjustments (Exposure, Contrast, Saturation, Temperature, Tint, Highlights, Shadows)
        if (state.exposure != 0f || state.contrast != 1.0f || state.saturation != 1.0f ||
            state.temperature != 0f || state.tint != 0f || state.highlights != 0f || state.shadows != 0f) {
            currentPixels = applyColorAdjustments(currentPixels, state)
        }

        // 2. Crop
        if (state.cropRect != null) {
            val cropped = applyCrop(currentPixels, currentWidth, currentHeight, state.cropRect)
            currentPixels = cropped.pixels
            currentWidth = cropped.width
            currentHeight = cropped.height
        }

        // 3. Flips
        if (state.flipHorizontal) {
            currentPixels = flipHorizontal(currentPixels, currentWidth, currentHeight)
        }
        if (state.flipVertical) {
            currentPixels = flipVertical(currentPixels, currentWidth, currentHeight)
        }

        // 4. Rotation (supports 90° increments; arbitrary angles handled via canvas layer)
        val normalizedDegrees = ((state.rotationDegrees % 360f) + 360f) % 360f
        when ((normalizedDegrees / 90f).roundToInt() % 4) {
            1 -> {
                val rotated = rotate90Clockwise(currentPixels, currentWidth, currentHeight)
                currentPixels = rotated.pixels
                currentWidth = rotated.width
                currentHeight = rotated.height
            }
            2 -> {
                currentPixels = rotate180(currentPixels, currentWidth, currentHeight)
            }
            3 -> {
                val rotated = rotate270Clockwise(currentPixels, currentWidth, currentHeight)
                currentPixels = rotated.pixels
                currentWidth = rotated.width
                currentHeight = rotated.height
            }
        }

        return EditedPixelBuffer(currentPixels, currentWidth, currentHeight)
    }

    /**
     * Applies exposure, contrast, saturation, temperature, and tint in a single pass.
     */
    fun applyColorAdjustments(pixels: IntArray, state: ImageEditState): IntArray {
        val result = IntArray(pixels.size)
        val expFactor = 2f.pow(state.exposure)
        val contrast = state.contrast
        val sat = state.saturation
        val tempShift = state.temperature * 32f
        val tintShift = state.tint * 24f

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel shr 24) and 0xFF
            var r = ((pixel shr 16) and 0xFF).toFloat()
            var g = ((pixel shr 8) and 0xFF).toFloat()
            var b = (pixel and 0xFF).toFloat()

            // Exposure
            if (state.exposure != 0f) {
                r *= expFactor
                g *= expFactor
                b *= expFactor
            }

            // Temperature & Tint
            if (state.temperature != 0f) {
                r += tempShift
                b -= tempShift
            }
            if (state.tint != 0f) {
                r += tintShift * 0.5f
                g -= tintShift
                b += tintShift * 0.5f
            }

            // Contrast
            if (contrast != 1.0f) {
                r = (r - 128f) * contrast + 128f
                g = (g - 128f) * contrast + 128f
                b = (b - 128f) * contrast + 128f
            }

            // Saturation
            if (sat != 1.0f) {
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                r = lum + (r - lum) * sat
                g = lum + (g - lum) * sat
                b = lum + (b - lum) * sat
            }

            // Highlights & Shadows
            if (state.highlights != 0f || state.shadows != 0f) {
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                val normLum = (lum / 255f).coerceIn(0f, 1f)
                val highFactor = normLum * normLum * state.highlights * 50f
                val shadowFactor = (1f - normLum) * (1f - normLum) * state.shadows * 50f
                val delta = highFactor + shadowFactor
                r += delta
                g += delta
                b += delta
            }

            val ir = r.coerceIn(0f, 255f).toInt()
            val ig = g.coerceIn(0f, 255f).toInt()
            val ib = b.coerceIn(0f, 255f).toInt()

            result[i] = (a shl 24) or (ir shl 16) or (ig shl 8) or ib
        }

        return result
    }

    /**
     * Crops image to normalized rectangle coordinates.
     */
    fun applyCrop(
        pixels: IntArray,
        width: Int,
        height: Int,
        crop: NormalizedRect
    ): EditedPixelBuffer {
        val startX = (crop.left * width).toInt().coerceIn(0, width - 1)
        val startY = (crop.top * height).toInt().coerceIn(0, height - 1)
        val endX = (crop.right * width).toInt().coerceIn(startX + 1, width)
        val endY = (crop.bottom * height).toInt().coerceIn(startY + 1, height)

        val newWidth = endX - startX
        val newHeight = endY - startY
        val cropped = IntArray(newWidth * newHeight)

        for (y in 0 until newHeight) {
            val srcRow = (startY + y) * width + startX
            val dstRow = y * newWidth
            System.arraycopy(pixels, srcRow, cropped, dstRow, newWidth)
        }

        return EditedPixelBuffer(cropped, newWidth, newHeight)
    }

    fun flipHorizontal(pixels: IntArray, width: Int, height: Int): IntArray {
        val result = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                result[y * width + (width - 1 - x)] = pixels[y * width + x]
            }
        }
        return result
    }

    fun flipVertical(pixels: IntArray, width: Int, height: Int): IntArray {
        val result = IntArray(pixels.size)
        for (y in 0 until height) {
            val srcOffset = y * width
            val dstOffset = (height - 1 - y) * width
            System.arraycopy(pixels, srcOffset, result, dstOffset, width)
        }
        return result
    }

    fun rotate90Clockwise(pixels: IntArray, width: Int, height: Int): EditedPixelBuffer {
        val newWidth = height
        val newHeight = width
        val result = IntArray(pixels.size)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val newX = height - 1 - y
                val newY = x
                result[newY * newWidth + newX] = pixels[y * width + x]
            }
        }
        return EditedPixelBuffer(result, newWidth, newHeight)
    }

    fun rotate180(pixels: IntArray, width: Int, height: Int): IntArray {
        val result = IntArray(pixels.size)
        val total = pixels.size
        for (i in 0 until total) {
            result[total - 1 - i] = pixels[i]
        }
        return result
    }

    fun rotate270Clockwise(pixels: IntArray, width: Int, height: Int): EditedPixelBuffer {
        val newWidth = height
        val newHeight = width
        val result = IntArray(pixels.size)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val newX = y
                val newY = width - 1 - x
                result[newY * newWidth + newX] = pixels[y * width + x]
            }
        }
        return EditedPixelBuffer(result, newWidth, newHeight)
    }
}

package com.gallery.core.edit

import com.gallery.core.model.ImageEditState
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Desktop (JVM) implementation for applying and exporting non-destructive image edits.
 */
object DesktopImageEditor {

    /**
     * Applies ImageEditState to a BufferedImage in memory.
     */
    fun applyEdit(source: BufferedImage, state: ImageEditState): BufferedImage {
        if (!state.isEdited) return source

        val width = source.width
        val height = source.height

        // Extract ARGB pixels
        val srcPixels = IntArray(width * height)
        source.getRGB(0, 0, width, height, srcPixels, 0, width)

        // Process through ImageEditEngine
        val edited = ImageEditEngine.applyEdits(srcPixels, width, height, state)

        // Construct target BufferedImage
        val target = BufferedImage(edited.width, edited.height, BufferedImage.TYPE_INT_ARGB)
        target.setRGB(0, 0, edited.width, edited.height, edited.pixels, 0, edited.width)
        return target
    }

    /**
     * Exports the edited image to a target file.
     * @param source Original or high-resolution decoded image
     * @param state Edit adjustments
     * @param targetFile File to write to
     * @param format "jpg", "png", "webp"
     * @param quality Compression quality 0.0f - 1.0f (for JPEG)
     */
    fun exportToFile(
        source: BufferedImage,
        state: ImageEditState,
        targetFile: File,
        format: String = "jpg",
        quality: Float = 0.92f
    ): Boolean {
        return try {
            val edited = applyEdit(source, state)
            val parent = targetFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            if (format.equals("jpg", ignoreCase = true) || format.equals("jpeg", ignoreCase = true)) {
                // Convert ARGB to RGB without alpha for JPEG
                val rgbImage = BufferedImage(edited.width, edited.height, BufferedImage.TYPE_INT_RGB)
                val g: Graphics2D = rgbImage.createGraphics()
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                g.drawImage(edited, 0, 0, null)
                g.dispose()

                val writers = ImageIO.getImageWritersByFormatName("jpeg")
                if (writers.hasNext()) {
                    val writer = writers.next()
                    val param = writer.defaultWriteParam
                    if (param.canWriteCompressed()) {
                        param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                        param.compressionQuality = quality.coerceIn(0.1f, 1.0f)
                    }
                    targetFile.outputStream().use { os ->
                        ImageIO.createImageOutputStream(os).use { ios ->
                            writer.output = ios
                            writer.write(null, IIOImage(rgbImage, null, null), param)
                        }
                    }
                    writer.dispose()
                    true
                } else {
                    ImageIO.write(rgbImage, "jpg", targetFile)
                }
            } else {
                ImageIO.write(edited, format, targetFile)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Saves edits directly to the source file, preserving original by creating a .bak copy first.
     */
    fun saveInPlaceWithBackup(
        sourceFile: File,
        sourceImage: BufferedImage,
        state: ImageEditState,
        format: String = "jpg",
        quality: Float = 0.95f
    ): Boolean {
        if (!sourceFile.exists()) return false
        val backupFile = File(sourceFile.parentFile, "${sourceFile.name}.bak")
        if (!backupFile.exists()) {
            try {
                sourceFile.copyTo(backupFile, overwrite = false)
            } catch (_: Exception) {
                return false
            }
        }
        return exportToFile(sourceImage, state, sourceFile, format, quality)
    }
}

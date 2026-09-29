package com.gallery.core.print

import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob
import javax.print.attribute.HashPrintRequestAttributeSet
import javax.print.attribute.standard.Copies
import javax.print.attribute.standard.JobName
import javax.print.attribute.standard.OrientationRequested
import java.util.Locale

/**
 * Picasa-style photo print layout presets.
 */
enum class PrintLayoutPreset(val displayName: String, val description: String) {
    FULL_PAGE("Full Page", "Fits the photo onto the full printable page area"),
    PHOTO_4x6("4 x 6 inch", "Classic 4x6 photo centered on page"),
    PHOTO_5x7("5 x 7 inch", "Medium 5x7 photo centered on page"),
    WALLET_4_UP("Wallet (4-Up)", "4 contact sheet wallet prints on a single sheet")
}

/**
 * Native desktop printing engine supporting Picasa-style photo layout templates.
 */
object DesktopPrintEngine {

    /**
     * Prints an image using the selected layout preset.
     * Launches the native Windows/system print dialog.
     */
    fun printImage(
        image: BufferedImage,
        preset: PrintLayoutPreset = PrintLayoutPreset.FULL_PAGE,
        jobTitle: String = "Picasa Photo Print"
    ): Boolean {
        val printerJob = PrinterJob.getPrinterJob() ?: return false
        val attrSet = HashPrintRequestAttributeSet()
        attrSet.add(Copies(1))
        attrSet.add(JobName(jobTitle, Locale.getDefault()))

        // Set orientation based on image aspect ratio
        if (image.width > image.height) {
            attrSet.add(OrientationRequested.LANDSCAPE)
        } else {
            attrSet.add(OrientationRequested.PORTRAIT)
        }

        printerJob.setPrintable(PhotoPrintable(image, preset))

        return try {
            if (printerJob.printDialog(attrSet)) {
                printerJob.print(attrSet)
                true
            } else {
                false // User cancelled dialog
            }
        } catch (_: Exception) {
            false
        }
    }

    private class PhotoPrintable(
        private val image: BufferedImage,
        private val preset: PrintLayoutPreset
    ) : Printable {

        override fun print(graphics: Graphics, pageFormat: PageFormat, pageIndex: Int): Int {
            if (pageIndex > 0) return Printable.NO_SUCH_PAGE

            val g2d = graphics as? Graphics2D ?: return Printable.NO_SUCH_PAGE
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            val imgW = image.width.toDouble()
            val imgH = image.height.toDouble()

            val pageX = pageFormat.imageableX
            val pageY = pageFormat.imageableY
            val pageW = pageFormat.imageableWidth
            val pageH = pageFormat.imageableHeight

            when (preset) {
                PrintLayoutPreset.FULL_PAGE -> {
                    // Center and scale to fit imageable page bounds maintaining aspect ratio
                    val scale = Math.min(pageW / imgW, pageH / imgH)
                    val drawW = imgW * scale
                    val drawH = imgH * scale
                    val drawX = pageX + (pageW - drawW) / 2.0
                    val drawY = pageY + (pageH - drawH) / 2.0
                    g2d.drawImage(image, drawX.toInt(), drawY.toInt(), drawW.toInt(), drawH.toInt(), null)
                }

                PrintLayoutPreset.PHOTO_4x6 -> {
                    // 72 points per inch in Java AWT: 4" = 288pt, 6" = 432pt
                    val targetW = 288.0
                    val targetH = 432.0
                    val scale = Math.min(targetW / imgW, targetH / imgH)
                    val drawW = imgW * scale
                    val drawH = imgH * scale
                    val drawX = pageX + (pageW - drawW) / 2.0
                    val drawY = pageY + (pageH - drawH) / 2.0
                    g2d.drawImage(image, drawX.toInt(), drawY.toInt(), drawW.toInt(), drawH.toInt(), null)
                }

                PrintLayoutPreset.PHOTO_5x7 -> {
                    // 5" = 360pt, 7" = 504pt
                    val targetW = 360.0
                    val targetH = 504.0
                    val scale = Math.min(targetW / imgW, targetH / imgH)
                    val drawW = imgW * scale
                    val drawH = imgH * scale
                    val drawX = pageX + (pageW - drawW) / 2.0
                    val drawY = pageY + (pageH - drawH) / 2.0
                    g2d.drawImage(image, drawX.toInt(), drawY.toInt(), drawW.toInt(), drawH.toInt(), null)
                }

                PrintLayoutPreset.WALLET_4_UP -> {
                    // 2x2 grid of wallet prints on a single sheet
                    val cellW = pageW / 2.0
                    val cellH = pageH / 2.0
                    val scale = Math.min(cellW * 0.9 / imgW, cellH * 0.9 / imgH)
                    val drawW = imgW * scale
                    val drawH = imgH * scale

                    for (row in 0..1) {
                        for (col in 0..1) {
                            val cellX = pageX + col * cellW
                            val cellY = pageY + row * cellH
                            val drawX = cellX + (cellW - drawW) / 2.0
                            val drawY = cellY + (cellH - drawH) / 2.0
                            g2d.drawImage(image, drawX.toInt(), drawY.toInt(), drawW.toInt(), drawH.toInt(), null)
                        }
                    }
                }
            }

            return Printable.PAGE_EXISTS
        }
    }
}

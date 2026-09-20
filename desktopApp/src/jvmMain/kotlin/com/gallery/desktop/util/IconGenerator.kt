package com.gallery.desktop.util

import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

object IconGenerator {

    /**
     * Renders the Material 3 Gallery Logo (matching Android ic_launcher)
     */
    fun renderLogo(size: Int): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = image.createGraphics()

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

            val s = size.toDouble()
            val center = s / 2.0
            val pad = s * 0.04
            val corner = s * 0.22

            // Dark Material 3 Surface Background (#111318) matching Android
            g.color = AwtColor(0x11, 0x13, 0x18)
            g.fill(RoundRectangle2D.Double(pad, pad, s - pad * 2, s - pad * 2, corner, corner))

            // Subtle border
            g.color = AwtColor(0x28, 0x2A, 0x32)
            g.stroke = BasicStroke((s * 0.015).toFloat())
            g.draw(RoundRectangle2D.Double(pad, pad, s - pad * 2, s - pad * 2, corner, corner))

            // 4 Material 3 Pinwheel Blades matching Android ic_launcher_foreground
            val bladeColors = listOf(
                AwtColor(0x00, 0x61, 0xA4), // Top: Primary Blue
                AwtColor(0x00, 0x68, 0x76), // Right: Secondary Teal
                AwtColor(0x98, 0x40, 0x61), // Bottom: Tertiary Coral
                AwtColor(0x70, 0x5D, 0x00)  // Left: Amber Tonal
            )

            val bladeW = s * 0.23
            val bladeH = s * 0.36
            val bladeCorner = bladeW * 0.7

            for (i in 0 until 4) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.rotate(Math.toRadians(i * 90.0), center, center)

                    g2.color = bladeColors[i]
                    val blade = RoundRectangle2D.Double(
                        center - bladeW / 2.0,
                        center - bladeH,
                        bladeW,
                        bladeH * 0.95,
                        bladeCorner,
                        bladeCorner
                    )
                    g2.fill(blade)
                } finally {
                    g2.dispose()
                }
            }

            // Center aperture iris ring (#111318 background + #D1E4FF ring)
            val outerRadius = s * 0.16
            g.color = AwtColor(0x11, 0x13, 0x18)
            g.fill(Ellipse2D.Double(center - outerRadius, center - outerRadius, outerRadius * 2, outerRadius * 2))

            val irisRadius = s * 0.11
            g.color = AwtColor(0x00, 0x61, 0xA4)
            g.fill(Ellipse2D.Double(center - irisRadius, center - irisRadius, irisRadius * 2, irisRadius * 2))

            // Aperture accent ring
            g.color = AwtColor(0xD1, 0xE4, 0xFF)
            g.stroke = BasicStroke((s * 0.02).toFloat())
            g.draw(Ellipse2D.Double(center - irisRadius, center - irisRadius, irisRadius * 2, irisRadius * 2))

            // Specular reflection accent
            val specRadius = s * 0.035
            g.color = AwtColor(255, 255, 255, 220)
            g.fill(Ellipse2D.Double(center + irisRadius * 0.3, center - irisRadius * 0.5, specRadius * 2, specRadius * 2))

        } finally {
            g.dispose()
        }

        return image
    }

    /**
     * Generates a multi-resolution Windows .ico file containing PNG-encoded images.
     */
    fun generateIcoFile(outputFile: File, sizes: List<Int> = listOf(256, 128, 64, 48, 32, 16)) {
        val pngDataList = sizes.map { sz ->
            val img = renderLogo(sz)
            val baos = ByteArrayOutputStream()
            ImageIO.write(img, "png", baos)
            baos.toByteArray()
        }

        val count = sizes.size
        val headerSize = 6
        val dirEntrySize = 16
        val entriesTotalSize = dirEntrySize * count
        var currentOffset = headerSize + entriesTotalSize

        val headerBuffer = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN)
        headerBuffer.putShort(0) // Reserved
        headerBuffer.putShort(1) // Type 1 = ICO
        headerBuffer.putShort(count.toShort())

        val entriesBuffer = ByteBuffer.allocate(entriesTotalSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            val sz = sizes[i]
            val pngBytes = pngDataList[i]

            entriesBuffer.put(if (sz >= 256) 0.toByte() else sz.toByte()) // Width
            entriesBuffer.put(if (sz >= 256) 0.toByte() else sz.toByte()) // Height
            entriesBuffer.put(0.toByte()) // Palette
            entriesBuffer.put(0.toByte()) // Reserved
            entriesBuffer.putShort(1.toShort()) // Color planes
            entriesBuffer.putShort(32.toShort()) // Bits per pixel
            entriesBuffer.putInt(pngBytes.size) // Size of image data
            entriesBuffer.putInt(currentOffset) // Offset

            currentOffset += pngBytes.size
        }

        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            fos.write(headerBuffer.array())
            fos.write(entriesBuffer.array())
            for (png in pngDataList) {
                fos.write(png)
            }
        }
    }
}

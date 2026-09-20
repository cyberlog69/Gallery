package com.gallery.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallery.desktop.ui.theme.PicasaTextPrimary
import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage

/**
 * Iconic Google Picasa 5-blade vibrant aperture shutter logo colors
 */
val PicasaBladeBlue = Color(0xFF389BF2)
val PicasaBladeTeal = Color(0xFF20C997)
val PicasaBladeYellow = Color(0xFFFFAE19)
val PicasaBladeRed = Color(0xFFFF5757)
val PicasaBladePurple = Color(0xFF9B51E0)

@Composable
fun PicasaApertureLogo(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = w * 0.44f

        val colors = listOf(
            PicasaBladeBlue,
            PicasaBladeTeal,
            PicasaBladeYellow,
            PicasaBladeRed,
            PicasaBladePurple
        )

        // Draw 5 overlapping curved aperture blades
        for (i in 0 until 5) {
            val startAngle = i * 72f - 90f
            drawArc(
                color = colors[i],
                startAngle = startAngle,
                sweepAngle = 72f,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Fill
            )
        }

        // Inner lens ring & aperture iris
        drawCircle(
            color = Color(0xFF0F1013),
            radius = radius * 0.42f,
            center = center,
            style = Fill
        )
        drawCircle(
            color = Color.White,
            radius = radius * 0.42f,
            center = center,
            style = Stroke(width = w * 0.035f)
        )
        drawCircle(
            color = Color(0xFF389BF2),
            radius = radius * 0.22f,
            center = center,
            style = Fill
        )
        // Specular lens reflection
        drawCircle(
            color = Color.White,
            radius = radius * 0.08f,
            center = Offset(center.x + radius * 0.08f, center.y - radius * 0.08f),
            style = Fill
        )
    }
}

@Composable
fun PicasaHeaderBadge(
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        PicasaApertureLogo(size = 28.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Picasa Photo Viewer",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PicasaTextPrimary
        )
    }
}

/**
 * Generates an ImageBitmap for the application window icon
 */
fun generatePicasaWindowIcon(sizePx: Int = 128): ImageBitmap {
    val img = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
    val g: Graphics2D = img.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        val center = sizePx / 2.0
        val radius = sizePx * 0.44

        val awtColors = listOf(
            AwtColor(0x38, 0x9B, 0xF2), // Blue
            AwtColor(0x20, 0xC9, 0x97), // Teal
            AwtColor(0xFF, 0xAE, 0x19), // Yellow
            AwtColor(0xFF, 0x57, 0x57), // Red
            AwtColor(0x9B, 0x51, 0xE0)  // Purple
        )

        for (i in 0 until 5) {
            val startAngle = (i * 72.0) - 90.0
            g.color = awtColors[i]
            val arc = Arc2D.Double(
                center - radius,
                center - radius,
                radius * 2.0,
                radius * 2.0,
                -startAngle,
                -72.0,
                Arc2D.PIE
            )
            g.fill(arc)
        }

        // Center lens
        g.color = AwtColor(0x0F, 0x10, 0x13)
        g.fill(Ellipse2D.Double(center - radius * 0.42, center - radius * 0.42, radius * 0.84, radius * 0.84))

        g.color = AwtColor.WHITE
        g.stroke = BasicStroke((sizePx * 0.035).toFloat())
        g.draw(Ellipse2D.Double(center - radius * 0.42, center - radius * 0.42, radius * 0.84, radius * 0.84))

        g.color = AwtColor(0x38, 0x9B, 0xF2)
        g.fill(Ellipse2D.Double(center - radius * 0.22, center - radius * 0.22, radius * 0.44, radius * 0.44))

        g.color = AwtColor.WHITE
        g.fill(Ellipse2D.Double(center + radius * 0.04, center - radius * 0.12, radius * 0.14, radius * 0.14))
    } finally {
        g.dispose()
    }
    return img.toComposeImageBitmap()
}

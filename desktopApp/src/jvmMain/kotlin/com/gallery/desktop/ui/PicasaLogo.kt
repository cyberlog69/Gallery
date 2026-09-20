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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallery.desktop.ui.theme.PicasaTextPrimary
import com.gallery.desktop.util.IconGenerator

// Material 3 Colors matching Android app ic_launcher_foreground
val M3BladeBlue = Color(0xFF0061A4)
val M3BladeTeal = Color(0xFF006876)
val M3BladeCoral = Color(0xFF984061)
val M3BladeAmber = Color(0xFF705D00)

@Composable
fun PicasaApertureLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)

        // Dark background matching Android ic_launcher_background
        drawRoundRect(
            color = Color(0xFF111318),
            size = this.size,
            cornerRadius = CornerRadius(w * 0.22f, h * 0.22f),
            style = Fill
        )

        val bladeColors = listOf(
            M3BladeBlue,
            M3BladeTeal,
            M3BladeCoral,
            M3BladeAmber
        )

        val bladeW = w * 0.23f
        val bladeH = h * 0.36f
        val corner = bladeW * 0.7f

        for (i in 0 until 4) {
            rotate(degrees = i * 90f, pivot = center) {
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = center.x - bladeW / 2f,
                            top = center.y - bladeH,
                            right = center.x + bladeW / 2f,
                            bottom = center.y - bladeH * 0.05f,
                            radiusX = corner,
                            radiusY = corner
                        )
                    )
                }
                drawPath(path = path, color = bladeColors[i], style = Fill)
            }
        }

        // Center aperture iris ring & specular accent
        drawCircle(
            color = Color(0xFF111318),
            radius = w * 0.16f,
            center = center,
            style = Fill
        )
        drawCircle(
            color = M3BladeBlue,
            radius = w * 0.11f,
            center = center,
            style = Fill
        )
        drawCircle(
            color = Color(0xFFD1E4FF),
            radius = w * 0.11f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.02f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = w * 0.035f,
            center = Offset(center.x + w * 0.035f, center.y - w * 0.035f),
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
 * Generates an ImageBitmap for the application window icon matching Android app
 */
fun generatePicasaWindowIcon(sizePx: Int = 128): ImageBitmap {
    return IconGenerator.renderLogo(sizePx).toComposeImageBitmap()
}


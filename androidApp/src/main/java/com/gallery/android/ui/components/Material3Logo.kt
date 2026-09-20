package com.gallery.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Themed Gallery Logo
 * Dynamically reacts to Material 3 / Material You system theme and wallpaper tonal palette.
 */
@Composable
fun Material3GalleryLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    useDynamicTheming: Boolean = true,
    // By default, colors are bound directly to the active Material 3 ColorScheme!
    primaryColor: Color = if (useDynamicTheming) MaterialTheme.colorScheme.primary else Color(0xFF0061A4),
    secondaryColor: Color = if (useDynamicTheming) MaterialTheme.colorScheme.secondary else Color(0xFF006876),
    tertiaryColor: Color = if (useDynamicTheming) MaterialTheme.colorScheme.tertiary else Color(0xFF984061),
    containerColor: Color = if (useDynamicTheming) MaterialTheme.colorScheme.primaryContainer else Color(0xFFD1E4FF),
    centerIrisColor: Color = if (useDynamicTheming) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFF001D36)
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val bladeLength = w * 0.40f
        val bladeThickness = w * 0.22f
        val corner = bladeThickness / 2f

        val bladeColors = listOf(
            primaryColor,
            secondaryColor,
            tertiaryColor,
            containerColor
        )

        // Draw 4 expressive Material 3 pinwheel capsule blades
        for (i in 0 until 4) {
            rotate(degrees = i * 90f, pivot = center) {
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = center.x - (bladeThickness / 2f),
                            top = center.y - bladeLength - (w * 0.04f),
                            right = center.x + (bladeThickness / 2f),
                            bottom = center.y + (bladeThickness / 2f),
                            radiusX = corner,
                            radiusY = corner
                        )
                    )
                }
                drawPath(path = path, color = bladeColors[i], style = Fill)
            }
        }

        // Center Material 3 aperture iris with subtle specular accent
        drawCircle(
            color = surfaceColor,
            radius = w * 0.15f,
            center = center,
            style = Fill
        )
        drawCircle(
            color = centerIrisColor,
            radius = w * 0.10f,
            center = center,
            style = Fill
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = w * 0.035f,
            center = Offset(center.x + (w * 0.035f), center.y - (w * 0.035f)),
            style = Fill
        )
    }
}

@Composable
fun Material3GalleryHeader(
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Material3GalleryLogo(size = 32.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Gallery",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

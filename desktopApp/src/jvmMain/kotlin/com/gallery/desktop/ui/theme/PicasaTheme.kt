package com.gallery.desktop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Classic Picasa dark / translucent palette
val PicasaBackground = Color(0xFF0F1013)
val PicasaSurface = Color(0xFF191B21)
val PicasaSurfaceTranslucent = Color(0xDD1B1D24)
val PicasaPillBackground = Color(0xEE22252F)
val PicasaAccent = Color(0xFF389BF2) // Picasa cyan/blue
val PicasaTextPrimary = Color(0xFFEEEEEE)
val PicasaTextSecondary = Color(0xFFA0A5B5)
val PicasaBorder = Color(0x33FFFFFF)
val PicasaStarYellow = Color(0xFFFFD13B)

private val DarkColors = darkColorScheme(
    primary = PicasaAccent,
    background = PicasaBackground,
    surface = PicasaSurface,
    onBackground = PicasaTextPrimary,
    onSurface = PicasaTextPrimary
)

@Composable
fun PicasaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}

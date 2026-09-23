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

// Authentic Windows Aero Glass Design Tokens (Picasa Photo Viewer)
val AeroAccent = Color(0xFF00C3FF) // Electric Aero Cyan
val AeroAccentDark = Color(0xFF007ECC)
val AeroGlow = Color(0x5500C3FF)
val AeroHoverHalo = Color(0x4000C3FF)
val AeroGlassDark = Color(0xDB111622)
val AeroGlassMedium = Color(0xCC182333)
val AeroGlassPill = Color(0xE6151E2B)
val AeroGlassBorder = Color(0x4DFFFFFF)
val AeroGlassBorderSubtle = Color(0x1FFFFFFF)

// Aero Gradients & Reflections
val AeroBackgroundGradient = androidx.compose.ui.graphics.Brush.radialGradient(
    colors = listOf(
        Color(0xFF242F42), // Subtle central soft sapphire/slate illumination
        Color(0xFF121722),
        Color(0xFF07090D)  // Smoky vignette obsidian edges
    )
)

val AeroPillGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
    colors = listOf(
        Color(0xF01F2A3B), // Sleek smoked glass top
        Color(0xFA0E131E)  // Deep glass bottom
    )
)

val AeroSpecularGloss = androidx.compose.ui.graphics.Brush.verticalGradient(
    0.0f to Color(0x55FFFFFF),
    0.46f to Color(0x18FFFFFF),
    0.50f to Color(0x00FFFFFF),
    1.0f to Color(0x0CFFFFFF)
)

val AeroBorderGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
    colors = listOf(
        Color(0x99FFFFFF), // Bright specular top edge reflection
        Color(0x33FFFFFF),
        Color(0x15FFFFFF),
        Color(0x40FFFFFF)  // Subtle bottom glass rim
    )
)

val AeroFilmstripGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
    colors = listOf(
        Color(0xD9101520),
        Color(0xF2080A0E)
    )
)

val AeroHeaderCapsuleGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
    colors = listOf(
        Color(0xCC1A2332),
        Color(0xEE0F141E)
    )
)

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

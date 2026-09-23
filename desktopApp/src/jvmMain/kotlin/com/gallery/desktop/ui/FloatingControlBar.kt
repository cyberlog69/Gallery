package com.gallery.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallery.core.ai.AiClassificationResult
import com.gallery.desktop.ui.theme.AeroAccent
import com.gallery.desktop.ui.theme.AeroBorderGradient
import com.gallery.desktop.ui.theme.AeroPillGradient
import com.gallery.desktop.ui.theme.AeroSpecularGloss
import com.gallery.desktop.ui.theme.PicasaAccent
import com.gallery.desktop.ui.theme.PicasaBorder
import com.gallery.desktop.ui.theme.PicasaPillBackground
import com.gallery.desktop.ui.theme.PicasaTextPrimary
import com.gallery.desktop.ui.theme.PicasaTextSecondary

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FloatingControlBar(
    zoom: Float,
    isSlideshowRunning: Boolean,
    showExif: Boolean,
    showFilmstrip: Boolean,
    aiResult: AiClassificationResult?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onToggleActualSize: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onToggleExif: () -> Unit,
    onToggleFilmstrip: () -> Unit,
    onOpenFolder: () -> Unit,
    onSetDefaultViewer: (() -> Unit)? = null,
    updateAvailable: Boolean = false,
    isCheckingUpdate: Boolean = false,
    onCheckUpdate: (() -> Unit)? = null,
    isAeroTheme: Boolean = true,
    onToggleAeroTheme: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .then(
                if (isAeroTheme) {
                    Modifier
                        .shadow(
                            elevation = 22.dp,
                            shape = pillShape,
                            spotColor = Color(0x6600C3FF),
                            ambientColor = Color(0x66000000)
                        )
                        .clip(pillShape)
                        .background(AeroPillGradient)
                        .border(BorderStroke(1.2.dp, AeroBorderGradient), pillShape)
                } else {
                    Modifier
                        .shadow(elevation = 16.dp, shape = pillShape)
                        .clip(pillShape)
                        .background(PicasaPillBackground)
                        .border(1.dp, PicasaBorder, pillShape)
                }
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        // Aero Specular Gloss reflection sheen overlay
        if (isAeroTheme) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(pillShape)
                    .background(AeroSpecularGloss)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Previous Button
            PicasaIconButton(
                icon = Icons.Default.ChevronLeft,
                contentDescription = "Previous Photo (Left Arrow)",
                isAeroTheme = isAeroTheme,
                onClick = onPrevious
            )

            // Next Button
            PicasaIconButton(
                icon = Icons.Default.ChevronRight,
                contentDescription = "Next Photo (Right Arrow)",
                isAeroTheme = isAeroTheme,
                onClick = onNext
            )

            PicasaDivider(isAeroTheme = isAeroTheme)

            // Zoom Out
            PicasaIconButton(
                icon = Icons.Default.ZoomOut,
                contentDescription = "Zoom Out (-)",
                isAeroTheme = isAeroTheme,
                onClick = onZoomOut
            )

            // Zoom Slider
            Slider(
                value = zoom,
                onValueChange = onZoomChange,
                valueRange = 0.5f..4.0f,
                modifier = Modifier.width(90.dp),
                colors = SliderDefaults.colors(
                    thumbColor = if (isAeroTheme) Color.White else PicasaAccent,
                    activeTrackColor = if (isAeroTheme) AeroAccent else PicasaAccent,
                    inactiveTrackColor = if (isAeroTheme) Color(0x35FFFFFF) else Color(0x55FFFFFF)
                )
            )

            // Zoom In
            PicasaIconButton(
                icon = Icons.Default.ZoomIn,
                contentDescription = "Zoom In (+)",
                isAeroTheme = isAeroTheme,
                onClick = onZoomIn
            )

            // Fit / 1:1 Toggle
            PicasaIconButton(
                icon = Icons.Default.CropFree,
                contentDescription = "Actual Size / Fit",
                tint = if (zoom != 1.0f) (if (isAeroTheme) AeroAccent else PicasaAccent) else PicasaTextPrimary,
                isActive = zoom != 1.0f,
                isAeroTheme = isAeroTheme,
                onClick = onToggleActualSize
            )

            PicasaDivider(isAeroTheme = isAeroTheme)

            // Rotate Left
            PicasaIconButton(
                icon = Icons.Default.RotateLeft,
                contentDescription = "Rotate Counterclockwise",
                isAeroTheme = isAeroTheme,
                onClick = onRotateLeft
            )

            // Rotate Right
            PicasaIconButton(
                icon = Icons.Default.RotateRight,
                contentDescription = "Rotate Clockwise (R)",
                isAeroTheme = isAeroTheme,
                onClick = onRotateRight
            )

            PicasaDivider(isAeroTheme = isAeroTheme)

            // Slideshow Button
            PicasaIconButton(
                icon = if (isSlideshowRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Toggle Slideshow (Spacebar)",
                tint = if (isSlideshowRunning) (if (isAeroTheme) AeroAccent else PicasaAccent) else PicasaTextPrimary,
                isActive = isSlideshowRunning,
                isAeroTheme = isAeroTheme,
                onClick = onToggleSlideshow
            )

            // EXIF Info Toggle
            PicasaIconButton(
                icon = Icons.Default.Info,
                contentDescription = "File & EXIF Info (I)",
                tint = if (showExif) (if (isAeroTheme) AeroAccent else PicasaAccent) else PicasaTextPrimary,
                isActive = showExif,
                isAeroTheme = isAeroTheme,
                onClick = onToggleExif
            )

            // Filmstrip Toggle
            PicasaIconButton(
                icon = Icons.Default.ViewCarousel,
                contentDescription = "Toggle Filmstrip (F)",
                tint = if (showFilmstrip) (if (isAeroTheme) AeroAccent else PicasaAccent) else PicasaTextPrimary,
                isActive = showFilmstrip,
                isAeroTheme = isAeroTheme,
                onClick = onToggleFilmstrip
            )

            // Choose Folder Button
            PicasaIconButton(
                icon = Icons.Default.FolderOpen,
                contentDescription = "Open Folder",
                isAeroTheme = isAeroTheme,
                onClick = onOpenFolder
            )

            // Set as Default Viewer Button
            if (onSetDefaultViewer != null) {
                PicasaIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "Set as Default Photo Viewer",
                    tint = PicasaTextSecondary,
                    isAeroTheme = isAeroTheme,
                    onClick = onSetDefaultViewer
                )
            }

            // Aero Theme Toggle
            if (onToggleAeroTheme != null) {
                PicasaDivider(isAeroTheme = isAeroTheme)
                PicasaIconButton(
                    icon = if (isAeroTheme) Icons.Default.AutoAwesome else Icons.Default.BrightnessMedium,
                    contentDescription = if (isAeroTheme) "Aero Glass Theme (Active - click to switch to Dark)" else "Dark Theme (Click to switch to Aero Glass)",
                    tint = if (isAeroTheme) AeroAccent else PicasaTextSecondary,
                    isActive = isAeroTheme,
                    isAeroTheme = isAeroTheme,
                    onClick = onToggleAeroTheme
                )
            }

            // In-App Updater Button
            if (onCheckUpdate != null) {
                PicasaDivider(isAeroTheme = isAeroTheme)
                PicasaIconButton(
                    icon = if (updateAvailable) Icons.Default.SystemUpdate else Icons.Default.Refresh,
                    contentDescription = if (updateAvailable) "Update Available" else "Check for Updates",
                    tint = if (updateAvailable) (if (isAeroTheme) AeroAccent else Color(0xFF389BF2)) else PicasaTextSecondary,
                    isActive = updateAvailable,
                    isAeroTheme = isAeroTheme,
                    onClick = onCheckUpdate
                )
            }

            // AI Tag Pill if available
            if (aiResult != null && aiResult.category != com.gallery.core.model.AiCategory.UNCATEGORIZED) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isAeroTheme) Color(0x3300C3FF) else Color(0x33389BF2))
                        .border(1.dp, if (isAeroTheme) Color(0x6600C3FF) else Color(0x66389BF2), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${aiResult.category.emoji} ${aiResult.category.displayName} (${(aiResult.confidence * 100).toInt()}%)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PicasaIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = PicasaTextPrimary,
    isActive: Boolean = false,
    isAeroTheme: Boolean = true,
    onClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }

    val currentTint = when {
        isActive && isAeroTheme -> AeroAccent
        isActive -> PicasaAccent
        isHovered && isAeroTheme -> Color(0xFF80E5FF)
        isHovered -> Color.White
        else -> tint
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isAeroTheme && (isHovered || isActive)) {
                    Modifier
                        .background(
                            Brush.radialGradient(
                                colors = if (isActive) {
                                    listOf(Color(0x6600C3FF), Color(0x2000C3FF), Color.Transparent)
                                } else {
                                    listOf(Color(0x4400C3FF), Color(0x1500C3FF), Color.Transparent)
                                }
                            )
                        )
                        .border(
                            1.dp,
                            if (isActive) Color(0x8800C3FF) else Color(0x4400C3FF),
                            RoundedCornerShape(8.dp)
                        )
                } else if (!isAeroTheme && isHovered) {
                    Modifier.background(Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                } else Modifier
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = currentTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun PicasaDivider(isAeroTheme: Boolean = true) {
    if (isAeroTheme) {
        Row(
            modifier = Modifier
                .height(20.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color(0x33000000))
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color(0x40FFFFFF))
            )
        }
    } else {
        Box(
            modifier = Modifier
                .height(20.dp)
                .width(1.dp)
                .background(Color(0x33FFFFFF))
                .padding(horizontal = 2.dp)
        )
    }
}

package com.gallery.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallery.core.ai.AiClassificationResult
import com.gallery.desktop.ui.theme.PicasaAccent
import com.gallery.desktop.ui.theme.PicasaBorder
import com.gallery.desktop.ui.theme.PicasaPillBackground
import com.gallery.desktop.ui.theme.PicasaTextPrimary
import com.gallery.desktop.ui.theme.PicasaTextSecondary

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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(PicasaPillBackground)
            .border(1.dp, PicasaBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Previous Button
            PicasaIconButton(
                icon = Icons.Default.ChevronLeft,
                contentDescription = "Previous Photo (Left Arrow)",
                onClick = onPrevious
            )

            // Next Button
            PicasaIconButton(
                icon = Icons.Default.ChevronRight,
                contentDescription = "Next Photo (Right Arrow)",
                onClick = onNext
            )

            PicasaDivider()

            // Zoom Out
            PicasaIconButton(
                icon = Icons.Default.ZoomOut,
                contentDescription = "Zoom Out (-)",
                onClick = onZoomOut
            )

            // Zoom Slider
            Slider(
                value = zoom,
                onValueChange = onZoomChange,
                valueRange = 0.5f..4.0f,
                modifier = Modifier.width(90.dp),
                colors = SliderDefaults.colors(
                    thumbColor = PicasaAccent,
                    activeTrackColor = PicasaAccent,
                    inactiveTrackColor = Color(0x55FFFFFF)
                )
            )

            // Zoom In
            PicasaIconButton(
                icon = Icons.Default.ZoomIn,
                contentDescription = "Zoom In (+)",
                onClick = onZoomIn
            )

            // Fit / 1:1 Toggle
            PicasaIconButton(
                icon = Icons.Default.CropFree,
                contentDescription = "Actual Size / Fit",
                tint = if (zoom != 1.0f) PicasaAccent else PicasaTextPrimary,
                onClick = onToggleActualSize
            )

            PicasaDivider()

            // Rotate Left
            PicasaIconButton(
                icon = Icons.Default.RotateLeft,
                contentDescription = "Rotate Counterclockwise",
                onClick = onRotateLeft
            )

            // Rotate Right
            PicasaIconButton(
                icon = Icons.Default.RotateRight,
                contentDescription = "Rotate Clockwise (R)",
                onClick = onRotateRight
            )

            PicasaDivider()

            // Slideshow Button
            PicasaIconButton(
                icon = if (isSlideshowRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Toggle Slideshow (Spacebar)",
                tint = if (isSlideshowRunning) PicasaAccent else PicasaTextPrimary,
                onClick = onToggleSlideshow
            )

            // EXIF Info Toggle
            PicasaIconButton(
                icon = Icons.Default.Info,
                contentDescription = "File & EXIF Info (I)",
                tint = if (showExif) PicasaAccent else PicasaTextPrimary,
                onClick = onToggleExif
            )

            // Filmstrip Toggle
            PicasaIconButton(
                icon = Icons.Default.ViewCarousel,
                contentDescription = "Toggle Filmstrip (F)",
                tint = if (showFilmstrip) PicasaAccent else PicasaTextPrimary,
                onClick = onToggleFilmstrip
            )

            // Choose Folder Button
            PicasaIconButton(
                icon = Icons.Default.FolderOpen,
                contentDescription = "Open Folder",
                onClick = onOpenFolder
            )

            // AI Tag Pill if available
            if (aiResult != null && aiResult.category != com.gallery.core.model.AiCategory.UNCATEGORIZED) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33389BF2))
                        .border(1.dp, Color(0x66389BF2), RoundedCornerShape(12.dp))
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

@Composable
fun PicasaIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = PicasaTextPrimary,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun PicasaDivider() {
    Box(
        modifier = Modifier
            .height(20.dp)
            .width(1.dp)
            .background(Color(0x33FFFFFF))
            .padding(horizontal = 2.dp)
    )
}

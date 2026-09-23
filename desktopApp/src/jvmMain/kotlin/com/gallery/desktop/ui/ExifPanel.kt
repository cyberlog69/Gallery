package com.gallery.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallery.core.model.MediaItem
import com.gallery.desktop.ui.theme.AeroAccent
import com.gallery.desktop.ui.theme.AeroBorderGradient
import com.gallery.desktop.ui.theme.AeroPillGradient
import com.gallery.desktop.ui.theme.AeroSpecularGloss
import com.gallery.desktop.ui.theme.PicasaAccent
import com.gallery.desktop.ui.theme.PicasaBorder
import com.gallery.desktop.ui.theme.PicasaPillBackground
import com.gallery.desktop.ui.theme.PicasaTextPrimary
import com.gallery.desktop.ui.theme.PicasaTextSecondary

@Composable
fun ExifPanel(
    item: MediaItem,
    onClose: () -> Unit,
    isAeroTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    val exif = item.exifData
    val panelShape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .width(320.dp)
            .then(
                if (isAeroTheme) {
                    Modifier
                        .shadow(20.dp, panelShape, spotColor = Color(0x6600C3FF))
                        .clip(panelShape)
                        .background(AeroPillGradient)
                        .border(BorderStroke(1.2.dp, AeroBorderGradient), panelShape)
                } else {
                    Modifier
                        .shadow(16.dp, panelShape)
                        .clip(panelShape)
                        .background(PicasaPillBackground)
                        .border(1.dp, PicasaBorder, panelShape)
                }
            )
            .padding(16.dp)
    ) {
        if (isAeroTheme) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(panelShape)
                    .background(AeroSpecularGloss)
            )
        }
        Column {
            Row(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Photo Information",
                    color = if (isAeroTheme) Color.White else PicasaTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.padding(0.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = if (isAeroTheme) Color(0xCCFFFFFF) else PicasaTextSecondary
                    )
                }
            }

            ExifRow("Filename", item.displayName, isAeroTheme)
            ExifRow("File Size", item.formattedSize, isAeroTheme)
            ExifRow("Resolution", if (item.width > 0) "${item.width} × ${item.height}" else exif?.resolutionString ?: "Unknown", isAeroTheme)
            ExifRow("Format", item.mimeType, isAeroTheme)

            if (exif != null) {
                val camera = exif.cameraString
                if (camera != "Unknown Camera") {
                    ExifRow("Camera", camera, isAeroTheme)
                }
                val aperture = exif.aperture
                if (!aperture.isNullOrBlank()) {
                    ExifRow("Aperture", aperture, isAeroTheme)
                }
                val shutterSpeed = exif.shutterSpeed
                if (!shutterSpeed.isNullOrBlank()) {
                    ExifRow("Exposure", "${shutterSpeed}s", isAeroTheme)
                }
                val iso = exif.iso
                if (!iso.isNullOrBlank()) {
                    ExifRow("ISO", "ISO $iso", isAeroTheme)
                }
                val focalLength = exif.focalLength
                if (!focalLength.isNullOrBlank()) {
                    ExifRow("Focal Length", focalLength, isAeroTheme)
                }
                val dateTimeOriginal = exif.dateTimeOriginal
                if (!dateTimeOriginal.isNullOrBlank()) {
                    ExifRow("Date Taken", dateTimeOriginal, isAeroTheme)
                }
            }

            if (item.aiCategory != com.gallery.core.model.AiCategory.UNCATEGORIZED) {
                Spacer(modifier = Modifier.height(8.dp))
                ExifRow("AI Category", "${item.aiCategory.emoji} ${item.aiCategory.displayName}", isAeroTheme)
            }
        }
    }
}

@Composable
private fun ExifRow(label: String, value: String, isAeroTheme: Boolean = true) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = if (isAeroTheme) AeroAccent else PicasaTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isAeroTheme) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            color = if (isAeroTheme) Color.White else PicasaTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

package com.gallery.desktop.ui

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
import com.gallery.desktop.ui.theme.PicasaAccent
import com.gallery.desktop.ui.theme.PicasaBorder
import com.gallery.desktop.ui.theme.PicasaPillBackground
import com.gallery.desktop.ui.theme.PicasaTextPrimary
import com.gallery.desktop.ui.theme.PicasaTextSecondary

@Composable
fun ExifPanel(
    item: MediaItem,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exif = item.exifData

    Box(
        modifier = modifier
            .width(320.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(PicasaPillBackground)
            .border(1.dp, PicasaBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Photo Information",
                    color = PicasaTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.padding(0.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = PicasaTextSecondary
                    )
                }
            }

            ExifRow("Filename", item.displayName)
            ExifRow("File Size", item.formattedSize)
            ExifRow("Resolution", if (item.width > 0) "${item.width} × ${item.height}" else exif?.resolutionString ?: "Unknown")
            ExifRow("Format", item.mimeType)

            if (exif != null) {
                val camera = exif.cameraString
                if (camera != "Unknown Camera") {
                    ExifRow("Camera", camera)
                }
                val aperture = exif.aperture
                if (!aperture.isNullOrBlank()) {
                    ExifRow("Aperture", aperture)
                }
                val shutterSpeed = exif.shutterSpeed
                if (!shutterSpeed.isNullOrBlank()) {
                    ExifRow("Exposure", "${shutterSpeed}s")
                }
                val iso = exif.iso
                if (!iso.isNullOrBlank()) {
                    ExifRow("ISO", "ISO $iso")
                }
                val focalLength = exif.focalLength
                if (!focalLength.isNullOrBlank()) {
                    ExifRow("Focal Length", focalLength)
                }
                val dateTimeOriginal = exif.dateTimeOriginal
                if (!dateTimeOriginal.isNullOrBlank()) {
                    ExifRow("Date Taken", dateTimeOriginal)
                }
            }

            if (item.aiCategory != com.gallery.core.model.AiCategory.UNCATEGORIZED) {
                Spacer(modifier = Modifier.height(8.dp))
                ExifRow("AI Category", "${item.aiCategory.emoji} ${item.aiCategory.displayName}")
            }
        }
    }
}

@Composable
private fun ExifRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = PicasaTextSecondary, fontSize = 12.sp, modifier = Modifier.width(100.dp))
        Text(text = value, color = PicasaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

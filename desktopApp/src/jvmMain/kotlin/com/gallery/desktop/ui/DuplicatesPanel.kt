package com.gallery.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
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
import com.gallery.core.ai.DuplicateCluster
import com.gallery.core.model.MediaItem
import com.gallery.desktop.ui.theme.AeroAccent
import com.gallery.desktop.ui.theme.AeroBorderGradient
import com.gallery.desktop.ui.theme.AeroPillGradient
import com.gallery.desktop.ui.theme.PicasaAccent
import com.gallery.desktop.ui.theme.PicasaPillBackground
import com.gallery.desktop.ui.theme.PicasaTextPrimary
import com.gallery.desktop.ui.theme.PicasaTextSecondary

@Composable
fun DuplicatesPanel(
    clusters: List<DuplicateCluster>,
    onSelectMedia: (MediaItem) -> Unit,
    onClose: () -> Unit,
    isAeroTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    val panelShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .width(340.dp)
            .height(400.dp)
            .then(
                if (isAeroTheme) {
                    Modifier
                        .shadow(elevation = 20.dp, shape = panelShape, spotColor = Color(0x6600C3FF))
                        .clip(panelShape)
                        .background(AeroPillGradient)
                        .border(BorderStroke(1.2.dp, AeroBorderGradient), panelShape)
                } else {
                    Modifier
                        .clip(panelShape)
                        .background(PicasaPillBackground)
                        .border(BorderStroke(1.dp, Color(0x33FFFFFF)), panelShape)
                }
            )
            .padding(18.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = if (isAeroTheme) AeroAccent else PicasaAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Duplicate & Similar Photos",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = PicasaTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (clusters.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No duplicates or similar shots detected in this folder.",
                        color = PicasaTextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(clusters) { cluster ->
                        DuplicateClusterCard(
                            cluster = cluster,
                            onSelectMedia = onSelectMedia,
                            isAeroTheme = isAeroTheme
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateClusterCard(
    cluster: DuplicateCluster,
    onSelectMedia: (MediaItem) -> Unit,
    isAeroTheme: Boolean
) {
    val cardShape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color(0x33FFFFFF))
            .border(BorderStroke(0.8.dp, Color(0x22FFFFFF)), cardShape)
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (cluster.averageDistance == 0) "Exact Duplicates" else "Similar Burst Shot",
                    color = if (cluster.averageDistance == 0) Color(0xFFFF5252) else (if (isAeroTheme) AeroAccent else PicasaAccent),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${cluster.duplicateItems.size + 1} photos",
                    color = PicasaTextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Primary Item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSelectMedia(cluster.primaryItem) }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = PicasaTextPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = cluster.primaryItem.displayName,
                    color = PicasaTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            // Duplicates
            cluster.duplicateItems.forEach { dup ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectMedia(dup) }
                        .padding(start = 12.dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "↳ ", color = PicasaTextSecondary, fontSize = 12.sp)
                    Text(
                        text = dup.displayName,
                        color = PicasaTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

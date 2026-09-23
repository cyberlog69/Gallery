package com.gallery.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallery.core.decoder.DesktopImageDecoder
import com.gallery.core.model.MediaItem
import com.gallery.desktop.ui.theme.AeroAccent
import com.gallery.desktop.ui.theme.AeroFilmstripGradient
import com.gallery.desktop.ui.theme.PicasaAccent
import com.gallery.desktop.ui.theme.PicasaBorder
import com.gallery.desktop.ui.theme.PicasaSurfaceTranslucent
import java.io.File

@Composable
fun Filmstrip(
    mediaItems: List<MediaItem>,
    currentIndex: Int,
    onSelectIndex: (Int) -> Unit,
    isAeroTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto scroll to center active item
    LaunchedEffect(currentIndex) {
        if (currentIndex in mediaItems.indices) {
            val targetIndex = (currentIndex - 3).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(86.dp)
            .then(
                if (isAeroTheme) {
                    Modifier
                        .background(AeroFilmstripGradient)
                        .border(
                            BorderStroke(
                                1.dp,
                                Brush.horizontalGradient(
                                    listOf(Color(0x15FFFFFF), Color(0x55FFFFFF), Color(0x15FFFFFF))
                                )
                            )
                        )
                } else {
                    Modifier
                        .background(PicasaSurfaceTranslucent)
                        .border(1.dp, PicasaBorder)
                }
            )
            .padding(vertical = 8.dp)
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(mediaItems) { index, item ->
                val isSelected = index == currentIndex
                FilmstripItem(
                    item = item,
                    isSelected = isSelected,
                    isAeroTheme = isAeroTheme,
                    onClick = { onSelectIndex(index) }
                )
            }
        }
    }
}

@Composable
fun FilmstripItem(
    item: MediaItem,
    isSelected: Boolean,
    isAeroTheme: Boolean = true,
    onClick: () -> Unit
) {
    val thumbnailBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = item.path) {
        val file = File(item.path)
        if (file.exists()) {
            val buff = DesktopImageDecoder.loadThumbnail(file, targetSizePx = 128)
            value = buff?.toComposeImageBitmap()
        }
    }

    val itemShape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .size(68.dp)
            .then(
                if (isAeroTheme && isSelected) {
                    Modifier.shadow(elevation = 10.dp, shape = itemShape, spotColor = Color(0xDD00C3FF))
                } else Modifier
            )
            .clip(itemShape)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = when {
                    isSelected && isAeroTheme -> AeroAccent
                    isSelected -> PicasaAccent
                    isAeroTheme -> Color(0x38FFFFFF)
                    else -> Color(0x33FFFFFF)
                },
                shape = itemShape
            )
            .background(if (isAeroTheme) Color(0xFF141A24) else Color(0xFF22242B))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(68.dp)
            )
        } else {
            Box(modifier = Modifier.size(68.dp).background(Color(0xFF262830)))
        }

        // Aero glossy glass highlight on selected item
        if (isAeroTheme && isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0x33FFFFFF),
                            0.5f to Color(0x00FFFFFF),
                            1.0f to Color(0x1500C3FF)
                        )
                    )
            )
        }

        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0x44000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

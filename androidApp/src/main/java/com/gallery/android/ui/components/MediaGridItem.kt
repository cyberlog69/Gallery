package com.gallery.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gallery.core.model.MediaItem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.MaterialTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridItem(
    item: MediaItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.5.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF22242B))
            .then(
                if (isSelected) {
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                } else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .crossfade(true)
                .size(300, 300) // Downsample for grid thumbnail RAM optimization
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Selection Checkmark Badge
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Unselected",
                        tint = Color(0xAAFFFFFF),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Video Badge & Duration
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.formattedDuration,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x66000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

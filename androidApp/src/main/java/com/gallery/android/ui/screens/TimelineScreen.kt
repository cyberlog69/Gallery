package com.gallery.android.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallery.android.ui.components.Material3GalleryHeader
import com.gallery.android.ui.components.MediaGridItem
import com.gallery.android.ui.viewmodel.GalleryViewModel
import com.gallery.core.model.AiCategory

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier
) {
    val filteredMedia by viewModel.filteredMedia.collectAsState()
    val groupedMedia by viewModel.groupedMedia.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // Material 3 App Header with Logo
        Material3GalleryHeader(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // AI Category Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) },
                    label = { Text("All") }
                )
            }
            items(AiCategory.entries.filter { it != AiCategory.UNCATEGORIZED }) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = {
                        viewModel.selectCategory(if (selectedCategory == category) null else category)
                    },
                    label = { Text("${category.emoji} ${category.displayName}") }
                )
            }
        }

        if (isLoading && filteredMedia.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredMedia.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (selectedCategory != null) "No items in this category" else "No photos or videos found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Fluid Grid with Pinch to Zoom columns
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom > 1.25f && gridColumns > 2) {
                                viewModel.setColumns(gridColumns - 1)
                            } else if (zoom < 0.8f && gridColumns < 5) {
                                viewModel.setColumns(gridColumns + 1)
                            }
                        }
                    }
            ) {
                groupedMedia.forEach { (dateHeader, itemsInGroup) ->
                    // Date Header
                    item(span = { GridItemSpan(gridColumns) }) {
                        Text(
                            text = dateHeader,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }

                    // Media items
                    itemsIndexed(itemsInGroup) { _, item ->
                        val globalIndex = filteredMedia.indexOf(item)
                        MediaGridItem(
                            item = item,
                            onClick = {
                                if (globalIndex >= 0) {
                                    viewModel.openViewer(globalIndex)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

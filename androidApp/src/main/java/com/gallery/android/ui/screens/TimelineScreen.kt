package com.gallery.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val selectedPerson by viewModel.selectedPerson.collectAsState()
    val isSemanticSearchActive by viewModel.isSemanticSearchActive.collectAsState()
    val semanticMatchedConcepts by viewModel.semanticMatchedConcepts.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isFavoriteFilter by viewModel.isFavoriteFilter.collectAsState()
    val selectedItemIds by viewModel.selectedItemIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Contextual Selection Action Bar vs Standard Header
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.clearSelection() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Selection",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${selectedItemIds.size} selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(modifier = Modifier.weight(1f))

                // Favorite Selected Action
                IconButton(onClick = { viewModel.batchToggleFavoriteSelected() }) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorite Selected",
                        tint = Color(0xFFFFD700)
                    )
                }

                // Delete Selected Action
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Selected",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                TextButton(onClick = {
                    if (selectedItemIds.size == filteredMedia.size) {
                        viewModel.clearSelection()
                    } else {
                        viewModel.selectAll()
                    }
                }) {
                    Text(
                        text = if (selectedItemIds.size == filteredMedia.size) "Deselect" else "All",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete ${selectedItemIds.size} items?") },
                text = { Text("Selected photos and videos will be removed from your gallery.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.batchDeleteSelected()
                            showDeleteConfirm = false
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (!isSelectionMode) {
            // Material 3 App Header with Logo & Updater Action
            Material3GalleryHeader(
                updateAvailable = updateInfo != null,
                isCheckingUpdate = isCheckingUpdate,
                onUpdateClick = {
                    if (updateInfo != null) {
                        viewModel.openUpdateDialog()
                    } else {
                        viewModel.checkForUpdates(silent = false)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search photos, albums, tags...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Offline Semantic Search Indicator
            if (searchQuery.isNotBlank() && isSemanticSearchActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Offline Semantic Match: ${if (semanticMatchedConcepts.isNotEmpty()) semanticMatchedConcepts.take(3).joinToString(" • ") else "256-D neural vector"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // AI Category & Favorite Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Selected Person Filter Chip
                if (selectedPerson != null) {
                    item {
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.selectPerson(null) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear person filter",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = { Text("Person: ${selectedPerson?.name}") }
                        )
                    }
                }

                // Favorites Filter Chip
                item {
                    FilterChip(
                        selected = isFavoriteFilter,
                        onClick = { viewModel.toggleFavoriteFilter() },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isFavoriteFilter) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text("Favorites") }
                    )
                }

                item {
                    FilterChip(
                        selected = selectedCategory == null && selectedPerson == null && !isFavoriteFilter,
                        onClick = {
                            viewModel.selectCategory(null)
                            viewModel.selectPerson(null)
                        },
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
                        text = if (searchQuery.isNotBlank()) "No media matching \"$searchQuery\""
                        else if (isFavoriteFilter) "No favorite photos yet"
                        else if (selectedCategory != null) "No items in this category"
                        else "No photos or videos found",
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
                        val isSelected = selectedItemIds.contains(item.id)

                        MediaGridItem(
                            item = item,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleItemSelection(item.id)
                                } else if (globalIndex >= 0) {
                                    viewModel.openViewer(globalIndex)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleItemSelection(item.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

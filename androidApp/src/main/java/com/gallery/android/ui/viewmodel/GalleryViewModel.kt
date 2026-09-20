package com.gallery.android.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.core.ai.OfflineAiClassifier
import com.gallery.core.model.AiCategory
import com.gallery.core.model.MediaItem
import com.gallery.core.scanner.MediaStoreScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.gallery.core.model.Album
import kotlinx.coroutines.flow.map

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _selectedCategory = MutableStateFlow<AiCategory?>(null)
    val selectedCategory: StateFlow<AiCategory?> = _selectedCategory.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<String?>(null)
    val selectedAlbum: StateFlow<String?> = _selectedAlbum.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _gridColumns = MutableStateFlow(3)
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _selectedItemIndex = MutableStateFlow<Int?>(null)
    val selectedItemIndex: StateFlow<Int?> = _selectedItemIndex.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val albums: StateFlow<List<Album>> = _mediaItems.map { items ->
        items.groupBy { it.albumName }.map { (folderName, itemsInFolder) ->
            Album(
                id = folderName.hashCode().toString(),
                name = folderName,
                path = itemsInFolder.firstOrNull()?.path ?: "",
                coverItem = itemsInFolder.firstOrNull(),
                itemCount = itemsInFolder.size
            )
        }.sortedByDescending { it.itemCount }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val albumItems: StateFlow<List<MediaItem>> = combine(
        _mediaItems,
        _selectedAlbum
    ) { items, albumName ->
        if (albumName == null) emptyList()
        else items.filter { it.albumName == albumName }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Filtered items based on active AI category and search query
    val filteredMedia: StateFlow<List<MediaItem>> = combine(
        _mediaItems,
        _selectedCategory,
        _searchQuery
    ) { items, category, query ->
        items.filter { item ->
            val matchesCategory = category == null || item.aiCategory == category
            val matchesQuery = query.isBlank() || item.displayName.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Group items by human-friendly date header (e.g. "Today", "Yesterday", "August 2026")
    val groupedMedia: StateFlow<Map<String, List<MediaItem>>> = filteredMedia.combine(
        MutableStateFlow(Unit)
    ) { items, _ ->
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val todayStr = dayFormat.format(Date())
        val yesterdayStr = dayFormat.format(Date(System.currentTimeMillis() - 86400000L))

        items.groupBy { item ->
            val itemDate = Date(item.dateModifiedSec * 1000)
            val dayKey = dayFormat.format(itemDate)
            when (dayKey) {
                todayStr -> "Today"
                yesterdayStr -> "Yesterday"
                else -> dateFormat.format(itemDate)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    fun loadMedia() {
        if (_isLoading.value) return
        _isLoading.value = true

        viewModelScope.launch {
            val list = mutableListOf<MediaItem>()
            val context = getApplication<Application>()
            MediaStoreScanner.scanAllMedia(context).collect { item ->
                list.add(item)
                _mediaItems.value = list.toList()
            }
            _isLoading.value = false

            // Run offline AI classification on scanned images incrementally in background
            runBackgroundAiClassification()
        }
    }

    private fun runBackgroundAiClassification() {
        viewModelScope.launch(Dispatchers.Default) {
            val context = getApplication<Application>()
            val items = _mediaItems.value.toMutableList()

            for (i in items.indices) {
                val item = items[i]
                if (item.isImage && item.aiCategory == AiCategory.UNCATEGORIZED) {
                    try {
                        val uri = Uri.parse(item.uri)
                        // Decode tiny 64x64 thumbnail for minimal RAM consumption (<16KB per image)
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = maxOf(1, item.width / 64)
                        }
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bmp = BitmapFactory.decodeStream(stream, null, options)
                            if (bmp != null) {
                                val scaled = Bitmap.createScaledBitmap(bmp, 64, 64, true)
                                val pixels = IntArray(64 * 64)
                                scaled.getPixels(pixels, 0, 64, 0, 0, 64, 64)
                                val ai = OfflineAiClassifier.classify(pixels, 64, 64, item.displayName)

                                items[i] = item.copy(
                                    aiCategory = ai.category,
                                    aiConfidence = ai.confidence
                                )
                                _mediaItems.value = items.toList()
                                scaled.recycle()
                                bmp.recycle()
                            }
                        }
                    } catch (e: Exception) {
                        // Skip unreadable files gracefully
                    }
                }
            }
        }
    }

    fun setColumns(cols: Int) {
        _gridColumns.value = cols.coerceIn(2, 5)
    }

    fun selectCategory(category: AiCategory?) {
        _selectedCategory.value = category
    }

    fun selectAlbum(albumName: String?) {
        _selectedAlbum.value = albumName
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openViewer(index: Int) {
        _selectedItemIndex.value = index
    }

    fun closeViewer() {
        _selectedItemIndex.value = null
    }
}

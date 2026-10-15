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
import com.gallery.core.updater.UpdateChecker
import com.gallery.core.updater.UpdateInfo
import com.gallery.android.updater.AndroidAppUpdater
import com.gallery.android.updater.DownloadProgress
import com.gallery.core.ai.DetectedFace
import com.gallery.core.ai.FaceDetectionEngine
import com.gallery.core.ai.OfflineNeuralEngine
import com.gallery.core.ai.OfflineOcrEngine
import com.gallery.core.ai.PersonCluster
import com.gallery.core.ai.SemanticSearchEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import java.io.File

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val APP_VERSION = "1.1.0"
    }

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    // Updater States
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadedApkFile = MutableStateFlow<File?>(null)
    val downloadedApkFile: StateFlow<File?> = _downloadedApkFile.asStateFlow()

    private var downloadJob: Job? = null

    // People & Face Clusters
    private val _peopleClusters = MutableStateFlow<List<PersonCluster>>(emptyList())
    val peopleClusters: StateFlow<List<PersonCluster>> = _peopleClusters.asStateFlow()

    private val _selectedPerson = MutableStateFlow<PersonCluster?>(null)
    val selectedPerson: StateFlow<PersonCluster?> = _selectedPerson.asStateFlow()

    // Semantic search feedback state
    private val _semanticMatchedConcepts = MutableStateFlow<List<String>>(emptyList())
    val semanticMatchedConcepts: StateFlow<List<String>> = _semanticMatchedConcepts.asStateFlow()

    private val _isSemanticSearchActive = MutableStateFlow(false)
    val isSemanticSearchActive: StateFlow<Boolean> = _isSemanticSearchActive.asStateFlow()

    private val _selectedCategory = MutableStateFlow<AiCategory?>(null)
    val selectedCategory: StateFlow<AiCategory?> = _selectedCategory.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<String?>(null)
    val selectedAlbum: StateFlow<String?> = _selectedAlbum.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isFavoriteFilter = MutableStateFlow(false)
    val isFavoriteFilter: StateFlow<Boolean> = _isFavoriteFilter.asStateFlow()

    private val _selectedItemIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedItemIds: StateFlow<Set<String>> = _selectedItemIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedItemIds.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

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

    // Filtered items based on active AI category, favorite filter, search query, and selected person
    val filteredMedia: StateFlow<List<MediaItem>> = combine(
        _mediaItems,
        _selectedCategory,
        _searchQuery,
        _isFavoriteFilter,
        _selectedPerson
    ) { items, category, query, favoriteOnly, person ->
        var candidates = items.filter { item ->
            val matchesCategory = category == null || item.aiCategory == category
            val matchesFavorite = !favoriteOnly || item.isFavorite
            val matchesPerson = person == null || person.mediaItemIds.contains(item.id)
            matchesCategory && matchesFavorite && matchesPerson
        }

        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotBlank()) {
            val semanticResults = SemanticSearchEngine.search(trimmedQuery, candidates, minScore = 0.28f)
            val semanticMatchedIds = semanticResults.map { it.item.id }.toSet()

            _semanticMatchedConcepts.value = semanticResults.flatMap { it.matchedConcepts }.distinct()
            _isSemanticSearchActive.value = semanticResults.isNotEmpty()

            candidates = candidates.filter { item ->
                semanticMatchedIds.contains(item.id) ||
                item.displayName.contains(trimmedQuery, ignoreCase = true) ||
                item.albumName.contains(trimmedQuery, ignoreCase = true) ||
                item.tags.any { it.contains(trimmedQuery, ignoreCase = true) } ||
                (item.ocrText?.contains(trimmedQuery, ignoreCase = true) == true)
            }
        } else {
            _semanticMatchedConcepts.value = emptyList()
            _isSemanticSearchActive.value = false
        }

        candidates
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
            val allDetectedFaces = mutableListOf<DetectedFace>()

            for (i in items.indices) {
                val item = items[i]
                if (item.isImage && item.quantizedEmbedding == null) {
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

                                // 1. Quantized Neural Classification & 256-D Embedding
                                val neuralResult = OfflineNeuralEngine.processImage(pixels, 64, 64, item.displayName)
                                val finalCategory = if (neuralResult.category != AiCategory.UNCATEGORIZED) {
                                    neuralResult.category
                                } else {
                                    OfflineAiClassifier.classify(pixels, 64, 64, item.displayName).category
                                }

                                // 2. Face Detection & 64-D Facial Morphology
                                val faces = FaceDetectionEngine.detectFaces(pixels, 64, 64, item.id)
                                if (faces.isNotEmpty()) {
                                    allDetectedFaces.addAll(faces)
                                }

                                // 3. Offline OCR & Document Text Extraction
                                var ocrText: String? = null
                                if (finalCategory == AiCategory.DOCUMENTS ||
                                    item.displayName.contains("receipt", ignoreCase = true) ||
                                    item.displayName.contains("doc", ignoreCase = true) ||
                                    item.displayName.contains("bill", ignoreCase = true) ||
                                    item.displayName.contains("scan", ignoreCase = true) ||
                                    neuralResult.tags.contains("document")
                                ) {
                                    val ocr = OfflineOcrEngine.extractText(pixels, 64, 64, item.displayName)
                                    if (ocr.fullText.isNotBlank()) {
                                        ocrText = ocr.fullText
                                    }
                                }

                                items[i] = item.copy(
                                    aiCategory = finalCategory,
                                    aiConfidence = maxOf(item.aiConfidence, neuralResult.confidence),
                                    tags = (item.tags + neuralResult.tags).distinct(),
                                    embeddingVector = neuralResult.embedding,
                                    quantizedEmbedding = neuralResult.quantizedEmbedding,
                                    detectedFaceCount = faces.size,
                                    faceIds = faces.map { it.faceId },
                                    ocrText = ocrText
                                )
                                _mediaItems.value = items.toList()

                                scaled.recycle()
                                bmp.recycle()

                                // Periodically cluster faces into people albums
                                if (allDetectedFaces.isNotEmpty() && (i % 5 == 0 || i == items.lastIndex)) {
                                    val clusters = FaceDetectionEngine.clusterFaces(allDetectedFaces)
                                    val existingNames = _peopleClusters.value.associate { it.personId to it.name }
                                    _peopleClusters.value = clusters.map { c ->
                                        existingNames[c.personId]?.let { customName -> c.copy(name = customName) } ?: c
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Skip unreadable files gracefully
                    }
                }
            }

            // Final face clustering pass
            if (allDetectedFaces.isNotEmpty()) {
                val clusters = FaceDetectionEngine.clusterFaces(allDetectedFaces)
                val existingNames = _peopleClusters.value.associate { it.personId to it.name }
                _peopleClusters.value = clusters.map { c ->
                    existingNames[c.personId]?.let { customName -> c.copy(name = customName) } ?: c
                }
            }
        }
    }

    fun selectPerson(person: PersonCluster?) {
        _selectedPerson.value = person
    }

    fun renamePerson(personId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val updated = _peopleClusters.value.map { cluster ->
            if (cluster.personId == personId) {
                cluster.copy(name = trimmed)
            } else cluster
        }
        _peopleClusters.value = updated
        if (_selectedPerson.value?.personId == personId) {
            _selectedPerson.value = _selectedPerson.value?.copy(name = trimmed)
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

    fun toggleFavoriteFilter() {
        _isFavoriteFilter.value = !_isFavoriteFilter.value
    }

    fun toggleItemSelection(id: String) {
        val current = _selectedItemIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedItemIds.value = current
    }

    fun selectAll() {
        _selectedItemIds.value = filteredMedia.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
    }

    fun batchToggleFavoriteSelected() {
        val selected = _selectedItemIds.value
        if (selected.isEmpty()) return
        val currentItems = _mediaItems.value
        val anyUnfavorite = currentItems.any { selected.contains(it.id) && !it.isFavorite }
        _mediaItems.value = currentItems.map { item ->
            if (selected.contains(item.id)) item.copy(isFavorite = anyUnfavorite) else item
        }
        clearSelection()
    }

    fun batchDeleteSelected() {
        val selected = _selectedItemIds.value
        if (selected.isEmpty()) return
        val remaining = _mediaItems.value.filter { !selected.contains(it.id) }
        _mediaItems.value = remaining
        clearSelection()
    }

    init {
        // Automatically check for updates silently in background on launch
        checkForUpdates(silent = true)
    }

    /**
     * Checks for updates against GitHub Releases.
     */
    fun checkForUpdates(silent: Boolean = false) {
        if (_isCheckingUpdate.value) return
        _isCheckingUpdate.value = true

        viewModelScope.launch {
            val result = UpdateChecker.checkForUpdate(APP_VERSION)
            _isCheckingUpdate.value = false

            result.onSuccess { info ->
                if (info != null) {
                    _updateInfo.value = info
                    _showUpdateDialog.value = true
                    // Post system notification so user knows even if outside app
                    val context = getApplication<Application>()
                    AndroidAppUpdater.showUpdateNotification(context, info)
                } else if (!silent) {
                    // Manual check: already up to date
                    _updateInfo.value = null
                }
            }.onFailure {
                // Silently ignore if offline, without disturbing user
            }
        }
    }

    fun openUpdateDialog() {
        _showUpdateDialog.value = true
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    /**
     * Starts downloading the APK update with progress reporting.
     */
    fun startDownloadUpdate() {
        val info = _updateInfo.value ?: return
        val apkAsset = info.apkAsset ?: return
        if (_isDownloading.value) return

        _isDownloading.value = true
        val context = getApplication<Application>()

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            AndroidAppUpdater.downloadApk(
                context = context,
                downloadUrl = apkAsset.downloadUrl,
                versionName = info.versionName
            ).collect { progress ->
                _downloadProgress.value = progress
                if (progress.isComplete && progress.file != null) {
                    _isDownloading.value = false
                    _downloadedApkFile.value = progress.file
                    AndroidAppUpdater.showDownloadCompleteNotification(
                        context,
                        progress.file,
                        info.versionName
                    )
                } else if (progress.error != null) {
                    _isDownloading.value = false
                }
            }
        }
    }

    /**
     * Launches Android package installer for the downloaded APK.
     */
    fun installDownloadedUpdate() {
        val file = _downloadedApkFile.value ?: return
        val context = getApplication<Application>()
        AndroidAppUpdater.installApk(context, file)
    }
}

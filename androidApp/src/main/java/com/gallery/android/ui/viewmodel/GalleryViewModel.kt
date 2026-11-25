package com.gallery.android.ui.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.android.BuildConfig
import com.gallery.android.updater.AndroidAppUpdater
import com.gallery.android.updater.DownloadProgress
import com.gallery.core.ai.FaceDetectionEngine
import com.gallery.core.ai.PersonCluster
import com.gallery.core.ai.SemanticSearchEngine
import com.gallery.core.analyzer.MediaAnalyzer
import com.gallery.core.model.AiCategory
import com.gallery.core.model.Album
import com.gallery.core.model.ExifData
import com.gallery.core.model.ImageEditState
import com.gallery.core.model.MediaItem
import com.gallery.core.persistence.AnalyzerVersion
import com.gallery.core.persistence.FileMetadataPersistence
import com.gallery.core.persistence.ItemMetadata
import com.gallery.core.persistence.MediaMetadataStore
import com.gallery.core.scanner.MediaStoreScanner
import com.gallery.core.updater.UpdateChecker
import com.gallery.core.updater.UpdateInfo
import com.gallery.core.util.ExifExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AnalysisProgress(
    val analyzed: Int,
    val total: Int,
    val isRunning: Boolean
) {
    val fraction: Float get() = if (total <= 0) 0f else analyzed.toFloat() / total
}

@OptIn(FlowPreview::class)
class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        val APP_VERSION: String = BuildConfig.VERSION_NAME
        private const val BATCH_SIZE = 200
        private const val FLUSH_EVERY = 200
        private const val SEARCH_DEBOUNCE_MS = 220L
    }

    private val metadataFile = File(application.filesDir, "gallery-metadata.dat")
    private val metadataPersistence = FileMetadataPersistence(metadataFile)
    private val store = MediaMetadataStore(metadataPersistence)

    // ---------------------------------------------------------------- catalog
    /** Mutable working copy. Published to [_mediaItems] only at batch boundaries. */
    private val catalog = ArrayList<MediaItem>(512)
    private val catalogIndex = HashMap<String, Int>(512)

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _analysisProgress = MutableStateFlow<AnalysisProgress?>(null)
    val analysisProgress: StateFlow<AnalysisProgress?> = _analysisProgress.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private var scanJob: Job? = null
    private var analysisJob: Job? = null

    // ---------------------------------------------------------------- filters
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

    val isSelectionMode: StateFlow<Boolean> = _selectedItemIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _gridColumns = MutableStateFlow(3)
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _selectedItemIndex = MutableStateFlow<Int?>(null)
    val selectedItemIndex: StateFlow<Int?> = _selectedItemIndex.asStateFlow()

    private val _semanticMatchedConcepts = MutableStateFlow<List<String>>(emptyList())
    val semanticMatchedConcepts: StateFlow<List<String>> = _semanticMatchedConcepts.asStateFlow()

    private val _isSemanticSearchActive = MutableStateFlow(false)
    val isSemanticSearchActive: StateFlow<Boolean> = _isSemanticSearchActive.asStateFlow()

    // --------------------------------------------------------------- derived
    val albums: StateFlow<List<Album>> = _mediaItems
        .map { items ->
            items.groupBy { it.albumName }
                .map { (folder, inFolder) ->
                    Album(
                        id = folder.hashCode().toString(),
                        name = folder,
                        path = inFolder.firstOrNull()?.path ?: "",
                        coverItem = inFolder.firstOrNull(),
                        itemCount = inFolder.size
                    )
                }
                .sortedByDescending { it.itemCount }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val albumItems: StateFlow<List<MediaItem>> = combine(_mediaItems, _selectedAlbum) { items, album ->
        if (album == null) emptyList() else items.filter { it.albumName == album }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _filteredMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    val filteredMedia: StateFlow<List<MediaItem>> = _filteredMedia.asStateFlow()

    private val _groupedMedia = MutableStateFlow<Map<String, List<MediaItem>>>(emptyMap())
    val groupedMedia: StateFlow<Map<String, List<MediaItem>>> = _groupedMedia.asStateFlow()

    private val debouncedQuery = _searchQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()

    private fun observeFilters() {
        viewModelScope.launch {
            combine(
                _mediaItems,
                _selectedCategory,
                debouncedQuery,
                _isFavoriteFilter,
                _selectedPerson
            ) { items, category, query, favoritesOnly, person ->
                FilterInput(items, category, query, favoritesOnly, person)
            }.collectLatest { input ->
                val (filtered, concepts) = withContext(Dispatchers.Default) {
                    computeFiltered(input)
                }
                _filteredMedia.value = filtered
                _semanticMatchedConcepts.value = concepts
                _isSemanticSearchActive.value = input.query.isNotBlank() && filtered.isNotEmpty()
                _groupedMedia.value = withContext(Dispatchers.Default) { groupByDate(filtered) }
            }
        }
    }

    private data class FilterInput(
        val items: List<MediaItem>,
        val category: AiCategory?,
        val query: String,
        val favoritesOnly: Boolean,
        val person: PersonCluster?
    )

    private fun computeFiltered(input: FilterInput): Pair<List<MediaItem>, List<String>> {
        val personIds = input.person?.mediaItemIds?.toSet()
        val base = input.items.filter { item ->
            (input.category == null || item.aiCategory == input.category) &&
                    (!input.favoritesOnly || item.isFavorite) &&
                    (personIds == null || personIds.contains(item.id))
        }

        val query = input.query.trim()
        if (query.isBlank()) return base to emptyList()

        val results = SemanticSearchEngine.search(query, base, minScore = 0.28f)
        if (results.isEmpty()) {
            // Fall back to a plain substring match so typing exact filenames always works.
            val plain = base.filter {
                it.displayName.contains(query, ignoreCase = true) ||
                        it.albumName.contains(query, ignoreCase = true)
            }
            return plain to emptyList()
        }
        return results.map { it.item } to results.flatMap { it.matchedConcepts }.distinct().take(6)
    }

    private fun groupByDate(items: List<MediaItem>): Map<String, List<MediaItem>> {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val today = dayFormat.format(Date())
        val yesterday = dayFormat.format(Date(System.currentTimeMillis() - 86_400_000L))

        val ordered = LinkedHashMap<String, MutableList<MediaItem>>()
        for (item in items) {
            val timestamp = if (item.dateTakenSec > 0) item.dateTakenSec else item.dateModifiedSec
            val date = Date(timestamp * 1000L)
            val dayKey = dayFormat.format(date)
            val header = when (dayKey) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> monthFormat.format(date)
            }
            ordered.getOrPut(header) { mutableListOf() } += item
        }
        return ordered
    }

    // ------------------------------------------------------------------ load
    fun loadMedia() {
        if (_isLoading.value || scanJob?.isActive == true) return
        _isLoading.value = true

        scanJob = viewModelScope.launch {
            // 1. Restore persisted user state + cached analysis.
            withContext(Dispatchers.IO) { store.reloadFromPersistence() }

            // 2. Scan MediaStore and merge with persisted state.
            val scanned = ArrayList<MediaItem>(512)
            MediaStoreScanner.scanAllMedia(getApplication()).collect { scanned += it }

            val merged = ArrayList<MediaItem>(scanned.size)
            for (item in scanned) {
                val cached = store.get(item.id)
                merged += cached?.applyTo(item) ?: item
            }

            // 3. Publish in batches so the grid appears immediately instead of
            //    after the entire library has been walked.
            catalog.clear()
            catalog.addAll(merged)
            rebuildCatalogIndex()
            for (end in BATCH_SIZE..catalog.size step BATCH_SIZE) {
                _mediaItems.value = catalog.subList(0, end).toList()
                delay(1)
            }
            _mediaItems.value = catalog.toList()

            // Do not prune metadata against this scan: on Android 14+ "Select photos"
            // access the scan only returns the chosen subset, and pruning would
            // silently drop favourites/edits for every other photo. Explicit deletes
            // remove their own records via store.remove().
            persist()
            _isLoading.value = false
            rebuildPeople()
            rebuildDuplicates()
            analyzeMissing()
        }
    }

    // --------------------------------------------------------------- analysis
    private fun analyzeMissing() {
        if (analysisJob?.isActive == true) return

        val pending = _mediaItems.value.filter { item ->
            item.isImage && !store.getOrCreate(item.id).isAnalysisFreshFor(item)
        }
        if (pending.isEmpty()) {
            _analysisProgress.value = null
            return
        }

        analysisJob = viewModelScope.launch {
            var analyzed = 0
            _analysisProgress.value = AnalysisProgress(0, pending.size, true)
            var sinceFlush = 0
            var sincePublish = 0

            for (item in pending) {
                if (!isActive) break
                val result = MediaAnalyzer.analyze(getApplication(), item) ?: continue

                withContext(Dispatchers.Main) {
                    store.update(item.id) { existing ->
                        existing.copy(
                            aiCategory = result.category,
                            aiConfidence = result.confidence,
                            tags = (existing.tags + result.tags).distinct(),
                            quantizedEmbedding = result.quantizedEmbedding,
                            perceptualHash = result.perceptualHash,
                            textRegionCount = result.textRegionCount,
                            faceSignatures = result.faceSignatures,
                            analyzedAtSec = item.dateModifiedSec,
                            analyzerVersion = AnalyzerVersion.CURRENT
                        )
                    }
                    applyStoreStateToCatalog(item.id)
                }

                analyzed++
                sinceFlush++
                sincePublish++
                _analysisProgress.value = AnalysisProgress(analyzed, pending.size, true)

                if (sincePublish >= 25) {
                    sincePublish = 0
                    _mediaItems.value = catalog.toList()
                }

                if (sinceFlush >= FLUSH_EVERY) {
                    sinceFlush = 0
                    persist()
                    rebuildPeople()
                    rebuildDuplicates()
                }
            }

            withContext(Dispatchers.Main) { _mediaItems.value = catalog.toList() }
            persist()
            rebuildPeople()
            rebuildDuplicates()
            _analysisProgress.value = null
        }
    }

    /** Mutates the working copy in place; the caller decides when to publish. */
    private fun applyStoreStateToCatalog(id: String) {
        val metadata = store.get(id) ?: return
        val index = catalogIndex[id] ?: return
        if (index !in catalog.indices || catalog[index].id != id) return
        catalog[index] = metadata.applyTo(catalog[index])
    }

    private fun rebuildCatalogIndex() {
        catalogIndex.clear()
        for (i in catalog.indices) catalogIndex[catalog[i].id] = i
    }

    /** Applies the store state for one item and republishes immediately. */
    private fun applyStoreState(id: String) {
        applyStoreStateToCatalog(id)
        _mediaItems.value = catalog.toList()
    }

    // ----------------------------------------------------------- persistence
    private fun persist() {
        if (!store.isDirty) return
        val snapshot = store.snapshot()
        store.markClean()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                metadataPersistence.save(com.gallery.core.persistence.MetadataCodec.encode(snapshot))
            } catch (_: Throwable) {
                // Next flush will retry; never crash the UI over metadata.
            }
        }
    }

    override fun onCleared() {
        persistNow()
        super.onCleared()
    }

    /** Synchronous flush used when the process may be going away. */
    fun persistNow() {
        if (!store.isDirty) return
        store.markClean()
        try {
            metadataPersistence.save(store.encode())
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------- people
    private val _peopleClusters = MutableStateFlow<List<PersonCluster>>(emptyList())
    val peopleClusters: StateFlow<List<PersonCluster>> = _peopleClusters.asStateFlow()

    private val _selectedPerson = MutableStateFlow<PersonCluster?>(null)
    val selectedPerson: StateFlow<PersonCluster?> = _selectedPerson.asStateFlow()

    private fun rebuildPeople() {
        val faces = ArrayList<com.gallery.core.ai.DetectedFace>()
        for ((id, metadata) in store.all()) {
            metadata.faceSignatures.forEachIndexed { index, signature ->
                if (signature.mediaItemId == id) faces += signature.toDetectedFace(index)
            }
        }
        val clusters = FaceDetectionEngine.clusterFaces(faces)
        val names = store.personNames()
        _peopleClusters.value = clusters.map { cluster ->
            names[cluster.personId]?.let { cluster.copy(name = it) } ?: cluster
        }
        _selectedPerson.value = _selectedPerson.value?.let { selected ->
            _peopleClusters.value.firstOrNull { it.personId == selected.personId }
        }
    }

    fun selectPerson(person: PersonCluster?) {
        _selectedPerson.value = person
    }

    fun renamePerson(personId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        store.setPersonName(personId, trimmed)
        persist()
        _peopleClusters.value = _peopleClusters.value.map {
            if (it.personId == personId) it.copy(name = trimmed) else it
        }
        if (_selectedPerson.value?.personId == personId) {
            _selectedPerson.value = _selectedPerson.value?.copy(name = trimmed)
        }
    }

    // ------------------------------------------------------------ duplicates
    private val _duplicateGroups = MutableStateFlow<List<List<MediaItem>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<MediaItem>>> = _duplicateGroups.asStateFlow()

    private fun rebuildDuplicates() {
        viewModelScope.launch(Dispatchers.Default) {
            val byId = _mediaItems.value.associateBy { it.id }
            val hashed = store.all().values
                .filter { it.perceptualHash != 0UL }
                .mapNotNull { metadata -> byId[metadata.id]?.let { it to metadata.perceptualHash } }
            if (hashed.size < 2) {
                _duplicateGroups.value = emptyList()
                return@launch
            }
            val clusters = com.gallery.core.ai.PerceptualHasher.clusterDuplicates(hashed, maxDistance = 6)
            _duplicateGroups.value = clusters.map { cluster ->
                listOf(cluster.primaryItem) + cluster.duplicateItems
            }
        }
    }

    // ---------------------------------------------------------------- edits
    fun editStateFor(item: MediaItem): ImageEditState = store.getEditState(item.id)

    fun saveEditState(itemId: String, state: ImageEditState) {
        store.setEditState(itemId, state)
        applyStoreState(itemId)
        persist()
    }

    // ----------------------------------------------------------------- exif
    private val exifCache = object : LinkedHashMap<String, ExifData?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExifData?>): Boolean =
            size > 32
    }

    suspend fun exifFor(item: MediaItem): ExifData? {
        exifCache[item.uri]?.let { return it }
        val exif = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(item.uri)
                if (uri.scheme == "content") {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        ExifExtractor.extract(it)
                    }
                } else {
                    ExifExtractor.extract(File(item.path))
                }
            } catch (_: Throwable) {
                null
            }
        }
        exifCache[item.uri] = exif
        return exif
    }

    // -------------------------------------------------------------- favorites
    fun toggleFavorite(itemId: String) {
        store.toggleFavorite(itemId)
        applyStoreState(itemId)
        persist()
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
        if (!current.add(id)) current.remove(id)
        _selectedItemIds.value = current
    }

    fun selectAll() {
        _selectedItemIds.value = _filteredMedia.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
    }

    fun batchToggleFavoriteSelected() {
        val selected = _selectedItemIds.value
        if (selected.isEmpty()) return
        val anyUnfavorite = _mediaItems.value.any { selected.contains(it.id) && !it.isFavorite }
        selected.forEach { store.setFavorite(it, anyUnfavorite) }
        selected.forEach { applyStoreState(it) }
        persist()
        clearSelection()
    }

    // ---------------------------------------------------------------- delete
    private val _deleteRequest = MutableStateFlow<IntentSender?>(null)
    val deleteRequest: StateFlow<IntentSender?> = _deleteRequest.asStateFlow()

    private var pendingDeleteIds: List<String> = emptyList()

    /**
     * Deletes real media. On Android 11+ a system Trash confirmation is launched
     * (the request surfaces through [deleteRequest] and the Activity must launch it);
     * earlier versions delete directly with the legacy write permission.
     */
    fun requestDeleteSelected() {
        val ids = _selectedItemIds.value.toList()
        if (ids.isEmpty()) return
        val uris = _mediaItems.value.filter { ids.contains(it.id) }.map { Uri.parse(it.uri) }
        if (uris.isEmpty()) {
            clearSelection()
            return
        }
        pendingDeleteIds = ids

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent: PendingIntent = MediaStore.createTrashRequest(
                    getApplication<Application>().contentResolver,
                    uris,
                    true
                )
                _deleteRequest.value = pendingIntent.intentSender
            } catch (e: Exception) {
                _userMessage.value = "Could not request deletion: ${e.message}"
                pendingDeleteIds = emptyList()
            }
        } else {
            deletePermanently(ids, uris)
        }
    }

    /** Call from the Activity once the system trash dialog returns. */
    fun onDeleteRequestResult(success: Boolean) {
        val ids = pendingDeleteIds
        pendingDeleteIds = emptyList()
        _deleteRequest.value = null
        if (!success) {
            _userMessage.value = "Deletion cancelled"
            return
        }
        val uris = _mediaItems.value.filter { ids.contains(it.id) }.map { Uri.parse(it.uri) }
        deletePermanently(ids, uris)
    }

    private fun deletePermanently(ids: List<String>, uris: List<Uri>) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            var deleted = 0
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    try {
                        if (resolver.delete(uri, null, null) > 0) deleted++
                    } catch (_: Throwable) {
                    }
                }
            }
            store.remove(ids)
            persist()
            catalog.removeAll { ids.contains(it.id) }
            rebuildCatalogIndex()
            _mediaItems.value = catalog.toList()
            clearSelection()
            rebuildPeople()
            rebuildDuplicates()
            _userMessage.value = if (deleted == uris.size) {
                "Moved to Trash"
            } else if (deleted > 0) {
                "Moved $deleted of ${uris.size} items to Trash"
            } else {
                "Could not delete: the system blocked the request"
            }
        }
    }

    // ---------------------------------------------------------------- updater
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

    // Declared last on purpose: init blocks run in source order, and both of these
    // read StateFlows/properties declared above (including _selectedPerson and the
    // update flows). Starting them earlier caused a NullPointerException at launch.
    init {
        observeFilters()
        checkForUpdates(silent = true)
    }

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
                    AndroidAppUpdater.showUpdateNotification(getApplication(), info)
                } else if (!silent) {
                    _updateInfo.value = null
                    _userMessage.value = "You're on the latest version"
                }
            }.onFailure {
                if (!silent) _userMessage.value = "Could not check for updates (offline?)"
            }
        }
    }

    fun openUpdateDialog() {
        _showUpdateDialog.value = true
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun startDownloadUpdate() {
        val info = _updateInfo.value ?: return
        val asset = info.apkAsset ?: run {
            _userMessage.value = "No APK attached to this release"
            return
        }
        if (_isDownloading.value) return

        _isDownloading.value = true
        _downloadProgress.value = DownloadProgress(progress = 0.01f)
        val context = getApplication<Application>()

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            AndroidAppUpdater.downloadApk(
                context = context,
                downloadUrl = asset.downloadUrl,
                versionName = info.versionName,
                expectedSha256 = asset.expectedSha256
            ).collect { progress ->
                _downloadProgress.value = progress
                when {
                    progress.error != null -> {
                        _isDownloading.value = false
                        _userMessage.value = progress.error
                    }
                    progress.isComplete && progress.file != null -> {
                        _isDownloading.value = false
                        _downloadedApkFile.value = progress.file
                        AndroidAppUpdater.showDownloadCompleteNotification(
                            context, progress.file, info.versionName
                        )
                    }
                }
            }
        }
    }

    fun installDownloadedUpdate() {
        val file = _downloadedApkFile.value ?: return
        AndroidAppUpdater.installApk(getApplication(), file)
    }

    fun consumeMessage() {
        _userMessage.value = null
    }
}

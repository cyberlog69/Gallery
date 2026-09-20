package com.gallery.desktop.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.gallery.core.ai.AiClassificationResult
import com.gallery.core.decoder.DesktopImageDecoder
import com.gallery.core.model.AiCategory
import com.gallery.core.model.MediaItem
import com.gallery.core.scanner.DesktopFileScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DesktopGalleryViewModel(
    private val scope: CoroutineScope
) {
    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentBitmap = MutableStateFlow<ImageBitmap?>(null)
    val currentBitmap: StateFlow<ImageBitmap?> = _currentBitmap.asStateFlow()

    private val _currentAiResult = MutableStateFlow<AiClassificationResult?>(null)
    val currentAiResult: StateFlow<AiClassificationResult?> = _currentAiResult.asStateFlow()

    private val _zoom = MutableStateFlow(1.0f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private val _rotation = MutableStateFlow(0)
    val rotation: StateFlow<Int> = _rotation.asStateFlow()

    private val _panOffset = MutableStateFlow(Pair(0f, 0f))
    val panOffset: StateFlow<Pair<Float, Float>> = _panOffset.asStateFlow()

    private val _isSlideshowRunning = MutableStateFlow(false)
    val isSlideshowRunning: StateFlow<Boolean> = _isSlideshowRunning.asStateFlow()

    private val _showExif = MutableStateFlow(false)
    val showExif: StateFlow<Boolean> = _showExif.asStateFlow()

    private val _showFilmstrip = MutableStateFlow(true)
    val showFilmstrip: StateFlow<Boolean> = _showFilmstrip.asStateFlow()

    private val _currentFolder = MutableStateFlow<File?>(null)
    val currentFolder: StateFlow<File?> = _currentFolder.asStateFlow()

    private val _activeCategoryFilter = MutableStateFlow<AiCategory?>(null)
    val activeCategoryFilter: StateFlow<AiCategory?> = _activeCategoryFilter.asStateFlow()

    private var slideshowJob: Job? = null
    private var loadJob: Job? = null

    val currentItem: MediaItem?
        get() = _mediaItems.value.getOrNull(_currentIndex.value)

    fun loadFolder(folder: File) {
        _currentFolder.value = folder
        scope.launch {
            val items = mutableListOf<MediaItem>()
            DesktopFileScanner.scanDirectory(folder, recursive = false).collect { item ->
                items.add(item)
                _mediaItems.value = items.toList()
                if (items.size == 1) {
                    selectIndex(0)
                }
            }
        }
    }

    fun selectIndex(index: Int) {
        val list = _mediaItems.value
        if (list.isEmpty()) return
        val newIndex = index.coerceIn(0, list.size - 1)
        if (_currentIndex.value == newIndex && _currentBitmap.value != null) return

        _currentIndex.value = newIndex
        resetTransform()
        loadCurrentMedia()
    }

    fun next() {
        val list = _mediaItems.value
        if (list.isEmpty()) return
        val nextIdx = (_currentIndex.value + 1) % list.size
        selectIndex(nextIdx)
    }

    fun previous() {
        val list = _mediaItems.value
        if (list.isEmpty()) return
        val prevIdx = if (_currentIndex.value - 1 < 0) list.size - 1 else _currentIndex.value - 1
        selectIndex(prevIdx)
    }

    fun rotateRight() {
        _rotation.value = (_rotation.value + 90) % 360
    }

    fun rotateLeft() {
        _rotation.value = (_rotation.value - 90 + 360) % 360
    }

    fun setZoom(newZoom: Float) {
        _zoom.value = newZoom.coerceIn(0.1f, 10.0f)
        if (_zoom.value <= 1.0f) {
            _panOffset.value = Pair(0f, 0f)
        }
    }

    fun zoomIn() {
        setZoom(_zoom.value * 1.25f)
    }

    fun zoomOut() {
        setZoom(_zoom.value / 1.25f)
    }

    fun toggleActualSize() {
        if (_zoom.value != 1.0f) {
            setZoom(1.0f)
            _panOffset.value = Pair(0f, 0f)
        } else {
            setZoom(2.0f)
        }
    }

    fun updatePan(dx: Float, dy: Float) {
        if (_zoom.value > 1.0f) {
            val (curX, curY) = _panOffset.value
            _panOffset.value = Pair(curX + dx, curY + dy)
        }
    }

    fun resetTransform() {
        _zoom.value = 1.0f
        _rotation.value = 0
        _panOffset.value = Pair(0f, 0f)
    }

    fun toggleSlideshow() {
        if (_isSlideshowRunning.value) {
            stopSlideshow()
        } else {
            startSlideshow()
        }
    }

    private fun startSlideshow() {
        _isSlideshowRunning.value = true
        slideshowJob?.cancel()
        slideshowJob = scope.launch {
            while (isActive && _isSlideshowRunning.value) {
                delay(3000)
                next()
            }
        }
    }

    private fun stopSlideshow() {
        _isSlideshowRunning.value = false
        slideshowJob?.cancel()
        slideshowJob = null
    }

    fun toggleExif() {
        _showExif.value = !_showExif.value
    }

    fun toggleFilmstrip() {
        _showFilmstrip.value = !_showFilmstrip.value
    }

    fun filterByCategory(category: AiCategory?) {
        _activeCategoryFilter.value = category
    }

    private fun loadCurrentMedia() {
        loadJob?.cancel()
        val item = currentItem ?: return

        loadJob = scope.launch {
            val file = File(item.path)
            if (!file.exists()) return@launch

            if (item.isImage) {
                // High-resolution preview downscaled to 1920 to keep RAM below ~20MB
                val buff = DesktopImageDecoder.loadThumbnail(file, targetSizePx = 1920)
                if (buff != null) {
                    _currentBitmap.value = buff.toComposeImageBitmap()
                }

                // Run offline AI classification in background
                val ai = DesktopImageDecoder.classifyImage(file)
                _currentAiResult.value = ai
            } else {
                // Video: generate first-frame thumbnail for preview
                val buff = DesktopImageDecoder.loadThumbnail(file, targetSizePx = 1280)
                if (buff != null) {
                    _currentBitmap.value = buff.toComposeImageBitmap()
                }
                _currentAiResult.value = null
            }
        }
    }

    fun deleteCurrent() {
        val item = currentItem ?: return
        val file = File(item.path)
        if (file.exists()) {
            file.delete()
        }
        val list = _mediaItems.value.toMutableList()
        list.remove(item)
        _mediaItems.value = list
        if (list.isNotEmpty()) {
            selectIndex(_currentIndex.value.coerceIn(0, list.size - 1))
        } else {
            _currentBitmap.value = null
        }
    }
}

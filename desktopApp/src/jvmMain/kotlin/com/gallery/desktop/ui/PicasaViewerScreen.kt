package com.gallery.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallery.desktop.ui.theme.PicasaAccent
import com.gallery.desktop.ui.theme.PicasaBackground
import com.gallery.desktop.ui.theme.PicasaTextPrimary
import com.gallery.desktop.ui.theme.PicasaTextSecondary
import com.gallery.desktop.viewmodel.DesktopGalleryViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PicasaViewerScreen(
    viewModel: DesktopGalleryViewModel,
    modifier: Modifier = Modifier
) {
    val mediaItems by viewModel.mediaItems.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val currentBitmap by viewModel.currentBitmap.collectAsState()
    val currentAiResult by viewModel.currentAiResult.collectAsState()
    val zoom by viewModel.zoom.collectAsState()
    val rotation by viewModel.rotation.collectAsState()
    val panOffset by viewModel.panOffset.collectAsState()
    val isSlideshowRunning by viewModel.isSlideshowRunning.collectAsState()
    val showExif by viewModel.showExif.collectAsState()
    val showFilmstrip by viewModel.showFilmstrip.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val showUpdatePill by viewModel.showUpdatePill.collectAsState()
    val showUpdateModal by viewModel.showUpdateModal.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val showSetDefaultDialog by viewModel.showSetDefaultDialog.collectAsState()

    val currentItem = viewModel.currentItem

    val openFolderDialog = {
        val dialog = FileDialog(null as Frame?, "Select Folder with Pictures or Videos", FileDialog.LOAD)
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null) {
            val selectedPath = if (file != null) File(dir, file) else File(dir)
            val targetFolder = if (selectedPath.isDirectory) selectedPath else selectedPath.parentFile
            if (targetFolder != null && targetFolder.exists()) {
                viewModel.loadFolder(targetFolder)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PicasaBackground)
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta < 0) {
                    viewModel.zoomIn()
                } else if (delta > 0) {
                    viewModel.zoomOut()
                }
            }
    ) {
        if (mediaItems.isEmpty()) {
            // Empty state: welcoming hero screen
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    PicasaApertureLogo(size = 84.dp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Picasa Photo Viewer",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = PicasaTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ultra-fast offline media viewer with AI classification & RAM optimization",
                        fontSize = 14.sp,
                        color = PicasaTextSecondary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = openFolderDialog,
                            colors = ButtonDefaults.buttonColors(containerColor = PicasaAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Pictures Folder", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.openSetDefaultDialog() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Set as Default Viewer", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Supports: JPEG, PNG, WebP, GIF, BMP, HEIC, AVIF, TIFF, RAW, MP4, MKV, AVI, MOV",
                        fontSize = 11.sp,
                        color = Color(0x77FFFFFF)
                    )
                }
            }
        } else {
            // Main Photo Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            viewModel.updatePan(dragAmount.x, dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap!!,
                        contentDescription = currentItem?.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                rotationZ = rotation.toFloat()
                                translationX = panOffset.first
                                translationY = panOffset.second
                            }
                    )
                    if (currentItem?.isVideo == true) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(40.dp))
                                .background(Color(0x99000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Play Video",
                                tint = Color.White,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                } else {
                    CircularProgressIndicator(color = PicasaAccent)
                }
            }

            // Top Header: File name and counter (e.g. 15 / 120)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PicasaApertureLogo(size = 22.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = currentItem?.displayName ?: "",
                        color = PicasaTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${currentIndex + 1} of ${mediaItems.size}",
                        color = PicasaTextSecondary,
                        fontSize = 13.sp
                    )
                    if (currentItem != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "(${currentItem.formattedSize})",
                            color = Color(0x66FFFFFF),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Top Floating Update Notification Pill
            AnimatedVisibility(
                visible = showUpdatePill && updateInfo != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                if (updateInfo != null) {
                    UpdateNotificationPill(
                        updateInfo = updateInfo!!,
                        onOpenDetails = { viewModel.openUpdateModal() },
                        onDownload = { viewModel.startDownloadUpdate() },
                        onDismiss = { viewModel.dismissUpdatePill() }
                    )
                }
            }

            // EXIF Info Panel
            if (showExif && currentItem != null) {
                ExifPanel(
                    item = currentItem,
                    onClose = { viewModel.toggleExif() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 60.dp, end = 24.dp)
                )
            }

            // Bottom Controls Section
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Floating Picasa Control Pill
                FloatingControlBar(
                    zoom = zoom,
                    isSlideshowRunning = isSlideshowRunning,
                    showExif = showExif,
                    showFilmstrip = showFilmstrip,
                    aiResult = currentAiResult,
                    onPrevious = { viewModel.previous() },
                    onNext = { viewModel.next() },
                    onZoomIn = { viewModel.zoomIn() },
                    onZoomOut = { viewModel.zoomOut() },
                    onZoomChange = { viewModel.setZoom(it) },
                    onToggleActualSize = { viewModel.toggleActualSize() },
                    onRotateLeft = { viewModel.rotateLeft() },
                    onRotateRight = { viewModel.rotateRight() },
                    onToggleSlideshow = { viewModel.toggleSlideshow() },
                    onToggleExif = { viewModel.toggleExif() },
                    onToggleFilmstrip = { viewModel.toggleFilmstrip() },
                    onOpenFolder = openFolderDialog,
                    onSetDefaultViewer = { viewModel.openSetDefaultDialog() },
                    updateAvailable = updateInfo != null,
                    isCheckingUpdate = isCheckingUpdate,
                    onCheckUpdate = {
                        if (updateInfo != null) {
                            viewModel.openUpdateModal()
                        } else {
                            viewModel.checkForUpdates(silent = false)
                        }
                    },
                    modifier = Modifier.padding(bottom = if (showFilmstrip) 12.dp else 24.dp)
                )

                // Bottom Filmstrip Carousel
                AnimatedVisibility(
                    visible = showFilmstrip,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Filmstrip(
                        mediaItems = mediaItems,
                        currentIndex = currentIndex,
                        onSelectIndex = { viewModel.selectIndex(it) }
                    )
                }
            }

            // Desktop Update Modal Dialog
            if (showUpdateModal && updateInfo != null) {
                UpdateModalDialog(
                    updateInfo = updateInfo!!,
                    downloadProgress = downloadProgress,
                    isDownloading = isDownloading,
                    onStartDownload = { viewModel.startDownloadUpdate() },
                    onOpenInBrowser = { viewModel.openReleaseInBrowser() },
                    onDismiss = { viewModel.dismissUpdateModal() }
                )
            }
        }

        // Set as Default Photo Viewer Dialog
        if (showSetDefaultDialog) {
            SetDefaultViewerDialog(
                sampleFile = currentItem?.path?.let { File(it) },
                onDismiss = { viewModel.dismissSetDefaultDialog() }
            )
        }
    }
}

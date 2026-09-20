package com.gallery.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.gallery.android.ui.components.UpdateNotificationDialog
import com.gallery.android.ui.screens.AlbumsScreen
import com.gallery.android.ui.screens.AiCategoriesScreen
import com.gallery.android.ui.screens.MediaViewerScreen
import com.gallery.android.ui.screens.TimelineScreen
import com.gallery.android.ui.theme.GalleryTheme
import com.gallery.android.ui.viewmodel.GalleryViewModel

enum class GalleryTab(val title: String) {
    PHOTOS("Photos"),
    ALBUMS("Albums"),
    AI_CATEGORIES("AI Explore")
}

class MainActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GalleryTheme {
                val selectedIndex by viewModel.selectedItemIndex.collectAsState()
                val filteredMedia by viewModel.filteredMedia.collectAsState()
                val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
                val updateInfo by viewModel.updateInfo.collectAsState()
                val downloadProgress by viewModel.downloadProgress.collectAsState()
                val isDownloading by viewModel.isDownloading.collectAsState()
                var currentTab by remember { mutableStateOf(GalleryTab.PHOTOS) }

                // Check if launched from update notification
                LaunchedEffect(intent) {
                    if (intent.getBooleanExtra("EXTRA_OPEN_UPDATE", false)) {
                        viewModel.openUpdateDialog()
                    }
                }

                // Permission launcher
                val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val mediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        (permissions[Manifest.permission.READ_MEDIA_IMAGES] == true ||
                         permissions[Manifest.permission.READ_MEDIA_VIDEO] == true)
                    } else {
                        permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
                    }
                    if (mediaGranted) {
                        viewModel.loadMedia()
                    }
                }

                LaunchedEffect(Unit) {
                    val allGranted = permissionsToRequest.all {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (allGranted) {
                        viewModel.loadMedia()
                    } else {
                        permissionLauncher.launch(permissionsToRequest)
                    }
                }

                if (selectedIndex != null) {
                    // Full-screen Viewer Mode
                    BackHandler { viewModel.closeViewer() }
                    MediaViewerScreen(
                        items = filteredMedia,
                        initialIndex = selectedIndex!!,
                        onBack = { viewModel.closeViewer() }
                    )
                } else {
                    // Standard Gallery Mode with Material 3 Navigation Bar
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentTab == GalleryTab.PHOTOS,
                                    onClick = { currentTab = GalleryTab.PHOTOS },
                                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Photos") },
                                    label = { Text(GalleryTab.PHOTOS.title) }
                                )
                                NavigationBarItem(
                                    selected = currentTab == GalleryTab.ALBUMS,
                                    onClick = { currentTab = GalleryTab.ALBUMS },
                                    icon = { Icon(Icons.Default.Folder, contentDescription = "Albums") },
                                    label = { Text(GalleryTab.ALBUMS.title) }
                                )
                                NavigationBarItem(
                                    selected = currentTab == GalleryTab.AI_CATEGORIES,
                                    onClick = { currentTab = GalleryTab.AI_CATEGORIES },
                                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI") },
                                    label = { Text(GalleryTab.AI_CATEGORIES.title) }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (currentTab) {
                                GalleryTab.PHOTOS -> {
                                    TimelineScreen(viewModel = viewModel)
                                }
                                GalleryTab.ALBUMS -> {
                                    AlbumsScreen(viewModel = viewModel)
                                }
                                GalleryTab.AI_CATEGORIES -> {
                                    AiCategoriesScreen(
                                        viewModel = viewModel,
                                        onCategorySelected = {
                                            currentTab = GalleryTab.PHOTOS
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // In-App Update Notification Dialog
                if (showUpdateDialog && updateInfo != null) {
                    UpdateNotificationDialog(
                        updateInfo = updateInfo!!,
                        downloadProgress = downloadProgress,
                        isDownloading = isDownloading,
                        onStartDownload = { viewModel.startDownloadUpdate() },
                        onInstall = { viewModel.installDownloadedUpdate() },
                        onDismiss = { viewModel.dismissUpdateDialog() }
                    )
                }
            }
        }
    }
}

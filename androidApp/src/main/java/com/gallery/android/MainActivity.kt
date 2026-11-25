package com.gallery.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    /** Incremented when a notification asks us to open the update dialog. */
    private val openUpdateSignal = mutableIntStateOf(0)

    /** Incremented on every resume so Compose can re-check permissions. */
    private val resumeSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleNotificationIntent(intent)

        setContent {
            GalleryTheme {
                val selectedIndex by viewModel.selectedItemIndex.collectAsState()
                val filteredMedia by viewModel.filteredMedia.collectAsState()
                val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
                val updateInfo by viewModel.updateInfo.collectAsState()
                val downloadProgress by viewModel.downloadProgress.collectAsState()
                val isDownloading by viewModel.isDownloading.collectAsState()
                val message by viewModel.userMessage.collectAsState()

                var currentTab by remember { mutableStateOf(GalleryTab.PHOTOS) }
                var hasMediaAccess by remember { mutableStateOf(hasMediaPermission()) }

                LaunchedEffect(openUpdateSignal.intValue) {
                    if (openUpdateSignal.intValue > 0) viewModel.openUpdateDialog()
                }

                LaunchedEffect(message) {
                    message?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }

                // Re-check permissions when returning from system settings.
                LaunchedEffect(resumeSignal.intValue) {
                    hasMediaAccess = hasMediaPermission()
                    if (hasMediaAccess && viewModel.mediaItems.value.isEmpty()) {
                        viewModel.loadMedia()
                    }
                }

                val mediaPermissions = rememberMediaPermissions()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasMediaAccess = hasMediaPermission()
                    if (hasMediaAccess) {
                        viewModel.loadMedia()
                        maybeRequestNotificationPermission()
                    }
                }

                val deleteLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    viewModel.onDeleteRequestResult(result.resultCode == Activity.RESULT_OK)
                }

                LaunchedEffect(Unit) {
                    viewModel.deleteRequest.collect { sender: IntentSender? ->
                        if (sender != null) {
                            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    if (hasMediaAccess) {
                        viewModel.loadMedia()
                    } else {
                        permissionLauncher.launch(mediaPermissions)
                    }
                }

                if (!hasMediaAccess) {
                    PermissionGate(
                        onRequest = { permissionLauncher.launch(mediaPermissions) },
                        onOpenSettings = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", packageName, null)
                                )
                            )
                        }
                    )
                } else if (selectedIndex != null) {
                    BackHandler { viewModel.closeViewer() }
                    MediaViewerScreen(
                        items = filteredMedia,
                        initialIndex = selectedIndex!!,
                        onBack = { viewModel.closeViewer() },
                        onMediaUpdated = { viewModel.loadMedia() }
                    )
                } else {
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
                                GalleryTab.PHOTOS -> TimelineScreen(viewModel = viewModel)
                                GalleryTab.ALBUMS -> AlbumsScreen(viewModel = viewModel)
                                GalleryTab.AI_CATEGORIES -> AiCategoriesScreen(
                                    viewModel = viewModel,
                                    onCategorySelected = { currentTab = GalleryTab.PHOTOS }
                                )
                            }
                        }
                    }
                }

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

    override fun onResume() {
        super.onResume()
        resumeSignal.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * A notification tap either asks for the update dialog or, when the download
     * already completed, launches the package installer for the downloaded APK.
     * The intent is explicit (MainActivity) so the PendingIntent cannot be hijacked;
     * the installer itself is started here with a read grant on our FileProvider URI.
     */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return

        val apkUri = intent.getStringExtra(EXTRA_APK_URI)
        if (apkUri != null) {
            intent.removeExtra(EXTRA_APK_URI)
            val mime = intent.getStringExtra(EXTRA_APK_MIME_TYPE)
                ?: "application/vnd.android.package-archive"
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(apkUri), mime)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            }.onFailure {
                Toast.makeText(this, "Could not open the package installer", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATE, false)) {
            openUpdateSignal.intValue++
        }
    }

    override fun onStop() {
        super.onStop()
        // Persist metadata while the process is still alive.
        viewModel.persistNow()
    }

    /**
     * Notification permission is optional (update alerts only) and is requested at
     * most once per install, never bundled with the media permission dialog.
     */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences("gallery_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("notif_asked", false)) return
        prefs.edit().putBoolean("notif_asked", true).apply()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4242) }
        }
    }

    private fun hasMediaPermission(): Boolean {
        val needed = when {
            // API 34+: partial ("Select photos") access grants only the
            // VISUAL_USER_SELECTED permission while IMAGES/VIDEO stay denied.
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return needed.any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @Composable
    private fun rememberMediaPermissions(): Array<String> = remember {
        when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    companion object {
        const val EXTRA_OPEN_UPDATE = "EXTRA_OPEN_UPDATE"
        const val EXTRA_APK_URI = "EXTRA_APK_URI"
        const val EXTRA_APK_MIME_TYPE = "EXTRA_APK_MIME_TYPE"
    }
}

@Composable
private fun PermissionGate(
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Photos permission needed",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Gallery is fully offline. It only needs read access to show your photos and videos - nothing is uploaded anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
        )
        Button(onClick = onRequest) { Text("Allow access") }
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 8.dp)) {
            Text("Open settings")
        }
    }
}

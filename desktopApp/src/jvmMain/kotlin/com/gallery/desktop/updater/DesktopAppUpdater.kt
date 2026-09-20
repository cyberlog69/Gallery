package com.gallery.desktop.updater

import com.gallery.core.updater.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.awt.Desktop
import java.awt.Image
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI

data class DesktopDownloadProgress(
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isComplete: Boolean = false,
    val file: File? = null,
    val error: String? = null
)

object DesktopAppUpdater {

    private var trayIcon: TrayIcon? = null

    /**
     * Opens the GitHub release page or asset download URL in the user's default browser.
     */
    fun openInBrowser(url: String) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Downloads the release asset into the user's Downloads directory with reactive progress reporting.
     */
    fun downloadAsset(
        downloadUrl: String,
        fileName: String
    ): Flow<DesktopDownloadProgress> = flow {
        val userHome = System.getProperty("user.home")
        val downloadsDir = File(userHome, "Downloads").apply { mkdirs() }
        val targetFile = File(downloadsDir, fileName)

        try {
            emit(DesktopDownloadProgress(progress = 0.01f, downloadedBytes = 0L, totalBytes = 0L))

            val connection = URI.create(downloadUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true

            var currentConnection = connection
            val status = currentConnection.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                val newUrl = currentConnection.getHeaderField("Location")
                currentConnection = URI.create(newUrl).toURL().openConnection() as HttpURLConnection
            }

            val totalBytes = currentConnection.contentLengthLong
            var downloadedBytes = 0L

            currentConnection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var lastEmitted = 0f

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0.5f
                        }

                        if (progress - lastEmitted >= 0.02f || downloadedBytes == totalBytes) {
                            lastEmitted = progress
                            emit(
                                DesktopDownloadProgress(
                                    progress = progress,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            )
                        }
                    }
                }
            }

            emit(
                DesktopDownloadProgress(
                    progress = 1.0f,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    isComplete = true,
                    file = targetFile
                )
            )
        } catch (e: Throwable) {
            emit(
                DesktopDownloadProgress(
                    progress = 0f,
                    error = e.message ?: "Download failed"
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Displays a native Windows notification balloon using SystemTray.
     */
    fun showWindowsNotification(updateInfo: UpdateInfo) {
        try {
            if (!SystemTray.isSupported()) return

            val tray = SystemTray.getSystemTray()
            if (trayIcon == null) {
                val dummyImage: Image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
                trayIcon = TrayIcon(dummyImage, "Gallery Viewer").apply {
                    isImageAutoSize = true
                }
                tray.add(trayIcon)
            }

            trayIcon?.displayMessage(
                "Gallery Update Available: v${updateInfo.versionName}",
                updateInfo.title.ifBlank { "A new version of Gallery is available to download." },
                TrayIcon.MessageType.INFO
            )
        } catch (e: Exception) {
            // Silently ignore if SystemTray is restricted or unsupported
        }
    }
}

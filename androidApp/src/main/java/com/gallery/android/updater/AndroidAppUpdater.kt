package com.gallery.android.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.gallery.android.MainActivity
import com.gallery.core.updater.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI

data class DownloadProgress(
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isComplete: Boolean = false,
    val file: File? = null,
    val error: String? = null
)

object AndroidAppUpdater {

    private const val CHANNEL_ID = "gallery_updates"
    private const val NOTIFICATION_ID_AVAILABLE = 1001
    private const val NOTIFICATION_ID_COMPLETE = 1002

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "App Updates"
            val descriptionText = "Notifications for new Gallery app versions and downloads"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Downloads APK asset in background with reactive progress reporting.
     */
    fun downloadApk(
        context: Context,
        downloadUrl: String,
        versionName: String
    ): Flow<DownloadProgress> = flow {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, "Gallery-v$versionName.apk")

        try {
            emit(DownloadProgress(progress = 0.01f, downloadedBytes = 0L, totalBytes = 0L))

            val connection = URI.create(downloadUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true

            // Follow redirects manually if needed (e.g. GitHub release CDN)
            var redirected = false
            var currentConnection = connection
            var status = currentConnection.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                redirected = true
                val newUrl = currentConnection.getHeaderField("Location")
                currentConnection = URI.create(newUrl).toURL().openConnection() as HttpURLConnection
            }

            val totalBytes = currentConnection.contentLength.toLong()
            var downloadedBytes = 0L

            currentConnection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var lastEmittedProgress = 0f

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0.5f
                        }

                        // Emit updates in ~2% increments to avoid overwhelming Compose state
                        if (progress - lastEmittedProgress >= 0.02f || downloadedBytes == totalBytes) {
                            lastEmittedProgress = progress
                            emit(
                                DownloadProgress(
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
                DownloadProgress(
                    progress = 1.0f,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    isComplete = true,
                    file = targetFile
                )
            )
        } catch (e: Throwable) {
            emit(
                DownloadProgress(
                    progress = 0f,
                    error = e.message ?: "Download failed"
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Triggers package installer with FileProvider URI.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(intent)
    }

    /**
     * Posts system notification informing the user about an available update.
     */
    fun showUpdateNotification(context: Context, updateInfo: UpdateInfo) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_OPEN_UPDATE", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Gallery Update Available: v${updateInfo.versionName}")
            .setContentText(updateInfo.title.ifBlank { "New version available with improvements!" })
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(if (updateInfo.body.isNotBlank()) updateInfo.body else updateInfo.title)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_AVAILABLE, notification)
        } catch (e: SecurityException) {
            // Android 13+ without POST_NOTIFICATIONS permission granted
        }
    }

    /**
     * Posts system notification when APK download completes, tapping it directly launches installer.
     */
    fun showDownloadCompleteNotification(context: Context, apkFile: File, versionName: String) {
        createNotificationChannel(context)

        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Gallery v$versionName Ready to Install")
            .setContentText("Download complete. Tap to install the latest version.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_COMPLETE, notification)
        } catch (e: SecurityException) {
            // Android 13+ without POST_NOTIFICATIONS permission granted
        }
    }
}

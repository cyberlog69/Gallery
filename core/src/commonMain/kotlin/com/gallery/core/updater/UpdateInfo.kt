package com.gallery.core.updater

/**
 * Metadata representation of a release asset (e.g., APK or JAR/MSI).
 */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long = 0L,
    val contentType: String = ""
)

/**
 * Release information fetched from GitHub Releases.
 */
data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val body: String,
    val releaseUrl: String,
    val publishedAt: String = "",
    val assets: List<ReleaseAsset> = emptyList()
) {
    val apkAsset: ReleaseAsset?
        get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    val desktopAsset: ReleaseAsset?
        get() = assets.firstOrNull {
            it.name.endsWith(".jar", ignoreCase = true) ||
            it.name.endsWith(".msi", ignoreCase = true) ||
            it.name.endsWith(".exe", ignoreCase = true) ||
            it.name.endsWith(".deb", ignoreCase = true)
        }

    val formattedApkSize: String
        get() = formatBytes(apkAsset?.sizeBytes ?: 0L)

    val formattedDesktopSize: String
        get() = formatBytes(desktopAsset?.sizeBytes ?: 0L)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "Unknown size"
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1.0) "%.1f MB".format(mb) else "${bytes / 1024} KB"
        }
    }
}

/**
 * State machine for the in-app update lifecycle.
 */
sealed class UpdateStatus {
    data object Idle : UpdateStatus()
    data object Checking : UpdateStatus()
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateStatus()
    data object UpToDate : UpdateStatus()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateStatus()
    data class ReadyToInstall(val filePath: String) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

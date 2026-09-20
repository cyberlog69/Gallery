package com.gallery.desktop.util

import java.io.File

object WindowsDefaultAppHelper {

    val SUPPORTED_EXTENSIONS = listOf(
        ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".avif", ".heic", ".tif", ".tiff", ".ico"
    )

    fun getExecutablePath(): String {
        try {
            val cmd = ProcessHandle.current().info().command().orElse(null)
            if (cmd != null && cmd.endsWith(".exe", ignoreCase = true) && !cmd.contains("java", ignoreCase = true)) {
                return cmd
            }
        } catch (e: Throwable) {
            // ignore
        }

        val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        val standardExe = File(programFiles, "PicasaGalleryViewer\\PicasaGalleryViewer.exe")
        if (standardExe.exists()) {
            return standardExe.absolutePath
        }

        val localAppData = System.getenv("LOCALAPPDATA") ?: ""
        if (localAppData.isNotBlank()) {
            val userExe = File(localAppData, "Programs\\PicasaGalleryViewer\\PicasaGalleryViewer.exe")
            if (userExe.exists()) {
                return userExe.absolutePath
            }
        }

        return standardExe.absolutePath
    }

    /**
     * Registers Picasa Photo Viewer into Windows Registry (HKCU)
     * No admin rights required.
     */
    fun registerInRegistry(): Result<Unit> = runCatching {
        val exePath = getExecutablePath()

        // 1. ProgID setup
        runReg("add \"HKCU\\Software\\Classes\\PicasaGalleryViewer.AssocFile\" /ve /t REG_SZ /d \"Picasa Photo Viewer Image\" /f")
        runReg("add \"HKCU\\Software\\Classes\\PicasaGalleryViewer.AssocFile\\DefaultIcon\" /ve /t REG_SZ /d \"\\\"$exePath\\\",0\" /f")
        runReg("add \"HKCU\\Software\\Classes\\PicasaGalleryViewer.AssocFile\\shell\\open\\command\" /ve /t REG_SZ /d \"\\\"$exePath\\\" \\\"%1\\\"\" /f")

        // 2. Application setup
        runReg("add \"HKCU\\Software\\Classes\\Applications\\PicasaGalleryViewer.exe\\shell\\open\\command\" /ve /t REG_SZ /d \"\\\"$exePath\\\" \\\"%1\\\"\" /f")
        runReg("add \"HKCU\\Software\\PicasaGalleryViewer\\Capabilities\" /v \"ApplicationName\" /t REG_SZ /d \"Picasa Photo Viewer\" /f")
        runReg("add \"HKCU\\Software\\PicasaGalleryViewer\\Capabilities\" /v \"ApplicationDescription\" /t REG_SZ /d \"Picasa-style Ultra-fast Media Viewer\" /f")

        for (ext in SUPPORTED_EXTENSIONS) {
            runReg("add \"HKCU\\Software\\Classes\\$ext\\OpenWithProgids\" /v \"PicasaGalleryViewer.AssocFile\" /t REG_NONE /f")
            runReg("add \"HKCU\\Software\\Classes\\Applications\\PicasaGalleryViewer.exe\\SupportedTypes\" /v \"$ext\" /t REG_SZ /d \"\" /f")
            runReg("add \"HKCU\\Software\\PicasaGalleryViewer\\Capabilities\\FileAssociations\" /v \"$ext\" /t REG_SZ /d \"PicasaGalleryViewer.AssocFile\" /f")
        }

        // 3. RegisteredApplications link
        runReg("add \"HKCU\\Software\\RegisteredApplications\" /v \"PicasaPhotoViewer\" /t REG_SZ /d \"Software\\PicasaGalleryViewer\\Capabilities\" /f")
    }

    /**
     * Opens Windows Settings > Default Apps
     */
    fun openDefaultAppsSettings() {
        try {
            ProcessBuilder("cmd", "/c", "start", "ms-settings:defaultapps").start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Launches the Windows native "Open With" dialog with "Always use this app" checkbox for a sample file.
     */
    fun openWithDialog(sampleFile: File?) {
        try {
            val fileToOpen = if (sampleFile != null && sampleFile.exists()) {
                sampleFile
            } else {
                // Create dummy temp picture file if none exists
                val tmp = File(System.getProperty("java.io.tmpdir"), "gallery_sample.jpg")
                if (!tmp.exists()) {
                    tmp.createNewFile()
                }
                tmp
            }
            ProcessBuilder("rundll32.exe", "shell32.dll,OpenAs_RunDLL", fileToOpen.absolutePath).start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun runReg(command: String) {
        val fullCmd = "reg $command"
        val process = ProcessBuilder("cmd", "/c", fullCmd).start()
        process.waitFor()
    }
}

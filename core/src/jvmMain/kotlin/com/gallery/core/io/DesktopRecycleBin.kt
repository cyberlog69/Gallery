package com.gallery.core.io

import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Windows & JVM Safe File Operations and Recycle Bin Manager.
 * Prevents accidental data loss by utilizing the native OS Recycle Bin (FOF_ALLOWUNDO)
 * with a reliable fallback trash folder.
 */
object DesktopRecycleBin {

    private val fallbackTrashDir by lazy {
        val dir = File(System.getProperty("user.home"), ".gallery_trash")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    /**
     * Safely moves a file to the OS Recycle Bin (Windows Recycle Bin / macOS Trash).
     * If native desktop trash is not supported, moves to a fallback ~/.gallery_trash folder.
     */
    fun moveToRecycleBin(file: File): Boolean {
        if (!file.exists()) return false

        // 1. Try Java Desktop Native Recycle Bin (Windows Shell FOF_ALLOWUNDO)
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                if (Desktop.getDesktop().moveToTrash(file)) {
                    return true
                }
            }
        } catch (_: Throwable) {
            // Native trash action threw an exception, proceed to fallback
        }

        // 2. Safe Fallback: Move to local trash folder with timestamp
        return try {
            val target = File(fallbackTrashDir, "${System.currentTimeMillis()}_${file.name}")
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Batches multiple files to the OS Recycle Bin.
     * @return Number of files successfully moved to trash.
     */
    fun batchMoveToRecycleBin(files: List<File>): Int {
        var count = 0
        for (f in files) {
            if (moveToRecycleBin(f)) {
                count++
            }
        }
        return count
    }

    /**
     * Batch renames files with date prefixes (e.g. "2026-09-29_Sunset.jpg").
     * Resolves collisions automatically with numbered suffixes.
     */
    fun batchRenameWithDate(
        files: List<File>,
        datePattern: String = "yyyy-MM-dd"
    ): List<File> {
        val dateFormat = SimpleDateFormat(datePattern, Locale.getDefault())
        val renamedList = mutableListOf<File>()

        for (file in files) {
            if (!file.exists()) continue
            val dateStr = dateFormat.format(Date(file.lastModified()))
            val ext = file.extension
            val baseName = file.nameWithoutExtension

            // If already prefixed with this date, keep as is
            if (baseName.startsWith(dateStr)) {
                renamedList.add(file)
                continue
            }

            var newName = if (ext.isNotEmpty()) "${dateStr}_${baseName}.$ext" else "${dateStr}_${baseName}"
            var targetFile = File(file.parentFile, newName)
            var counter = 1
            while (targetFile.exists() && targetFile.absolutePath != file.absolutePath) {
                val candidate = if (ext.isNotEmpty()) "${dateStr}_${baseName}_$counter.$ext" else "${dateStr}_${baseName}_$counter"
                targetFile = File(file.parentFile, candidate)
                counter++
            }

            try {
                if (file.renameTo(targetFile)) {
                    renamedList.add(targetFile)
                } else {
                    renamedList.add(file)
                }
            } catch (_: Exception) {
                renamedList.add(file)
            }
        }

        return renamedList
    }

    /**
     * Moves a batch of files safely to a destination directory.
     */
    fun batchMoveToFolder(files: List<File>, destinationFolder: File): List<File> {
        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs()
        }
        val movedFiles = mutableListOf<File>()

        for (file in files) {
            if (!file.exists()) continue
            var target = File(destinationFolder, file.name)
            var counter = 1
            while (target.exists()) {
                val ext = file.extension
                val base = file.nameWithoutExtension
                val candidateName = if (ext.isNotEmpty()) "${base}_$counter.$ext" else "${base}_$counter"
                target = File(destinationFolder, candidateName)
                counter++
            }

            try {
                Files.move(file.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                movedFiles.add(target)
            } catch (_: Exception) {
                // If atomic move across drives fails, try copy + safe trash
                try {
                    Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    file.delete()
                    movedFiles.add(target)
                } catch (_: Exception) {
                    // Skip
                }
            }
        }

        return movedFiles
    }
}

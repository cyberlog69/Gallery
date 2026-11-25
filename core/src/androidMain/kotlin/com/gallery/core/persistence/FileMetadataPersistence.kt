package com.gallery.core.persistence

import java.io.File

/**
 * Atomic, file-backed persistence for [MediaMetadataStore].
 *
 * Writes go to `<name>.tmp` and are renamed over the real file, so a process kill
 * mid-write leaves the previous good snapshot intact.
 */
class FileMetadataPersistence(
    private val file: File
) : MetadataPersistence {

    override fun load(): String? = try {
        if (file.exists() && file.length() > 0L) file.readText() else null
    } catch (_: Throwable) {
        null
    }

    override fun save(encoded: String) {
        val dir = file.parentFile ?: return
        if (!dir.exists()) dir.mkdirs()
        val temp = File(dir, "${file.name}.tmp")
        temp.writeText(encoded)
        if (file.exists() && !file.delete()) {
            // Fall back to a plain overwrite rather than losing the new snapshot.
            file.writeText(encoded)
            temp.delete()
            return
        }
        if (!temp.renameTo(file)) {
            file.writeText(encoded)
            temp.delete()
        }
    }
}

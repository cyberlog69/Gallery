package com.gallery.core.vault

/**
 * Metadata record for an item secured in the encrypted vault.
 */
data class VaultRecord(
    val id: String,
    val encryptedFilename: String,
    val originalDisplayName: String,
    val originalSizeBytes: Long,
    val dateEncryptedSec: Long,
    val mimeType: String = "image/jpeg"
) {
    fun toJson(): String {
        return """{"id":"$id","encFile":"$encryptedFilename","name":"$originalDisplayName","size":$originalSizeBytes,"date":$dateEncryptedSec,"mime":"$mimeType"}"""
    }

    companion object {
        fun fromJson(json: String): VaultRecord? {
            return try {
                fun getStr(key: String): String {
                    return "\"$key\":\\s*\"([^\"]+)\"".toRegex().find(json)?.groupValues?.get(1) ?: ""
                }
                fun getLong(key: String): Long {
                    return "\"$key\":\\s*([0-9]+)".toRegex().find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                }
                val id = getStr("id")
                if (id.isEmpty()) return null
                VaultRecord(
                    id = id,
                    encryptedFilename = getStr("encFile"),
                    originalDisplayName = getStr("name"),
                    originalSizeBytes = getLong("size"),
                    dateEncryptedSec = getLong("date"),
                    mimeType = getStr("mime")
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

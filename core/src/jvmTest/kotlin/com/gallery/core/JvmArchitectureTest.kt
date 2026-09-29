package com.gallery.core

import com.gallery.core.edit.DesktopImageEditor
import com.gallery.core.model.ImageEditState
import com.gallery.core.persistence.DesktopMetadataStore
import com.gallery.core.vault.JvmVaultEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File

class JvmArchitectureTest {

    @Test
    fun testJvmVaultEngineEncryptionDecryption() {
        val passphrase = "SecureGalleryPassword2026!".toCharArray()
        val salt = JvmVaultEngine.generateSalt()
        val key = JvmVaultEngine.deriveKey(passphrase, salt)

        val sampleSecretData = "Super secret sensitive photo EXIF and raw pixel payload".toByteArray()

        // Encrypt with AES-256-GCM
        val encryptedPayload = JvmVaultEngine.encrypt(sampleSecretData, key)
        assertTrue("Ciphertext must be longer than plaintext due to IV and GCM auth tag",
            encryptedPayload.size > sampleSecretData.size + 12)

        // Decrypt with key
        val decryptedData = JvmVaultEngine.decrypt(encryptedPayload, key)
        assertArrayEquals(sampleSecretData, decryptedData)

        // Memory wiping
        JvmVaultEngine.wipeMemory(decryptedData)
        assertTrue(decryptedData.all { it == 0.toByte() })
    }

    @Test
    fun testDesktopMetadataStorePersistence() {
        val tempFile = File.createTempFile("gallery_test_metadata", ".json")
        tempFile.deleteOnExit()

        val store = DesktopMetadataStore(tempFile)
        assertFalse(store.isFavorite("photo123"))

        // Toggle favorite
        store.setFavorite("photo123", true)
        assertTrue(store.isFavorite("photo123"))

        // Set edit state
        val edit = ImageEditState(exposure = 0.8f, rotationDegrees = 90f)
        store.setEditState("photo123", edit)
        assertEquals(0.8f, store.getEditState("photo123").exposure, 0.01f)

        // Reload store from disk file to verify persistence
        val reloadedStore = DesktopMetadataStore(tempFile)
        assertTrue(reloadedStore.isFavorite("photo123"))
        assertEquals(0.8f, reloadedStore.getEditState("photo123").exposure, 0.01f)
    }

    @Test
    fun testDesktopImageEditorExport() {
        val img = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
        val edit = ImageEditState(exposure = 0.5f)
        val tempOutput = File.createTempFile("edited_export", ".jpg")
        tempOutput.deleteOnExit()

        val success = DesktopImageEditor.exportToFile(img, edit, tempOutput, format = "jpg")
        assertTrue(success)
        assertTrue(tempOutput.exists())
        assertTrue(tempOutput.length() > 0)
    }

    @Test
    fun testThumbnailDiskCache() {
        val tempDir = java.nio.file.Files.createTempDirectory("thumb_cache_test").toFile()
        tempDir.deleteOnExit()

        val diskCache = com.gallery.core.cache.ThumbnailDiskCache(tempDir, maxCapacityBytes = 1000L)
        val sampleData = ByteArray(400) { 1 }

        assertFalse(diskCache.has("keyA"))
        diskCache.put("keyA", sampleData)
        assertTrue(diskCache.has("keyA"))
        assertArrayEquals(sampleData, diskCache.get("keyA"))
        assertEquals(400L, diskCache.currentSize())

        // Put another 400 bytes
        diskCache.put("keyB", sampleData)
        assertEquals(800L, diskCache.currentSize())

        // Exceeding capacity should evict keyA (oldest)
        diskCache.put("keyC", sampleData)
        assertTrue("Cache size must stay <= 1000 bytes", diskCache.currentSize() <= 1000L)
        assertFalse("Oldest entry keyA should have been evicted", diskCache.has("keyA"))
        assertTrue(diskCache.has("keyC"))
    }
}

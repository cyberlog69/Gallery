package com.gallery.core

import com.gallery.core.edit.DesktopImageEditor
import com.gallery.core.edit.ImageEditEngine
import com.gallery.core.io.DesktopClipboardHelper
import com.gallery.core.io.DesktopRecycleBin
import com.gallery.core.model.AspectRatioPreset
import com.gallery.core.model.ImageEditState
import com.gallery.core.scanner.IncrementalDesktopScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

class Phase1EvolutionTest {

    @Test
    fun testHighlightsAndShadowsEditing() {
        val state = ImageEditState(
            exposure = 0.2f,
            contrast = 1.1f,
            highlights = 0.5f,
            shadows = -0.3f,
            straightenAngle = 15.0f
        )
        assertTrue(state.isEdited)

        // JSON round-trip
        val json = state.toJson()
        val parsed = ImageEditState.fromJson(json)
        assertEquals(0.2f, parsed.exposure, 0.01f)
        assertEquals(1.1f, parsed.contrast, 0.01f)
        assertEquals(0.5f, parsed.highlights, 0.01f)
        assertEquals(-0.3f, parsed.shadows, 0.01f)
        assertEquals(15.0f, parsed.straightenAngle, 0.01f)

        // Pixel processing
        val pixels = IntArray(100) { 0xFF808080.toInt() } // neutral gray
        val edited = ImageEditEngine.applyEdits(pixels, 10, 10, state)
        assertEquals(10, edited.width)
        assertEquals(10, edited.height)
        assertFalse(edited.pixels.contentEquals(pixels))
    }

    @Test
    fun testAspectRatioPresetCalculation() {
        // Landscape input (1920x1080)
        val squareCrop = AspectRatioPreset.SQUARE.calculateRect(1920, 1080)
        assertTrue("Square crop on landscape image must crop sides", squareCrop.left > 0f)
        assertTrue("Square crop on landscape image must crop sides", squareCrop.right < 1f)
        assertEquals(0f, squareCrop.top, 0.001f)
        assertEquals(1f, squareCrop.bottom, 0.001f)

        // Portrait input (1080x1920)
        val landscapeCrop = AspectRatioPreset.LANDSCAPE.calculateRect(1080, 1920)
        assertEquals(0f, landscapeCrop.left, 0.001f)
        assertEquals(1f, landscapeCrop.right, 0.001f)
        assertTrue("Landscape crop on portrait image must crop top/bottom", landscapeCrop.top > 0f)
        assertTrue("Landscape crop on portrait image must crop top/bottom", landscapeCrop.bottom < 1f)
    }

    @Test
    fun testSaveInPlaceWithBackup() {
        val tempDir = Files.createTempDirectory("backup_test").toFile()
        tempDir.deleteOnExit()
        val sampleFile = File(tempDir, "sunset.jpg")

        val img = BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(img, "jpg", sampleFile)
        assertTrue(sampleFile.exists())

        val edit = ImageEditState(exposure = 0.5f)
        val saved = DesktopImageEditor.saveInPlaceWithBackup(sampleFile, img, edit)
        assertTrue(saved)

        // Check backup file exists
        val backupFile = File(tempDir, "sunset.jpg.bak")
        assertTrue("Backup file must be preserved", backupFile.exists())
        assertTrue("Original file must be updated", sampleFile.length() > 0)
    }

    @Test
    fun testIncrementalDesktopScanner() {
        val tempIndex = File.createTempFile("lib_index", ".json")
        tempIndex.deleteOnExit()

        val scanner = IncrementalDesktopScanner(tempIndex)
        val tempDir = Files.createTempDirectory("scanner_test").toFile()
        tempDir.deleteOnExit()

        val sample1 = File(tempDir, "mountain.jpg")
        val img = BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(img, "jpg", sample1)

        val item1 = scanner.getOrIndexFile(sample1)
        assertEquals("mountain.jpg", item1.displayName)
        assertEquals(1, scanner.cachedItemCount)

        // Second call should return instantly from cache
        val itemCached = scanner.getOrIndexFile(sample1)
        assertEquals(item1.id, itemCached.id)

        // Full text search
        val results = scanner.searchLibrary("mountain")
        assertEquals(1, results.size)
        assertEquals("mountain.jpg", results[0].displayName)

        val emptyResults = scanner.searchLibrary("nonexistent_photo_xyz")
        assertTrue(emptyResults.isEmpty())
    }

    @Test
    fun testDesktopRecycleBinAndBatchOperations() {
        val tempDir = Files.createTempDirectory("recycle_test").toFile()
        tempDir.deleteOnExit()

        val file1 = File(tempDir, "photoA.jpg")
        file1.writeText("test data 1")
        val file2 = File(tempDir, "photoB.jpg")
        file2.writeText("test data 2")

        // Batch rename with date
        val renamed = DesktopRecycleBin.batchRenameWithDate(listOf(file1, file2))
        assertEquals(2, renamed.size)
        assertTrue(renamed.all { it.exists() })
        assertTrue("Filename should contain date", renamed[0].name.matches(Regex("\\d{4}-\\d{2}-\\d{2}_.*")))

        // Move to Recycle Bin (or fallback trash)
        val trashed = DesktopRecycleBin.moveToRecycleBin(renamed[0])
        assertTrue("File must be safely trashed", trashed)
        assertFalse("Original location must no longer have file", renamed[0].exists())
    }

    @Test
    fun testDesktopClipboardHelper() {
        val tempDir = Files.createTempDirectory("clip_test").toFile()
        tempDir.deleteOnExit()
        val file = File(tempDir, "test.png")
        file.writeText("sample")

        // Copy files
        val success = DesktopClipboardHelper.copyFilesToClipboard(listOf(file))
        assertTrue(success)

        // Copy image
        val img = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
        val imgSuccess = DesktopClipboardHelper.copyImageToClipboard(img)
        assertTrue(imgSuccess)
    }
}

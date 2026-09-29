package com.gallery.core

import com.gallery.core.ai.PerceptualHasher
import com.gallery.core.edit.ImageEditEngine
import com.gallery.core.model.ImageEditState
import com.gallery.core.model.MediaItem
import com.gallery.core.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureEvolutionTest {

    @Test
    fun testImageEditStateSerialization() {
        val state = ImageEditState(
            cropRect = NormalizedRect(0.1f, 0.2f, 0.8f, 0.9f),
            rotationDegrees = 90f,
            flipHorizontal = true,
            flipVertical = false,
            exposure = 0.5f,
            contrast = 1.2f,
            saturation = 1.1f,
            temperature = 0.3f,
            tint = -0.1f
        )
        val json = state.toJson()
        val deserialized = ImageEditState.fromJson(json)

        assertTrue(deserialized.isEdited)
        assertEquals(90f, deserialized.rotationDegrees, 0.01f)
        assertTrue(deserialized.flipHorizontal)
        assertFalse(deserialized.flipVertical)
        assertEquals(0.5f, deserialized.exposure, 0.01f)
        assertEquals(1.2f, deserialized.contrast, 0.01f)
        assertEquals(0.3f, deserialized.temperature, 0.01f)
        assertEquals(0.1f, deserialized.cropRect?.left ?: 0f, 0.01f)
        assertEquals(0.8f, deserialized.cropRect?.right ?: 0f, 0.01f)
    }

    @Test
    fun testImageEditEngineCrop() {
        // Create 100x100 pixel grid
        val width = 100
        val height = 100
        val pixels = IntArray(width * height) { idx -> idx }

        val crop = NormalizedRect(0.2f, 0.2f, 0.7f, 0.7f)
        val state = ImageEditState(cropRect = crop)
        val result = ImageEditEngine.applyEdits(pixels, width, height, state)

        assertEquals(50, result.width)
        assertEquals(50, result.height)
        assertEquals(50 * 50, result.pixels.size)
    }

    @Test
    fun testImageEditEngineColorAdjustment() {
        // Solid mid-gray image: RGB(128, 128, 128)
        val midGray = (0xFF shl 24) or (128 shl 16) or (128 shl 8) or 128
        val pixels = IntArray(64) { midGray }

        // Boost exposure (+1.0 EV doubles brightness)
        val state = ImageEditState(exposure = 1.0f)
        val result = ImageEditEngine.applyEdits(pixels, 8, 8, state)

        val samplePixel = result.pixels[0]
        val r = (samplePixel shr 16) and 0xFF
        assertTrue("Exposure boost must increase pixel intensity", r > 128)
    }

    @Test
    fun testImageEditEngineRotationAndFlip() {
        val width = 10
        val height = 20
        val pixels = IntArray(width * height) { 0xFF000000.toInt() }

        // 90 degree rotation swaps width and height
        val state = ImageEditState(rotationDegrees = 90f, flipHorizontal = true)
        val result = ImageEditEngine.applyEdits(pixels, width, height, state)

        assertEquals(20, result.width)
        assertEquals(10, result.height)
    }

    @Test
    fun testPerceptualHasherDuplicateDetection() {
        // Checkerboard pixels guarantee high and low alternating values
        val pixelsA = IntArray(64 * 64) { idx ->
            val x = idx % 64
            val y = idx / 64
            if ((x + y) % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }
        val pixelsB = pixelsA.clone()

        val hashA = PerceptualHasher.computeDHash(pixelsA, 64, 64)
        val hashB = PerceptualHasher.computeDHash(pixelsB, 64, 64)

        assertTrue("Hash must not be 0UL for non-empty image", hashA != 0UL)
        assertEquals(hashA, hashB)
        assertEquals(0, PerceptualHasher.hammingDistance(hashA, hashB))
        assertTrue(PerceptualHasher.isSimilar(hashA, hashB, maxDistance = 0))

        // Clustering duplicates
        val itemA = MediaItem(id = "1", path = "/photos/1.jpg", displayName = "1.jpg")
        val itemB = MediaItem(id = "2", path = "/photos/2.jpg", displayName = "2.jpg")
        val itemC = MediaItem(id = "3", path = "/photos/3.jpg", displayName = "3.jpg")

        // Invert some bits for C
        val hashC = hashA xor 0xFFFFFFFFFFFFFFFFUL

        val clusters = PerceptualHasher.clusterDuplicates(
            listOf(itemA to hashA, itemB to hashB, itemC to hashC),
            maxDistance = 2
        )

        assertEquals(1, clusters.size)
        assertEquals("1", clusters[0].primaryItem.id)
        assertEquals(1, clusters[0].duplicateItems.size)
        assertEquals("2", clusters[0].duplicateItems[0].id)
    }

    @Test
    fun testLocalAiEngineEmbeddingsAndSimilarity() {
        val pixelsA = IntArray(32 * 32) { 0xFF2E8B57.toInt() } // Forest green
        val pixelsB = IntArray(32 * 32) { 0xFF2E8B57.toInt() } // Identical forest green
        val pixelsC = IntArray(32 * 32) { 0xFFFF0000.toInt() } // Vibrant red

        val embA = com.gallery.core.ai.OfflineAiClassifier.generateEmbedding(pixelsA, 32, 32)
        val embB = com.gallery.core.ai.OfflineAiClassifier.generateEmbedding(pixelsB, 32, 32)
        val embC = com.gallery.core.ai.OfflineAiClassifier.generateEmbedding(pixelsC, 32, 32)

        assertEquals(16, embA.size)
        // Cosine similarity of identical images should be ~1.0
        val simAB = com.gallery.core.ai.OfflineAiClassifier.computeSimilarity(embA, embB)
        assertEquals(1.0f, simAB, 0.05f)

        // Forest green vs Red should have much lower similarity
        val simAC = com.gallery.core.ai.OfflineAiClassifier.computeSimilarity(embA, embC)
        assertTrue("Different scenes must have lower similarity than identical ones", simAC < simAB)
    }

    @Test
    fun testExpandedMediaItemModelDefaults() {
        val item = MediaItem(
            id = "test-id",
            path = "/sdcard/DCIM/camera.jpg",
            displayName = "camera.jpg",
            tags = listOf("family", "holiday"),
            isVaulted = true
        )
        assertFalse(item.hasEdits)
        assertTrue(item.isVaulted)
        assertEquals(2, item.tags.size)
        assertEquals(0UL, item.perceptualHash)
    }
}

package com.gallery.core

import com.gallery.core.ai.FaceSignature
import com.gallery.core.model.AiCategory
import com.gallery.core.model.ImageEditState
import com.gallery.core.model.NormalizedRect
import com.gallery.core.persistence.MediaMetadataStore
import com.gallery.core.persistence.MetadataPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryPersistence(initial: String? = null) : MetadataPersistence {
    var stored: String? = initial
    override fun load(): String? = stored
    override fun save(encoded: String) {
        stored = encoded
    }
}

class MetadataPersistenceTest {

    @Test
    fun testRoundTripPreservesUserStateAndAnalysis() {
        val persistence = InMemoryPersistence()
        val store = MediaMetadataStore(persistence)

        store.setFavorite("item-1", true)
        store.addTag("item-1", "holiday, summer")
        store.addTag("item-1", "pipe|name%")
        store.setEditState(
            "item-1",
            ImageEditState(rotationDegrees = 90f, exposure = 0.5f, cropRect = NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f))
        )
        store.setPerceptualHash("item-1", 0xDEADBEEFCAFEUL)

        val embedding = ByteArray(256) { (it - 128).toByte() }
        val faceSignature = FaceSignature(
            mediaItemId = "item-1",
            quantizedEmbedding = ByteArray(64) { (it * 3).toByte() },
            box = NormalizedRect(0.2f, 0.3f, 0.6f, 0.8f),
            confidence = 0.81f
        )

        store.update("item-1") { existing ->
            existing.copy(
                aiCategory = AiCategory.NATURE,
                aiConfidence = 0.87f,
                quantizedEmbedding = embedding,
                textRegionCount = 7,
                faceSignatures = listOf(faceSignature),
                analyzedAtSec = 1_700_000_000L,
                analyzerVersion = com.gallery.core.persistence.AnalyzerVersion.CURRENT
            )
        }
        store.setPersonName("person_x", "Alex")

        val written = store.flush { encoded -> persistence.save(encoded) }
        assertTrue("The store should have been dirty and flushed", written)
        assertTrue(persistence.stored!!.isNotBlank())

        val reloaded = MediaMetadataStore(persistence)
        assertTrue(reloaded.loadFromPersistence())
        assertFalse(reloaded.isDirty)

        val metadata = reloaded.get("item-1")
        assertTrue(metadata!!.isFavorite)
        assertTrue(metadata.tags.contains("holiday, summer"))
        assertTrue(metadata.tags.contains("pipe|name%"))
        assertEquals(90f, metadata.editState.rotationDegrees, 0.01f)
        assertEquals(0.9f, metadata.editState.cropRect!!.right, 0.01f)
        assertEquals(0xDEADBEEFCAFEUL, metadata.perceptualHash)
        assertEquals(AiCategory.NATURE, metadata.aiCategory)
        assertEquals(0.87f, metadata.aiConfidence, 0.001f)
        assertTrue(embedding.contentEquals(metadata.quantizedEmbedding))
        assertEquals(7, metadata.textRegionCount)
        assertEquals(1, metadata.faceSignatures.size)
        assertEquals(0.81f, metadata.faceSignatures[0].confidence, 0.01f)
        assertEquals("Alex", reloaded.getPersonName("person_x"))
    }

    @Test
    fun testCorruptRecordDoesNotDiscardOtherRecords() {
        val persistence = InMemoryPersistence()
        val store = MediaMetadataStore(persistence)
        store.setFavorite("good", true)
        store.setFavorite("also_good", true)
        store.flush { encoded -> persistence.save(encoded) }

        // Splice a malformed record into the middle of the payload.
        val lines = persistence.stored!!.lines().toMutableList()
        val insertAt = lines.indexOfLast { it.startsWith("M|") }
        lines.add(insertAt, "M|broken|1")
        persistence.stored = lines.joinToString("\n")

        val reloaded = MediaMetadataStore(persistence)
        reloaded.loadFromPersistence()

        assertTrue(reloaded.isFavorite("good"))
        assertTrue(reloaded.isFavorite("also_good"))
        assertEquals(null, reloaded.get("broken"))
    }

    @Test
    fun testUnknownPayloadStartsClean() {
        val persistence = InMemoryPersistence("this is not our format\nM|1|1")
        val store = MediaMetadataStore(persistence)

        assertTrue(store.loadFromPersistence())
        assertTrue(store.all().isEmpty())
    }
}

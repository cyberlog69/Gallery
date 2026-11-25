package com.gallery.core

import com.gallery.core.ai.DetectedFace
import com.gallery.core.ai.DocumentLayoutAnalyzer
import com.gallery.core.ai.FaceDetectionEngine
import com.gallery.core.ai.OfflineVisionEngine
import com.gallery.core.ai.SemanticSearchEngine
import com.gallery.core.model.AiCategory
import com.gallery.core.model.MediaItem
import com.gallery.core.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the offline analysis pipeline.
 *
 * These assert *behaviour of the shipped heuristics*: descriptor shape/quantization,
 * keyword search, face candidate clustering and document *layout* detection. They
 * deliberately do not assert any transcribed text, because this project ships no OCR
 * model and must never fabricate one.
 */
class Phase2IntelligenceTest {

    @Test
    fun testVisionDescriptorShapeQuantizationAndClassification() {
        val width = 64
        val height = 64
        val greenPixels = IntArray(width * height) {
            // Rich foliage green: R: 30, G: 160, B: 40
            (0xFF shl 24) or (30 shl 16) or (160 shl 8) or 40
        }

        val result = OfflineVisionEngine.processImage(greenPixels, width, height, "forest_trail.jpg")

        assertEquals(OfflineVisionEngine.EMBEDDING_DIM, result.embedding.size)
        assertEquals(OfflineVisionEngine.EMBEDDING_DIM, result.quantizedEmbedding.size)

        for (b in result.quantizedEmbedding) {
            assertTrue("Byte value must be valid INT8", b in -128..127)
        }

        assertEquals(AiCategory.NATURE, result.category)
        assertTrue(result.tags.contains("nature"))
        assertTrue("Confidence should be high for vivid uniform green", result.confidence >= 0.70f)

        // Descriptor must be L2-normalized.
        var normSq = 0f
        for (v in result.embedding) normSq += v * v
        assertEquals(1f, kotlin.math.sqrt(normSq), 1e-3f)

        val simSelf = OfflineVisionEngine.computeQuantizedSimilarity(
            result.quantizedEmbedding,
            result.quantizedEmbedding
        )
        assertTrue("Self similarity must be approx 1.0f", simSelf >= 0.98f)
    }

    @Test
    fun testSemanticSearchEngineNaturalLanguageQueries() {
        val width = 64
        val height = 64

        val greenPixels = IntArray(width * height) {
            (0xFF shl 24) or (30 shl 16) or (170 shl 8) or 35
        }
        val natureResult = OfflineVisionEngine.processImage(greenPixels, width, height, "hiking_forest.jpg")
        val natureItem = MediaItem(
            id = "item_nature",
            path = "/storage/emulated/0/DCIM/hiking_forest.jpg",
            displayName = "hiking_forest.jpg",
            albumName = "Vacation",
            aiCategory = natureResult.category,
            aiConfidence = natureResult.confidence,
            tags = natureResult.tags,
            quantizedEmbedding = natureResult.quantizedEmbedding
        )

        val docItem = MediaItem(
            id = "item_doc",
            path = "/storage/emulated/0/Documents/grocery_receipt.jpg",
            displayName = "grocery_receipt.jpg",
            albumName = "Receipts",
            aiCategory = AiCategory.DOCUMENTS,
            aiConfidence = 0.90f,
            tags = listOf("receipt", "document", "shopping"),
            textRegionCount = 6
        )

        val items = listOf(natureItem, docItem)

        val queryVector = SemanticSearchEngine.encodeQuery("forest trees hike")
        assertEquals(OfflineVisionEngine.EMBEDDING_DIM, queryVector.size)

        val searchNatureResults = SemanticSearchEngine.search("forest green nature", items, minScore = 0.20f)
        assertTrue("Search should return nature item as top match", searchNatureResults.isNotEmpty())
        assertEquals("item_nature", searchNatureResults.first().item.id)

        val searchDocResults = SemanticSearchEngine.search("receipt document", items, minScore = 0.20f)
        assertTrue("Search should match document item via tags", searchDocResults.isNotEmpty())
        assertEquals("item_doc", searchDocResults.first().item.id)
    }

    @Test
    fun testFaceDetectionAndAppearanceClustering() {
        val width = 160
        val height = 160

        // Synthetic portrait with skin-toned face area and darker eyes.
        val portraitPixels = IntArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if (x in 48..112 && y in 32..128) {
                if (y in 60..70 && (x in 60..70 || x in 90..100)) {
                    (0xFF shl 24) or (20 shl 16) or (20 shl 8) or 20
                } else if (y in 100..112 && x in 70..90) {
                    (0xFF shl 24) or (220 shl 16) or (110 shl 8) or 100
                } else {
                    (0xFF shl 24) or (215 shl 16) or (160 shl 8) or 125
                }
            } else {
                (0xFF shl 24) or (80 shl 16) or (80 shl 8) or 90
            }
        }

        val faces = FaceDetectionEngine.detectFaces(portraitPixels, width, height, "portrait_1")
        assertTrue("Should detect at least 1 face candidate in synthetic portrait", faces.isNotEmpty())

        val face = faces.first()
        assertEquals(FaceDetectionEngine.FACE_EMBEDDING_DIM, face.faceEmbedding.size)
        assertTrue("Confidence should be positive", face.confidence > 0f)
        assertTrue(face.boundingBox.width > 0f)
        assertTrue(face.boundingBox.height > 0f)

        val face1 = DetectedFace(
            faceId = "f1",
            mediaItemId = "img_1",
            boundingBox = NormalizedRect(0.2f, 0.2f, 0.6f, 0.6f),
            confidence = 0.85f,
            faceEmbedding = face.faceEmbedding.copyOf()
        )
        val face2 = DetectedFace(
            faceId = "f2",
            mediaItemId = "img_2",
            boundingBox = NormalizedRect(0.2f, 0.2f, 0.6f, 0.6f),
            confidence = 0.88f,
            faceEmbedding = face.faceEmbedding.copyOf()
        )
        val face3 = DetectedFace(
            faceId = "f3",
            mediaItemId = "img_3",
            boundingBox = NormalizedRect(0.1f, 0.1f, 0.5f, 0.5f),
            confidence = 0.80f,
            faceEmbedding = FloatArray(FaceDetectionEngine.FACE_EMBEDDING_DIM) {
                if (it % 2 == 0) -0.5f else 0.5f
            }
        )

        val clusters = FaceDetectionEngine.clusterFaces(
            listOf(face1, face2, face3),
            similarityThreshold = 0.70f,
            minDistinctMedia = 1
        )
        assertTrue("Should cluster into distinct person groups", clusters.size >= 2)

        val mainCluster = clusters.firstOrNull { it.mediaItemIds.contains("img_1") }
        assertNotNull(mainCluster)
        assertTrue(
            "Face 1 and Face 2 should be in the same person cluster",
            mainCluster!!.mediaItemIds.contains("img_2")
        )

        // Cluster ids must be stable across calls (no positional ids).
        val again = FaceDetectionEngine.clusterFaces(
            listOf(face1, face2, face3),
            similarityThreshold = 0.70f,
            minDistinctMedia = 1
        )
        assertEquals(
            clusters.map { it.personId }.toSet(),
            again.map { it.personId }.toSet()
        )
    }

    @Test
    fun testDocumentLayoutDetectionReportsStructureNotText() {
        val width = 128
        val height = 128
        val textBandRows = listOf(20..24, 40..44, 60..64, 80..84, 100..104)

        val docPixels = IntArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            val isTextRow = textBandRows.any { y in it }
            // Alternating dark strokes so each text row has strong horizontal contrast.
            if (isTextRow && x % 4 < 2) {
                (0xFF shl 24) or (30 shl 16) or (30 shl 8) or 30
            } else {
                (0xFF shl 24) or (245 shl 16) or (245 shl 8) or 245
            }
        }

        val analysis = DocumentLayoutAnalyzer.analyze(docPixels, width, height, "store_receipt.png")

        assertTrue("Should identify image as document-like", analysis.isDocument)
        assertTrue("Should find multiple text bands", analysis.lineRegions.size >= 2)
        assertEquals(analysis.lineRegions.size, analysis.textRegionCount)
        assertTrue("Paper coverage should be high", analysis.paperCoverage > 0.5f)
        assertTrue("Ink coverage should be positive", analysis.inkCoverage > 0f)
    }

    @Test
    fun testMediaItemAnalysisProperties() {
        val dummyEmbedding = ByteArray(256) { it.toByte() }
        val item = MediaItem(
            id = "test_item_1",
            path = "/storage/test.jpg",
            displayName = "test.jpg",
            quantizedEmbedding = dummyEmbedding,
            detectedFaceCount = 2,
            faceIds = listOf("face_a", "face_b"),
            textRegionCount = 4
        )

        assertTrue(item.hasFaces)
        assertEquals(2, item.detectedFaceCount)
        assertTrue(item.hasTextRegions)
        assertEquals(4, item.textRegionCount)
        assertEquals(256, item.quantizedEmbedding?.size)
        assertFalse(item.isVideo)
        assertTrue(item.isImage)
    }
}

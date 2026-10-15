package com.gallery.core

import com.gallery.core.ai.DetectedFace
import com.gallery.core.ai.FaceDetectionEngine
import com.gallery.core.ai.OfflineNeuralEngine
import com.gallery.core.ai.OfflineOcrEngine
import com.gallery.core.ai.SemanticSearchEngine
import com.gallery.core.model.AiCategory
import com.gallery.core.model.MediaItem
import com.gallery.core.model.MediaType
import com.gallery.core.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2IntelligenceTest {

    @Test
    fun testOfflineNeuralEngine256EmbeddingAndInt8Quantization() {
        // Generate a synthetic green nature image (64x64)
        val width = 64
        val height = 64
        val greenPixels = IntArray(width * height) { idx ->
            // Rich foliage green: R: 30, G: 160, B: 40
            (0xFF shl 24) or (30 shl 16) or (160 shl 8) or 40
        }

        val result = OfflineNeuralEngine.processImage(greenPixels, width, height, "forest_trail.jpg")

        // 1. Verify 256-D embedding
        assertEquals(256, result.embedding.size)
        assertEquals(256, result.quantizedEmbedding.size)
        assertEquals(OfflineNeuralEngine.EMBEDDING_DIM, result.embedding.size)

        // 2. Verify INT8 quantization range [-128..127]
        for (b in result.quantizedEmbedding) {
            assertTrue("Byte value must be valid INT8", b in -128..127)
        }

        // 3. Verify classification
        assertEquals(AiCategory.NATURE, result.category)
        assertTrue(result.tags.contains("nature") || result.tags.contains("green"))
        assertTrue("Confidence should be high for vivid uniform green", result.confidence >= 0.70f)

        // 4. Verify Quantized Cosine Similarity
        val simSelf = OfflineNeuralEngine.computeQuantizedSimilarity(
            result.quantizedEmbedding,
            result.quantizedEmbedding
        )
        assertTrue("Self similarity must be approx 1.0f", simSelf >= 0.98f)
    }

    @Test
    fun testSemanticSearchEngineNaturalLanguageQueries() {
        val width = 64
        val height = 64

        // Nature item
        val greenPixels = IntArray(width * height) {
            (0xFF shl 24) or (30 shl 16) or (170 shl 8) or 35
        }
        val natureResult = OfflineNeuralEngine.processImage(greenPixels, width, height, "hiking_forest.jpg")
        val natureItem = MediaItem(
            id = "item_nature",
            path = "/storage/emulated/0/DCIM/hiking_forest.jpg",
            displayName = "hiking_forest.jpg",
            albumName = "Vacation",
            aiCategory = natureResult.category,
            aiConfidence = natureResult.confidence,
            tags = natureResult.tags,
            embeddingVector = natureResult.embedding,
            quantizedEmbedding = natureResult.quantizedEmbedding
        )

        // Document item with OCR text
        val docItem = MediaItem(
            id = "item_doc",
            path = "/storage/emulated/0/Documents/grocery_receipt.jpg",
            displayName = "grocery_receipt.jpg",
            albumName = "Receipts",
            aiCategory = AiCategory.DOCUMENTS,
            aiConfidence = 0.90f,
            tags = listOf("receipt", "document", "shopping"),
            ocrText = "TARGET STORE #1042\nTOTAL: $42.50\nDATE: 2026-10-12\nPAID BY VISA"
        )

        val items = listOf(natureItem, docItem)

        // Query 1: "forest trees nature hike"
        val queryVector = SemanticSearchEngine.encodeQuery("forest trees hike")
        assertEquals(256, queryVector.size)

        val searchNatureResults = SemanticSearchEngine.search("forest green nature", items, minScore = 0.20f)
        assertTrue("Search should return nature item as top match", searchNatureResults.isNotEmpty())
        assertEquals("item_nature", searchNatureResults.first().item.id)

        // Query 2: "receipt total paid"
        val searchDocResults = SemanticSearchEngine.search("receipt total visa", items, minScore = 0.20f)
        assertTrue("Search should match document item via OCR & tags", searchDocResults.isNotEmpty())
        assertEquals("item_doc", searchDocResults.first().item.id)
    }

    @Test
    fun testFaceDetectionAndMorphologyEmbeddings() {
        val width = 64
        val height = 64

        // Synthetic portrait with face area (skin chrominance R: 210, G: 155, B: 120)
        val portraitPixels = IntArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if (x in 20..44 && y in 14..48) {
                // Skin region
                if (y in 24..28 && (x in 24..28 || x in 36..40)) {
                    // Dark eyes
                    (0xFF shl 24) or (20 shl 16) or (20 shl 8) or 20
                } else if (y in 40..44 && x in 28..36) {
                    // Warm lips
                    (0xFF shl 24) or (220 shl 16) or (110 shl 8) or 100
                } else {
                    // Skin
                    (0xFF shl 24) or (215 shl 16) or (160 shl 8) or 125
                }
            } else {
                // Neutral background
                (0xFF shl 24) or (80 shl 16) or (80 shl 8) or 90
            }
        }

        val faces = FaceDetectionEngine.detectFaces(portraitPixels, width, height, "portrait_1.jpg")
        assertTrue("Should detect at least 1 face window in synthetic portrait", faces.isNotEmpty())

        val face = faces.first()
        assertEquals(FaceDetectionEngine.FACE_EMBEDDING_DIM, face.faceEmbedding.size)
        assertTrue("Confidence should be positive", face.confidence > 0f)
        assertTrue(face.boundingBox.width > 0f)
        assertTrue(face.boundingBox.height > 0f)

        // Test Face Clustering
        val face1 = DetectedFace(
            faceId = "f1",
            mediaItemId = "img_1",
            boundingBox = NormalizedRect(0.2f, 0.2f, 0.6f, 0.6f),
            confidence = 0.85f,
            faceEmbedding = face.faceEmbedding.copyOf()
        )
        // Similar face embedding for same person
        val face2 = DetectedFace(
            faceId = "f2",
            mediaItemId = "img_2",
            boundingBox = NormalizedRect(0.2f, 0.2f, 0.6f, 0.6f),
            confidence = 0.88f,
            faceEmbedding = face.faceEmbedding.copyOf()
        )
        // Different person embedding
        val face3 = DetectedFace(
            faceId = "f3",
            mediaItemId = "img_3",
            boundingBox = NormalizedRect(0.1f, 0.1f, 0.5f, 0.5f),
            confidence = 0.80f,
            faceEmbedding = FloatArray(FaceDetectionEngine.FACE_EMBEDDING_DIM) { if (it % 2 == 0) -0.5f else 0.5f }
        )

        val clusters = FaceDetectionEngine.clusterFaces(listOf(face1, face2, face3), similarityThreshold = 0.70f)
        assertTrue("Should cluster into distinct person groups", clusters.size >= 2)

        val mainCluster = clusters.firstOrNull { it.mediaItemIds.contains("img_1") }
        assertNotNull(mainCluster)
        assertTrue("Face 1 and Face 2 should be in the same person cluster", mainCluster!!.mediaItemIds.contains("img_2"))
    }

    @Test
    fun testOfflineOcrEngineParsing() {
        val width = 64
        val height = 64

        // Synthetic document with high contrast white background and dark text lines
        val docPixels = IntArray(width * height) { idx ->
            val y = idx / width
            val isTextRow = (y in 16..18) || (y in 26..28) || (y in 36..38)
            if (isTextRow) {
                // Dark text pixels
                (0xFF shl 24) or (30 shl 16) or (30 shl 8) or 30
            } else {
                // White page background
                (0xFF shl 24) or (245 shl 16) or (245 shl 8) or 245
            }
        }

        val ocrResult = OfflineOcrEngine.extractText(docPixels, width, height, "store_receipt.png")

        assertTrue("Should identify image as document or receipt", ocrResult.isDocumentOrReceipt)
        assertTrue("Extracted lines should not be empty", ocrResult.lines.isNotEmpty())
        assertTrue("Full text should contain receipt keywords or content", ocrResult.fullText.isNotBlank())
        assertTrue("Should detect currency amounts or receipt headers", ocrResult.hasAmounts || ocrResult.fullText.contains("TOTAL", ignoreCase = true))
    }

    @Test
    fun testMediaItemPhase2Properties() {
        val dummyEmbedding = ByteArray(256) { it.toByte() }
        val item = MediaItem(
            id = "test_item_1",
            path = "/storage/test.jpg",
            displayName = "test.jpg",
            quantizedEmbedding = dummyEmbedding,
            detectedFaceCount = 2,
            faceIds = listOf("face_a", "face_b"),
            ocrText = "INVOICE #9910 TOTAL $120.00"
        )

        assertTrue(item.hasFaces)
        assertEquals(2, item.detectedFaceCount)
        assertTrue(item.hasOcrText)
        assertEquals(256, item.quantizedEmbedding?.size)
        assertFalse(item.isVideo)
        assertTrue(item.isImage)
    }
}

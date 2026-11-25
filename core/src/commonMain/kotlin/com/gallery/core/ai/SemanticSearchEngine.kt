package com.gallery.core.ai

import com.gallery.core.model.AiCategory
import com.gallery.core.model.MediaItem
import kotlin.math.sqrt

data class SemanticSearchResult(
    val item: MediaItem,
    val score: Float,
    val matchedConcepts: List<String>
)

/**
 * Offline search over the library.
 *
 * Ranking combines two honest signals:
 *  1. exact-ish textual matches (file name, album, user/AI tags, category name)
 *  2. cosine similarity of the 256-d hand-crafted [OfflineVisionEngine] descriptor,
 *     used only as a re-ranking bonus.
 *
 * A photo is never returned purely because its descriptor happens to point in the
 * same direction as the query: an explicit textual hit (or an extremely close
 * near-duplicate) is required. This keeps "dog" from matching a warm-coloured
 * sunset just because both are orange.
 */
object SemanticSearchEngine {

    private val STOP_WORDS = setOf(
        "a", "an", "the", "in", "on", "at", "with", "and", "or", "of", "to", "from",
        "by", "for", "is", "are", "was", "were", "my", "some", "playing", "looking", "showing",
        "photo", "photos", "picture", "pictures", "image", "images"
    )

    /** Query token -> equivalent tags the classifier may have attached. */
    private val CONCEPT_MAP = mapOf(
        "nature" to listOf("nature", "landscape", "outdoors"),
        "forest" to listOf("nature", "greenery", "plants", "trees"),
        "tree" to listOf("nature", "greenery", "plants"),
        "trees" to listOf("nature", "greenery", "plants"),
        "mountain" to listOf("nature", "landscape", "sky"),
        "mountains" to listOf("nature", "landscape", "sky"),
        "hike" to listOf("nature", "outdoors", "landscape"),
        "beach" to listOf("water", "sky", "nature"),
        "ocean" to listOf("water", "sky", "nature"),
        "sea" to listOf("water", "sky", "nature"),
        "lake" to listOf("water", "nature"),
        "river" to listOf("water", "nature"),
        "sky" to listOf("sky", "blue sky"),
        "sunset" to listOf("sunset", "warm tones", "nature"),
        "sunrise" to listOf("sunset", "warm tones", "nature"),
        "snow" to listOf("nature", "landscape"),
        "winter" to listOf("nature", "landscape"),
        "flower" to listOf("nature", "greenery"),
        "flowers" to listOf("nature", "greenery"),
        "garden" to listOf("nature", "greenery", "plants"),

        "person" to listOf("people", "person", "portrait"),
        "people" to listOf("people", "person", "portrait"),
        "man" to listOf("people", "person", "portrait"),
        "woman" to listOf("people", "person", "portrait"),
        "girl" to listOf("people", "person", "portrait"),
        "boy" to listOf("people", "person", "portrait"),
        "selfie" to listOf("selfie", "people", "portrait"),
        "portrait" to listOf("portrait", "people", "person"),
        "face" to listOf("people", "portrait"),
        "family" to listOf("people", "person"),
        "friends" to listOf("people", "person"),

        "food" to listOf("food", "meal", "dining"),
        "meal" to listOf("food", "meal", "dining"),
        "dinner" to listOf("food", "meal", "dining"),
        "lunch" to listOf("food", "meal", "dining"),
        "breakfast" to listOf("food", "meal"),
        "dessert" to listOf("food", "dining"),
        "cake" to listOf("food", "dining"),
        "coffee" to listOf("food", "dining"),
        "pizza" to listOf("food", "dining"),
        "restaurant" to listOf("food", "dining"),

        "dog" to listOf("pets", "animals"),
        "dogs" to listOf("pets", "animals"),
        "puppy" to listOf("pets", "animals"),
        "cat" to listOf("pets", "animals"),
        "cats" to listOf("pets", "animals"),
        "kitten" to listOf("pets", "animals"),
        "pet" to listOf("pets", "animals"),
        "pets" to listOf("pets", "animals"),
        "animal" to listOf("animals", "pets"),
        "animals" to listOf("animals", "pets"),

        "document" to listOf("document", "text", "paper", "scan"),
        "documents" to listOf("document", "text", "paper", "scan"),
        "receipt" to listOf("document", "text", "paper", "scan"),
        "receipts" to listOf("document", "text", "paper", "scan"),
        "bill" to listOf("document", "text", "paper"),
        "invoice" to listOf("document", "text", "paper"),
        "paper" to listOf("paper", "document"),
        "text" to listOf("text", "document"),
        "scan" to listOf("scan", "document"),
        "contract" to listOf("document", "text", "paper"),
        "note" to listOf("document", "text"),
        "notes" to listOf("document", "text"),

        "city" to listOf("city", "architecture", "structure"),
        "building" to listOf("architecture", "city", "structure"),
        "buildings" to listOf("architecture", "city", "structure"),
        "architecture" to listOf("architecture", "city", "structure"),
        "street" to listOf("city", "structure"),
        "house" to listOf("architecture", "structure"),
        "skyline" to listOf("city", "architecture"),

        "car" to listOf("city", "structure"),
        "cars" to listOf("city", "structure"),
        "bike" to listOf("city", "structure"),
        "motorcycle" to listOf("city", "structure"),
        "vehicle" to listOf("city", "structure"),
        "plane" to listOf("sky", "landscape"),
        "airplane" to listOf("sky", "landscape"),
        "boat" to listOf("water", "nature"),

        "screenshot" to listOf("screenshot", "ui", "screen"),
        "screenshots" to listOf("screenshot", "ui", "screen"),
        "screen" to listOf("screenshot", "screen")
    )

    fun tokenize(query: String): List<String> = query.lowercase()
        .split(Regex("[^a-zA-Z0-9]+"))
        .filter { it.isNotBlank() && it !in STOP_WORDS }

    /**
     * Builds a query descriptor in the same 256-d space as [OfflineVisionEngine].
     * Used for re-ranking only - see the class KDoc.
     */
    fun encodeQuery(query: String): FloatArray {
        val vector = FloatArray(OfflineVisionEngine.EMBEDDING_DIM)
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return vector

        fun boostGreenCells() {
            for (i in 8..15) vector[i * 4 + 1] += 1.2f
        }

        fun boostBlueSkyCells() {
            for (i in 0..7) {
                vector[i * 4 + 2] += 1.5f
                vector[i * 4 + 3] += 1.0f
            }
        }

        for (token in tokens) {
            when (token) {
                in setOf("nature", "forest", "tree", "trees", "green", "garden", "hike", "flower", "flowers", "plants") -> {
                    vector[68] += 2.5f
                    vector[65] += 1.8f
                    boostGreenCells()
                }

                in setOf("sky", "beach", "ocean", "sea", "blue", "water", "lake", "river", "boat") -> {
                    vector[69] += 2.0f
                    vector[66] += 2.0f
                    boostBlueSkyCells()
                }

                in setOf("sunset", "sunrise", "golden", "dusk", "dawn", "orange") -> {
                    vector[64] += 2.2f
                    vector[65] += 1.0f
                    vector[71] += 1.5f
                    vector[79] += 1.5f
                    for (i in 4..11) vector[i * 4] += 1.4f
                }

                in setOf("person", "people", "man", "woman", "girl", "boy", "portrait", "selfie", "face", "family", "friends") -> {
                    vector[70] += 3.0f
                    for (i in listOf(5, 6, 9, 10)) {
                        vector[i * 4] += 1.6f
                        vector[i * 4 + 1] += 1.0f
                    }
                }

                in setOf("food", "meal", "dinner", "lunch", "cake", "dessert", "pizza", "restaurant", "dining", "coffee") -> {
                    vector[71] += 3.0f
                    vector[64] += 1.4f
                    vector[79] += 1.4f
                    for (i in 5..10) vector[i * 4] += 1.2f
                }

                in setOf("dog", "dogs", "puppy", "cat", "cats", "kitten", "pet", "pets", "animal", "animals") -> {
                    // Fur texture: high local edge energy, mid saturation.
                    vector[71] += 0.8f
                    for (i in 0 until 16) vector[96 + i * 4] += 0.6f
                    vector[76] += 0.8f
                }

                in setOf("document", "documents", "receipt", "receipts", "invoice", "bill", "paper", "text", "scan", "contract", "note", "notes") -> {
                    vector[72] += 3.5f
                    vector[74] += 2.2f
                    vector[67] += 1.6f
                    for (i in 0 until 16) vector[i * 4 + 3] += 1.4f
                }

                in setOf("city", "building", "buildings", "architecture", "street", "urban", "skyline", "house") -> {
                    vector[75] += 2.2f
                    vector[76] += 1.4f
                    vector[77] += 2.0f
                    for (i in 0 until 16) vector[96 + i * 4 + 2] += 0.5f
                }

                in setOf("car", "cars", "automobile", "vehicle", "bike", "motorcycle") -> {
                    vector[77] += 1.6f
                    vector[73] += 0.8f
                    for (i in 8..15) vector[i * 4 + 3] -= 0.4f
                }

                in setOf("screenshot", "screenshots", "screen", "ui") -> {
                    vector[74] += 2.0f
                    vector[75] += 2.0f
                    vector[67] += 1.0f
                }

                "white", "snow" -> {
                    vector[67] += 3.0f
                    vector[72] += 1.5f
                }

                "dark", "night" -> {
                    vector[73] += 3.0f
                    vector[67] -= 2.0f
                }

                "red" -> {
                    vector[64] += 2.5f
                    vector[160 + 0] += 0.8f
                }

                "blue" -> {
                    vector[66] += 2.5f
                    vector[160 + 38] += 0.8f
                }

                "green" -> {
                    vector[65] += 2.5f
                    vector[160 + 20] += 0.8f
                }
            }
        }

        var normSq = 0f
        for (v in vector) normSq += v * v
        val norm = sqrt(normSq).coerceAtLeast(1e-6f)
        for (i in vector.indices) vector[i] /= norm
        return vector
    }

    /**
     * @param minScore minimum combined score (0..1) for a result.
     */
    fun search(
        query: String,
        items: List<MediaItem>,
        minScore: Float = 0.30f
    ): List<SemanticSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()

        val tokens = tokenize(cleanQuery)
        if (tokens.isEmpty()) return emptyList()

        val queryVector = encodeQuery(cleanQuery)
        val queryBytes = OfflineVisionEngine.quantizeToInt8(queryVector)
        val loweredQuery = cleanQuery.lowercase()
        val results = ArrayList<SemanticSearchResult>()

        for (item in items) {
            val matched = LinkedHashSet<String>()
            var score = 0f

            // --- 1. Explicit textual signals ---------------------------------
            if (item.displayName.lowercase().contains(loweredQuery)) {
                score += 0.55f
                matched += "filename"
            }
            if (item.albumName.lowercase().contains(loweredQuery)) {
                score += 0.40f
                matched += "album: ${item.albumName}"
            }

            val itemTags = item.tags.map { it.lowercase() }

            for (token in tokens) {
                if (itemTags.any { it == token }) {
                    score += 0.40f
                    matched += token
                } else if (itemTags.any { it.contains(token) || token.contains(it) }) {
                    score += 0.25f
                    matched += token
                }
                if (item.displayName.lowercase().contains(token)) {
                    score += 0.20f
                    matched += "filename: $token"
                }
                if (item.albumName.lowercase().contains(token)) {
                    score += 0.15f
                    matched += "album: $token"
                }
                if (item.aiCategory.name.lowercase().contains(token)) {
                    score += 0.25f
                    matched += item.aiCategory.displayName
                }
                for (related in CONCEPT_MAP[token].orEmpty()) {
                    if (itemTags.any { it == related }) {
                        score += 0.12f
                        if (matched.size < 4) matched += related
                    }
                }
            }

            // --- 2. Descriptor similarity (re-rank only) ---------------------
            val bytes = item.quantizedEmbedding
            if (bytes != null && bytes.size == queryBytes.size) {
                val similarity = OfflineVisionEngine.computeQuantizedSimilarity(queryBytes, bytes)
                if (similarity > 0f) score += similarity * 0.25f
            }

            if (matched.isEmpty() && score < 0.80f) continue
            if (score < minScore) continue

            results += SemanticSearchResult(
                item = item,
                score = score.coerceAtMost(1f),
                matchedConcepts = matched.take(4).toList()
            )
        }

        return results.sortedByDescending { it.score }
    }

    /** Human-readable reason a category was matched, for the search chips. */
    fun describeCategory(category: AiCategory): String = category.displayName
}

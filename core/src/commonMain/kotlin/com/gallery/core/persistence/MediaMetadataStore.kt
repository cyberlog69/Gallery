package com.gallery.core.persistence

import com.gallery.core.model.AiCategory
import com.gallery.core.model.ImageEditState
import com.gallery.core.model.MediaItem
import com.gallery.core.ai.FaceSignature

/**
 * Bumped whenever the on-device analysis pipeline changes in a way that makes
 * previously cached results stale. Stored per item so a re-scan only recomputes
 * what is actually outdated.
 */
object AnalyzerVersion {
    const val CURRENT = 3
}

/**
 * Everything about a media item that must survive process death.
 *
 * Kept deliberately separate from [MediaItem]: MediaItem is rebuilt from MediaStore
 * on every scan, while this is the user/analysis state that MediaStore cannot store.
 */
data class ItemMetadata(
    val id: String,
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val editState: ImageEditState = ImageEditState.DEFAULT,
    val perceptualHash: ULong = 0UL,
    val aiCategory: AiCategory = AiCategory.UNCATEGORIZED,
    val aiConfidence: Float = 0f,
    val quantizedEmbedding: ByteArray? = null,
    val textRegionCount: Int = 0,
    val faceSignatures: List<FaceSignature> = emptyList(),
    /** `MediaItem.dateModifiedSec` observed when the analysis ran. */
    val analyzedAtSec: Long = 0L,
    val analyzerVersion: Int = 0
) {
    val isAnalyzed: Boolean
        get() = analyzedAtSec > 0L && analyzerVersion == AnalyzerVersion.CURRENT &&
                aiCategory != AiCategory.UNCATEGORIZED

    val hasAnalysisCache: Boolean
        get() = analyzedAtSec > 0L && analyzerVersion == AnalyzerVersion.CURRENT &&
                quantizedEmbedding != null

    fun isAnalysisFreshFor(item: MediaItem): Boolean =
        hasAnalysisCache && analyzedAtSec == item.dateModifiedSec

    /** Folds the persisted state onto a freshly scanned MediaStore row. */
    fun applyTo(item: MediaItem): MediaItem = item.copy(
        isFavorite = isFavorite,
        tags = (item.tags + tags).distinct(),
        editState = editState,
        perceptualHash = perceptualHash,
        aiCategory = aiCategory,
        aiConfidence = aiConfidence,
        quantizedEmbedding = quantizedEmbedding,
        textRegionCount = textRegionCount,
        faceIds = faceSignatures.mapIndexed { index, _ -> "${item.id}_face_${index + 1}" },
        detectedFaceCount = faceSignatures.size
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ItemMetadata) return false
        return id == other.id &&
                isFavorite == other.isFavorite &&
                tags == other.tags &&
                editState == other.editState &&
                perceptualHash == other.perceptualHash &&
                aiCategory == other.aiCategory &&
                aiConfidence == other.aiConfidence &&
                textRegionCount == other.textRegionCount &&
                faceSignatures == other.faceSignatures &&
                analyzedAtSec == other.analyzedAtSec &&
                analyzerVersion == other.analyzerVersion &&
                (quantizedEmbedding?.contentEquals(other.quantizedEmbedding) ?: (other.quantizedEmbedding == null))
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + editState.hashCode()
        result = 31 * result + aiCategory.hashCode()
        result = 31 * result + aiConfidence.hashCode()
        result = 31 * result + (quantizedEmbedding?.contentHashCode() ?: 0)
        result = 31 * result + textRegionCount
        return result
    }
}

/**
 * Platform-backed string storage. Android supplies a file-backed implementation;
 * tests supply an in-memory one.
 */
interface MetadataPersistence {
    fun load(): String?
    fun save(encoded: String)
}

/**
 * Single source of truth for user state (favorites, edits, tags) plus the cached
 * output of the offline analysis pipeline.
 *
 * Threading: mutations are expected to happen on one thread (the main thread in
 * the app). [snapshot] returns an immutable copy so encoding can happen on IO.
 * No locking is performed by design - see `GalleryViewModel` for the enforcement.
 */
class MediaMetadataStore(
    private val persistence: MetadataPersistence? = null
) {
    private val items = LinkedHashMap<String, ItemMetadata>()
    private val personNames = LinkedHashMap<String, String>()
    private var dirty = false

    /**
     * Loads the persisted snapshot into memory.
     *
     * Kept explicit (not in `init`) so callers can run it off the main thread;
     * decoding a multi-megabyte metadata file during ViewModel construction would
     * jank the first frame.
     */
    fun loadFromPersistence(): Boolean {
        val encoded = persistence?.load()?.takeIf { it.isNotBlank() } ?: return false
        val decoded = MetadataCodec.decode(encoded)
        items.clear()
        items.putAll(decoded.items)
        personNames.clear()
        personNames.putAll(decoded.personNames)
        dirty = false
        return true
    }

    fun get(id: String): ItemMetadata? = items[id]

    fun put(metadata: ItemMetadata) {
        val existing = items[metadata.id]
        if (existing == metadata) return
        items[metadata.id] = metadata
        dirty = true
    }

    /** Applies a non-analysis change (favorite toggle, edit, rename) preserving cached analysis. */
    fun update(id: String, transform: (ItemMetadata) -> ItemMetadata) {
        val current = items[id] ?: ItemMetadata(id = id)
        put(transform(current))
    }

    fun getOrCreate(id: String): ItemMetadata = items[id] ?: ItemMetadata(id = id)

    fun isFavorite(id: String): Boolean = items[id]?.isFavorite == true

    fun setFavorite(id: String, favorite: Boolean) = update(id) { it.copy(isFavorite = favorite) }

    fun toggleFavorite(id: String): Boolean {
        val next = !isFavorite(id)
        setFavorite(id, next)
        return next
    }

    fun getEditState(id: String): ImageEditState = items[id]?.editState ?: ImageEditState.DEFAULT

    fun setEditState(id: String, state: ImageEditState) = update(id) { it.copy(editState = state) }

    fun clearEditState(id: String) = update(id) { it.copy(editState = ImageEditState.DEFAULT) }

    fun getTags(id: String): List<String> = items[id]?.tags ?: emptyList()

    fun addTag(id: String, tag: String) = update(id) { it.copy(tags = (it.tags + tag).distinct()) }

    fun removeTag(id: String, tag: String) = update(id) { it.copy(tags = it.tags - tag) }

    fun getPerceptualHash(id: String): ULong = items[id]?.perceptualHash ?: 0UL

    fun setPerceptualHash(id: String, hash: ULong) = update(id) { it.copy(perceptualHash = hash) }

    fun getAllFavorites(): Set<String> = items.values.filter { it.isFavorite }.map { it.id }.toSet()

    fun getAllEditedIds(): Set<String> =
        items.values.filter { it.editState.isEdited }.map { it.id }.toSet()

    /** Drops state for items that no longer exist in the library. */
    fun retainOnly(ids: Set<String>) {
        val stale = items.keys.filter { it !in ids }
        if (stale.isEmpty()) return
        stale.forEach { items.remove(it) }
        dirty = true
    }

    fun remove(ids: Collection<String>) {
        var changed = false
        ids.forEach { if (items.remove(it) != null) changed = true }
        if (changed) dirty = true
    }

    fun getPersonName(personId: String): String? = personNames[personId]

    fun setPersonName(personId: String, name: String) {
        if (personNames[personId] == name) return
        personNames[personId] = name
        dirty = true
    }

    fun personNames(): Map<String, String> = personNames.toMap()

    fun all(): Map<String, ItemMetadata> = items.toMap()

    fun snapshot(): Snapshot = Snapshot(items.toMap(), personNames.toMap())

    val isDirty: Boolean get() = dirty

    /** Marks the in-memory state as already persisted (after an async write). */
    fun markClean() {
        dirty = false
    }

    /** Encodes synchronously. Callers should invoke this off the main thread. */
    fun encode(): String = MetadataCodec.encode(snapshot())

    /**
     * Persists the current snapshot, if anything changed.
     * Returns false when there was nothing to write.
     */
    fun flush(encodeAndSave: (String) -> Unit): Boolean {
        if (!dirty) return false
        val encoded = encode()
        encodeAndSave(encoded)
        dirty = false
        return true
    }

    fun reloadFromPersistence() {
        loadFromPersistence()
    }

    data class Snapshot(
        val items: Map<String, ItemMetadata>,
        val personNames: Map<String, String>
    )
}

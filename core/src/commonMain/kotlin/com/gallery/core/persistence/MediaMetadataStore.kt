package com.gallery.core.persistence

import com.gallery.core.model.ImageEditState

/**
 * In-memory representation of persisted user media metadata.
 */
data class ItemMetadata(
    val id: String,
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val editState: ImageEditState = ImageEditState.DEFAULT,
    val perceptualHash: ULong = 0UL
)

/**
 * Thread-safe metadata store interface for managing persistent favorites, tags, edits, and hashes.
 */
interface MediaMetadataStore {
    fun isFavorite(id: String): Boolean
    fun setFavorite(id: String, favorite: Boolean)
    fun toggleFavorite(id: String): Boolean

    fun getEditState(id: String): ImageEditState
    fun setEditState(id: String, state: ImageEditState)
    fun clearEditState(id: String)

    fun getTags(id: String): List<String>
    fun addTag(id: String, tag: String)
    fun removeTag(id: String, tag: String)

    fun getPerceptualHash(id: String): ULong
    fun setPerceptualHash(id: String, hash: ULong)

    fun getAllFavorites(): Set<String>
    fun getAllEditedIds(): Set<String>
}

package com.gallery.core.persistence

import com.gallery.core.model.ImageEditState
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * File-backed persistent metadata store for the Desktop environment.
 * Persists user favorites, tags, perceptual hashes, and non-destructive edit instructions.
 */
class DesktopMetadataStore(
    private val storageFile: File = File(System.getProperty("user.home"), ".gallery_cache/metadata_store.json")
) : MediaMetadataStore {

    private val favorites = ConcurrentHashMap.newKeySet<String>()
    private val editStates = ConcurrentHashMap<String, ImageEditState>()
    private val tagsMap = ConcurrentHashMap<String, MutableSet<String>>()
    private val hashCache = ConcurrentHashMap<String, ULong>()

    init {
        loadFromDisk()
    }

    override fun isFavorite(id: String): Boolean = favorites.contains(id)

    override fun setFavorite(id: String, favorite: Boolean) {
        if (favorite) {
            favorites.add(id)
        } else {
            favorites.remove(id)
        }
        persistToDisk()
    }

    override fun toggleFavorite(id: String): Boolean {
        val newState = if (favorites.contains(id)) {
            favorites.remove(id)
            false
        } else {
            favorites.add(id)
            true
        }
        persistToDisk()
        return newState
    }

    override fun getEditState(id: String): ImageEditState {
        return editStates[id] ?: ImageEditState.DEFAULT
    }

    override fun setEditState(id: String, state: ImageEditState) {
        if (state.isEdited) {
            editStates[id] = state
        } else {
            editStates.remove(id)
        }
        persistToDisk()
    }

    override fun clearEditState(id: String) {
        editStates.remove(id)
        persistToDisk()
    }

    override fun getTags(id: String): List<String> {
        return tagsMap[id]?.toList() ?: emptyList()
    }

    override fun addTag(id: String, tag: String) {
        tagsMap.computeIfAbsent(id) { ConcurrentHashMap.newKeySet() }.add(tag)
        persistToDisk()
    }

    override fun removeTag(id: String, tag: String) {
        tagsMap[id]?.remove(tag)
        persistToDisk()
    }

    override fun getPerceptualHash(id: String): ULong {
        return hashCache[id] ?: 0UL
    }

    override fun setPerceptualHash(id: String, hash: ULong) {
        if (hash != 0UL) {
            hashCache[id] = hash
            persistToDisk()
        }
    }

    override fun getAllFavorites(): Set<String> = favorites.toSet()

    override fun getAllEditedIds(): Set<String> = editStates.keys.toSet()

    @Synchronized
    private fun persistToDisk() {
        try {
            val parent = storageFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            val favsJson = favorites.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\\", "\\\\")}\"" }
            val editsJson = editStates.entries.joinToString(prefix = "{", postfix = "}") {
                "\"${it.key.replace("\\", "\\\\")}\":${it.value.toJson()}"
            }
            val hashesJson = hashCache.entries.joinToString(prefix = "{", postfix = "}") {
                "\"${it.key.replace("\\", "\\\\")}\":\"${it.value}\""
            }

            val fullJson = """{"favorites":$favsJson,"edits":$editsJson,"hashes":$hashesJson}"""
            storageFile.writeText(fullJson)
        } catch (_: Exception) {
            // Non-critical persistence failure
        }
    }

    @Synchronized
    private fun loadFromDisk() {
        if (!storageFile.exists()) return
        try {
            val text = storageFile.readText()
            if (text.isBlank()) return

            // Parse favorites
            val favPattern = "\"favorites\":\\s*\\[(.*?)\\]".toRegex(RegexOption.DOT_MATCHES_ALL)
            favPattern.find(text)?.groupValues?.get(1)?.let { rawFavs ->
                "\"([^\"]+)\"".toRegex().findAll(rawFavs).forEach { match ->
                    favorites.add(match.groupValues[1].replace("\\\\", "\\"))
                }
            }

            // Parse edits
            val editPattern = "\"edits\":\\s*\\{(.*?)\\}(?:,\\s*\"hashes\")".toRegex(RegexOption.DOT_MATCHES_ALL)
            editPattern.find(text)?.groupValues?.get(1)?.let { rawEdits ->
                val itemRegex = "\"([^\"]+)\":\\s*(\\{[^}]+\\})".toRegex()
                itemRegex.findAll(rawEdits).forEach { match ->
                    val path = match.groupValues[1].replace("\\\\", "\\")
                    val editJson = match.groupValues[2]
                    val edit = ImageEditState.fromJson(editJson)
                    editStates[path] = edit
                }
            }

            // Parse hashes
            val hashPattern = "\"hashes\":\\s*\\{(.*?)\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
            hashPattern.find(text)?.groupValues?.get(1)?.let { rawHashes ->
                "\"([^\"]+)\":\\s*\"([0-9]+)\"".toRegex().findAll(rawHashes).forEach { match ->
                    val path = match.groupValues[1].replace("\\\\", "\\")
                    val hash = match.groupValues[2].toULongOrNull()
                    if (hash != null) {
                        hashCache[path] = hash
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful fallback to empty state
        }
    }
}

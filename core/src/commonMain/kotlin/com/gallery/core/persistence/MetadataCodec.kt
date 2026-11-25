package com.gallery.core.persistence

import com.gallery.core.ai.FaceSignature
import com.gallery.core.model.AiCategory
import com.gallery.core.model.ImageEditState
import com.gallery.core.model.NormalizedRect

/**
 * Dependency-free, line-oriented codec for [MediaMetadataStore].
 *
 * The format is intentionally boring: one record per line, `|` separated, with
 * every payload field percent-escaped. It is cheap to write atomically, trivial
 * to debug, and needs no JSON/serialization dependency in commonMain.
 *
 * ```
 * gallery-metadata v1
 * M|id|favorite|tags|editJson|hash|category|confidence|embeddingHex|textRegions|faceIds|analyzedAt|version
 * P|personId|name
 * ```
 */
object MetadataCodec {

    private const val HEADER = "gallery-metadata v1"

    fun encode(snapshot: MediaMetadataStore.Snapshot): String {
        val out = StringBuilder(1024 + snapshot.items.size * 160)
        out.append(HEADER).append('\n')

        for (item in snapshot.items.values) {
            out.append("M|")
                .append(escape(item.id)).append('|')
                .append(if (item.isFavorite) '1' else '0').append('|')
                .append(item.tags.joinToString(",") { escape(it) }).append('|')
                .append(escape(item.editState.toJson())).append('|')
                .append(item.perceptualHash.toString(16)).append('|')
                .append(item.aiCategory.name).append('|')
                .append(item.aiConfidence.toString()).append('|')
                .append(item.quantizedEmbedding?.let { toHex(it) } ?: "").append('|')
                .append(item.textRegionCount).append('|')
                .append(encodeFaces(item.faceSignatures)).append('|')
                .append(item.analyzedAtSec).append('|')
                .append(item.analyzerVersion)
                .append('\n')
        }

        for ((personId, name) in snapshot.personNames) {
            out.append("P|").append(escape(personId)).append('|').append(escape(name)).append('\n')
        }

        return out.toString()
    }

    fun decode(encoded: String): MediaMetadataStore.Snapshot {
        val items = LinkedHashMap<String, ItemMetadata>()
        val people = LinkedHashMap<String, String>()

        val firstLine = encoded.lineSequence().firstOrNull()?.trim().orEmpty()
        if (!firstLine.startsWith("gallery-metadata")) {
            // Unknown/legacy payload: start clean rather than import garbage.
            return MediaMetadataStore.Snapshot(items, people)
        }

        for (rawLine in encoded.lineSequence()) {
            if (rawLine.isBlank()) continue
            val fields = rawLine.split('|')
            try {
                when (fields[0]) {
                    "M" -> {
                        if (fields.size < 13) continue
                        val metadata = ItemMetadata(
                            id = unescape(fields[1]),
                            isFavorite = fields[2] == "1",
                            tags = decodeList(fields[3]),
                            editState = ImageEditState.fromJson(unescape(fields[4])),
                            perceptualHash = fields[5].toULongOrNull(16) ?: 0UL,
                            aiCategory = AiCategory.fromString(fields[6]),
                            aiConfidence = fields[7].toFloatOrNull() ?: 0f,
                            quantizedEmbedding = fields[8].takeIf { it.isNotEmpty() }?.let { fromHex(it) },
                            textRegionCount = fields[9].toIntOrNull() ?: 0,
                            faceSignatures = decodeFaces(fields[10], unescape(fields[1])),
                            analyzedAtSec = fields[11].toLongOrNull() ?: 0L,
                            analyzerVersion = fields[12].toIntOrNull() ?: 0
                        )
                        items[metadata.id] = metadata
                    }

                    "P" -> {
                        if (fields.size >= 3) {
                            people[unescape(fields[1])] = unescape(fields[2])
                        }
                    }
                }
            } catch (_: Exception) {
                // A single corrupt record must not discard the whole library.
            }
        }

        return MediaMetadataStore.Snapshot(items, people)
    }

    private fun decodeList(field: String): List<String> {
        if (field.isEmpty()) return emptyList()
        return field.split(',').filter { it.isNotBlank() }.map { unescape(it) }
    }

    /**
     * Face payload:
     * `hex64:left:top:right:bottom:confidence` records joined by `;`,
     * with box coordinates as integer percentages.
     */
    private fun encodeFaces(faces: List<FaceSignature>): String =
        faces.joinToString(";") { face ->
            val box = face.box
            listOf(
                toHex(face.quantizedEmbedding),
                (box.left * 100).toInt().toString(),
                (box.top * 100).toInt().toString(),
                (box.right * 100).toInt().toString(),
                (box.bottom * 100).toInt().toString(),
                (face.confidence * 100).toInt().toString()
            ).joinToString(":")
        }

    private fun decodeFaces(field: String, mediaItemId: String): List<FaceSignature> {
        if (field.isBlank()) return emptyList()
        return field.split(';').mapNotNull { record ->
            try {
                val parts = record.split(':')
                if (parts.size < 6) return@mapNotNull null
                val bytes = fromHex(parts[0]) ?: return@mapNotNull null
                FaceSignature(
                    mediaItemId = mediaItemId,
                    quantizedEmbedding = bytes,
                    box = NormalizedRect(
                        left = (parts[1].toInt() / 100f).coerceIn(0f, 1f),
                        top = (parts[2].toInt() / 100f).coerceIn(0f, 1f),
                        right = (parts[3].toInt() / 100f).coerceIn(0f, 1f),
                        bottom = (parts[4].toInt() / 100f).coerceIn(0f, 1f)
                    ),
                    confidence = (parts[5].toInt() / 100f).coerceIn(0f, 1f)
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun escape(value: String): String {
        if (value.isEmpty()) return ""
        val sb = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '%' -> sb.append("%25")
                '|' -> sb.append("%7C")
                ',' -> sb.append("%2C")
                '\n' -> sb.append("%0A")
                '\r' -> sb.append("%0D")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun unescape(value: String): String {
        if (value.indexOf('%') < 0) return value
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            if (value[i] == '%' && i + 2 < value.length) {
                val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    sb.append(hex.toChar())
                    i += 3
                    continue
                }
            }
            sb.append(value[i])
            i++
        }
        return sb.toString()
    }

    private const val HEX_DIGITS = "0123456789abcdef"

    private fun toHex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(HEX_DIGITS[v ushr 4]).append(HEX_DIGITS[v and 0x0F])
        }
        return out.toString()
    }

    private fun fromHex(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = hex[i * 2].digitToIntOrNull(16) ?: return null
            val lo = hex[i * 2 + 1].digitToIntOrNull(16) ?: return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}

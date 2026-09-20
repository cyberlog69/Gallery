package com.gallery.core.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight, zero-dependency update checker.
 * Uses standard HttpURLConnection to query GitHub Releases API without external network libraries.
 */
object UpdateChecker {

    private const val GITHUB_API_URL = "https://api.github.com/repos"
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 8000

    /**
     * Checks if a newer version is available on GitHub Releases.
     * Returns Result.success with UpdateInfo if an update is available.
     * Returns Result.success(null) if already on the latest version or no release exists.
     * Returns Result.failure if network is offline or request fails.
     */
    suspend fun checkForUpdate(
        currentVersion: String,
        repoOwner: String = "cyberlog69",
        repoName: String = "Gallery"
    ): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URI.create("$GITHUB_API_URL/$repoOwner/$repoName/releases/latest").toURL()
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Gallery-App/$currentVersion")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                // No releases published yet
                return@withContext Result.success(null)
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    Exception("HTTP $responseCode: ${connection.responseMessage}")
                )
            }

            val json = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val info = parseReleaseJson(json)

            if (isNewerVersion(info.versionName, currentVersion)) {
                Result.success(info)
            } else {
                Result.success(null)
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Compares two semantic version strings.
     * Returns true if remoteVersion is strictly greater than currentVersion.
     * Example: "1.1.0" > "1.0.0", "v1.0.1" > "1.0.0", "1.0.0" == "1.0.0" -> false
     */
    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val r = cleanVersion(remoteVersion).split('.', '-', '_')
        val c = cleanVersion(currentVersion).split('.', '-', '_')
        val maxLen = maxOf(r.size, c.size)

        for (i in 0 until maxLen) {
            val rPart = r.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            val cPart = c.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            if (rPart > cPart) return true
            if (rPart < cPart) return false
        }
        return false
    }

    fun cleanVersion(version: String): String {
        return version.trim().removePrefix("v").removePrefix("V")
    }

    /**
     * Fast, zero-dependency parser for GitHub Release JSON payload.
     */
    fun parseReleaseJson(json: String): UpdateInfo {
        val tagName = extractJsonString(json, "tag_name") ?: ""
        val versionName = cleanVersion(tagName)
        val title = extractJsonString(json, "name") ?: tagName
        val body = extractJsonString(json, "body") ?: ""
        val releaseUrl = extractJsonString(json, "html_url") ?: ""
        val publishedAt = extractJsonString(json, "published_at") ?: ""

        val assets = extractAssets(json)

        return UpdateInfo(
            tagName = tagName,
            versionName = versionName,
            title = title,
            body = body,
            releaseUrl = releaseUrl,
            publishedAt = publishedAt,
            assets = assets
        )
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"".toRegex()
        val match = pattern.find(json) ?: return null
        val raw = match.groupValues[1]
        return unescapeJson(raw)
    }

    private fun extractAssets(json: String): List<ReleaseAsset> {
        val assetsList = mutableListOf<ReleaseAsset>()
        val assetsIdx = json.indexOf("\"assets\"")
        if (assetsIdx == -1) return emptyList()

        val startBracket = json.indexOf('[', assetsIdx)
        if (startBracket == -1) return emptyList()

        var depth = 0
        var endBracket = -1
        for (i in startBracket until json.length) {
            if (json[i] == '[') depth++
            else if (json[i] == ']') {
                depth--
                if (depth == 0) {
                    endBracket = i
                    break
                }
            }
        }
        if (endBracket == -1) return emptyList()

        val assetsBlock = json.substring(startBracket, endBracket + 1)
        val objectRegex = "\\{([^}]+)\\}".toRegex()

        for (match in objectRegex.findAll(assetsBlock)) {
            val obj = match.groupValues[1]
            val name = extractJsonString(obj, "name")
            val url = extractJsonString(obj, "browser_download_url")
            val sizeMatch = "\"size\"\\s*:\\s*(\\d+)".toRegex().find(obj)
            val size = sizeMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val contentType = extractJsonString(obj, "content_type") ?: ""

            if (name != null && url != null) {
                assetsList.add(
                    ReleaseAsset(
                        name = name,
                        downloadUrl = url,
                        sizeBytes = size,
                        contentType = contentType
                    )
                )
            }
        }

        return assetsList
    }

    private fun unescapeJson(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                when (val next = input[i + 1]) {
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        if (i + 5 < input.length) {
                            val hex = input.substring(i + 2, i + 6)
                            val codePoint = hex.toIntOrNull(16)
                            if (codePoint != null) {
                                sb.append(codePoint.toChar())
                                i += 6
                            } else {
                                sb.append(c)
                                i++
                            }
                        } else {
                            sb.append(c)
                            i++
                        }
                    }
                    else -> {
                        sb.append(next)
                        i += 2
                    }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}

package com.gallery.core.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun testVersionComparison() {
        assertTrue(UpdateChecker.isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(UpdateChecker.isNewerVersion("v1.1.0", "1.0.0"))
        assertTrue(UpdateChecker.isNewerVersion("2.0.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.0.1", "1.0.0"))
        assertTrue(UpdateChecker.isNewerVersion("v2.1.3", "v2.1.2"))

        assertFalse(UpdateChecker.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("0.9.9", "1.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.0", "1.1.0"))
    }

    @Test
    fun testParseReleaseJson() {
        val sampleJson = """
        {
          "tag_name": "v1.1.0",
          "name": "Gallery v1.1.0 - In-App Updater & Improvements",
          "body": "### What's New\n- In-app updater\n- Enhanced performance\n- Bug fixes",
          "html_url": "https://github.com/cyberlog69/Gallery/releases/tag/v1.1.0",
          "published_at": "2026-09-20T18:00:00Z",
          "assets": [
            {
              "name": "androidApp-debug.apk",
              "browser_download_url": "https://github.com/cyberlog69/Gallery/releases/download/v1.1.0/androidApp-debug.apk",
              "size": 22020096,
              "content_type": "application/vnd.android.package-archive"
            },
            {
              "name": "PicasaGalleryViewer-windows-x64-1.1.0.jar",
              "browser_download_url": "https://github.com/cyberlog69/Gallery/releases/download/v1.1.0/PicasaGalleryViewer-windows-x64-1.1.0.jar",
              "size": 78643200,
              "content_type": "application/java-archive"
            }
          ]
        }
        """.trimIndent()

        val info = UpdateChecker.parseReleaseJson(sampleJson)

        assertEquals("v1.1.0", info.tagName)
        assertEquals("1.1.0", info.versionName)
        assertEquals("Gallery v1.1.0 - In-App Updater & Improvements", info.title)
        assertTrue(info.body.contains("In-app updater"))
        assertEquals("https://github.com/cyberlog69/Gallery/releases/tag/v1.1.0", info.releaseUrl)

        assertNotNull(info.apkAsset)
        assertEquals("androidApp-debug.apk", info.apkAsset?.name)
        assertEquals(22020096L, info.apkAsset?.sizeBytes)

        assertNotNull(info.desktopAsset)
        assertEquals("PicasaGalleryViewer-windows-x64-1.1.0.jar", info.desktopAsset?.name)
        assertEquals(78643200L, info.desktopAsset?.sizeBytes)
    }
}

package com.tdpham.navitvbrowser

import com.tdpham.navitvbrowser.util.SiteActivityTracker
import com.tdpham.navitvbrowser.util.WebViewCacheManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheManagementUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dayMs = 24L * 60L * 60L * 1000L

    @Test
    fun testNormalizeOrigin_standardAndEdgeUrls() {
        assertEquals("https://www.youtube.com", SiteActivityTracker.normalizeOrigin("https://WWW.YouTube.com/watch?v=abc"))
        assertEquals("https://example.com", SiteActivityTracker.normalizeOrigin("https://example.com:443/"))
        assertEquals("http://example.com", SiteActivityTracker.normalizeOrigin("http://example.com:80/news"))
        assertEquals("http://192.168.1.50:8080", SiteActivityTracker.normalizeOrigin("http://192.168.1.50:8080/dashboard"))
        assertEquals("https://search.example.org", SiteActivityTracker.normalizeOrigin("https://search.example.org/q?a=1|2 3"))

        assertNull(SiteActivityTracker.normalizeOrigin("file:///android_asset/dashboard.html"))
        assertNull(SiteActivityTracker.normalizeOrigin("about:blank"))
        assertNull(SiteActivityTracker.normalizeOrigin("javascript:alert(1)"))
        assertNull(SiteActivityTracker.normalizeOrigin("data:text/html,hi"))
        assertNull(SiteActivityTracker.normalizeOrigin("   "))
        assertNull(SiteActivityTracker.normalizeOrigin(null))
    }

    @Test
    fun testIsInactive_thirtyDayBoundary() {
        val now = 1_800_000_000_000L
        val threshold = SiteActivityTracker.INACTIVE_SITE_THRESHOLD_MS
        assertEquals(30L * dayMs, threshold)

        assertFalse(SiteActivityTracker.isInactive(null, now))
        assertFalse(SiteActivityTracker.isInactive(0L, now))
        assertFalse(SiteActivityTracker.isInactive(now - (29L * dayMs), now))
        assertFalse(SiteActivityTracker.isInactive(now - threshold, now))
        assertTrue(SiteActivityTracker.isInactive(now - threshold - 1L, now))
        assertTrue(SiteActivityTracker.isInactive(now - (45L * dayMs), now))
    }

    @Test
    fun testEvaluateOrigins_deletesInactiveSeedsUntrackedPreservesActive() {
        val now = 1_800_000_000_000L
        val storageOrigins = listOf(
            "https://inactive-tracked.com/",
            "https://inactive-history-only.com/",
            "https://active-recent.com/",
            "https://saved-by-history.com/",
            "https://brand-new-origin.com/"
        )

        val trackedOrigins = mapOf(
            "https://inactive-tracked.com" to (now - 31L * dayMs),
            "https://active-recent.com" to (now - 5L * dayMs),
            "https://saved-by-history.com" to (now - 45L * dayMs)
        )

        val historyOrigins = mapOf(
            "https://inactive-history-only.com" to (now - 60L * dayMs),
            "https://saved-by-history.com" to (now - 2L * dayMs)
        )

        val plan = SiteActivityTracker.evaluateOrigins(
            storageOrigins = storageOrigins,
            trackedOrigins = trackedOrigins,
            historyOrigins = historyOrigins,
            nowMs = now
        )

        assertEquals(
            listOf("https://inactive-tracked.com/", "https://inactive-history-only.com/"),
            plan.originsToDelete
        )
        assertEquals(
            listOf("https://brand-new-origin.com"),
            plan.originsToSeed
        )
        assertEquals(
            listOf("https://active-recent.com/", "https://saved-by-history.com/"),
            plan.activeOrigins
        )
    }

    @Test
    fun testFindStaleTrackedOrigins_respectsHistoryActivity() {
        val now = 1_800_000_000_000L
        val tracked = mapOf(
            "https://old.example.com" to (now - 40L * dayMs),
            "https://recent.example.com" to (now - 10L * dayMs),
            "https://revived.example.com" to (now - 50L * dayMs)
        )
        val history = mapOf(
            "https://revived.example.com" to (now - 1L * dayMs)
        )

        val stale = SiteActivityTracker.findStaleTrackedOrigins(tracked, history, now)
        assertEquals(listOf("https://old.example.com"), stale)
    }

    @Test
    fun testDeleteExpiredFilesRecursive_prunesOldWebViewCacheAndProtectsMetadata() {
        val now = System.currentTimeMillis()
        val cutoff = now - SiteActivityTracker.INACTIVE_SITE_THRESHOLD_MS

        val webViewHttpCache = tempFolder.newFolder("WebView", "Default", "HTTP Cache", "Cache_Data")
        val oldCacheEntry = File(webViewHttpCache, "f_000001").apply {
            writeBytes(ByteArray(4096) { 1 })
            setLastModified(now - 35L * dayMs)
        }
        val freshCacheEntry = File(webViewHttpCache, "f_000002").apply {
            writeBytes(ByteArray(2048) { 2 })
            setLastModified(now - 3L * dayMs)
        }
        val protectedIndex = File(webViewHttpCache, "index").apply {
            writeBytes(ByteArray(512) { 3 })
            setLastModified(now - 60L * dayMs)
        }
        val protectedDb = File(tempFolder.root, "browser_database.db").apply {
            writeBytes(ByteArray(1024) { 4 })
            setLastModified(now - 60L * dayMs)
        }

        val freed = WebViewCacheManager.deleteExpiredFilesRecursive(tempFolder.root, cutoff)

        assertEquals(4096L, freed)
        assertFalse("Expired cache file (>30d) should be deleted", oldCacheEntry.exists())
        assertTrue("Fresh cache file (<30d) should remain", freshCacheEntry.exists())
        assertTrue("Chromium cache index metadata must be protected", protectedIndex.exists())
        assertTrue("Database files must be protected", protectedDb.exists())
    }

    @Test
    fun testFormatByteSize() {
        assertEquals("0 B", WebViewCacheManager.formatByteSize(0L))
        assertEquals("512 B", WebViewCacheManager.formatByteSize(512L))
        assertEquals("1.5 KB", WebViewCacheManager.formatByteSize(1536L))
        assertEquals("2.0 MB", WebViewCacheManager.formatByteSize(2L * 1024L * 1024L))
    }
}

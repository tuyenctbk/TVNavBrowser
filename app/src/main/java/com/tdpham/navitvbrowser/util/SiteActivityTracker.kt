package com.tdpham.navitvbrowser.util

import android.content.Context
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks last-visited timestamps per web origin (scheme://host[:port]) so background
 * maintenance can identify and clear browser storage & cache for sites inactive > 30 days
 * without touching active sessions, bookmarks, or Room schema versions.
 */
object SiteActivityTracker {

    private const val PREFS_NAME = "site_activity_tracker"
    private const val WRITE_THROTTLE_MS = 5 * 60 * 1000L // Avoid redundant disk writes on same site

    const val INACTIVE_SITE_THRESHOLD_DAYS = 30L
    const val INACTIVE_SITE_THRESHOLD_MS = INACTIVE_SITE_THRESHOLD_DAYS * 24L * 60L * 60L * 1000L

    private val recentWriteCache = ConcurrentHashMap<String, Long>()

    data class InactiveOriginPlan(
        val originsToDelete: List<String>,
        val originsToSeed: List<String>,
        val activeOrigins: List<String>
    )

    /**
     * Normalizes an http/https URL or WebStorage origin key into a canonical origin string
     * (`scheme://host` or `scheme://host:port` for non-default ports).
     * Returns null for non-web schemes (`file://`, `about:`, `javascript:`, `data:`) or invalid URLs.
     */
    fun normalizeOrigin(url: String?): String? {
        val trimmed = url?.trim() ?: return null
        if (trimmed.isEmpty()) return null

        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null

        val parsed = runCatching {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase() ?: return@runCatching null
            val host = uri.host?.lowercase() ?: return@runCatching null
            if (scheme != "http" && scheme != "https") return@runCatching null
            val port = uri.port
            formatOrigin(scheme, host, port)
        }.getOrNull()

        if (parsed != null) return parsed

        // Fallback for real-world URLs with unencoded query characters (spaces, pipes, brackets)
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = trimmed.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null

        val afterScheme = trimmed.substring(schemeEnd + 3)
        val authority = afterScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
            .trim()
        if (authority.isEmpty() || authority.any { it.isWhitespace() }) return null

        val colonIndex = authority.lastIndexOf(':')
        val host: String
        val port: Int
        if (colonIndex > 0 && !authority.endsWith(']')) {
            val portCandidate = authority.substring(colonIndex + 1).toIntOrNull()
            if (portCandidate != null) {
                host = authority.substring(0, colonIndex).lowercase()
                port = portCandidate
            } else {
                host = authority.lowercase()
                port = -1
            }
        } else {
            host = authority.lowercase()
            port = -1
        }
        if (host.isEmpty()) return null
        return formatOrigin(scheme, host, port)
    }

    private fun formatOrigin(scheme: String, host: String, port: Int): String {
        val isDefaultPort = port <= 0 ||
            (scheme == "http" && port == 80) ||
            (scheme == "https" && port == 443)
        return if (isDefaultPort) "$scheme://$host" else "$scheme://$host:$port"
    }

    /** Extracts the lowercase hostname from a normalized origin or URL. */
    fun hostOfOrigin(originOrUrl: String): String {
        val normalized = normalizeOrigin(originOrUrl) ?: return UrlUtils.hostOf(originOrUrl).lowercase()
        val afterScheme = normalized.substringAfter("://")
        return afterScheme.substringBefore(':').lowercase()
    }

    /**
     * Returns true strictly when [lastActiveMs] is known and older than [thresholdMs]
     * relative to [nowMs].
     */
    fun isInactive(
        lastActiveMs: Long?,
        nowMs: Long,
        thresholdMs: Long = INACTIVE_SITE_THRESHOLD_MS
    ): Boolean {
        if (lastActiveMs == null || lastActiveMs <= 0L) return false
        return (nowMs - lastActiveMs) > thresholdMs
    }

    /**
     * Evaluates a set of raw WebStorage origin keys against both [trackedOrigins] and
     * Room browsing [historyOrigins] (both keyed by canonical normalized origin).
     *
     * - Origins whose most recent activity is strictly older than [thresholdMs] are queued for deletion.
     * - Untracked origins with no history timestamp are queued for seeding so active sessions on
     *   fresh upgrades are not wiped prematurely before their 30-day inactivity window elapses.
     * - Recently visited origins are preserved untouched.
     */
    fun evaluateOrigins(
        storageOrigins: Collection<String>,
        trackedOrigins: Map<String, Long>,
        historyOrigins: Map<String, Long>,
        nowMs: Long,
        thresholdMs: Long = INACTIVE_SITE_THRESHOLD_MS
    ): InactiveOriginPlan {
        val toDelete = mutableListOf<String>()
        val toSeed = mutableListOf<String>()
        val active = mutableListOf<String>()

        for (rawOrigin in storageOrigins) {
            val canonical = normalizeOrigin(rawOrigin) ?: continue
            val trackedTs = trackedOrigins[canonical]
            val historyTs = historyOrigins[canonical]
            val lastActive = maxTimestamp(trackedTs, historyTs)

            when {
                lastActive == null -> toSeed.add(canonical)
                isInactive(lastActive, nowMs, thresholdMs) -> toDelete.add(rawOrigin)
                else -> active.add(rawOrigin)
            }
        }

        return InactiveOriginPlan(
            originsToDelete = toDelete,
            originsToSeed = toSeed.distinct(),
            activeOrigins = active
        )
    }

    /**
     * Identifies canonical origins in [trackedOrigins] that have been inactive longer than
     * [thresholdMs] (taking [historyOrigins] into account) so stale tracker metadata and
     * per-site preferences can also be reclaimed.
     */
    fun findStaleTrackedOrigins(
        trackedOrigins: Map<String, Long>,
        historyOrigins: Map<String, Long>,
        nowMs: Long,
        thresholdMs: Long = INACTIVE_SITE_THRESHOLD_MS
    ): List<String> {
        val stale = mutableListOf<String>()
        for ((origin, trackedTs) in trackedOrigins) {
            val lastActive = maxTimestamp(trackedTs, historyOrigins[origin])
            if (isInactive(lastActive, nowMs, thresholdMs)) {
                stale.add(origin)
            }
        }
        return stale
    }

    private fun maxTimestamp(a: Long?, b: Long?): Long? = when {
        a != null && b != null -> maxOf(a, b)
        a != null -> a
        else -> b
    }

    /**
     * Records a visit to [url]'s origin. Throttled in-memory per origin so rapid navigation
     * on the same website avoids repeated SharedPreferences writes.
     */
    fun recordVisit(context: Context, url: String?, timestampMs: Long = System.currentTimeMillis()) {
        val origin = normalizeOrigin(url) ?: return
        val lastWritten = recentWriteCache[origin]
        if (lastWritten != null && (timestampMs - lastWritten) in 0 until WRITE_THROTTLE_MS) {
            return
        }
        recentWriteCache[origin] = timestampMs
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(origin, timestampMs)
            .apply()
    }

    fun getAllTrackedOrigins(context: Context): Map<String, Long> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val result = HashMap<String, Long>()
        for ((key, value) in prefs.all) {
            if (value is Long && value > 0L) {
                val canonical = normalizeOrigin(key) ?: continue
                val existing = result[canonical]
                if (existing == null || value > existing) {
                    result[canonical] = value
                }
            }
        }
        return result
    }

    fun seedUntrackedOrigins(
        context: Context,
        origins: Collection<String>,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        if (origins.isEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var changed = false
        for (raw in origins) {
            val canonical = normalizeOrigin(raw) ?: continue
            if (!prefs.contains(canonical)) {
                editor.putLong(canonical, timestampMs)
                recentWriteCache[canonical] = timestampMs
                changed = true
            }
        }
        if (changed) editor.apply()
    }

    fun removeOrigins(context: Context, origins: Collection<String>) {
        if (origins.isEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var changed = false
        for (raw in origins) {
            val canonical = normalizeOrigin(raw) ?: raw
            if (prefs.contains(canonical)) {
                editor.remove(canonical)
                changed = true
            }
            if (raw != canonical && prefs.contains(raw)) {
                editor.remove(raw)
                changed = true
            }
            recentWriteCache.remove(canonical)
        }
        if (changed) editor.apply()
    }

    fun clearAll(context: Context) {
        recentWriteCache.clear()
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}

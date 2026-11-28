package com.tdpham.navitvbrowser.util

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tdpham.navitvbrowser.data.db.BrowserDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Periodic, background WebView & Browser Storage Cache Management System.
 *
 * - Schedules a daily WorkManager background job ([CacheCleanupWorker]) that survives process death.
 * - Identifies websites inactive for over 30 days using [SiteActivityTracker] + Room history timestamps.
 * - Clears per-origin browser storage (`localStorage`, `IndexedDB`, Cache Storage) via
 *   [WebStorage.deleteOrigin] while preserving active sites, bookmarks, and login sessions.
 * - Prunes aged cache files (> 30 days) and enforces device-aware cache size budgets.
 * - Coordinates safe Chromium HTTP disk cache flushing (`WebView.clearCache(true)`) on lifecycle stop
 *   without spawning headless WebView instances in background workers.
 */
object WebViewCacheManager {

    private const val TAG = "WebViewCacheManager"
    const val UNIQUE_WORK_NAME = "navitv_periodic_cache_cleanup"

    private const val META_PREFS = "cache_maintenance_meta"
    private const val KEY_LAST_CLEANUP_TS = "last_cleanup_ts"
    private const val KEY_LAST_INACTIVE_CLEARED = "last_inactive_cleared"
    private const val KEY_LAST_BYTES_FREED = "last_bytes_freed"
    private const val KEY_PENDING_DISK_CACHE_FLUSH = "pending_webview_disk_cache_flush"

    // 30-day inactivity policy for site storage & expired disk cache
    const val INACTIVE_SITE_THRESHOLD_MS = SiteActivityTracker.INACTIVE_SITE_THRESHOLD_MS
    private const val LOW_RAM_CACHE_FILE_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L // 7 days on low-RAM TVs

    // Target max cache directory size budget
    private const val DEFAULT_CACHE_BUDGET_BYTES = 60L * 1024L * 1024L // 60 MB
    private const val LOW_RAM_CACHE_BUDGET_BYTES = 25L * 1024L * 1024L // 25 MB

    private const val WEB_STORAGE_QUERY_TIMEOUT_MS = 5_000L

    data class CleanupResult(
        val inactiveOriginsCleared: Int,
        val storageBytesFreed: Long,
        val cacheFilesBytesFreed: Long,
        val staleTrackedEntriesRemoved: Int,
        val scheduledWebViewDiskCacheFlush: Boolean,
        val timestampMs: Long
    ) {
        val totalBytesFreed: Long
            get() = storageBytesFreed + cacheFilesBytesFreed
    }

    data class LastCleanupSummary(
        val timestampMs: Long,
        val inactiveOriginsCleared: Int,
        val totalBytesFreed: Long
    )

    /**
     * Initializes periodic background cache management according to the user's preference.
     * Safe to invoke from [android.app.Application.onCreate] on an IO coroutine.
     */
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        if (AppPreferences.isAutoCacheCleanupEnabled(appContext)) {
            schedulePeriodicCleanup(appContext)
        } else {
            cancelPeriodicCleanup(appContext)
        }
    }

    /**
     * Schedules a daily background cleanup job via Jetpack WorkManager.
     * Uses battery-not-low constraint and 4-hour flex window to run unobtrusively.
     */
    fun schedulePeriodicCleanup(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<CacheCleanupWorker>(
                24L, TimeUnit.HOURS,
                4L, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag(UNIQUE_WORK_NAME)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
            Log.d(TAG, "Periodic 30-day inactive-site cache cleanup scheduled (every 24h).")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule WorkManager periodic cache cleanup", e)
        }
    }

    /** Cancels the scheduled periodic background cleanup job. */
    fun cancelPeriodicCleanup(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.d(TAG, "Periodic cache cleanup cancelled.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel periodic cache cleanup", e)
        }
    }

    /**
     * Executes full inactive-site storage cleanup (> 30 days) and aged WebView/app cache pruning.
     *
     * @param isAppInBackground true when invoked from [CacheCleanupWorker]; enables safe session
     *   cookie cleanup without disrupting active foreground browsing.
     * @param activeWebView optional live WebView instance; if provided and a disk cache flush is
     *   needed, clears Chromium's disk cache immediately instead of deferring to `onStop()`.
     */
    suspend fun cleanupInactiveSitesAndCache(
        context: Context,
        isAppInBackground: Boolean = true,
        activeWebView: WebView? = null,
        nowMs: Long = System.currentTimeMillis()
    ): CleanupResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        var inactiveOriginsCleared = 0
        var storageBytesFreed = 0L
        var staleTrackedRemoved = 0
        var cacheFilesFreed = 0L
        var shouldFlushDiskCache = false

        try {
            // 1. Gather per-origin activity timestamps from both SiteActivityTracker and Room History
            val trackedOrigins = SiteActivityTracker.getAllTrackedOrigins(appContext)
            val historyOrigins = loadHistoryOriginTimestamps(appContext)

            // 2. Inspect WebStorage origins on the Main thread (official WebView API requirement)
            val storageOriginsMap = fetchWebStorageOrigins()
            if (storageOriginsMap.isNotEmpty()) {
                val plan = SiteActivityTracker.evaluateOrigins(
                    storageOrigins = storageOriginsMap.keys,
                    trackedOrigins = trackedOrigins,
                    historyOrigins = historyOrigins,
                    nowMs = nowMs,
                    thresholdMs = INACTIVE_SITE_THRESHOLD_MS
                )

                // Seed newly seen untracked origins so their 30-day inactivity timer starts cleanly
                if (plan.originsToSeed.isNotEmpty()) {
                    SiteActivityTracker.seedUntrackedOrigins(appContext, plan.originsToSeed, nowMs)
                }

                // Delete WebStorage for sites inactive > 30 days
                if (plan.originsToDelete.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val webStorage = runCatching { WebStorage.getInstance() }.getOrNull()
                        if (webStorage != null) {
                            for (rawOrigin in plan.originsToDelete) {
                                try {
                                    val usage = storageOriginsMap[rawOrigin] ?: 0L
                                    webStorage.deleteOrigin(rawOrigin)
                                    inactiveOriginsCleared++
                                    storageBytesFreed += usage
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to delete WebStorage origin: $rawOrigin", e)
                                }
                            }
                        }
                    }
                    SiteActivityTracker.removeOrigins(appContext, plan.originsToDelete)
                    for (rawOrigin in plan.originsToDelete) {
                        val host = SiteActivityTracker.hostOfOrigin(rawOrigin)
                        if (host.isNotEmpty()) {
                            AppPreferences.clearSitePreferencesForHost(appContext, host)
                        }
                    }
                    shouldFlushDiskCache = true
                }
            }

            // 3. Reclaim stale tracker entries (> 30 days inactive) & stale per-site preferences
            val refreshedTracked = SiteActivityTracker.getAllTrackedOrigins(appContext)
            val staleTracked = SiteActivityTracker.findStaleTrackedOrigins(
                trackedOrigins = refreshedTracked,
                historyOrigins = historyOrigins,
                nowMs = nowMs,
                thresholdMs = INACTIVE_SITE_THRESHOLD_MS
            )
            if (staleTracked.isNotEmpty()) {
                SiteActivityTracker.removeOrigins(appContext, staleTracked)
                for (origin in staleTracked) {
                    val host = SiteActivityTracker.hostOfOrigin(origin)
                    if (host.isNotEmpty()) {
                        AppPreferences.clearSitePreferencesForHost(appContext, host)
                    }
                }
                staleTrackedRemoved = staleTracked.size
                shouldFlushDiskCache = true
            }

            // 4. Prune expired cache files (> 30 days on standard TVs, > 7 days on low-RAM TVs)
            //    and enforce device-aware disk cache size budget
            val (freedFiles, exceededBudget) = pruneExpiredCacheFiles(appContext, nowMs)
            cacheFilesFreed = freedFiles
            if (exceededBudget) {
                shouldFlushDiskCache = true
            }

            // 5. Flush Chromium HTTP disk cache safely
            var scheduledFlush = false
            if (shouldFlushDiskCache) {
                if (activeWebView != null) {
                    withContext(Dispatchers.Main) {
                        runCatching { activeWebView.clearCache(true) }
                    }
                    setPendingDiskCacheFlush(appContext, false)
                } else {
                    setPendingDiskCacheFlush(appContext, true)
                    scheduledFlush = true
                }
            }

            // 6. Clean expired session-only cookies when running in background (never touches persistent logins)
            if (isAppInBackground) {
                withContext(Dispatchers.Main) {
                    runCatching {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.removeSessionCookies(null)
                        cookieManager.flush()
                    }
                }
            }

            val result = CleanupResult(
                inactiveOriginsCleared = inactiveOriginsCleared,
                storageBytesFreed = storageBytesFreed,
                cacheFilesBytesFreed = cacheFilesFreed,
                staleTrackedEntriesRemoved = staleTrackedRemoved,
                scheduledWebViewDiskCacheFlush = scheduledFlush,
                timestampMs = nowMs
            )

            saveLastCleanupSummary(appContext, result)

            if (result.inactiveOriginsCleared > 0 || result.totalBytesFreed > 0L) {
                FirebaseInitializer.logEvent(
                    "background_cache_cleanup",
                    mapOf(
                        "inactive_sites" to result.inactiveOriginsCleared.toString(),
                        "bytes_freed" to result.totalBytesFreed.toString()
                    )
                )
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error during inactive-site & cache cleanup", e)
            CleanupResult(
                inactiveOriginsCleared = inactiveOriginsCleared,
                storageBytesFreed = storageBytesFreed,
                cacheFilesBytesFreed = cacheFilesFreed,
                staleTrackedEntriesRemoved = staleTrackedRemoved,
                scheduledWebViewDiskCacheFlush = false,
                timestampMs = nowMs
            )
        }
    }

    /**
     * Checks and consumes any pending Chromium disk cache flush flagged by the background worker.
     * Call from [android.app.Activity.onStop] to safely invoke `webView.clearCache(true)`
     * without interrupting active rendering or spawning a headless WebView in background.
     */
    fun consumePendingDiskCacheFlush(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getBoolean(KEY_PENDING_DISK_CACHE_FLUSH, false)
        if (pending) {
            prefs.edit().putBoolean(KEY_PENDING_DISK_CACHE_FLUSH, false).apply()
        }
        return pending
    }

    private fun setPendingDiskCacheFlush(context: Context, pending: Boolean) {
        context.applicationContext
            .getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING_DISK_CACHE_FLUSH, pending)
            .apply()
    }

    fun getLastCleanupSummary(context: Context): LastCleanupSummary? {
        val prefs = context.applicationContext.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
        val ts = prefs.getLong(KEY_LAST_CLEANUP_TS, 0L)
        if (ts <= 0L) return null
        return LastCleanupSummary(
            timestampMs = ts,
            inactiveOriginsCleared = prefs.getInt(KEY_LAST_INACTIVE_CLEARED, 0),
            totalBytesFreed = prefs.getLong(KEY_LAST_BYTES_FREED, 0L)
        )
    }

    private fun saveLastCleanupSummary(context: Context, result: CleanupResult) {
        context.applicationContext
            .getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CLEANUP_TS, result.timestampMs)
            .putInt(KEY_LAST_INACTIVE_CLEARED, result.inactiveOriginsCleared + result.staleTrackedEntriesRemoved)
            .putLong(KEY_LAST_BYTES_FREED, result.totalBytesFreed)
            .apply()
    }

    private suspend fun loadHistoryOriginTimestamps(context: Context): Map<String, Long> {
        return try {
            val visits = BrowserDatabase.getInstance(context).historyDao().getLatestVisitTimestamps()
            val byOrigin = HashMap<String, Long>()
            for (visit in visits) {
                val origin = SiteActivityTracker.normalizeOrigin(visit.url) ?: continue
                val existing = byOrigin[origin]
                if (existing == null || visit.timestamp > existing) {
                    byOrigin[origin] = visit.timestamp
                }
            }
            byOrigin
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query Room history timestamps for cache cleanup", e)
            emptyMap()
        }
    }

    private suspend fun fetchWebStorageOrigins(): Map<String, Long> {
        return withTimeoutOrNull(WEB_STORAGE_QUERY_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    try {
                        WebStorage.getInstance().getOrigins { rawMap ->
                            val origins = HashMap<String, Long>()
                            if (rawMap != null) {
                                for ((key, value) in rawMap) {
                                    val originKey = key as? String ?: continue
                                    val usage = (value as? WebStorage.Origin)?.usage ?: 0L
                                    origins[originKey] = usage
                                }
                            }
                            if (cont.isActive) cont.resume(origins)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "WebStorage.getOrigins unavailable in this context", e)
                        if (cont.isActive) cont.resume(emptyMap())
                    }
                }
            }
        } ?: emptyMap()
    }

    /**
     * Scans cache directories and deletes files older than the inactivity threshold,
     * then trims oldest cache entries if total size exceeds the device memory budget.
     *
     * @return Pair(bytesFreed, exceededBudget)
     */
    internal fun pruneExpiredCacheFiles(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): Pair<Long, Boolean> {
        var totalBytesFreed = 0L
        var exceededBudget = false

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = activityManager?.isLowRamDevice ?: false

        val maxAgeMs = if (isLowRam) LOW_RAM_CACHE_FILE_MAX_AGE_MS else INACTIVE_SITE_THRESHOLD_MS
        val budgetBytes = if (isLowRam) LOW_RAM_CACHE_BUDGET_BYTES else DEFAULT_CACHE_BUDGET_BYTES
        val cutoffTime = nowMs - maxAgeMs

        val cacheDirs = getWebViewCacheDirectories(context)

        // 1. Delete cache files older than cutoffTime
        for (dir in cacheDirs) {
            if (dir.exists() && dir.isDirectory) {
                totalBytesFreed += deleteExpiredFilesRecursive(dir, cutoffTime)
            }
        }

        // 2. If total cache size still exceeds device budget, prune oldest files until under budget
        val currentTotalSize = calculateTotalCacheSize(context)
        if (currentTotalSize > budgetBytes) {
            exceededBudget = true
            val excess = currentTotalSize - budgetBytes
            totalBytesFreed += pruneOldestFiles(cacheDirs, excess)
        }

        return totalBytesFreed to exceededBudget
    }

    /**
     * Public helper kept for direct testing & manual pruning of a specific directory tree.
     */
    internal fun deleteExpiredFilesRecursive(dir: File, cutoffTime: Long): Long {
        var freed = 0L
        val files = dir.listFiles() ?: return 0L

        for (file in files) {
            if (isProtectedFile(file.name)) continue

            if (file.isDirectory) {
                freed += deleteExpiredFilesRecursive(file, cutoffTime)
                if (file.listFiles()?.isEmpty() == true) {
                    file.delete()
                }
            } else {
                if (file.lastModified() < cutoffTime) {
                    val len = file.length()
                    if (file.delete()) {
                        freed += len
                    }
                }
            }
        }
        return freed
    }

    /**
     * Completely purges all temporary WebView cache and web storage (triggered manually from Settings).
     */
    suspend fun clearEntireCache(context: Context, activeWebView: WebView? = null): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val appContext = context.applicationContext
                withContext(Dispatchers.Main) {
                    runCatching { WebStorage.getInstance().deleteAllData() }
                    runCatching { activeWebView?.clearCache(true) }
                }

                SiteActivityTracker.clearAll(appContext)
                if (activeWebView == null) {
                    setPendingDiskCacheFlush(appContext, true)
                }

                val cacheDirs = getWebViewCacheDirectories(appContext)
                for (dir in cacheDirs) {
                    if (dir.exists() && dir.isDirectory) {
                        // Delete children only; never delete Android's root cacheDir / externalCacheDir itself
                        dir.listFiles()?.forEach { deleteRecursively(it) }
                    }
                }
                Log.d(TAG, "Full WebView cache & WebStorage purge completed")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing entire cache", e)
                false
            }
        }

    /** Calculates total bytes consumed by app & WebView cache directories. */
    fun calculateTotalCacheSize(context: Context): Long {
        var total = 0L
        val cacheDirs = getWebViewCacheDirectories(context)
        for (dir in cacheDirs) {
            if (dir.exists()) {
                total += getFolderSize(dir)
            }
        }
        return total
    }

    /** Formats a byte count into a clean human-readable string (e.g. "3.4 MB"). */
    fun formatByteSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    private fun getWebViewCacheDirectories(context: Context): List<File> {
        val list = mutableListOf<File>()
        // Standard temporary cache dirs (includes cacheDir/WebView HTTP Cache; never touches dataDir/app_webview)
        context.cacheDir?.let { list.add(it) }
        context.codeCacheDir?.let { list.add(it) }
        context.externalCacheDir?.let { list.add(it) }
        return list.distinctBy { it.absolutePath }
    }

    private fun pruneOldestFiles(dirs: List<File>, bytesToFree: Long): Long {
        var freed = 0L
        val allFiles = mutableListOf<File>()

        fun collectFiles(dir: File) {
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (isProtectedFile(f.name)) continue
                if (f.isDirectory) collectFiles(f) else allFiles.add(f)
            }
        }

        dirs.forEach { if (it.exists()) collectFiles(it) }
        allFiles.sortBy { it.lastModified() }

        for (file in allFiles) {
            if (freed >= bytesToFree) break
            val len = file.length()
            if (file.delete()) {
                freed += len
            }
        }

        return freed
    }

    /**
     * Protects critical database, cookie, preferences, and active Chromium cache index metadata files
     * while allowing stale HTTP/code cache payload files inside `cacheDir/WebView` to be pruned.
     */
    internal fun isProtectedFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower == "index" ||
            lower == "index-dir" ||
            lower == "the-real-index" ||
            lower.startsWith("cookies") ||
            lower.startsWith("browser_database") ||
            lower.startsWith("user_prefs") ||
            lower.startsWith("app_webview") ||
            lower.startsWith("shared_prefs") ||
            lower.startsWith("databases") ||
            lower.endsWith(".db") ||
            lower.endsWith("-wal") ||
            lower.endsWith("-shm") ||
            lower.endsWith("-journal")
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return size
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        if (!isProtectedFile(file.name)) {
            file.delete()
        }
    }
}

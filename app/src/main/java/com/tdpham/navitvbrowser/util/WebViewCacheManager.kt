package com.tdpham.navitvbrowser.util

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persistent WebView Cache Management System.
 * Periodically clears expired HTTP cache, code cache, and temporary WebStorage
 * to maintain peak browsing responsiveness and prevent storage bloat on Android TV devices.
 */
object WebViewCacheManager {

    private const val TAG = "WebViewCacheManager"

    // Expiration threshold: 3 days for standard TV devices, 24 hours for Low-RAM devices
    private const val DEFAULT_MAX_CACHE_AGE_MS = 3 * 24 * 60 * 60 * 1000L // 3 days
    private const val LOW_RAM_MAX_CACHE_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours

    // Target max cache directory size
    private const val DEFAULT_CACHE_BUDGET_BYTES = 60 * 1024 * 1024L // 60 MB
    private const val LOW_RAM_CACHE_BUDGET_BYTES = 25 * 1024 * 1024L // 25 MB

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var periodicJob: Job? = null

    /**
     * Initializes background periodic cache inspection and pruning.
     */
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        startPeriodicCleanup(appContext)
    }

    fun startPeriodicCleanup(context: Context) {
        if (periodicJob?.isActive == true) return

        periodicJob = scope.launch {
            // Delay initial cleanup so it never runs during app startup / browsing setup
            delay(15 * 60 * 1000L)
            while (isActive) {
                try {
                    pruneExpiredCache(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic cache cleanup", e)
                }
                // Run check every 4 hours
                delay(4 * 60 * 60 * 1000L)
            }
        }
    }

    /**
     * Scans and deletes expired WebView cache items asynchronously.
     */
    suspend fun pruneExpiredCache(context: Context): Long = withContext(Dispatchers.IO) {
        var totalBytesFreed = 0L
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val isLowRam = activityManager?.isLowRamDevice ?: false

            val maxAgeMs = if (isLowRam) LOW_RAM_MAX_CACHE_AGE_MS else DEFAULT_MAX_CACHE_AGE_MS
            val budgetBytes = if (isLowRam) LOW_RAM_CACHE_BUDGET_BYTES else DEFAULT_CACHE_BUDGET_BYTES
            val cutoffTime = System.currentTimeMillis() - maxAgeMs

            val cacheDirs = getWebViewCacheDirectories(context)

            // 1. Delete files older than cutoffTime
            for (dir in cacheDirs) {
                if (dir.exists() && dir.isDirectory) {
                    totalBytesFreed += deleteExpiredFilesRecursive(dir, cutoffTime)
                }
            }

            // 2. If total size still exceeds budget, prune oldest files until under budget
            val currentTotalSize = calculateTotalCacheSize(context)
            if (currentTotalSize > budgetBytes) {
                val excess = currentTotalSize - budgetBytes
                totalBytesFreed += pruneOldestFiles(cacheDirs, excess)
            }

            Log.d(TAG, "Cache maintenance completed: freed $totalBytesFreed bytes. Low-RAM mode: $isLowRam")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prune expired WebView cache", e)
        }
        totalBytesFreed
    }

    /**
     * Completely purges all temporary WebView cache and web storage.
     */
    suspend fun clearEntireCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            // Clear WebStorage quotas via official API
            withContext(Dispatchers.Main) {
                try {
                    WebStorage.getInstance().deleteAllData()
                } catch (_: Exception) {}
            }

            val cacheDirs = getWebViewCacheDirectories(context)
            for (dir in cacheDirs) {
                if (dir.exists()) {
                    deleteRecursively(dir)
                }
            }
            Log.d(TAG, "Full WebView cache purge completed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing entire cache", e)
            false
        }
    }

    /**
     * Calculates total bytes consumed by WebView caches.
     */
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

    private fun getWebViewCacheDirectories(context: Context): List<File> {
        val list = mutableListOf<File>()

        // Internal app cache (Standard app temporary caches only - NEVER delete app_webview directly)
        context.cacheDir?.let { cache ->
            list.add(cache)
        }

        // External cache if available
        context.externalCacheDir?.let { list.add(it) }

        return list.distinct()
    }

    private fun deleteExpiredFilesRecursive(dir: File, cutoffTime: Long): Long {
        var freed = 0L
        val files = dir.listFiles() ?: return 0L

        for (file in files) {
            // Protect critical cookie and local database files from expiration deletion
            if (isProtectedFile(file.name)) continue

            if (file.isDirectory) {
                freed += deleteExpiredFilesRecursive(file, cutoffTime)
                // Remove directory if empty
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

    private fun pruneOldestFiles(dirs: List<File>, bytesToFree: Long): Long {
        var freed = 0L
        val allFiles = mutableListOf<File>()

        fun collectFiles(dir: File) {
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (isProtectedFile(f.name)) continue
                if (f.isDirectory) collectFiles(f)
                else allFiles.add(f)
            }
        }

        dirs.forEach { if (it.exists()) collectFiles(it) }

        // Sort ascending by lastModified to delete the oldest first
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

    private fun isProtectedFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.startsWith("cookies") ||
                lower.startsWith("browser_database") ||
                lower.startsWith("user_prefs") ||
                lower.startsWith("app_webview") ||
                lower.startsWith("webview") ||
                lower.startsWith("shared_prefs") ||
                lower.startsWith("databases") ||
                lower.endsWith(".db") ||
                lower.endsWith("-wal") ||
                lower.endsWith("-shm")
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

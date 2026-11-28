package com.tdpham.navitvbrowser.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * OS-scheduled periodic background worker (via Jetpack WorkManager) that automatically
 * clears browser WebStorage and expired WebView cache for sites inactive for over 30 days.
 */
class CacheCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!AppPreferences.isAutoCacheCleanupEnabled(applicationContext)) {
            Log.d(TAG, "Automatic 30-day cache cleanup is disabled by user preference; skipping.")
            return Result.success()
        }

        return try {
            val result = WebViewCacheManager.cleanupInactiveSitesAndCache(
                context = applicationContext,
                isAppInBackground = true
            )
            Log.d(
                TAG,
                "Background cache cleanup completed: inactiveOrigins=${result.inactiveOriginsCleared}, " +
                    "storageFreed=${result.storageBytesFreed}B, cacheFilesFreed=${result.cacheFilesBytesFreed}B, " +
                    "staleTracked=${result.staleTrackedEntriesRemoved}"
            )
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background cache cleanup failed (attempt $runAttemptCount)", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "CacheCleanupWorker"
        private const val MAX_RETRIES = 3
    }
}

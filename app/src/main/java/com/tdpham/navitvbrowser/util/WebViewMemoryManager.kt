package com.tdpham.navitvbrowser.util

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import com.tdpham.navitvbrowser.R

class WebViewMemoryManager(
    private val context: Context,
    private val webView: WebView
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "WebViewMemoryManager"
        private const val MAX_HISTORY_STEPS = 25
    }

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val isLowRamDevice: Boolean = activityManager?.isLowRamDevice ?: false

    init {
        context.registerComponentCallbacks(this)
        configureWebViewForMemory()
    }

    private fun configureWebViewForMemory() {
        webView.settings.apply {
            // Adjust cache mode based on RAM availability
            cacheMode = if (isLowRamDevice) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            
            // Enable storage mechanisms safely
            domStorageEnabled = true
            databaseEnabled = true
            
            // Render priority and hardware layer optimization for TV devices
            if (isLowRamDevice) {
                @Suppress("DEPRECATION")
                setRenderPriority(WebSettings.RenderPriority.HIGH)
            }
        }
        
        Log.d(TAG, "WebView memory manager initialized. Low RAM device: $isLowRamDevice")
    }

    fun optimizeOnNavigation() {
        val historyList = webView.copyBackForwardList()
        if (historyList.size > MAX_HISTORY_STEPS) {
            // Trim memory footprint when navigation history grows too large
            Log.d(TAG, "Navigation history exceeds $MAX_HISTORY_STEPS steps (${historyList.size}). Clearing non-essential history.")
            webView.clearHistory()
        }
    }

    fun forceTrimMemory(isCritical: Boolean = false) {
        try {
            Log.w(TAG, "Executing force memory trim (critical: $isCritical)...")
            webView.clearCache(isCritical)
            webView.freeMemory()
            System.gc()
            if (isCritical) {
                Toast.makeText(context, R.string.memory_warning_low_ram, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during memory trim", e)
        }
    }

    override fun onTrimMemory(level: Int) {
        Log.w(TAG, "onTrimMemory received level: $level")
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                forceTrimMemory(isCritical = true)
            }
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                forceTrimMemory(isCritical = false)
            }
        }
    }

    override fun onLowMemory() {
        Log.w(TAG, "onLowMemory callback triggered")
        forceTrimMemory(isCritical = true)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    fun onPause() {
        webView.onPause()
        webView.pauseTimers()
    }

    fun onResume() {
        webView.onResume()
        webView.resumeTimers()
    }

    fun destroy() {
        try {
            context.unregisterComponentCallbacks(this)
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(true)
            webView.removeAllViews()
            webView.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying WebView safely", e)
        }
    }
}

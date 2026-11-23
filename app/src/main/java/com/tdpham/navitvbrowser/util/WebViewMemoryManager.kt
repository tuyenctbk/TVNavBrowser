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

    private val appContext = context.applicationContext
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val isLowRamDevice: Boolean = activityManager?.isLowRamDevice ?: false

    @Volatile
    private var isDestroyed = false

    init {
        appContext.registerComponentCallbacks(this)
        configureWebViewForMemory()
    }

    private fun configureWebViewForMemory() {
        webView.settings.apply {
            cacheMode = WebSettings.LOAD_DEFAULT
            domStorageEnabled = true
            databaseEnabled = true
        }
        
        Log.d(TAG, "WebView memory manager initialized. Low RAM device: $isLowRamDevice")
    }

    fun optimizeOnNavigation() {
        if (isDestroyed) return
        try {
            val historyList = webView.copyBackForwardList()
            if (historyList.size > MAX_HISTORY_STEPS) {
                Log.d(TAG, "Navigation history exceeds $MAX_HISTORY_STEPS steps (${historyList.size}). Clearing non-essential history.")
                webView.clearHistory()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing navigation history", e)
        }
    }

    fun forceTrimMemory(isCritical: Boolean = false) {
        if (isDestroyed) return
        val action = Runnable {
            if (isDestroyed) return@Runnable
            try {
                Log.w(TAG, "Executing safe memory trim (critical: $isCritical)...")
                // Only clear in-memory web cache without blocking disk I/O or triggering Stop-The-World GC
                webView.clearCache(false)
                if (isCritical) {
                    Toast.makeText(appContext, R.string.memory_warning_low_ram, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during memory trim", e)
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }

    override fun onTrimMemory(level: Int) {
        if (isDestroyed) return
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
        if (isDestroyed) return
        Log.w(TAG, "onLowMemory callback triggered")
        forceTrimMemory(isCritical = true)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    fun onPause() {
        if (!isDestroyed) webView.onPause()
    }

    fun onResume() {
        if (!isDestroyed) webView.onResume()
    }

    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        try {
            appContext.unregisterComponentCallbacks(this)
            webView.removeJavascriptInterface("AndroidApp")
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = android.webkit.WebViewClient()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying WebView safely", e)
        }
    }
}

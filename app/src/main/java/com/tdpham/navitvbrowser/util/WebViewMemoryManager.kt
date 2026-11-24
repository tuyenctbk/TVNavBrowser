package com.tdpham.navitvbrowser.util

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.tdpham.navitvbrowser.R

class WebViewMemoryManager(
    private val context: Context,
    private val webView: WebView
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "WebViewMemoryManager"
        private const val MAX_HISTORY_STEPS_LOW_RAM = 25
        private const val MAX_HISTORY_STEPS_DEFAULT = 50
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
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
            @Suppress("DEPRECATION")
            databaseEnabled = true
        }

        Log.d(TAG, "WebView memory manager initialized. Low RAM device: $isLowRamDevice")
    }

    fun optimizeOnNavigation() {
        if (isDestroyed) return
        try {
            val maxSteps = if (isLowRamDevice) MAX_HISTORY_STEPS_LOW_RAM else MAX_HISTORY_STEPS_DEFAULT
            val historyList = webView.copyBackForwardList()
            if (historyList.size > maxSteps) {
                Log.d(TAG, "Navigation history exceeds $maxSteps steps (${historyList.size}). Clearing non-essential history.")
                webView.clearHistory()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing navigation history", e)
        }
    }

    fun forceTrimMemory(isCritical: Boolean = false) {
        if (isDestroyed) return
        val trimAction = Runnable {
            if (isDestroyed) return@Runnable
            val activity = context as? Activity
            if (activity != null && (activity.isFinishing || activity.isDestroyed)) return@Runnable
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
        if (Looper.myLooper() == Looper.getMainLooper()) {
            trimAction.run()
        } else {
            mainHandler.post(trimAction)
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        if (isDestroyed) return
        Log.w(TAG, "onTrimMemory received level: $level")
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                forceTrimMemory(isCritical = true)
            }
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                forceTrimMemory(isCritical = false)
            }
        }
    }

    @Deprecated("Deprecated in Java")
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
        } catch (_: Exception) {}
        try {
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.removeJavascriptInterface("AndroidApp")
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying WebView safely", e)
        }
    }
}

package com.tdpham.navitvbrowser

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.webkit.WebViewCompat
import com.tdpham.navitvbrowser.util.AppPreferences
import com.tdpham.navitvbrowser.util.RatingHelper

class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if WebView is available before proceeding
        if (!isWebViewAvailable()) {
            Toast.makeText(this, getString(R.string.error_webview_missing), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val onboardingComplete = AppPreferences.isOnboardingComplete(this)

        val target = if (onboardingComplete) {
            MainActivity::class.java
        } else {
            OnboardingActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }

    private fun isWebViewAvailable(): Boolean {
        // First try the standard AndroidX way
        try {
            if (WebViewCompat.getCurrentWebViewPackage(this) != null) {
                return true
            }
        } catch (_: Exception) {
            // Fall through to fallback
        }

        // Fallback: try to instantiate CookieManager or access WebView settings.
        // This works on most devices even if the package isn't explicitly reported.
        return try {
            android.webkit.CookieManager.getInstance()
            true
        } catch (_: Exception) {
            false
        }
    }
}

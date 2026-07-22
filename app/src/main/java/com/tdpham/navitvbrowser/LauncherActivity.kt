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
        return try {
            WebViewCompat.getCurrentWebViewPackage(this) != null
        } catch (_: Exception) {
            // Fallback: try to instantiate CookieManager as a last resort
            try {
                android.webkit.CookieManager.getInstance()
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}

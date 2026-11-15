package com.tdpham.navitvbrowser

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.webkit.WebViewCompat
import com.tdpham.navitvbrowser.util.AppPreferences

class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if WebView is available before proceeding
        if (!isWebViewAvailable()) {
            Toast.makeText(this, getString(R.string.error_webview_missing), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Zero-friction Time-to-Value: land directly in the TV Browser & Portal Dashboard
        if (!AppPreferences.isOnboardingComplete(this)) {
            AppPreferences.setOnboardingComplete(this, true)
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun isWebViewAvailable(): Boolean {
        try {
            if (WebViewCompat.getCurrentWebViewPackage(this) != null) {
                return true
            }
        } catch (_: Exception) {
            // Fall through to fallback
        }

        return try {
            android.webkit.CookieManager.getInstance()
            true
        } catch (_: Exception) {
            false
        }
    }
}

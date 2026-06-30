package com.tdpham.navitvbrowser

import android.os.Bundle
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tdpham.navitvbrowser.data.db.BrowserDatabase
import com.tdpham.navitvbrowser.ui.FocusAnimationHelper
import com.tdpham.navitvbrowser.util.AppPreferences
import com.tdpham.navitvbrowser.util.FirebaseInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)

        FirebaseInitializer.logEvent("settings_screen_opened")

        val etHomepage: EditText = findViewById(R.id.etHomepage)
        val btnSaveHomepage: Button = findViewById(R.id.btnSaveHomepage)
        val btnClearHistory: Button = findViewById(R.id.btnClearHistory)
        val btnClearBookmarks: Button = findViewById(R.id.btnClearBookmarks)
        val btnClearCache: Button = findViewById(R.id.btnClearCache)
        val switchBlockEmbeddedAds: Switch = findViewById(R.id.switchBlockEmbeddedAds)
        val switchForceDarkMode: Switch = findViewById(R.id.switchForceDarkMode)
        val switchAutoFullscreen: Switch = findViewById(R.id.switchAutoFullscreen)

        etHomepage.setText(AppPreferences.getHomepage(this))
        switchBlockEmbeddedAds.isChecked = AppPreferences.isBlockEmbeddedAdsEnabled(this)
        switchBlockEmbeddedAds.setOnCheckedChangeListener { _, isChecked ->
            AppPreferences.setBlockEmbeddedAdsEnabled(this, isChecked)
            FirebaseInitializer.logEvent("settings_adblock_changed", mapOf("enabled" to isChecked))
            Toast.makeText(
                this,
                if (isChecked) R.string.settings_block_ads_enabled else R.string.settings_block_ads_disabled,
                Toast.LENGTH_SHORT
            ).show()
        }

        switchForceDarkMode.isChecked = AppPreferences.isForceDarkModeEnabled(this)
        switchForceDarkMode.setOnCheckedChangeListener { _, isChecked ->
            AppPreferences.setForceDarkModeEnabled(this, isChecked)
            FirebaseInitializer.logEvent("settings_darkmode_changed", mapOf("enabled" to isChecked))
            Toast.makeText(this, if (isChecked) R.string.settings_dark_mode_enabled else R.string.settings_dark_mode_disabled, Toast.LENGTH_SHORT).show()
        }

        switchAutoFullscreen.isChecked = AppPreferences.isAutoFullscreenEnabled(this)
        switchAutoFullscreen.setOnCheckedChangeListener { _, isChecked ->
            AppPreferences.setAutoFullscreenEnabled(this, isChecked)
            FirebaseInitializer.logEvent("settings_autofullscreen_changed", mapOf("enabled" to isChecked))
            val msgRes = if (isChecked) R.string.settings_auto_fullscreen_enabled else R.string.settings_auto_fullscreen_disabled
            Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
        }

        val db = BrowserDatabase.getInstance(applicationContext)

        FocusAnimationHelper.applyAll(
            btnSaveHomepage, btnClearHistory, btnClearBookmarks, btnClearCache, etHomepage,
            switchBlockEmbeddedAds, switchForceDarkMode, switchAutoFullscreen
        )

        btnSaveHomepage.setOnClickListener {
            val url = etHomepage.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, R.string.settings_homepage_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val normalized = if (url.startsWith("http://") || url.startsWith("https://")) {
                url
            } else {
                "https://$url"
            }
            AppPreferences.setHomepage(this, normalized)
            FirebaseInitializer.logEvent("settings_homepage_saved", mapOf("url" to normalized))
            Toast.makeText(this, R.string.settings_homepage_saved, Toast.LENGTH_SHORT).show()
        }

        btnClearHistory.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { db.historyDao().clearAll() }
                FirebaseInitializer.logEvent("settings_history_cleared")
                Toast.makeText(this@SettingsActivity, R.string.settings_history_cleared, Toast.LENGTH_SHORT).show()
            }
        }

        btnClearBookmarks.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { db.bookmarkDao().clearAll() }
                FirebaseInitializer.logEvent("settings_bookmarks_cleared")
                Toast.makeText(this@SettingsActivity, R.string.settings_bookmarks_cleared, Toast.LENGTH_SHORT).show()
            }
        }

        btnClearCache.setOnClickListener {
            try {
                WebView(applicationContext).clearCache(true)
                FirebaseInitializer.logEvent("settings_cache_cleared")
                Toast.makeText(this, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, R.string.settings_cache_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

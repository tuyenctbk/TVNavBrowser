package com.tdpham.tvnavbrowser

import android.os.Bundle
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tdpham.tvnavbrowser.data.db.BrowserDatabase
import com.tdpham.tvnavbrowser.ui.FocusAnimationHelper
import com.tdpham.tvnavbrowser.util.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)

        val etHomepage: EditText = findViewById(R.id.etHomepage)
        val btnSaveHomepage: Button = findViewById(R.id.btnSaveHomepage)
        val btnClearHistory: Button = findViewById(R.id.btnClearHistory)
        val btnClearBookmarks: Button = findViewById(R.id.btnClearBookmarks)
        val btnClearCache: Button = findViewById(R.id.btnClearCache)
        val switchBlockEmbeddedAds: Switch = findViewById(R.id.switchBlockEmbeddedAds)

        etHomepage.setText(AppPreferences.getHomepage(this))
        switchBlockEmbeddedAds.isChecked = AppPreferences.isBlockEmbeddedAdsEnabled(this)
        switchBlockEmbeddedAds.setOnCheckedChangeListener { _, isChecked ->
            AppPreferences.setBlockEmbeddedAdsEnabled(this, isChecked)
            Toast.makeText(
                this,
                if (isChecked) R.string.settings_block_ads_enabled else R.string.settings_block_ads_disabled,
                Toast.LENGTH_SHORT
            ).show()
        }

        val db = BrowserDatabase.getInstance(applicationContext)

        FocusAnimationHelper.applyAll(
            btnSaveHomepage, btnClearHistory, btnClearBookmarks, btnClearCache, etHomepage
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
            Toast.makeText(this, R.string.settings_homepage_saved, Toast.LENGTH_SHORT).show()
        }

        btnClearHistory.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { db.historyDao().clearAll() }
                Toast.makeText(this@SettingsActivity, R.string.settings_history_cleared, Toast.LENGTH_SHORT).show()
            }
        }

        btnClearBookmarks.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { db.bookmarkDao().clearAll() }
                Toast.makeText(this@SettingsActivity, R.string.settings_bookmarks_cleared, Toast.LENGTH_SHORT).show()
            }
        }

        btnClearCache.setOnClickListener {
            try {
                WebView(applicationContext).clearCache(true)
                Toast.makeText(this, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, R.string.settings_cache_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

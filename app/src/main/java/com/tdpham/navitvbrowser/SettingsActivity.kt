package com.tdpham.navitvbrowser

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tdpham.navitvbrowser.data.db.BrowserDatabase
import com.tdpham.navitvbrowser.ui.FocusAnimationHelper
import com.tdpham.navitvbrowser.util.AiEngine
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

        // AI Settings UI
        val rgAiMode: RadioGroup = findViewById(R.id.rgAiMode)
        val rbAiAuto: RadioButton = findViewById(R.id.rbAiAuto)
        val rbAiManual: RadioButton = findViewById(R.id.rbAiManual)
        val llAiManualConfig: LinearLayout = findViewById(R.id.llAiManualConfig)
        val spAiEngine: Spinner = findViewById(R.id.spAiEngine)
        val etAiKey: EditText = findViewById(R.id.etAiKey)
        val etAiEndpoint: EditText = findViewById(R.id.etAiEndpoint)
        val etAiModel: EditText = findViewById(R.id.etAiModel)
        val btnSaveAiConfig: Button = findViewById(R.id.btnSaveAiConfig)

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

        // Initialize AI Mode
        val currentMode = AppPreferences.getAiMode(this)
        if (currentMode == "MANUAL") {
            rbAiManual.isChecked = true
            llAiManualConfig.visibility = View.VISIBLE
        } else {
            rbAiAuto.isChecked = true
            llAiManualConfig.visibility = View.GONE
        }

        rgAiMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rbAiManual) "MANUAL" else "AUTO"
            llAiManualConfig.visibility = if (mode == "MANUAL") View.VISIBLE else View.GONE
        }

        // Initialize AI Engine Spinner
        val engines = AiEngine.getAllIds()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, engines)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spAiEngine.adapter = adapter
        spAiEngine.setSelection(engines.indexOf(AppPreferences.getAiEngine(this)).coerceAtLeast(0))

        etAiKey.setText(AppPreferences.getAiCustomKey(this))
        etAiEndpoint.setText(AppPreferences.getAiCustomEndpoint(this))
        etAiModel.setText(AppPreferences.getAiCustomModel(this))

        btnSaveAiConfig.setOnClickListener {
            val mode = if (rbAiManual.isChecked) "MANUAL" else "AUTO"
            AppPreferences.setAiMode(this, mode)
            AppPreferences.setAiEngine(this, spAiEngine.selectedItem.toString())
            AppPreferences.setAiCustomKey(this, etAiKey.text.toString().trim())
            AppPreferences.setAiCustomEndpoint(this, etAiEndpoint.text.toString().trim())
            AppPreferences.setAiCustomModel(this, etAiModel.text.toString().trim())
            
            FirebaseInitializer.logEvent("settings_ai_config_saved", mapOf("mode" to mode))
            Toast.makeText(this, R.string.settings_ai_config_saved, Toast.LENGTH_SHORT).show()
        }

        val db = BrowserDatabase.getInstance(applicationContext)

        FocusAnimationHelper.applyAll(
            btnSaveHomepage, btnClearHistory, btnClearBookmarks, btnClearCache, etHomepage,
            switchBlockEmbeddedAds, switchForceDarkMode, switchAutoFullscreen,
            rbAiAuto, rbAiManual, spAiEngine, etAiKey, etAiEndpoint, etAiModel, btnSaveAiConfig
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

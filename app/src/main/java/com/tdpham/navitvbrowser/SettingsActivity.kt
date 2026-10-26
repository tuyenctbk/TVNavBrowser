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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tdpham.navitvbrowser.data.db.BrowserDatabase
import com.tdpham.navitvbrowser.ui.FocusAnimationHelper
import com.tdpham.navitvbrowser.ui.ThemeHelper
import com.tdpham.navitvbrowser.util.AppPreferences
import com.tdpham.navitvbrowser.util.EmbeddedAdBlocker
import com.tdpham.navitvbrowser.util.FirebaseInitializer
import com.tdpham.navitvbrowser.data.sync.BookmarkSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)

        ThemeHelper.applyActivityThemeView(this, findViewById(android.R.id.content))

        FirebaseInitializer.logEvent("settings_screen_opened")

        // Interface Theme Selection (Light / Dark)
        val cardThemeDark: View = findViewById(R.id.cardThemeDark)
        val cardThemeLight: View = findViewById(R.id.cardThemeLight)
        val tvThemeDarkBadge: TextView = findViewById(R.id.tvThemeDarkBadge)
        val tvThemeLightBadge: TextView = findViewById(R.id.tvThemeLightBadge)

        fun updateThemeSelectionUi() {
            val isLight = AppPreferences.isLightTheme(this)
            tvThemeDarkBadge.visibility = if (!isLight) View.VISIBLE else View.GONE
            tvThemeLightBadge.visibility = if (isLight) View.VISIBLE else View.GONE
            cardThemeDark.alpha = if (!isLight) 1.0f else 0.75f
            cardThemeLight.alpha = if (isLight) 1.0f else 0.75f
        }

        fun onThemeSelected(theme: String) {
            if (AppPreferences.getAppTheme(this) == theme) return
            AppPreferences.setAppTheme(this, theme)
            updateThemeSelectionUi()
            val themeName = if (theme == AppPreferences.THEME_LIGHT) getString(R.string.settings_theme_light) else getString(R.string.settings_theme_dark)
            Toast.makeText(this, getString(R.string.settings_theme_changed, themeName), Toast.LENGTH_SHORT).show()
            FirebaseInitializer.logEvent("settings_theme_changed", mapOf("theme" to theme))
            recreate()
        }

        cardThemeDark.setOnClickListener { onThemeSelected(AppPreferences.THEME_DARK) }
        cardThemeLight.setOnClickListener { onThemeSelected(AppPreferences.THEME_LIGHT) }
        updateThemeSelectionUi()

        // Mouse Pointer Speed Selection (Slow / Normal / Fast)
        val cardMouseSpeedSlow: View = findViewById(R.id.cardMouseSpeedSlow)
        val cardMouseSpeedNormal: View = findViewById(R.id.cardMouseSpeedNormal)
        val cardMouseSpeedFast: View = findViewById(R.id.cardMouseSpeedFast)
        val tvMouseSpeedSlowBadge: TextView = findViewById(R.id.tvMouseSpeedSlowBadge)
        val tvMouseSpeedNormalBadge: TextView = findViewById(R.id.tvMouseSpeedNormalBadge)
        val tvMouseSpeedFastBadge: TextView = findViewById(R.id.tvMouseSpeedFastBadge)

        fun updateMouseSpeedUi() {
            val currentSpeed = AppPreferences.getMouseSpeed(this)
            tvMouseSpeedSlowBadge.visibility = if (currentSpeed == AppPreferences.MOUSE_SPEED_SLOW) View.VISIBLE else View.GONE
            tvMouseSpeedNormalBadge.visibility = if (currentSpeed == AppPreferences.MOUSE_SPEED_NORMAL) View.VISIBLE else View.GONE
            tvMouseSpeedFastBadge.visibility = if (currentSpeed == AppPreferences.MOUSE_SPEED_FAST) View.VISIBLE else View.GONE
            cardMouseSpeedSlow.alpha = if (currentSpeed == AppPreferences.MOUSE_SPEED_SLOW) 1.0f else 0.75f
            cardMouseSpeedNormal.alpha = if (currentSpeed == AppPreferences.MOUSE_SPEED_NORMAL) 1.0f else 0.75f
            cardMouseSpeedFast.alpha = if (currentSpeed == AppPreferences.MOUSE_SPEED_FAST) 1.0f else 0.75f
        }

        fun onMouseSpeedSelected(speed: String) {
            if (AppPreferences.getMouseSpeed(this) == speed) return
            AppPreferences.setMouseSpeed(this, speed)
            updateMouseSpeedUi()
            val speedLabel = when (speed) {
                AppPreferences.MOUSE_SPEED_SLOW -> getString(R.string.settings_mouse_speed_slow)
                AppPreferences.MOUSE_SPEED_FAST -> getString(R.string.settings_mouse_speed_fast)
                else -> getString(R.string.settings_mouse_speed_normal)
            }
            Toast.makeText(this, getString(R.string.settings_mouse_speed_changed, speedLabel), Toast.LENGTH_SHORT).show()
            FirebaseInitializer.logEvent("settings_mouse_speed_changed", mapOf("speed" to speed))
        }

        cardMouseSpeedSlow.setOnClickListener { onMouseSpeedSelected(AppPreferences.MOUSE_SPEED_SLOW) }
        cardMouseSpeedNormal.setOnClickListener { onMouseSpeedSelected(AppPreferences.MOUSE_SPEED_NORMAL) }
        cardMouseSpeedFast.setOnClickListener { onMouseSpeedSelected(AppPreferences.MOUSE_SPEED_FAST) }
        updateMouseSpeedUi()

        val etHomepage: EditText = findViewById(R.id.etHomepage)
        val btnSaveHomepage: Button = findViewById(R.id.btnSaveHomepage)
        val btnClearHistory: Button = findViewById(R.id.btnClearHistory)
        val btnClearBookmarks: Button = findViewById(R.id.btnClearBookmarks)
        val btnClearCache: Button = findViewById(R.id.btnClearCache)
        val switchBlockEmbeddedAds: Switch = findViewById(R.id.switchBlockEmbeddedAds)
        val switchForceDarkMode: Switch = findViewById(R.id.switchForceDarkMode)
        val switchAutoFullscreen: Switch = findViewById(R.id.switchAutoFullscreen)

        // Custom Ad Blocker Rules
        val etCustomAdRule: EditText = findViewById(R.id.etCustomAdRule)
        val btnAddAdRule: Button = findViewById(R.id.btnAddAdRule)
        val customRulesContainer: LinearLayout = findViewById(R.id.customRulesContainer)

        fun refreshCustomRules() {
            customRulesContainer.removeAllViews()
            val rules = AppPreferences.getCustomAdRules(this)
            EmbeddedAdBlocker.updateCustomRules(rules)

            if (rules.isEmpty()) {
                val tvEmpty = android.widget.TextView(this).apply {
                    text = getString(R.string.settings_no_custom_rules)
                    setTextColor(getColor(R.color.text_secondary))
                    textSize = 13f
                    setPadding(0, 8, 0, 8)
                }
                customRulesContainer.addView(tvEmpty)
                return
            }

            for (rule in rules) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 4, 0, 4) }
                    setBackgroundResource(R.drawable.bg_url_bar)
                    setPadding(12, 6, 12, 6)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val tvRule = android.widget.TextView(this).apply {
                    text = rule
                    setTextColor(getColor(R.color.text_primary))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val btnRemove = android.widget.ImageButton(this).apply {
                    setImageResource(R.drawable.ic_nav_delete)
                    setBackgroundResource(R.drawable.bg_toolbar_btn)
                    contentDescription = getString(R.string.settings_remove_rule)
                    setOnClickListener {
                        AppPreferences.removeCustomAdRule(this@SettingsActivity, rule)
                        Toast.makeText(this@SettingsActivity, getString(R.string.settings_adblock_rule_removed, rule), Toast.LENGTH_SHORT).show()
                        refreshCustomRules()
                    }
                }
                FocusAnimationHelper.apply(btnRemove)

                row.addView(tvRule)
                row.addView(btnRemove)
                customRulesContainer.addView(row)
            }
        }

        refreshCustomRules()

        val addRuleAction = {
            val rule = etCustomAdRule.text.toString().trim()
            if (rule.isNotEmpty()) {
                AppPreferences.addCustomAdRule(this, rule)
                etCustomAdRule.setText("")
                Toast.makeText(this, getString(R.string.settings_adblock_rule_added, rule), Toast.LENGTH_SHORT).show()
                refreshCustomRules()
            }
        }
        btnAddAdRule.setOnClickListener { addRuleAction() }
        etCustomAdRule.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                addRuleAction()
                true
            } else false
        }

        // Firestore Cloud Sync
        val etSyncUserId: EditText = findViewById(R.id.etSyncUserId)
        val btnSaveSyncId: Button = findViewById(R.id.btnSaveSyncId)
        val btnTriggerCloudSync: Button = findViewById(R.id.btnTriggerCloudSync)

        etSyncUserId.setText(AppPreferences.getSyncUserId(this))
        btnSaveSyncId.setOnClickListener {
            val id = etSyncUserId.text.toString().trim()
            if (id.isNotEmpty()) {
                AppPreferences.setSyncUserId(this, id)
                Toast.makeText(this, R.string.sync_user_id_saved, Toast.LENGTH_SHORT).show()
            }
        }

        val db = BrowserDatabase.getInstance(applicationContext)
        val bookmarkRepo = com.tdpham.navitvbrowser.data.repository.BookmarkRepository(db.bookmarkDao())

        btnTriggerCloudSync.setOnClickListener {
            Toast.makeText(this, R.string.sync_bookmarks_syncing, Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val result = com.tdpham.navitvbrowser.data.sync.BookmarkSyncManager.syncBookmarks(this@SettingsActivity, bookmarkRepo)
                result.onSuccess { count ->
                    Toast.makeText(this@SettingsActivity, getString(R.string.sync_bookmarks_success, count), Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this@SettingsActivity, R.string.sync_bookmarks_error, Toast.LENGTH_LONG).show()
                }
            }
        }

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

        FocusAnimationHelper.applyAll(
            cardThemeDark, cardThemeLight,
            cardMouseSpeedSlow, cardMouseSpeedNormal, cardMouseSpeedFast,
            btnSaveHomepage, btnClearHistory, btnClearBookmarks, btnClearCache, etHomepage,
            switchBlockEmbeddedAds, switchForceDarkMode, switchAutoFullscreen
        )

        val saveHomepageAction = {
            val url = etHomepage.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, R.string.settings_homepage_empty, Toast.LENGTH_SHORT).show()
            } else {
                val normalized = if (url.startsWith("http://") || url.startsWith("https://")) {
                    url
                } else {
                    "https://$url"
                }
                AppPreferences.setHomepage(this, normalized)
                FirebaseInitializer.logEvent("settings_homepage_saved", mapOf("host" to com.tdpham.navitvbrowser.util.UrlUtils.hostOf(normalized)))
                Toast.makeText(this, R.string.settings_homepage_saved, Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveHomepage.setOnClickListener { saveHomepageAction() }
        etHomepage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO
            ) {
                saveHomepageAction()
                true
            } else {
                false
            }
        }

        btnClearHistory.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this, ThemeHelper.getDialogTheme(this))
                .setTitle(R.string.settings_clear_history)
                .setMessage(R.string.settings_clear_history_confirm)
                .setPositiveButton(R.string.settings_clear_history) { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { db.historyDao().clearAll() }
                        FirebaseInitializer.logEvent("settings_history_cleared")
                        Toast.makeText(this@SettingsActivity, R.string.settings_history_cleared, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        btnClearBookmarks.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this, ThemeHelper.getDialogTheme(this))
                .setTitle(R.string.settings_clear_bookmarks)
                .setMessage(R.string.settings_clear_bookmarks_confirm)
                .setPositiveButton(R.string.settings_clear_bookmarks) { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            db.bookmarkDao().clearAll()
                            // Otherwise the next sync would restore them from the cloud
                            BookmarkSyncManager.clearRemoteBookmarks(applicationContext)
                        }
                        FirebaseInitializer.logEvent("settings_bookmarks_cleared")
                        Toast.makeText(this@SettingsActivity, R.string.settings_bookmarks_cleared, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        btnClearCache.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this, ThemeHelper.getDialogTheme(this))
                .setTitle(R.string.settings_clear_cache)
                .setMessage(R.string.settings_clear_cache_confirm)
                .setPositiveButton(R.string.settings_clear_cache) { _, _ ->
                    lifecycleScope.launch {
                        try {
                            com.tdpham.navitvbrowser.util.WebViewCacheManager.clearEntireCache(applicationContext)
                            FirebaseInitializer.logEvent("settings_cache_cleared")
                            Toast.makeText(this@SettingsActivity, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            Toast.makeText(this@SettingsActivity, R.string.settings_cache_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}

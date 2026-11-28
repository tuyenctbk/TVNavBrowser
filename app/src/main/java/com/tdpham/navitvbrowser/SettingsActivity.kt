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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tdpham.navitvbrowser.data.db.BrowserDatabase
import com.tdpham.navitvbrowser.ui.FocusAnimationHelper
import com.tdpham.navitvbrowser.ui.ThemeHelper
import com.tdpham.navitvbrowser.util.AppPreferences
import com.tdpham.navitvbrowser.util.EmbeddedAdBlocker
import com.tdpham.navitvbrowser.util.FirebaseInitializer
import com.tdpham.navitvbrowser.util.UrlUtils
import com.tdpham.navitvbrowser.data.sync.BookmarkSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)
        }

        ThemeHelper.applyActivityThemeView(this, findViewById(android.R.id.content))

        val sectionBg = ThemeHelper.getSettingsSectionBackground(this)
        val badgeBg = ThemeHelper.getStepBadgeBackground(this)
        val itemBg = ThemeHelper.getFocusableItemBackground(this)
        val btnSettingsBack: android.widget.ImageButton = findViewById(R.id.btnSettingsBack)
        btnSettingsBack.setBackgroundResource(ThemeHelper.getToolbarButtonBackground(this))
        btnSettingsBack.imageTintList = android.content.res.ColorStateList.valueOf(ThemeHelper.getTextPrimaryColor(this))
        FocusAnimationHelper.apply(btnSettingsBack)
        btnSettingsBack.setOnClickListener { finish() }

        listOf(
            R.id.themeSectionContainer,
            R.id.mouseSpeedSectionContainer,
            R.id.homepageSectionContainer,
            R.id.adblockSectionContainer,
            R.id.cloudSyncSectionContainer,
            R.id.preferencesSectionContainer,
            R.id.supportSectionContainer,
            R.id.dataSectionContainer
        ).forEachIndexed { idx, id ->
            findViewById<View>(id)?.let { section ->
                section.setBackgroundResource(sectionBg)
                FocusAnimationHelper.animateEntrance(section, idx)
            }
        }

        listOf(
            R.id.cardThemeDark,
            R.id.cardThemeLight,
            R.id.cardMouseSpeedSlow,
            R.id.cardMouseSpeedNormal,
            R.id.cardMouseSpeedFast
        ).forEach { findViewById<View>(it)?.setBackgroundResource(itemBg) }

        val btnSupportDevelopment = findViewById<Button>(R.id.btnSupportDevelopment)
        btnSupportDevelopment?.setOnClickListener {
            com.tdpham.navitvbrowser.ui.DonationDialogHelper.showDonationDialog(this)
        }

        listOf(
            R.id.tvThemeDarkBadge,
            R.id.tvThemeLightBadge,
            R.id.tvMouseSpeedSlowBadge,
            R.id.tvMouseSpeedNormalBadge,
            R.id.tvMouseSpeedFastBadge
        ).forEach { findViewById<View>(it)?.setBackgroundResource(badgeBg) }

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

        fun hideKeyboard(view: View) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }

        fun refreshCustomRules(restoreFocusIndex: Int = -1) {
            customRulesContainer.removeAllViews()
            val rules = AppPreferences.getCustomAdRules(this)
            EmbeddedAdBlocker.updateCustomRules(rules)

            if (rules.isEmpty()) {
                val tvEmpty = android.widget.TextView(this).apply {
                    text = getString(R.string.settings_no_custom_rules)
                    setTextColor(ThemeHelper.getTextSecondaryColor(this@SettingsActivity))
                    textSize = 13f
                    setPadding(0, 8, 0, 8)
                }
                customRulesContainer.addView(tvEmpty)
                if (restoreFocusIndex >= 0) {
                    etCustomAdRule.requestFocus()
                }
                return
            }

            rules.forEachIndexed { idx, rule ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 4, 0, 4) }
                    setBackgroundResource(ThemeHelper.getUrlBarBackground(this@SettingsActivity))
                    setPadding(12, 6, 12, 6)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val tvRule = android.widget.TextView(this).apply {
                    text = rule
                    setTextColor(ThemeHelper.getTextPrimaryColor(this@SettingsActivity))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val btnRemove = android.widget.ImageButton(this).apply {
                    setImageResource(R.drawable.ic_nav_delete)
                    setBackgroundResource(ThemeHelper.getToolbarButtonBackground(this@SettingsActivity))
                    imageTintList = android.content.res.ColorStateList.valueOf(ThemeHelper.getTextSecondaryColor(this@SettingsActivity))
                    contentDescription = getString(R.string.settings_remove_rule)
                    setOnClickListener {
                        AppPreferences.removeCustomAdRule(this@SettingsActivity, rule)
                        Toast.makeText(this@SettingsActivity, getString(R.string.settings_adblock_rule_removed, rule), Toast.LENGTH_SHORT).show()
                        refreshCustomRules(restoreFocusIndex = idx)
                    }
                }
                FocusAnimationHelper.apply(btnRemove)

                row.addView(tvRule)
                row.addView(btnRemove)
                customRulesContainer.addView(row)
            }

            if (restoreFocusIndex >= 0 && customRulesContainer.childCount > 0) {
                val targetRow = customRulesContainer.getChildAt(restoreFocusIndex.coerceIn(0, customRulesContainer.childCount - 1)) as? LinearLayout
                targetRow?.getChildAt(1)?.requestFocus()
            }
        }

        refreshCustomRules()

        val addRuleAction = {
            val rule = etCustomAdRule.text.toString().trim()
            if (rule.isNotEmpty()) {
                if (AppPreferences.addCustomAdRule(this, rule)) {
                    etCustomAdRule.setText("")
                    hideKeyboard(etCustomAdRule)
                    Toast.makeText(this, getString(R.string.settings_adblock_rule_added, rule.lowercase()), Toast.LENGTH_SHORT).show()
                    refreshCustomRules()
                }
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
            val cleanId = AppPreferences.sanitizeSyncUserId(id)
            if (cleanId.isNotEmpty()) {
                AppPreferences.setSyncUserId(this, cleanId)
                etSyncUserId.setText(cleanId)
                hideKeyboard(etSyncUserId)
                com.tdpham.navitvbrowser.util.UsageQuotaManager.startRealtimeSync(this)
                Toast.makeText(this, R.string.sync_user_id_saved, Toast.LENGTH_SHORT).show()
            }
        }

        val db = BrowserDatabase.getInstance(applicationContext)
        val bookmarkRepo = com.tdpham.navitvbrowser.data.repository.BookmarkRepository(db.bookmarkDao())

        btnTriggerCloudSync.setOnClickListener {
            Toast.makeText(this, R.string.sync_bookmarks_syncing, Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                com.tdpham.navitvbrowser.util.UsageQuotaManager.startRealtimeSync(this@SettingsActivity)
                val result = com.tdpham.navitvbrowser.data.sync.BookmarkSyncManager.syncBookmarks(this@SettingsActivity, bookmarkRepo)
                if (isFinishing || isDestroyed) return@launch
                result.onSuccess { count ->
                    Toast.makeText(applicationContext, getString(R.string.sync_bookmarks_success, count), Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(applicationContext, R.string.sync_bookmarks_error, Toast.LENGTH_LONG).show()
                }
            }
        }

        if (AppPreferences.hasCustomHomepage(this)) {
            etHomepage.setText(AppPreferences.getHomepage(this))
        } else {
            etHomepage.setText("")
        }
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

        val switchEcoLiteAi: Switch = findViewById(R.id.switchEcoLiteAi)
        switchEcoLiteAi.isChecked = com.tdpham.navitvbrowser.util.UsageQuotaManager.isEcoLiteMode(this)
        switchEcoLiteAi.setOnCheckedChangeListener { _, isChecked ->
            if (com.tdpham.navitvbrowser.util.UsageQuotaManager.isEcoLiteMode(this) != isChecked) {
                com.tdpham.navitvbrowser.util.UsageQuotaManager.setEcoLiteMode(this, isChecked)
                Toast.makeText(
                    this,
                    if (isChecked) R.string.ai_eco_lite_on else R.string.ai_eco_lite_off,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                com.tdpham.navitvbrowser.util.UsageQuotaManager.observeQuotaSnapshot(this@SettingsActivity)
                    .collect { snap ->
                        if (switchEcoLiteAi.isChecked != snap.ecoLiteMode) {
                            switchEcoLiteAi.isChecked = snap.ecoLiteMode
                        }
                        val (hours, mins) = com.tdpham.navitvbrowser.util.UsageQuotaManager.getTimeUntilReset()
                        switchEcoLiteAi.text = getString(
                            R.string.settings_eco_lite_ai_live_format,
                            getString(R.string.settings_eco_lite_ai),
                            snap.remainingCredits,
                            snap.totalAllowance,
                            hours,
                            mins
                        )
                    }
            }
        }

        FocusAnimationHelper.applyAll(
            cardThemeDark, cardThemeLight,
            cardMouseSpeedSlow, cardMouseSpeedNormal, cardMouseSpeedFast,
            btnSaveHomepage, btnClearHistory, btnClearBookmarks, btnClearCache, etHomepage,
            switchBlockEmbeddedAds, switchForceDarkMode, switchAutoFullscreen, switchEcoLiteAi,
            btnAddAdRule, etCustomAdRule, etSyncUserId, btnSaveSyncId, btnTriggerCloudSync
        )
        btnSupportDevelopment?.let { FocusAnimationHelper.apply(it) }

        val saveHomepageAction = {
            val url = etHomepage.text.toString().trim()
            if (url.isEmpty()) {
                if (AppPreferences.hasCustomHomepage(this)) {
                    AppPreferences.clearCustomHomepage(this)
                    Toast.makeText(this, R.string.nav_returning_home, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.settings_homepage_empty, Toast.LENGTH_SHORT).show()
                }
            } else {
                val normalized = when {
                    url.equals(AppPreferences.DASHBOARD_ASSET_URL, ignoreCase = true) ||
                        url.equals("dashboard", ignoreCase = true) -> AppPreferences.DASHBOARD_ASSET_URL
                    else -> UrlUtils.resolveInput(url)
                }
                if (normalized == AppPreferences.DASHBOARD_ASSET_URL) {
                    AppPreferences.clearCustomHomepage(this)
                    etHomepage.setText("")
                } else {
                    AppPreferences.setHomepage(this, normalized)
                    etHomepage.setText(normalized)
                }
                FirebaseInitializer.logEvent("settings_homepage_saved", mapOf("host" to UrlUtils.hostOf(normalized)))
                hideKeyboard(etHomepage)
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

        val switchAutoCacheCleanup: Switch? = findViewById(R.id.switchAutoCacheCleanup)
        val tvCacheMaintenanceStatus: TextView? = findViewById(R.id.tvCacheMaintenanceStatus)

        fun refreshCacheMaintenanceStatus() {
            lifecycleScope.launch {
                val (sizeBytes, lastSummary) = withContext(Dispatchers.IO) {
                    val size = com.tdpham.navitvbrowser.util.WebViewCacheManager.calculateTotalCacheSize(applicationContext)
                    val summary = com.tdpham.navitvbrowser.util.WebViewCacheManager.getLastCleanupSummary(applicationContext)
                    size to summary
                }
                val formattedSize = com.tdpham.navitvbrowser.util.WebViewCacheManager.formatByteSize(sizeBytes)
                tvCacheMaintenanceStatus?.text = if (lastSummary != null && (lastSummary.inactiveOriginsCleared > 0 || lastSummary.totalBytesFreed > 0L)) {
                    getString(
                        R.string.settings_cache_status_last_run,
                        formattedSize,
                        lastSummary.inactiveOriginsCleared,
                        com.tdpham.navitvbrowser.util.WebViewCacheManager.formatByteSize(lastSummary.totalBytesFreed)
                    )
                } else {
                    getString(R.string.settings_cache_status_idle, formattedSize)
                }
            }
        }

        switchAutoCacheCleanup?.isChecked = AppPreferences.isAutoCacheCleanupEnabled(this)
        switchAutoCacheCleanup?.setOnCheckedChangeListener { _, isChecked ->
            AppPreferences.setAutoCacheCleanupEnabled(this, isChecked)
            if (isChecked) {
                com.tdpham.navitvbrowser.util.WebViewCacheManager.schedulePeriodicCleanup(applicationContext)
            } else {
                com.tdpham.navitvbrowser.util.WebViewCacheManager.cancelPeriodicCleanup(applicationContext)
            }
            FirebaseInitializer.logEvent("settings_auto_cache_cleanup_changed", mapOf("enabled" to isChecked))
            Toast.makeText(
                this,
                if (isChecked) R.string.settings_auto_cache_enabled else R.string.settings_auto_cache_disabled,
                Toast.LENGTH_SHORT
            ).show()
        }
        refreshCacheMaintenanceStatus()

        btnClearCache.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this, ThemeHelper.getDialogTheme(this))
                .setTitle(R.string.settings_clear_cache)
                .setMessage(R.string.settings_clear_cache_confirm)
                .setPositiveButton(R.string.settings_clear_cache) { _, _ ->
                    lifecycleScope.launch {
                        try {
                            val cleared = com.tdpham.navitvbrowser.util.WebViewCacheManager.clearEntireCache(applicationContext)
                            if (cleared) {
                                FirebaseInitializer.logEvent("settings_cache_cleared")
                                Toast.makeText(this@SettingsActivity, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
                                refreshCacheMaintenanceStatus()
                            } else {
                                Toast.makeText(this@SettingsActivity, R.string.settings_cache_failed, Toast.LENGTH_SHORT).show()
                            }
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

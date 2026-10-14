package com.tdpham.navitvbrowser.util

import android.content.Context

object AppPreferences {

    private const val PREFS = "tvnav_prefs"
    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    private const val KEY_HOMEPAGE = "homepage_url"
    private const val KEY_HOMEPAGE_CUSTOM = "homepage_custom"
    private const val KEY_BLOCK_EMBEDDED_ADS = "block_embedded_ads"
    private const val KEY_FORCE_DARK_MODE = "force_dark_mode"
    private const val KEY_AUTO_FULLSCREEN = "auto_fullscreen"
    private const val KEY_APP_THEME = "app_interface_theme"
    private const val KEY_CUSTOM_AD_RULES = "custom_ad_rules"
    private const val KEY_FIRESTORE_SYNC_ENABLED = "firestore_sync_enabled"
    private const val KEY_SYNC_USER_ID = "sync_user_id"
    private const val DEFAULT_HOMEPAGE = "https://www.google.com"

    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"

    fun getAppTheme(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_APP_THEME, THEME_DARK) ?: THEME_DARK

    fun setAppTheme(context: Context, theme: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_THEME, theme)
            .apply()
    }

    fun isLightTheme(context: Context): Boolean = getAppTheme(context) == THEME_LIGHT

    fun getCustomAdRules(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_CUSTOM_AD_RULES, emptySet()) ?: emptySet()

    fun addCustomAdRule(context: Context, rule: String) {
        val trimmed = rule.trim().lowercase()
        if (trimmed.isEmpty()) return
        val current = getCustomAdRules(context).toMutableSet()
        current.add(trimmed)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_CUSTOM_AD_RULES, current)
            .apply()
    }

    fun removeCustomAdRule(context: Context, rule: String) {
        val current = getCustomAdRules(context).toMutableSet()
        if (current.remove(rule.trim().lowercase())) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_CUSTOM_AD_RULES, current)
                .apply()
        }
    }

    fun isFirestoreSyncEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FIRESTORE_SYNC_ENABLED, true)

    fun setFirestoreSyncEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FIRESTORE_SYNC_ENABLED, enabled)
            .apply()
    }

    fun getSyncUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var userId = prefs.getString(KEY_SYNC_USER_ID, null)
        if (userId.isNullOrBlank()) {
            userId = "tv_user_" + java.util.UUID.randomUUID().toString().take(8)
            prefs.edit().putString(KEY_SYNC_USER_ID, userId).apply()
        }
        return userId
    }

    fun setSyncUserId(context: Context, userId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SYNC_USER_ID, userId.trim())
            .apply()
    }

    fun isOnboardingComplete(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun setOnboardingComplete(context: Context, complete: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, complete)
            .apply()
    }

    fun getHomepage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HOMEPAGE, DEFAULT_HOMEPAGE) ?: DEFAULT_HOMEPAGE

    fun setHomepage(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HOMEPAGE, url)
            .putBoolean(KEY_HOMEPAGE_CUSTOM, true)
            .apply()
    }

    fun hasCustomHomepage(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HOMEPAGE_CUSTOM, false)

    fun resolveHomepage(context: Context): String {
        if (hasCustomHomepage(context)) {
            return getHomepage(context)
        }
        return RemoteConfigHelper.getHomepageUrl().ifBlank { DEFAULT_HOMEPAGE }
    }

    @Volatile
    private var cachedBlockEmbeddedAds: Boolean? = null

    @Volatile
    private var cachedAutoFullscreen: Boolean? = null

    fun isBlockEmbeddedAdsEnabled(context: Context): Boolean {
        val cached = cachedBlockEmbeddedAds
        if (cached != null) return cached
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BLOCK_EMBEDDED_ADS, true)
        cachedBlockEmbeddedAds = value
        return value
    }

    fun setBlockEmbeddedAdsEnabled(context: Context, enabled: Boolean) {
        cachedBlockEmbeddedAds = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BLOCK_EMBEDDED_ADS, enabled)
            .apply()
    }

    fun isForceDarkModeEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FORCE_DARK_MODE, false)

    fun setForceDarkModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FORCE_DARK_MODE, enabled)
            .apply()
    }

    fun isAutoFullscreenEnabled(context: Context): Boolean {
        val cached = cachedAutoFullscreen
        if (cached != null) return cached
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_FULLSCREEN, false)
        cachedAutoFullscreen = value
        return value
    }

    fun setAutoFullscreenEnabled(context: Context, enabled: Boolean) {
        cachedAutoFullscreen = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_FULLSCREEN, enabled)
            .apply()
    }
}

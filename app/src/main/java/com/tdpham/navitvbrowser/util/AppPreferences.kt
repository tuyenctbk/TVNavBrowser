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
    private const val DEFAULT_HOMEPAGE = "https://www.google.com"

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

    fun isBlockEmbeddedAdsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BLOCK_EMBEDDED_ADS, true)

    fun setBlockEmbeddedAdsEnabled(context: Context, enabled: Boolean) {
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

    fun isAutoFullscreenEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_FULLSCREEN, false)

    fun setAutoFullscreenEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_FULLSCREEN, enabled)
            .apply()
    }
}

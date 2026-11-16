package com.tdpham.navitvbrowser.util

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

object RemoteConfigHelper {

    private const val KEY_HOMEPAGE = "homepage_url"
    private const val KEY_ADBLOCK_LIST = "adblock_suffixes"
    private const val KEY_LATEST_VERSION = "latest_version_code"
    private const val KEY_UPDATE_URL = "update_url"
    private const val KEY_ADS_ENABLED = "ads_enabled"
    private const val KEY_ADS_MIN_DAYS = "ads_min_days"
    private const val KEY_ADS_MIN_OPENS = "ads_min_opens"
    private const val KEY_SAFE_BROWSING = "safe_browsing_enabled"
    private const val KEY_AI_MODEL_NAME = "ai_model_name"
    private const val KEY_AI_LITE_MODEL_NAME = "ai_lite_model_name"
    private const val DEFAULT_HOMEPAGE = "https://www.google.com"
    private const val DEFAULT_AI_MODEL = "gemini-3.8-flash"
    private const val DEFAULT_AI_LITE_MODEL = "gemini-3.5-flash-lite"

    @Volatile
    private var cachedAdBlockSet: Set<String>? = null

    fun fetchAndActivate() {
        try {
            val remoteConfig = Firebase.remoteConfig
            remoteConfig.setConfigSettingsAsync(
                remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 }
            )
            remoteConfig.setDefaultsAsync(mapOf(
                KEY_HOMEPAGE to DEFAULT_HOMEPAGE,
                KEY_ADBLOCK_LIST to "",
                KEY_LATEST_VERSION to 0,
                KEY_UPDATE_URL to "",
                KEY_ADS_ENABLED to true,
                KEY_ADS_MIN_DAYS to 15L,
                KEY_ADS_MIN_OPENS to 3L,
                KEY_SAFE_BROWSING to true,
                KEY_AI_MODEL_NAME to DEFAULT_AI_MODEL,
                KEY_AI_LITE_MODEL_NAME to DEFAULT_AI_LITE_MODEL
            ))
            remoteConfig.fetchAndActivate().addOnCompleteListener {
                synchronized(this) {
                    cachedAdBlockSet = null
                }
            }
        } catch (_: Exception) {
            // Safe fallback on devices without Google Play Services / Firebase init
        }
    }

    fun getHomepageUrl(): String {
        val url = runCatching { Firebase.remoteConfig.getString(KEY_HOMEPAGE) }.getOrDefault("")
        return url.ifBlank { DEFAULT_HOMEPAGE }
    }

    fun getAdBlockSet(): Set<String> {
        return cachedAdBlockSet ?: synchronized(this) {
            cachedAdBlockSet ?: run {
                val raw = runCatching { Firebase.remoteConfig.getString(KEY_ADBLOCK_LIST) }.getOrDefault("")
                val set = if (raw.isBlank()) emptySet() else raw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
                cachedAdBlockSet = set
                set
            }
        }
    }

    fun getLatestVersionCode(): Int {
        return runCatching { Firebase.remoteConfig.getLong(KEY_LATEST_VERSION).toInt() }.getOrDefault(0)
    }

    fun getUpdateUrl(): String {
        return runCatching { Firebase.remoteConfig.getString(KEY_UPDATE_URL) }.getOrDefault("")
    }

    fun isAdsEnabled(): Boolean {
        return runCatching { Firebase.remoteConfig.getBoolean(KEY_ADS_ENABLED) }.getOrDefault(true)
    }

    fun getMinDays(): Int {
        return runCatching { Firebase.remoteConfig.getLong(KEY_ADS_MIN_DAYS).toInt() }.getOrDefault(15)
    }

    fun getMinOpens(): Int {
        return runCatching { Firebase.remoteConfig.getLong(KEY_ADS_MIN_OPENS).toInt() }.getOrDefault(3)
    }

    fun isSafeBrowsingEnabled(): Boolean {
        return runCatching { Firebase.remoteConfig.getBoolean(KEY_SAFE_BROWSING) }.getOrDefault(true)
    }

    fun getAiModelName(): String {
        val model = runCatching { Firebase.remoteConfig.getString(KEY_AI_MODEL_NAME) }.getOrDefault("")
        return model.ifBlank { DEFAULT_AI_MODEL }
    }

    fun getAiLiteModelName(): String {
        val model = runCatching { Firebase.remoteConfig.getString(KEY_AI_LITE_MODEL_NAME) }.getOrDefault("")
        return model.ifBlank { DEFAULT_AI_LITE_MODEL }
    }
}

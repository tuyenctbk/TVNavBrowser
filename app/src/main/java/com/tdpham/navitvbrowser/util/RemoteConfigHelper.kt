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
    private const val DEFAULT_HOMEPAGE = "https://www.google.com"

    @Volatile
    private var cachedAdBlockSet: Set<String>? = null

    fun fetchAndActivate() {
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
            KEY_ADS_MIN_OPENS to 3L
        ))
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            synchronized(this) {
                cachedAdBlockSet = null
            }
        }
    }

    fun getHomepageUrl(): String {
        val url = Firebase.remoteConfig.getString(KEY_HOMEPAGE)
        return url.ifBlank { DEFAULT_HOMEPAGE }
    }

    fun getAdBlockSet(): Set<String> {
        return cachedAdBlockSet ?: synchronized(this) {
            cachedAdBlockSet ?: run {
                val raw = Firebase.remoteConfig.getString(KEY_ADBLOCK_LIST)
                val set = if (raw.isBlank()) emptySet() else raw.split(",").map { it.trim().lowercase() }.toSet()
                cachedAdBlockSet = set
                set
            }
        }
    }

    fun getLatestVersionCode(): Int {
        return Firebase.remoteConfig.getLong(KEY_LATEST_VERSION).toInt()
    }

    fun getUpdateUrl(): String {
        return Firebase.remoteConfig.getString(KEY_UPDATE_URL)
    }

    fun isAdsEnabled(): Boolean {
        return Firebase.remoteConfig.getBoolean(KEY_ADS_ENABLED)
    }

    fun getMinDays(): Int {
        return Firebase.remoteConfig.getLong(KEY_ADS_MIN_DAYS).toInt()
    }

    fun getMinOpens(): Int {
        return Firebase.remoteConfig.getLong(KEY_ADS_MIN_OPENS).toInt()
    }
}

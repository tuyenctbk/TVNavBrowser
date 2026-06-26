package com.tdpham.navitvbrowser.util

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

object RemoteConfigHelper {

    private const val KEY_HOMEPAGE = "homepage_url"
    private const val KEY_ADBLOCK_LIST = "adblock_suffixes"
    private const val KEY_LATEST_VERSION = "latest_version_code"
    private const val KEY_UPDATE_URL = "update_url"
    private const val DEFAULT_HOMEPAGE = "https://www.google.com"

    fun fetchAndActivate() {
        val remoteConfig = Firebase.remoteConfig
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 }
        )
        remoteConfig.setDefaultsAsync(mapOf(
            KEY_HOMEPAGE to DEFAULT_HOMEPAGE,
            KEY_ADBLOCK_LIST to "",
            KEY_LATEST_VERSION to 0,
            KEY_UPDATE_URL to ""
        ))
        remoteConfig.fetchAndActivate()
    }

    fun getHomepageUrl(): String {
        val url = Firebase.remoteConfig.getString(KEY_HOMEPAGE)
        return url.ifBlank { DEFAULT_HOMEPAGE }
    }

    fun getAdBlockList(): List<String> {
        val raw = Firebase.remoteConfig.getString(KEY_ADBLOCK_LIST)
        return if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }
    }

    fun getLatestVersionCode(): Int {
        return Firebase.remoteConfig.getLong(KEY_LATEST_VERSION).toInt()
    }

    fun getUpdateUrl(): String {
        return Firebase.remoteConfig.getString(KEY_UPDATE_URL)
    }
}

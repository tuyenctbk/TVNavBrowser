package com.tdpham.navitvbrowser.util

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

object RemoteConfigHelper {

    private const val KEY_HOMEPAGE = "homepage_url"
    private const val DEFAULT_HOMEPAGE = "https://www.google.com"

    fun fetchAndActivate() {
        val remoteConfig = Firebase.remoteConfig
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 }
        )
        remoteConfig.setDefaultsAsync(mapOf(KEY_HOMEPAGE to DEFAULT_HOMEPAGE))
        remoteConfig.fetchAndActivate()
    }

    fun getHomepageUrl(): String {
        val url = Firebase.remoteConfig.getString(KEY_HOMEPAGE)
        return url.ifBlank { DEFAULT_HOMEPAGE }
    }
}

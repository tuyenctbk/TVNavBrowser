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
    private const val KEY_OPENAI_API_KEY = "openai_api_key"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_GROQ_API_KEY = "groq_api_key"
    private const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
    private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
    private const val KEY_MISTRAL_API_KEY = "mistral_api_key"
    private const val KEY_SILICONFLOW_API_KEY = "siliconflow_api_key"
    private const val KEY_TOGETHER_API_KEY = "together_api_key"
    private const val KEY_CEREBRAS_API_KEY = "cerebras_api_key"
    private const val KEY_AI_ROUTING_CASCADE = "ai_routing_cascade"
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
            KEY_ADS_MIN_OPENS to 3L,
            KEY_SAFE_BROWSING to false,
            KEY_OPENAI_API_KEY to "",
            KEY_GEMINI_API_KEY to "",
            KEY_GROQ_API_KEY to "",
            KEY_DEEPSEEK_API_KEY to "",
            KEY_OPENROUTER_API_KEY to "",
            KEY_MISTRAL_API_KEY to "",
            KEY_SILICONFLOW_API_KEY to "",
            KEY_TOGETHER_API_KEY to "",
            KEY_CEREBRAS_API_KEY to "",
            KEY_AI_ROUTING_CASCADE to "GEMINI,GROQ"
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

    fun isSafeBrowsingEnabled(): Boolean {
        return Firebase.remoteConfig.getBoolean(KEY_SAFE_BROWSING)
    }

    fun getOpenaiApiKey(): String = Firebase.remoteConfig.getString(KEY_OPENAI_API_KEY)
    fun getGeminiApiKey(): String = Firebase.remoteConfig.getString(KEY_GEMINI_API_KEY)
    fun getGroqApiKey(): String = Firebase.remoteConfig.getString(KEY_GROQ_API_KEY)
    fun getDeepSeekApiKey(): String = Firebase.remoteConfig.getString(KEY_DEEPSEEK_API_KEY)
    fun getOpenRouterApiKey(): String = Firebase.remoteConfig.getString(KEY_OPENROUTER_API_KEY)
    fun getMistralApiKey(): String = Firebase.remoteConfig.getString(KEY_MISTRAL_API_KEY)
    fun getSiliconFlowApiKey(): String = Firebase.remoteConfig.getString(KEY_SILICONFLOW_API_KEY)
    fun getTogetherApiKey(): String = Firebase.remoteConfig.getString(KEY_TOGETHER_API_KEY)
    fun getCerebrasApiKey(): String = Firebase.remoteConfig.getString(KEY_CEREBRAS_API_KEY)
    
    fun getAiRoutingCascade(): List<String> {
        val raw = Firebase.remoteConfig.getString(KEY_AI_ROUTING_CASCADE)
        return if (raw.isBlank()) listOf("GEMINI") else raw.split(",").map { it.trim().uppercase() }
    }
}

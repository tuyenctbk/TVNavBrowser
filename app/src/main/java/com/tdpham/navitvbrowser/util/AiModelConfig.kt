package com.tdpham.navitvbrowser.util

/**
 * Centralized Model-Agnostic Capability Tiers (Playbook Section 1.A).
 * Never hardcode model names inside UI screens or Activities; reference capability tiers here
 * (with RemoteConfig overrides) so model upgrades happen in one place.
 */
object AiModelConfig {
    /** Tier 1 — Primary Standard model used when quota is healthy (< 80%) and Eco Lite Mode is off. */
    const val DEFAULT_PRIMARY_REASONING_MODEL = "gemini-3.8-flash"

    /** Tier 2 — High-Efficiency Lite model used at >= 80% quota, on 429 rate-limit, or when Eco Lite Mode is on. */
    const val DEFAULT_ECONOMY_LITE_MODEL = "gemini-3.5-flash-lite"

    /** Tier 3 — Zero-Cost Local Deterministic Engine identifier (0 cloud tokens, 100% offline capable). */
    const val LOCAL_DETERMINISTIC_ENGINE = "local-extractive-v3"

    enum class CapabilityTier {
        TIER_1_PRIMARY_CLOUD,
        TIER_2_ECONOMY_LITE,
        TIER_3_LOCAL_ZERO_COST
    }

    fun getPrimaryReasoningModel(): String {
        return RemoteConfigHelper.getAiModelName().ifBlank { DEFAULT_PRIMARY_REASONING_MODEL }
    }

    fun getEconomyLiteModel(): String {
        return RemoteConfigHelper.getAiLiteModelName().ifBlank { DEFAULT_ECONOMY_LITE_MODEL }
    }
}

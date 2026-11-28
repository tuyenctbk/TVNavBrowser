package com.tdpham.navitvbrowser.util

import android.content.Context
import androidx.core.content.edit

object DonationManager {

    private const val PREFS = "donation_prefs"
    private const val KEY_FIRST_INSTALL_TIME = "first_install_time"
    private const val KEY_APP_LAUNCH_COUNT = "app_launch_count"
    private const val KEY_CORE_OP_COUNT = "core_op_count"
    private const val KEY_IS_SUPPORTER = "is_supporter"
    private const val KEY_SUPPORTER_TIER = "supporter_tier"
    private const val KEY_BANNER_DISMISSED_TIME = "banner_dismissed_time"
    private const val KEY_AUTO_PROMPT_DISMISSED_TIME = "auto_prompt_dismissed_time"
    private const val KEY_LAST_MILESTONE_SHOWN = "last_milestone_shown"

    private const val BANNER_COOLDOWN_MS = 14L * 24 * 60 * 60 * 1000 // 14 days
    private const val AUTO_PROMPT_COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    private const val MIN_INSTALL_AGE_MS = 2L * 24 * 60 * 60 * 1000 // 2 days

    fun initOrRecordLaunch(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        prefs.edit {
            if (prefs.getLong(KEY_FIRST_INSTALL_TIME, 0L) == 0L) {
                putLong(KEY_FIRST_INSTALL_TIME, now)
            }
            putInt(KEY_APP_LAUNCH_COUNT, prefs.getInt(KEY_APP_LAUNCH_COUNT, 0) + 1)
        }
    }

    fun recordCoreOperation(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val newCount = prefs.getInt(KEY_CORE_OP_COUNT, 0) + 1
        prefs.edit { putInt(KEY_CORE_OP_COUNT, newCount) }
        return newCount
    }

    fun getCoreOpCount(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CORE_OP_COUNT, 0)
    }

    fun getLaunchCount(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_APP_LAUNCH_COUNT, 0)
    }

    fun isSupporter(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_IS_SUPPORTER, false)
    }

    fun setSupporter(context: Context, isSupporter: Boolean, tier: String = "Supporter") {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_IS_SUPPORTER, isSupporter)
            putString(KEY_SUPPORTER_TIER, tier)
        }
    }

    fun getSupporterTier(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SUPPORTER_TIER, "Supporter") ?: "Supporter"
    }

    private fun meetsSatisfactionSafeguard(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val firstInstall = prefs.getLong(KEY_FIRST_INSTALL_TIME, System.currentTimeMillis())
        val age = System.currentTimeMillis() - firstInstall
        val launches = prefs.getInt(KEY_APP_LAUNCH_COUNT, 0)
        val coreOps = prefs.getInt(KEY_CORE_OP_COUNT, 0)

        // 3+ launches/ops AND 2+ days since install
        return (age >= MIN_INSTALL_AGE_MS) && (launches >= 3 || coreOps >= 3)
    }

    /** Persistent in-browser donation banners are disabled to keep the TV viewport clean. */
    fun shouldShowBanner(context: Context): Boolean = false

    fun dismissBanner(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_BANNER_DISMISSED_TIME, System.currentTimeMillis())
        }
    }

    /** Automatic startup donation popups are disabled; support options live quietly in Settings. */
    fun shouldShowAutoPrompt(context: Context): Boolean = false

    fun dismissAutoPrompt(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_AUTO_PROMPT_DISMISSED_TIME, System.currentTimeMillis())
        }
    }

    /** Interruptive milestone dialogs are disabled so browsing and saving bookmarks stay frictionless. */
    fun checkAndGetMilestone(context: Context, newOpCount: Int): Int? = null
}

package com.tdpham.navitvbrowser.util

import android.app.Activity
import android.content.Context
import androidx.core.content.edit

object SmartPromptHelper {

    private const val PREFS = "prompt_prefs"
    private const val KEY_RATING_SHOWN = "rating_shown"

    private const val PEP_THRESHOLD_RATING = 15

    fun maybeShowNextPrompt(activity: Activity, onShowSupporterPrompt: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) return
        // 1. Priority: Critical Updates
        if (EngagementHelper.canCheckUpdate(activity) && UpdateHelper.maybeShowUpdateDialog(activity)) {
            return
        }

        // 2. Supporter auto-prompt (only if no update prompt was shown)
        if (!DonationManager.isSupporter(activity) && DonationManager.shouldShowAutoPrompt(activity)) {
            DonationManager.dismissAutoPrompt(activity)
            onShowSupporterPrompt?.invoke()
            return
        }

        val pep = EngagementHelper.getPep(activity)
        val days = EngagementHelper.getDaysUsedCount(activity)
        val launches = EngagementHelper.getLaunches(activity)
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val ratingPermanentlyProcessed = prefs.getBoolean(KEY_RATING_SHOWN, false) || RatingHelper.isRatingDismissed(activity)

        // 2. High-Value Play Store Rating Prompt (after verified multi-day engagement)
        if (!ratingPermanentlyProcessed) {
            if (pep >= PEP_THRESHOLD_RATING && days >= 3 && launches >= 5 && RatingHelper.canShowRatingDialog(activity)) {
                RatingHelper.showRatingDialog(activity) {
                    prefs.edit { putBoolean(KEY_RATING_SHOWN, true) }
                }
            }
        }
    }
}

package com.tdpham.navitvbrowser.util

import android.app.Activity
import android.content.Context
import androidx.core.content.edit

object SmartPromptHelper {

    private const val PREFS = "prompt_prefs"
    private const val KEY_RATING_SHOWN = "rating_shown"
    private const val KEY_SHARE_SHOWN = "share_shown"

    private const val PEP_THRESHOLD_RATING = 15
    private const val PEP_THRESHOLD_SHARE = 25

    fun maybeShowNextPrompt(activity: Activity) {
        // 1. Priority: Critical Updates
        if (EngagementHelper.canCheckUpdate(activity) && UpdateHelper.maybeShowUpdateDialog(activity)) {
            return
        }

        val pep = EngagementHelper.getPep(activity)
        val days = EngagementHelper.getDaysUsedCount(activity)
        val launches = EngagementHelper.getLaunches(activity)
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val ratingPermanentlyProcessed = prefs.getBoolean(KEY_RATING_SHOWN, false) || RatingHelper.isRatingDismissed(activity)

        // 2. Rating Prompt
        if (!ratingPermanentlyProcessed) {
            if (pep >= PEP_THRESHOLD_RATING && days >= 3 && launches >= 5 && RatingHelper.canShowRatingDialog(activity)) {
                RatingHelper.showRatingDialog(activity) {
                    prefs.edit { putBoolean(KEY_RATING_SHOWN, true) }
                }
                return
            }
        }

        // 3. Share Prompt (after rating has been processed)
        if (!prefs.getBoolean(KEY_SHARE_SHOWN, false) && ratingPermanentlyProcessed) {
            if (pep >= PEP_THRESHOLD_SHARE) {
                ShareHelper.showShareDialog(activity)
                prefs.edit { putBoolean(KEY_SHARE_SHOWN, true) }
            }
        }
    }
}

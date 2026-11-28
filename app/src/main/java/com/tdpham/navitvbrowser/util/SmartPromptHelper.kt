package com.tdpham.navitvbrowser.util

import android.app.Activity
import android.content.Context
import androidx.core.content.edit

object SmartPromptHelper {

    private const val PREFS = "prompt_prefs"
    private const val KEY_RATING_SHOWN = "rating_shown"

    private const val PEP_THRESHOLD_RATING = 35
    private const val MIN_DAYS_FOR_RATING = 7
    private const val MIN_LAUNCHES_FOR_RATING = 10
    private const val MIN_LAUNCHES_FOR_UPDATE = 3

    fun maybeShowNextPrompt(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        val launches = EngagementHelper.getLaunches(activity)

        // 1. Optional Update notice (only after user has used the app across multiple sessions)
        if (launches >= MIN_LAUNCHES_FOR_UPDATE &&
            EngagementHelper.canCheckUpdate(activity) &&
            UpdateHelper.maybeShowUpdateDialog(activity)
        ) {
            return
        }

        val pep = EngagementHelper.getPep(activity)
        val days = EngagementHelper.getDaysUsedCount(activity)
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val ratingPermanentlyProcessed =
            prefs.getBoolean(KEY_RATING_SHOWN, false) || RatingHelper.isRatingDismissed(activity)

        // 2. Non-intrusive Play Store Rating prompt only after sustained multi-week engagement
        if (!ratingPermanentlyProcessed &&
            pep >= PEP_THRESHOLD_RATING &&
            days >= MIN_DAYS_FOR_RATING &&
            launches >= MIN_LAUNCHES_FOR_RATING &&
            RatingHelper.canShowRatingDialog(activity)
        ) {
            RatingHelper.showRatingDialog(activity) {
                prefs.edit { putBoolean(KEY_RATING_SHOWN, true) }
            }
        }
    }
}

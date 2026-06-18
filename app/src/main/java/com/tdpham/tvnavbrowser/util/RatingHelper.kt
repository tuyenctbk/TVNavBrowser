package com.tdpham.tvnavbrowser.util

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory
import com.tdpham.tvnavbrowser.R

object RatingHelper {

    private const val PREFS = "tvnav_prefs"
    private const val KEY_LAUNCH_COUNT = "launch_count"
    private const val KEY_DONT_SHOW_AGAIN = "dont_show_again"
    private const val LAUNCHES_UNTIL_PROMPT = 5

    fun maybeShowRating(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONT_SHOW_AGAIN, false)) return

        val launches = prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_LAUNCH_COUNT, launches).apply()

        if (launches >= LAUNCHES_UNTIL_PROMPT) {
            showRatingDialog(activity, prefs)
        }
    }

    private fun showRatingDialog(activity: Activity, prefs: SharedPreferences) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.rating_title)
            .setMessage(R.string.rating_message)
            .setPositiveButton(R.string.rating_rate) { dialog, _ ->
                dialog.dismiss()
                prefs.edit().putBoolean(KEY_DONT_SHOW_AGAIN, true).apply()
                launchInAppReview(activity)
            }
            .setNegativeButton(R.string.rating_remind_later) { dialog, _ ->
                dialog.dismiss()
                prefs.edit().putInt(KEY_LAUNCH_COUNT, 0).apply()
            }
            .setNeutralButton(R.string.rating_no_thanks) { dialog, _ ->
                dialog.dismiss()
                prefs.edit().putBoolean(KEY_DONT_SHOW_AGAIN, true).apply()
            }
            .create()
            .show()
    }

    private fun launchInAppReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                manager.launchReviewFlow(activity, task.result)
            } else {
                openPlayStore(activity)
            }
        }
    }

    private fun openPlayStore(activity: Activity) {
        val packageName = activity.packageName
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName")
        )
        if (marketIntent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(marketIntent)
        } else {
            activity.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
            )
        }
    }
}

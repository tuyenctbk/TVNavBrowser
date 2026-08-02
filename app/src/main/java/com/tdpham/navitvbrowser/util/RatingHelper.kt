package com.tdpham.navitvbrowser.util

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory
import com.tdpham.navitvbrowser.R

object RatingHelper {

    private const val PREFS = "tvnav_prefs"
    private const val KEY_DONT_SHOW_AGAIN = "dont_show_again"
    private const val KEY_LAST_REMIND_TIME = "last_rating_remind_time"

    fun isRatingDismissed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DONT_SHOW_AGAIN, false)
    }

    fun canShowRatingDialog(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONT_SHOW_AGAIN, false)) return false
        val lastRemind = prefs.getLong(KEY_LAST_REMIND_TIME, 0L)
        val now = System.currentTimeMillis()
        return (now - lastRemind >= 3 * 24 * 60 * 60 * 1000L)
    }

    fun showRatingDialog(activity: Activity, onDismissedPermanently: (() -> Unit)? = null) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONT_SHOW_AGAIN, false)) {
            onDismissedPermanently?.invoke()
            return
        }

        AlertDialog.Builder(activity, R.style.Theme_TVNavBrowser_Dialog)
            .setTitle(R.string.rating_title)
            .setMessage(R.string.rating_message)
            .setPositiveButton(R.string.rating_rate) { dialog, _ ->
                dialog.dismiss()
                prefs.edit().putBoolean(KEY_DONT_SHOW_AGAIN, true).apply()
                onDismissedPermanently?.invoke()
                launchInAppReview(activity)
            }
            .setNegativeButton(R.string.rating_remind_later) { dialog, _ ->
                dialog.dismiss()
                prefs.edit().putLong(KEY_LAST_REMIND_TIME, System.currentTimeMillis()).apply()
            }
            .setNeutralButton(R.string.rating_no_thanks) { dialog, _ ->
                dialog.dismiss()
                prefs.edit().putBoolean(KEY_DONT_SHOW_AGAIN, true).apply()
                onDismissedPermanently?.invoke()
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

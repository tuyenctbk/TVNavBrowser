package com.tdpham.navitvbrowser.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import com.tdpham.navitvbrowser.R

object UpdateHelper {

    private const val PREFS = "update_prefs"
    private const val KEY_LAST_CHECKED_VERSION = "last_suggested_version"

    fun maybeShowUpdateDialog(activity: Activity) {
        val latestVersion = RemoteConfigHelper.getLatestVersionCode()
        val currentVersion = com.tdpham.navitvbrowser.BuildConfig.VERSION_CODE

        if (latestVersion > currentVersion) {
            val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastSuggested = prefs.getInt(KEY_LAST_CHECKED_VERSION, 0)

            if (latestVersion > lastSuggested) {
                showDialog(activity, latestVersion)
            }
        }
    }

    private fun showDialog(activity: Activity, latestVersion: Int) {
        AlertDialog.Builder(activity, R.style.Theme_TVNavBrowser_Dialog)
            .setTitle(R.string.update_available_title)
            .setMessage(R.string.update_available_message)
            .setPositiveButton(R.string.update_now) { _, _ ->
                val updateUrl = RemoteConfigHelper.getUpdateUrl()
                val url = updateUrl.ifBlank { "market://details?id=${activity.packageName}" }
                
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (_: Exception) {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=${activity.packageName}".toUri()))
                }
            }
            .setNegativeButton(R.string.update_later) { dialog, _ ->
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                    putInt(KEY_LAST_CHECKED_VERSION, latestVersion)
                }
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
}

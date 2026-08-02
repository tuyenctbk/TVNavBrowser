package com.tdpham.navitvbrowser.util

import android.content.Context
import androidx.core.content.edit

object EngagementHelper {

    private const val PREFS = "engagement_prefs"
    private const val KEY_PEP = "positive_engagement_points"
    private const val KEY_LAUNCHES = "launch_count"
    private const val KEY_FIRST_INSTALL_TIME = "first_install_time"
    private const val KEY_LAST_DAY_USED = "last_day_used"
    private const val KEY_DAYS_USED_COUNT = "days_used_count"
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check"

    fun recordLaunch(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        
        prefs.edit {
            putInt(KEY_LAUNCHES, prefs.getInt(KEY_LAUNCHES, 0) + 1)
            if (prefs.getLong(KEY_FIRST_INSTALL_TIME, 0L) == 0L) {
                putLong(KEY_FIRST_INSTALL_TIME, now)
            }
            
            val lastDay = prefs.getLong(KEY_LAST_DAY_USED, 0L)
            val currentDay = now / (24 * 60 * 60 * 1000)
            if (currentDay > lastDay) {
                putLong(KEY_LAST_DAY_USED, currentDay)
                putInt(KEY_DAYS_USED_COUNT, prefs.getInt(KEY_DAYS_USED_COUNT, 0) + 1)
            }
        }
    }

    fun recordAiSummary(context: Context) = addPep(context, 5)
    fun recordBookmarkAdded(context: Context) = addPep(context, 3)
    fun recordPageLoaded(context: Context) = addPep(context, 1)

    private fun addPep(context: Context, points: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putInt(KEY_PEP, prefs.getInt(KEY_PEP, 0) + points)
        }
    }

    fun getPep(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_PEP, 0)
    fun getLaunches(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_LAUNCHES, 0)
    fun getDaysUsedCount(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_DAYS_USED_COUNT, 0)

    fun canCheckUpdate(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        val now = System.currentTimeMillis()
        return (now - lastCheck > 24 * 60 * 60 * 1000L)
    }

    fun recordUpdateCheck(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()) }
    }
}

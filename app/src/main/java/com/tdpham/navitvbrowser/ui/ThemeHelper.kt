package com.tdpham.navitvbrowser.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tdpham.navitvbrowser.R
import com.tdpham.navitvbrowser.util.AppPreferences

object ThemeHelper {

    fun isLightTheme(context: Context): Boolean = AppPreferences.isLightTheme(context)

    fun applyTheme(activity: Activity) {
        if (isLightTheme(activity)) {
            activity.setTheme(R.style.Theme_TVNavBrowser_Light)
        } else {
            activity.setTheme(R.style.Theme_TVNavBrowser)
        }
    }

    fun getDialogTheme(context: Context): Int {
        return if (isLightTheme(context)) {
            R.style.Theme_TVNavBrowser_Light_Dialog
        } else {
            R.style.Theme_TVNavBrowser_Dialog
        }
    }

    fun getScreenBackground(context: Context): Int {
        return if (isLightTheme(context)) R.drawable.bg_screen_light else R.drawable.bg_screen
    }

    fun getToolbarBackground(context: Context): Int {
        return if (isLightTheme(context)) R.drawable.bg_toolbar_light else R.drawable.bg_toolbar
    }

    fun getUrlBarBackground(context: Context): Int {
        return if (isLightTheme(context)) R.drawable.bg_url_bar_light else R.drawable.bg_url_bar
    }

    fun getToolbarButtonBackground(context: Context): Int {
        return if (isLightTheme(context)) R.drawable.bg_toolbar_btn_light else R.drawable.bg_toolbar_btn
    }

    fun getFocusableItemBackground(context: Context): Int {
        return if (isLightTheme(context)) R.drawable.focusable_item_background_light else R.drawable.focusable_item_background
    }

    fun getOverlayCardBackground(context: Context): Int {
        return if (isLightTheme(context)) R.drawable.bg_overlay_card_light else R.drawable.bg_overlay_card
    }

    fun getTextPrimaryColor(context: Context): Int {
        return ContextCompat.getColor(
            context,
            if (isLightTheme(context)) R.color.light_text_primary else R.color.text_primary
        )
    }

    fun getTextSecondaryColor(context: Context): Int {
        return ContextCompat.getColor(
            context,
            if (isLightTheme(context)) R.color.light_text_secondary else R.color.text_secondary
        )
    }

    fun getAccentColor(context: Context): Int {
        return ContextCompat.getColor(
            context,
            if (isLightTheme(context)) R.color.light_accent else R.color.accent
        )
    }

    fun applyActivityThemeView(
        activity: Activity,
        rootView: View?,
        titleView: TextView? = null,
        subtitleView: TextView? = null
    ) {
        val isLight = isLightTheme(activity)
        rootView?.setBackgroundResource(if (isLight) R.drawable.bg_screen_light else R.drawable.bg_screen)
        titleView?.setTextColor(getAccentColor(activity))
        subtitleView?.setTextColor(getTextSecondaryColor(activity))
    }
}

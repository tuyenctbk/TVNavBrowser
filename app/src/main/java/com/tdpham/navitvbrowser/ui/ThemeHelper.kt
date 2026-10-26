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

    fun getSettingsSectionBackground(context: Context): Int {
        return if (isLightTheme(context)) R.drawable.bg_settings_section_light else R.drawable.bg_settings_section
    }

    fun getStepBadgeBackground(context: Context): Int {
        return if (isLightTheme(context)) R.drawable.bg_step_badge_light else R.drawable.bg_step_badge
    }

    fun applyActivityThemeView(
        activity: Activity,
        rootView: View?,
        titleView: TextView? = null,
        subtitleView: TextView? = null
    ) {
        val isLight = isLightTheme(activity)
        val screenBg = if (isLight) R.drawable.bg_screen_light else R.drawable.bg_screen
        rootView?.setBackgroundResource(screenBg)
        titleView?.setTextColor(getTextPrimaryColor(activity))
        subtitleView?.setTextColor(getTextSecondaryColor(activity))
        if (rootView is android.view.ViewGroup) {
            for (i in 0 until rootView.childCount) {
                val child = rootView.getChildAt(i)
                if (child is android.widget.ScrollView || child is androidx.core.widget.NestedScrollView || child is android.widget.FrameLayout) {
                    child.setBackgroundResource(screenBg)
                }
            }
            applyThemeRecursively(activity, rootView, isLight)
        }
    }

    private fun applyThemeRecursively(context: Context, viewGroup: android.view.ViewGroup, isLight: Boolean) {
        viewGroup.clipChildren = false
        viewGroup.clipToPadding = false
        val textPrimary = getTextPrimaryColor(context)
        val textSecondary = getTextSecondaryColor(context)
        val accent = getAccentColor(context)
        val focusBg = getFocusableItemBackground(context)
        val btnBg = getToolbarButtonBackground(context)
        val urlBg = getUrlBarBackground(context)
        val screenBg = if (isLight) R.drawable.bg_screen_light else R.drawable.bg_screen

        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            when (child) {
                is android.widget.ScrollView -> {
                    child.clipChildren = false
                    child.clipToPadding = false
                    child.isFocusable = false
                    child.isFocusableInTouchMode = false
                    child.setBackgroundResource(screenBg)
                    applyThemeRecursively(context, child, isLight)
                }
                is androidx.core.widget.NestedScrollView -> {
                    child.clipChildren = false
                    child.clipToPadding = false
                    child.isFocusable = false
                    child.isFocusableInTouchMode = false
                    child.setBackgroundResource(screenBg)
                    applyThemeRecursively(context, child, isLight)
                }
                is android.widget.Button -> {
                    child.setBackgroundResource(btnBg)
                    child.setTextColor(textPrimary)
                }
                is EditText -> {
                    child.setBackgroundResource(urlBg)
                    child.setTextColor(textPrimary)
                    child.setHintTextColor(textSecondary)
                }
                is android.widget.Switch -> {
                    child.setTextColor(textPrimary)
                }
                is android.view.ViewGroup -> {
                    child.clipChildren = false
                    child.clipToPadding = false
                    if (child.isFocusable && child.isClickable && child !is androidx.recyclerview.widget.RecyclerView) {
                        child.setBackgroundResource(focusBg)
                    }
                    applyThemeRecursively(context, child, isLight)
                }
                is TextView -> {
                    val currentColors = child.textColors
                    if (child.currentTextColor == ContextCompat.getColor(context, R.color.accent) ||
                        child.currentTextColor == ContextCompat.getColor(context, R.color.light_accent)
                    ) {
                        child.setTextColor(accent)
                    } else if (child.currentTextColor == ContextCompat.getColor(context, R.color.text_primary) ||
                        child.currentTextColor == ContextCompat.getColor(context, R.color.light_text_primary) ||
                        child.currentTextColor == ContextCompat.getColor(context, R.color.white)
                    ) {
                        child.setTextColor(textPrimary)
                    } else if (child.currentTextColor == ContextCompat.getColor(context, R.color.text_secondary) ||
                        child.currentTextColor == ContextCompat.getColor(context, R.color.light_text_secondary)
                    ) {
                        child.setTextColor(textSecondary)
                    }
                }
                is ImageView -> {
                    if (child.imageTintList != null) {
                        val tintColor = child.imageTintList?.defaultColor
                        if (tintColor == ContextCompat.getColor(context, R.color.accent) ||
                            tintColor == ContextCompat.getColor(context, R.color.light_accent)
                        ) {
                            child.imageTintList = ColorStateList.valueOf(accent)
                        } else if (tintColor == ContextCompat.getColor(context, R.color.text_primary) ||
                            tintColor == ContextCompat.getColor(context, R.color.light_text_primary) ||
                            tintColor == ContextCompat.getColor(context, R.color.white)
                        ) {
                            child.imageTintList = ColorStateList.valueOf(textPrimary)
                        } else if (tintColor == ContextCompat.getColor(context, R.color.text_secondary) ||
                            tintColor == ContextCompat.getColor(context, R.color.light_text_secondary)
                        ) {
                            child.imageTintList = ColorStateList.valueOf(textSecondary)
                        }
                    }
                }
            }
        }
    }
}

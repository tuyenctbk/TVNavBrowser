package com.tdpham.navitvbrowser.ui

import android.view.View
import android.view.animation.OvershootInterpolator

object FocusAnimationHelper {

    private const val FOCUSED_SCALE = 1.03f
    private const val ANIM_DURATION_MS = 160L
    private val overshootInterpolator = OvershootInterpolator(1.15f)

    fun apply(
        view: View,
        focusedScale: Float = FOCUSED_SCALE,
        onFocusChange: ((View, Boolean) -> Unit)? = null
    ) {
        view.setOnFocusChangeListener { v, hasFocus ->
            val target = if (hasFocus) focusedScale else 1f
            
            // Cancel any ongoing animations on the view
            v.animate().cancel()

            v.animate()
                .scaleX(target)
                .scaleY(target)
                .setInterpolator(if (hasFocus) overshootInterpolator else null)
                .setDuration(ANIM_DURATION_MS)
                .start()

            v.elevation = if (hasFocus) 6f else 0f
            v.translationZ = if (hasFocus) 4f else 0f
            onFocusChange?.invoke(v, hasFocus)
        }
    }

    fun applyAll(vararg views: View) {
        views.forEach { apply(it) }
    }
}


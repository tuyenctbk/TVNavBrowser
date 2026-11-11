package com.tdpham.navitvbrowser.ui

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object FocusAnimationHelper {

    private const val FOCUSED_SCALE = 1.07f
    private const val ANIM_DURATION_IN_MS = 175L
    private const val ANIM_DURATION_OUT_MS = 130L
    private val overshootInterpolator = OvershootInterpolator(1.35f)
    private val decelerateInterpolator = DecelerateInterpolator()

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
                .setInterpolator(if (hasFocus) overshootInterpolator else decelerateInterpolator)
                .setDuration(if (hasFocus) ANIM_DURATION_IN_MS else ANIM_DURATION_OUT_MS)
                .start()

            v.elevation = if (hasFocus) 10f else 0f
            v.translationZ = if (hasFocus) 6f else 0f
            onFocusChange?.invoke(v, hasFocus)
        }
    }

    fun applyAll(vararg views: View) {
        views.forEach { apply(it) }
    }
}

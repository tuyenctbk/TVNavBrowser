package com.tdpham.navitvbrowser.ui

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object FocusAnimationHelper {

    private const val FOCUSED_SCALE = 1.07f
    private const val PRESSED_SCALE = 0.96f
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

        view.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (event.repeatCount == 0) {
                            v.animate().scaleX(PRESSED_SCALE).scaleY(PRESSED_SCALE).setDuration(75L).start()
                        }
                    }
                    KeyEvent.ACTION_UP -> {
                        val restScale = if (v.hasFocus()) focusedScale else 1f
                        v.animate().scaleX(restScale).scaleY(restScale).setInterpolator(overshootInterpolator).setDuration(130L).start()
                    }
                }
            }
            false
        }
    }

    fun applyAll(vararg views: View) {
        views.forEach { apply(it) }
    }

    fun animateEntrance(view: View, index: Int) {
        if (index > 10) return
        view.alpha = 0f
        view.translationY = 18f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(index * 28L)
            .setDuration(210L)
            .setInterpolator(decelerateInterpolator)
            .start()
    }
}

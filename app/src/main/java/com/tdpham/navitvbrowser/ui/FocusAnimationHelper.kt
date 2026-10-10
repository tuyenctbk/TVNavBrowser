package com.tdpham.navitvbrowser.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.tdpham.navitvbrowser.R

object FocusAnimationHelper {

    private const val FOCUSED_SCALE = 1.08f
    private const val ANIM_DURATION_MS = 170L
    private val overshootInterpolator = OvershootInterpolator(1.25f)

    fun apply(
        view: View,
        focusedScale: Float = FOCUSED_SCALE,
        onFocusChange: ((View, Boolean) -> Unit)? = null
    ) {
        view.setOnFocusChangeListener { v, hasFocus ->
            val target = if (hasFocus) focusedScale else 1f
            
            // Cancel any ongoing animations on the view
            v.animate().cancel()
            val existingGlow = v.getTag(R.id.tag_glow_animator) as? ValueAnimator
            existingGlow?.cancel()
            v.setTag(R.id.tag_glow_animator, null)

            v.animate()
                .scaleX(target)
                .scaleY(target)
                .translationZ(if (hasFocus) 10f else 0f)
                .setInterpolator(if (hasFocus) overshootInterpolator else null)
                .setDuration(ANIM_DURATION_MS)
                .withEndAction {
                    if (hasFocus && v.isAttachedToWindow) {
                        // Subtle breathing glow / scale pulse while remaining focused
                        val pulse = ValueAnimator.ofFloat(focusedScale, focusedScale + 0.025f).apply {
                            duration = 850
                            repeatMode = ValueAnimator.REVERSE
                            repeatCount = ValueAnimator.INFINITE
                            interpolator = AccelerateDecelerateInterpolator()
                            addUpdateListener { anim ->
                                if (!v.isAttachedToWindow || !v.hasFocus()) {
                                    anim.cancel()
                                    return@addUpdateListener
                                }
                                val s = anim.animatedValue as Float
                                v.scaleX = s
                                v.scaleY = s
                            }
                        }
                        v.setTag(R.id.tag_glow_animator, pulse)
                        pulse.start()
                    }
                }
                .start()

            v.elevation = if (hasFocus) 16f else 0f
            onFocusChange?.invoke(v, hasFocus)
        }
    }

    fun applyAll(vararg views: View) {
        views.forEach { apply(it) }
    }
}


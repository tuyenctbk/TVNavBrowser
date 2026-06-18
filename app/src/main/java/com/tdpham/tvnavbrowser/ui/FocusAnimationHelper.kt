package com.tdpham.tvnavbrowser.ui

import android.view.View

object FocusAnimationHelper {

  private const val FOCUSED_SCALE = 1.06f
  private const val ANIM_DURATION_MS = 140L

  fun apply(view: View, focusedScale: Float = FOCUSED_SCALE) {
    view.setOnFocusChangeListener { v, hasFocus ->
      val target = if (hasFocus) focusedScale else 1f
      v.animate()
        .scaleX(target)
        .scaleY(target)
        .setDuration(ANIM_DURATION_MS)
        .start()
      v.elevation = if (hasFocus) 10f else 0f
    }
  }

  fun applyAll(vararg views: View) {
    views.forEach { apply(it) }
  }
}

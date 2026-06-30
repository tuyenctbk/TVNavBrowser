package com.tdpham.navitvbrowser.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.webkit.WebView

/**
 * D-pad driven virtual cursor for browsing when a physical mouse is not available.
 */
class VirtualCursorController(
    private val container: View,
    private val cursor: ImageView,
    private val webView: WebView,
    private val speed: Float = 32f
) {
    var isEnabled: Boolean = false
        private set

    var onCursorActivity: (() -> Unit)? = null
    var onExitUp: (() -> Unit)? = null

    private var pulseAnimator: ObjectAnimator? = null

    fun setEnabled(enabled: Boolean) {
        if (isEnabled == enabled) return
        isEnabled = enabled
        if (enabled) {
            cursor.visibility = View.VISIBLE
            centerCursor()
            startPulse()
            webView.requestFocus()
        } else {
            cursor.visibility = View.GONE
            stopPulse()
        }
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (!isEnabled) return false
        
        val keyCode = event.keyCode
        val repeatCount = event.repeatCount
        
        // Acceleration: ramp up speed by 25% per repeat, capping at 3.5x
        val accel = (1f + (repeatCount * 0.25f)).coerceAtMost(3.5f)
        val currentSpeed = speed * accel

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                move(0f, -currentSpeed)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                move(0f, currentSpeed)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                move(-currentSpeed, 0f)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                move(currentSpeed, 0f)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X -> {
                simulateClick()
                true
            }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                webView.pageUp(false)
                true
            }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                webView.pageDown(false)
                true
            }
            else -> false
        }
    }

    fun hideForPhysicalPointer() {
        if (isEnabled) {
            cursor.visibility = View.GONE
        }
    }

    fun showIfEnabled() {
        if (isEnabled) {
            cursor.visibility = View.VISIBLE
        }
    }

    fun simulateClick() {
        val x = cursor.x
        val y = cursor.y
        val downTime = SystemClock.uptimeMillis()

        val wasVisible = cursor.visibility == View.VISIBLE
        if (wasVisible) {
            cursor.visibility = View.INVISIBLE
        }

        val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val upEvent = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, x, y, 0)

        container.dispatchTouchEvent(downEvent)
        container.dispatchTouchEvent(upEvent)

        downEvent.recycle()
        upEvent.recycle()

        if (wasVisible) {
            cursor.visibility = View.VISIBLE
        }

        cursor.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
            cursor.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        }.start()

        onCursorActivity?.invoke()
    }

    fun move(dx: Float, dy: Float) {
        container.post {
            val maxX = (container.width - cursor.width).coerceAtLeast(0).toFloat()
            val maxY = (container.height - cursor.height).coerceAtLeast(0).toFloat()
            
            val oldY = cursor.y
            cursor.x = (cursor.x + dx).coerceIn(0f, maxX)
            cursor.y = (cursor.y + dy).coerceIn(0f, maxY)

            // Edge Scrolling: If cursor is at top/bottom limit, scroll the webpage
            if (cursor.y <= 0f && dy < 0) {
                if (webView.canScrollVertically(-1)) {
                    webView.scrollBy(0, dy.toInt() * 2)
                } else {
                    onExitUp?.invoke()
                }
            } else if (cursor.y >= maxY && dy > 0) {
                webView.scrollBy(0, dy.toInt() * 2)
            }

            onCursorActivity?.invoke()
        }
    }

    private fun centerCursor() {
        container.post {
            if (container.width == 0 || container.height == 0) return@post
            cursor.x = container.width / 2f - cursor.width / 2f
            cursor.y = container.height / 2f - cursor.height / 2f
        }
    }

    private fun startPulse() {
        stopPulse()
        pulseAnimator = ObjectAnimator.ofFloat(cursor, View.ALPHA, 1f, 0.65f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        cursor.alpha = 1f
    }
}

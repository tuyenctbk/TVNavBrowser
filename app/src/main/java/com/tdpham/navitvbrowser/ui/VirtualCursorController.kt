package com.tdpham.navitvbrowser.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.widget.ImageView

/**
 * High-performance, 60fps smooth virtual cursor for Android TV browsing.
 * Uses a VSYNC-synchronized frame loop to completely eliminate initial key-repeat
 * delay freezes and remote packet-drop stuttering.
 */
class VirtualCursorController(
    private val container: View,
    private val cursor: ImageView,
    private val webView: WebView,
    private val baseSpeed: Float = 10f,
    private val maxSpeed: Float = 28f
) : Choreographer.FrameCallback {

    var isEnabled: Boolean = false
        private set

    var onCursorActivity: (() -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isFrameCallbackPosted = false

    private var holdingUp = false
    private var holdingDown = false
    private var holdingLeft = false
    private var holdingRight = false

    private var pressStartTime = 0L
    private var lastEventTime = 0L

    init {
        cursor.isClickable = false
        cursor.isFocusable = false
    }

    fun setEnabled(enabled: Boolean) {
        if (isEnabled == enabled) return
        isEnabled = enabled
        if (enabled) {
            cursor.visibility = View.VISIBLE
            cursor.alpha = 1f
            centerCursor()
            webView.requestFocus()
        } else {
            stopMotion()
            cursor.visibility = View.GONE
        }
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (!isEnabled) return false

        val keyCode = event.keyCode
        val action = event.action

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    holdingUp = true
                    onDirectionPressed()
                } else if (action == KeyEvent.ACTION_UP) {
                    holdingUp = false
                    onDirectionReleased()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    holdingDown = true
                    onDirectionPressed()
                } else if (action == KeyEvent.ACTION_UP) {
                    holdingDown = false
                    onDirectionReleased()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    holdingLeft = true
                    onDirectionPressed()
                } else if (action == KeyEvent.ACTION_UP) {
                    holdingLeft = false
                    onDirectionReleased()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    holdingRight = true
                    onDirectionPressed()
                } else if (action == KeyEvent.ACTION_UP) {
                    holdingRight = false
                    onDirectionReleased()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X -> {
                if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    simulateClick()
                }
                return true
            }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    webView.pageUp(false)
                }
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    webView.pageDown(false)
                }
                return true
            }
        }
        return false
    }

    private fun onDirectionPressed() {
        val now = SystemClock.uptimeMillis()
        if (pressStartTime == 0L) {
            pressStartTime = now
        }
        lastEventTime = now
        startFrameLoop()
    }

    private fun onDirectionReleased() {
        lastEventTime = SystemClock.uptimeMillis()
        if (!holdingUp && !holdingDown && !holdingLeft && !holdingRight) {
            pressStartTime = 0L
            stopFrameLoop()
        }
    }

    private fun startFrameLoop() {
        if (!isFrameCallbackPosted) {
            isFrameCallbackPosted = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun stopFrameLoop() {
        if (isFrameCallbackPosted) {
            Choreographer.getInstance().removeFrameCallback(this)
            isFrameCallbackPosted = false
        }
    }

    private fun stopMotion() {
        holdingUp = false
        holdingDown = false
        holdingLeft = false
        holdingRight = false
        pressStartTime = 0L
        stopFrameLoop()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isEnabled || !isFrameCallbackPosted) return

        val now = SystemClock.uptimeMillis()
        // Safety watchdog: In case a Bluetooth/IR remote dropped ACTION_UP, stop after 800ms of silence
        // (Allows for standard Android TV 400-500ms initial key-repeat timeouts)
        if (now - lastEventTime > 800L && lastEventTime > 0L) {
            stopMotion()
            return
        }

        var dirX = 0f
        var dirY = 0f
        if (holdingLeft) dirX -= 1f
        if (holdingRight) dirX += 1f
        if (holdingUp) dirY -= 1f
        if (holdingDown) dirY += 1f

        if (dirX == 0f && dirY == 0f) {
            stopMotion()
            return
        }

        // Normalize diagonal speed
        if (dirX != 0f && dirY != 0f) {
            dirX *= 0.7071f
            dirY *= 0.7071f
        }

        // Smooth acceleration based on duration of continuous holding
        val duration = (now - pressStartTime).coerceAtLeast(0L)
        val rampProgress = (duration / 400f).coerceIn(0f, 1f)
        val currentSpeed = baseSpeed + (maxSpeed - baseSpeed) * rampProgress

        val dx = dirX * currentSpeed
        val dy = dirY * currentSpeed

        applyMove(dx, dy, duration)

        // Schedule next frame
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun move(dx: Float, dy: Float) {
        applyMove(dx, dy, 0L)
    }

    private fun applyMove(dx: Float, dy: Float, holdDurationMs: Long) {
        if (container.width <= 0 || container.height <= 0) return

        val maxX = (container.width - cursor.width).coerceAtLeast(0).toFloat()
        val maxY = (container.height - cursor.height).coerceAtLeast(0).toFloat()

        cursor.x = (cursor.x + dx).coerceIn(0f, maxX)
        cursor.y = (cursor.y + dy).coerceIn(0f, maxY)

        // Smooth Edge Scrolling
        val scrollMultiplier = (1.5f + (holdDurationMs / 300f)).coerceIn(1.5f, 5.0f)
        val scrollDeltaY = (dy * scrollMultiplier).toInt()
        val scrollDeltaX = (dx * scrollMultiplier).toInt()

        if (cursor.y <= 0f && dy < 0) {
            if (webView.canScrollVertically(-1)) {
                webView.scrollBy(0, scrollDeltaY)
            }
        } else if (cursor.y >= maxY && dy > 0) {
            if (webView.canScrollVertically(1)) {
                webView.scrollBy(0, scrollDeltaY)
            }
        }

        if (cursor.x <= 0f && dx < 0 && webView.canScrollHorizontally(-1)) {
            webView.scrollBy(scrollDeltaX, 0)
        } else if (cursor.x >= maxX && dx > 0 && webView.canScrollHorizontally(1)) {
            webView.scrollBy(scrollDeltaX, 0)
        }

        onCursorActivity?.invoke()
    }

    fun hideForPhysicalPointer() {
        if (isEnabled) {
            stopMotion()
            cursor.visibility = View.GONE
        }
    }

    fun showIfEnabled() {
        if (isEnabled) {
            cursor.visibility = View.VISIBLE
        }
    }

    fun simulateClick() {
        val x = cursor.x + 4f
        val y = cursor.y + 4f
        val downTime = SystemClock.uptimeMillis()

        val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        }
        container.dispatchTouchEvent(downEvent)
        downEvent.recycle()

        // 40ms touch contact window for Chromium Blink gesture detection to register clean tap
        mainHandler.postDelayed({
            val upTime = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0).apply {
                source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            }
            container.dispatchTouchEvent(upEvent)
            upEvent.recycle()

            webView.requestFocus()
        }, 40L)

        cursor.animate().scaleX(0.78f).scaleY(0.78f).setDuration(60).withEndAction {
            cursor.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        }.start()

        onCursorActivity?.invoke()
    }

    fun centerCursor() {
        val performCenter = Runnable {
            if (container.width > 0 && container.height > 0) {
                cursor.x = container.width / 2f - cursor.width / 2f
                cursor.y = container.height / 2f - cursor.height / 2f
            }
        }
        if (container.width > 0 && container.height > 0) {
            performCenter.run()
        } else {
            container.post(performCenter)
        }
    }
}

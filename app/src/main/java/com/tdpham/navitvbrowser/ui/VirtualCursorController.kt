package com.tdpham.navitvbrowser.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Smooth, time-based virtual pointer for Android TV remotes and gamepads.
 *
 * Design goals (driven by user feedback: "mouse is clunky", "pointer gets stuck", "can't click"):
 *  - A short tap moves a small, predictable step for precise aiming; holding glides with an eased
 *    acceleration that is frame-rate independent (TVs run at 50/60/120 Hz).
 *  - The click hotspot is the visible arrow tip and can reach every pixel of the screen, including
 *    the right/bottom edges that the old clamp made unreachable.
 *  - Edge scrolling works on pages whose content scrolls inside an inner element (SPAs, YouTube,
 *    game portals) by falling back to synthetic mouse-wheel events at the pointer position.
 *  - Analog sticks drive the pointer continuously even while the stick is held still.
 *  - Long-pressing OK is exposed as a separate action (used to toggle game controls).
 */
class VirtualCursorController(
    private val container: View,
    private val cursor: ImageView,
    private val webView: WebView,
    private val clickRipple: View? = null
) : Choreographer.FrameCallback {

    companion object {
        /** Arrow tip position inside ic_cursor_pointer (24x24 viewport). */
        private const val HOTSPOT_X_FRACTION = 4f / 24f
        private const val HOTSPOT_Y_FRACTION = 2f / 24f

        private const val TAP_STEP_DP = 9f
        private const val HOLD_DELAY_MS = 130L
        private const val RAMP_MS = 750f
        private const val MIN_SPEED_DP_PER_S = 340f
        private const val MAX_SPEED_DP_PER_S = 1500f
        private const val JOYSTICK_MAX_SPEED_DP_PER_S = 1300f
        private const val JOYSTICK_DEADZONE = 0.18f

        private const val LONG_PRESS_MS = 650L
        private const val IDLE_FADE_DELAY_MS = 6000L
        private const val IDLE_ALPHA = 0.35f
        private const val HOVER_INTERVAL_MS = 45L
        private const val WHEEL_INTERVAL_MS = 70L
        private const val MAX_FRAME_DT_MS = 50f
    }

    var isEnabled: Boolean = false
        private set

    /** True while a synthetic touch event is being dispatched, preventing MainActivity from misinterpreting it. */
    var isDispatchingSyntheticEvent: Boolean = false
        private set

    /** 0.6 (slow) .. 1.5 (fast); applied to glide speed and joystick speed. */
    var speedMultiplier: Float = 1f
        set(value) {
            field = value.coerceIn(0.4f, 2f)
        }

    var onCursorActivity: (() -> Unit)? = null

    /** Invoked when OK/Select is held. When null, a long press behaves like a normal click. */
    var onSelectLongPress: (() -> Unit)? = null

    private val density = container.resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isFrameCallbackPosted = false
    private var lastFrameNanos = 0L

    private var isClickPending = false
    private var pendingDownRunnable: Runnable? = null
    private var pendingUpRunnable: Runnable? = null

    private var holdingUp = false
    private var holdingDown = false
    private var holdingLeft = false
    private var holdingRight = false
    private var pressStartTime = 0L
    private var lastKeyEventTime = 0L

    private var joystickX = 0f
    private var joystickY = 0f

    private var selectPending = false
    private var selectLongFired = false

    private var hasPlacedCursor = false
    private var lastHoverTime = 0L
    private var lastWheelTime = 0L

    /**
     * If a Bluetooth/IR remote drops ACTION_UP we stop after this much silence. It must exceed the
     * device's initial key-repeat delay, otherwise a held key would stutter before repeats arrive.
     */
    private val watchdogMs: Long = max(900L, ViewConfiguration.getKeyRepeatTimeout().toLong() + 450L)

    private val idleFadeRunnable = Runnable {
        if (isEnabled && cursor.visibility == View.VISIBLE) {
            cursor.animate().alpha(IDLE_ALPHA).setDuration(450).start()
        }
    }

    init {
        cursor.isClickable = false
        cursor.isFocusable = false
        clickRipple?.apply {
            isClickable = false
            isFocusable = false
            visibility = View.GONE
        }
    }

    /** Hotspot (arrow tip) offset inside the cursor view, in pixels. */
    private val hotspotOffsetX: Float get() = cursor.width * HOTSPOT_X_FRACTION
    private val hotspotOffsetY: Float get() = cursor.height * HOTSPOT_Y_FRACTION

    /** Hotspot position in [container] coordinates. */
    val hotspotX: Float get() = cursor.x + hotspotOffsetX
    val hotspotY: Float get() = cursor.y + hotspotOffsetY

    fun setEnabled(enabled: Boolean) {
        if (isEnabled == enabled) return
        isEnabled = enabled
        if (enabled) {
            cursor.visibility = View.VISIBLE
            cursor.alpha = 0f
            cursor.scaleX = 0.6f
            cursor.scaleY = 0.6f
            cursor.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setInterpolator(DecelerateInterpolator())
                .setDuration(180)
                .start()
            if (!hasPlacedCursor) centerCursor() else clampIntoBounds()
            webView.requestFocus()
            scheduleIdleFade()
        } else {
            stopMotion()
            cancelPendingClick()
            selectPending = false
            mainHandler.removeCallbacks(idleFadeRunnable)
            cursor.animate().cancel()
            cursor.visibility = View.GONE
        }
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (!isEnabled) return false
        val isDown = event.action == KeyEvent.ACTION_DOWN

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { onDirectionKey(isDown, event) { holdingUp = it }; return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { onDirectionKey(isDown, event) { holdingDown = it }; return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { onDirectionKey(isDown, event) { holdingLeft = it }; return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { onDirectionKey(isDown, event) { holdingRight = it }; return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X -> {
                handleSelectKey(event)
                return true
            }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (isDown) scrollPage(-1)
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (isDown) scrollPage(1)
                return true
            }
        }
        return false
    }

    private inline fun onDirectionKey(isDown: Boolean, event: KeyEvent, setHeld: (Boolean) -> Unit) {
        val now = SystemClock.uptimeMillis()
        lastKeyEventTime = now
        wakeCursor()
        if (isDown) {
            setHeld(true)
            if (pressStartTime == 0L) pressStartTime = now
            if (event.repeatCount == 0) {
                // Immediate, small step so single taps are precise and feel responsive.
                nudgeTowardsHeldDirections()
            }
            startFrameLoop()
        } else {
            setHeld(false)
            if (!anyDirectionHeld()) {
                pressStartTime = 0L
                if (!isJoystickActive()) stopFrameLoop()
                dispatchHover(force = true)
            }
        }
    }

    private fun nudgeTowardsHeldDirections() {
        var dx = 0f
        var dy = 0f
        if (holdingLeft) dx -= 1f
        if (holdingRight) dx += 1f
        if (holdingUp) dy -= 1f
        if (holdingDown) dy += 1f
        if (dx == 0f && dy == 0f) return
        val step = TAP_STEP_DP * density * (0.75f + 0.25f * speedMultiplier)
        applyMove(dx * step, dy * step, 0L)
    }

    private fun handleSelectKey(event: KeyEvent) {
        wakeCursor()
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            animatePress(true)
            simulateClick()
            mainHandler.postDelayed({ animatePress(false) }, 110L)
        }
    }

    /** Analog stick input (-1..1). Keeps gliding while the stick is held still. */
    fun setJoystick(x: Float, y: Float) {
        if (!isEnabled) return
        joystickX = if (abs(x) > JOYSTICK_DEADZONE) x else 0f
        joystickY = if (abs(y) > JOYSTICK_DEADZONE) y else 0f
        if (isJoystickActive()) {
            wakeCursor()
            startFrameLoop()
        } else if (!anyDirectionHeld()) {
            stopFrameLoop()
            dispatchHover(force = true)
        }
    }

    private fun isJoystickActive() = joystickX != 0f || joystickY != 0f
    private fun anyDirectionHeld() = holdingUp || holdingDown || holdingLeft || holdingRight

    private fun startFrameLoop() {
        if (!isFrameCallbackPosted) {
            isFrameCallbackPosted = true
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun stopFrameLoop() {
        if (isFrameCallbackPosted) {
            Choreographer.getInstance().removeFrameCallback(this)
            isFrameCallbackPosted = false
        }
        lastFrameNanos = 0L
    }

    private fun stopMotion() {
        holdingUp = false
        holdingDown = false
        holdingLeft = false
        holdingRight = false
        joystickX = 0f
        joystickY = 0f
        pressStartTime = 0L
        stopFrameLoop()
    }

    override fun doFrame(frameTimeNanos: Long) {
        isFrameCallbackPosted = false
        if (!isEnabled) return

        val now = SystemClock.uptimeMillis()
        if (anyDirectionHeld() && now - lastKeyEventTime > watchdogMs) {
            // Lost ACTION_UP: release the keys but keep any joystick motion.
            holdingUp = false
            holdingDown = false
            holdingLeft = false
            holdingRight = false
            pressStartTime = 0L
        }

        val dtMs = if (lastFrameNanos == 0L) 16.6f
        else ((frameTimeNanos - lastFrameNanos) / 1_000_000f).coerceIn(1f, MAX_FRAME_DT_MS)
        lastFrameNanos = frameTimeNanos

        var dx = 0f
        var dy = 0f
        var holdMs = 0L

        if (anyDirectionHeld()) {
            var dirX = 0f
            var dirY = 0f
            if (holdingLeft) dirX -= 1f
            if (holdingRight) dirX += 1f
            if (holdingUp) dirY -= 1f
            if (holdingDown) dirY += 1f
            if (dirX != 0f && dirY != 0f) {
                dirX *= 0.7071f
                dirY *= 0.7071f
            }
            holdMs = (now - pressStartTime).coerceAtLeast(0L)
            if (holdMs >= HOLD_DELAY_MS) {
                val p = ((holdMs - HOLD_DELAY_MS) / RAMP_MS).coerceIn(0f, 1f)
                val eased = p * p * (3f - 2f * p) // smoothstep: gentle start, no sudden jumps
                val speed = (MIN_SPEED_DP_PER_S + (MAX_SPEED_DP_PER_S - MIN_SPEED_DP_PER_S) * eased) * speedMultiplier
                val distance = speed * density * dtMs / 1000f
                dx += dirX * distance
                dy += dirY * distance
            }
        }

        if (isJoystickActive()) {
            val rawMagnitude = sqrt(joystickX * joystickX + joystickY * joystickY)
            val magnitude = rawMagnitude.coerceAtMost(1f)
            // Quadratic response curve: fine control near the centre, fast at full tilt.
            val speed = JOYSTICK_MAX_SPEED_DP_PER_S * magnitude * magnitude * speedMultiplier
            val distance = speed * density * dtMs / 1000f
            dx += joystickX / rawMagnitude * distance
            dy += joystickY / rawMagnitude * distance
            holdMs = max(holdMs, 600L)
        }

        if (!anyDirectionHeld() && !isJoystickActive()) {
            stopFrameLoop()
            dispatchHover(force = true)
            return
        }

        if (dx != 0f || dy != 0f) applyMove(dx, dy, holdMs)

        isFrameCallbackPosted = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    /** Relative move in pixels (kept for callers that drive the pointer directly). */
    fun move(dx: Float, dy: Float) {
        if (!isEnabled) return
        wakeCursor()
        applyMove(dx, dy, 0L)
    }

    private fun applyMove(dx: Float, dy: Float, holdDurationMs: Long) {
        if (container.width <= 0 || container.height <= 0) return

        // Let the arrow tip reach every pixel of the screen (the old clamp kept the whole icon
        // on-screen, so the right/bottom ~28dp — e.g. player controls — were unreachable).
        val minX = -hotspotOffsetX
        val minY = -hotspotOffsetY
        val maxX = container.width - 1f - hotspotOffsetX
        val maxY = container.height - 1f - hotspotOffsetY

        cursor.x = (cursor.x + dx).coerceIn(minX, maxX)
        cursor.y = (cursor.y + dy).coerceIn(minY, maxY)
        hasPlacedCursor = true

        val atTop = cursor.y <= minY + 0.5f && dy < 0
        val atBottom = cursor.y >= maxY - 0.5f && dy > 0
        val atLeft = cursor.x <= minX + 0.5f && dx < 0
        val atRight = cursor.x >= maxX - 0.5f && dx > 0

        if (atTop || atBottom || atLeft || atRight) {
            edgeScroll(
                dirX = if (atLeft) -1 else if (atRight) 1 else 0,
                dirY = if (atTop) -1 else if (atBottom) 1 else 0,
                dx = dx,
                dy = dy,
                holdDurationMs = holdDurationMs
            )
        }

        dispatchHover(force = false)
        onCursorActivity?.invoke()
    }

    private fun edgeScroll(dirX: Int, dirY: Int, dx: Float, dy: Float, holdDurationMs: Long) {
        val scrollMultiplier = (1.5f + (holdDurationMs / 300f)).coerceIn(1.5f, 5.0f)
        var needsWheelY = false
        var needsWheelX = false

        if (dirY != 0) {
            if (webView.canScrollVertically(dirY)) {
                val delta = (dy * scrollMultiplier).toInt().let { if (it == 0) dirY else it }
                webView.scrollBy(0, delta)
            } else {
                needsWheelY = true
            }
        }
        if (dirX != 0) {
            if (webView.canScrollHorizontally(dirX)) {
                val delta = (dx * scrollMultiplier).toInt().let { if (it == 0) dirX else it }
                webView.scrollBy(delta, 0)
            } else {
                needsWheelX = true
            }
        }

        if (needsWheelY || needsWheelX) {
            // The document itself can't scroll: the content probably lives in an inner scroller.
            // A wheel event at the pointer lets Chromium scroll whatever element is under it.
            val amount = (0.6f + holdDurationMs / 900f).coerceIn(0.6f, 1.6f)
            dispatchWheel(
                vScroll = if (needsWheelY) -dirY * amount else 0f,
                hScroll = if (needsWheelX) dirX * amount else 0f,
                throttle = true
            )
        }
    }

    private fun scrollPage(direction: Int) {
        wakeCursor()
        if (webView.canScrollVertically(direction)) {
            if (direction < 0) webView.pageUp(false) else webView.pageDown(false)
        } else {
            dispatchWheel(vScroll = -direction * 4f, hScroll = 0f, throttle = false)
        }
        onCursorActivity?.invoke()
    }

    /** Hotspot mapped into WebView coordinates, clamped inside it, or null if not laid out. */
    private fun hotspotInWebView(): Pair<Float, Float>? {
        if (webView.width <= 0 || webView.height <= 0) return null
        val containerLoc = IntArray(2)
        val webLoc = IntArray(2)
        container.getLocationInWindow(containerLoc)
        webView.getLocationInWindow(webLoc)
        val x = hotspotX + containerLoc[0] - webLoc[0]
        val y = hotspotY + containerLoc[1] - webLoc[1]
        return x.coerceIn(1f, webView.width - 2f) to y.coerceIn(1f, webView.height - 2f)
    }

    private fun isHotspotOverWebView(): Boolean {
        if (webView.width <= 0 || webView.height <= 0 || webView.visibility != View.VISIBLE) return false
        val containerLoc = IntArray(2)
        val webLoc = IntArray(2)
        container.getLocationInWindow(containerLoc)
        webView.getLocationInWindow(webLoc)
        val x = hotspotX + containerLoc[0] - webLoc[0]
        val y = hotspotY + containerLoc[1] - webLoc[1]
        return x >= 0 && y >= 0 && x < webView.width && y < webView.height
    }

    private fun dispatchWheel(vScroll: Float, hScroll: Float, throttle: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (throttle && now - lastWheelTime < WHEEL_INTERVAL_MS) return
        lastWheelTime = now
        val (x, y) = hotspotInWebView() ?: return
        try {
            val props = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
            val coords = MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
                setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
            }
            val event = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(props), arrayOf(coords),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0
            )
            webView.dispatchGenericMotionEvent(event)
            // Not recycled on purpose: see the note in simulateClick().
        } catch (_: Throwable) {
            // Synthetic wheel is best-effort only.
        }
    }

    /**
     * Sends mouse hover moves so hover menus, tooltips and "show controls on mouse move" video
     * players react to the pointer like they would on a desktop.
     */
    private fun dispatchHover(force: Boolean) {
        if (!isEnabled || !isHotspotOverWebView()) return
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastHoverTime < HOVER_INTERVAL_MS) return
        lastHoverTime = now
        val (x, y) = hotspotInWebView() ?: return
        try {
            val props = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
            val coords = MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
            }
            val event = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_HOVER_MOVE, 1, arrayOf(props), arrayOf(coords),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0
            )
            webView.dispatchGenericMotionEvent(event)
        } catch (_: Throwable) {
            // Hover is cosmetic; never let it break pointer movement.
        }
    }

    fun hideForPhysicalPointer() {
        if (isEnabled) {
            stopMotion()
            cancelPendingClick()
            mainHandler.removeCallbacks(idleFadeRunnable)
            cursor.visibility = View.GONE
        }
    }

    fun showIfEnabled() {
        if (isEnabled) {
            cursor.visibility = View.VISIBLE
            wakeCursor()
        }
    }

    fun onPause() {
        stopMotion()
        cancelPendingClick()
        selectPending = false
        mainHandler.removeCallbacks(idleFadeRunnable)
    }

    private fun wakeCursor() {
        if (!isEnabled) return
        if (cursor.visibility != View.VISIBLE) cursor.visibility = View.VISIBLE
        if (cursor.alpha < 1f) {
            cursor.animate().alpha(1f).setDuration(90).start()
        }
        scheduleIdleFade()
    }

    private fun scheduleIdleFade() {
        mainHandler.removeCallbacks(idleFadeRunnable)
        mainHandler.postDelayed(idleFadeRunnable, IDLE_FADE_DELAY_MS)
    }

    private fun animatePress(pressed: Boolean) {
        val target = if (pressed) 0.82f else 1f
        cursor.animate().scaleX(target).scaleY(target).setDuration(if (pressed) 70 else 120).start()
    }

    private fun showClickRipple() {
        val ripple = clickRipple ?: return
        if (ripple.width <= 0) {
            // First use: lay it out invisibly, then animate on the next frame.
            ripple.visibility = View.INVISIBLE
            ripple.post { if (ripple.width > 0) showClickRipple() }
            return
        }
        ripple.animate().cancel()
        ripple.x = hotspotX - ripple.width / 2f
        ripple.y = hotspotY - ripple.height / 2f
        ripple.scaleX = 0.3f
        ripple.scaleY = 0.3f
        ripple.alpha = 0.95f
        ripple.visibility = View.VISIBLE
        ripple.animate()
            .scaleX(1.25f)
            .scaleY(1.25f)
            .alpha(0f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(340)
            .withEndAction { ripple.visibility = View.GONE }
            .start()
    }

    private fun cancelPendingClick() {
        pendingDownRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingDownRunnable = null
        }
        pendingUpRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingUpRunnable = null
            try {
                val now = SystemClock.uptimeMillis()
                val isOverWeb = isHotspotOverWebView()
                val webCoords = if (isOverWeb) hotspotInWebView() else null
                val targetView = if (isOverWeb && webCoords != null) webView else container
                val x = if (isOverWeb && webCoords != null) webCoords.first else hotspotX
                val y = if (isOverWeb && webCoords != null) webCoords.second else hotspotY

                val cancelEvent = obtainTouch(now, now, MotionEvent.ACTION_CANCEL, x, y)
                isDispatchingSyntheticEvent = true
                try {
                    targetView.dispatchTouchEvent(cancelEvent)
                } finally {
                    isDispatchingSyntheticEvent = false
                }
            } catch (_: Throwable) {
                // Ignore cancellation dispatch issues during teardown
            }
        }
        isClickPending = false
    }

    private fun obtainTouch(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float): MotionEvent {
        val props = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }
        return MotionEvent.obtain(
            downTime, eventTime, action, 1, arrayOf(props), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
    }

    fun simulateClick() {
        if (!isEnabled || isClickPending) return
        isClickPending = true

        val downRunnable = Runnable {
            pendingDownRunnable = null
            if (!isEnabled) {
                isClickPending = false
                return@Runnable
            }

            // Tap exactly where the arrow tip is pointing
            val isOverWeb = isHotspotOverWebView()
            val webCoords = if (isOverWeb) hotspotInWebView() else null
            val targetView = if (isOverWeb && webCoords != null) webView else container
            val clickX = if (isOverWeb && webCoords != null) webCoords.first else hotspotX
            val clickY = if (isOverWeb && webCoords != null) webCoords.second else hotspotY
            val downTime = SystemClock.uptimeMillis()

            val downEvent = obtainTouch(downTime, downTime, MotionEvent.ACTION_DOWN, clickX, clickY)

            isDispatchingSyntheticEvent = true
            try {
                targetView.dispatchTouchEvent(downEvent)
            } catch (e: Throwable) {
                isClickPending = false
                return@Runnable
            } finally {
                isDispatchingSyntheticEvent = false
            }

            val upRunnable = Runnable {
                pendingUpRunnable = null
                try {
                    val upTime = SystemClock.uptimeMillis()
                    val upEvent = obtainTouch(downTime, upTime, MotionEvent.ACTION_UP, clickX, clickY)
                    isDispatchingSyntheticEvent = true
                    try {
                        targetView.dispatchTouchEvent(upEvent)
                    } finally {
                        isDispatchingSyntheticEvent = false
                    }
                    if (isEnabled && isOverWeb) {
                        webView.requestFocus()
                    }
                } catch (_: Throwable) {
                    // Ignore exceptions during teardown
                } finally {
                    isClickPending = false
                }
            }

            pendingUpRunnable = upRunnable
            // 45ms touch contact window for Chromium Blink gesture detection to register clean tap
            mainHandler.postDelayed(upRunnable, 45L)

            showClickRipple()
            onCursorActivity?.invoke()
        }

        pendingDownRunnable = downRunnable
        // Decouple touch dispatch from current KeyEvent loop to prevent re-entrant dispatch crashes in Chromium
        mainHandler.post(downRunnable)
    }

    fun centerCursor() {
        val performCenter = Runnable {
            if (container.width > 0 && container.height > 0) {
                cursor.x = container.width / 2f - hotspotOffsetX
                cursor.y = container.height / 2f - hotspotOffsetY
                hasPlacedCursor = true
            }
        }
        if (container.width > 0 && container.height > 0 && cursor.width > 0) {
            performCenter.run()
        } else {
            container.post(performCenter)
        }
    }

    /** Keeps a previously placed pointer on-screen after layout changes (e.g. fullscreen toggles). */
    private fun clampIntoBounds() {
        container.post {
            if (container.width <= 0 || container.height <= 0) return@post
            cursor.x = cursor.x.coerceIn(-hotspotOffsetX, container.width - 1f - hotspotOffsetX)
            cursor.y = cursor.y.coerceIn(-hotspotOffsetY, container.height - 1f - hotspotOffsetY)
        }
    }
}

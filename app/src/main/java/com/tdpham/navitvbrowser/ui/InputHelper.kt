package com.tdpham.navitvbrowser.ui

import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent

object InputHelper {

    fun isMouseEvent(event: MotionEvent): Boolean =
        event.isFromSource(InputDevice.SOURCE_MOUSE) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE))

    fun isTouchEvent(event: MotionEvent): Boolean =
        event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) ||
            event.isFromSource(InputDevice.SOURCE_TOUCHPAD)
}

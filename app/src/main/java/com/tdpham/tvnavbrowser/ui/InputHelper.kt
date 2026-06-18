package com.tdpham.tvnavbrowser.ui

import android.view.InputDevice
import android.view.MotionEvent

object InputHelper {

  fun isMouseEvent(event: MotionEvent): Boolean =
    event.isFromSource(InputDevice.SOURCE_MOUSE) ||
      event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)

  fun isTouchEvent(event: MotionEvent): Boolean =
    event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) ||
      event.isFromSource(InputDevice.SOURCE_TOUCHPAD)
}

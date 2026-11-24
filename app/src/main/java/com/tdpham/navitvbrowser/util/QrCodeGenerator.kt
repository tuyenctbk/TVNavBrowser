package com.tdpham.navitvbrowser.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {

    fun generateQrCode(text: String, width: Int = 400, height: Int = 400): Bitmap? {
        if (text.isBlank() || width <= 0 || height <= 0) return null
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height)
            val pixels = IntArray(width * height)
            var offset = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[offset++] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}

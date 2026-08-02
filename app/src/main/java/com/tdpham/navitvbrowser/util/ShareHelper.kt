package com.tdpham.navitvbrowser.util

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.tdpham.navitvbrowser.R

object ShareHelper {

    fun showShareDialog(activity: Activity) {
        val packageName = activity.packageName
        val playStoreUrl = "https://play.google.com/store/apps/details?id=$packageName"

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_share_qr, null)
        val ivQr = dialogView.findViewById<ImageView>(R.id.ivQrCode)
        ivQr.setImageBitmap(generateQrBitmap(playStoreUrl, 500))

        AlertDialog.Builder(activity, R.style.Theme_TVNavBrowser_Dialog)
            .setTitle(R.string.share_title)
            .setMessage(R.string.share_message)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun generateQrBitmap(content: String, size: Int): Bitmap {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val offset = y * size
            for (x in 0 until size) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }
}

package com.tdpham.navitvbrowser.util

import android.app.Activity
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.tdpham.navitvbrowser.R
import com.tdpham.navitvbrowser.ui.ThemeHelper

object ShareHelper {

    fun showShareDialog(activity: Activity) {
        val packageName = activity.packageName
        val playStoreUrl = "https://play.google.com/store/apps/details?id=$packageName"

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_share_qr, null)
        val ivQr = dialogView.findViewById<ImageView>(R.id.ivQrCode)
        QrCodeGenerator.generateQrCode(playStoreUrl, 480, 480)?.let { ivQr.setImageBitmap(it) }

        AlertDialog.Builder(activity, ThemeHelper.getDialogTheme(activity))
            .setTitle(R.string.share_title)
            .setMessage(R.string.share_message)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun showPageQrDialog(activity: Activity, pageUrl: String?, pageTitle: String? = null) {
        val cleanUrl = pageUrl?.trim().orEmpty()
        if (cleanUrl.isBlank() || cleanUrl.startsWith("file:///")) {
            showShareDialog(activity)
            return
        }

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_share_qr, null)
        val ivQr = dialogView.findViewById<ImageView>(R.id.ivQrCode)
        QrCodeGenerator.generateQrCode(cleanUrl, 480, 480)?.let { ivQr.setImageBitmap(it) }

        val message = if (!pageTitle.isNullOrBlank()) {
            "${activity.getString(R.string.qr_open_on_phone_msg)}\n\n$pageTitle"
        } else {
            activity.getString(R.string.qr_open_on_phone_msg)
        }

        AlertDialog.Builder(activity, ThemeHelper.getDialogTheme(activity))
            .setTitle(R.string.qr_open_on_phone_title)
            .setMessage(message)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}

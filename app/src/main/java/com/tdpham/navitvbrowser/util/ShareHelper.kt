package com.tdpham.navitvbrowser.util

import android.app.Activity
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.tdpham.navitvbrowser.R
import com.tdpham.navitvbrowser.ui.ThemeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ShareHelper {

    fun showShareDialog(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val packageName = activity.packageName
        val playStoreUrl = "https://play.google.com/store/apps/details?id=$packageName"

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_share_qr, null)
        val ivQr = dialogView.findViewById<ImageView>(R.id.ivQrCode)

        val dialog = AlertDialog.Builder(activity, ThemeHelper.getDialogTheme(activity))
            .setTitle(R.string.share_title)
            .setMessage(R.string.share_message)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .create()

        bindAsyncQr(activity, dialog, ivQr, playStoreUrl)
        dialog.show()
    }

    fun showPageQrDialog(activity: Activity, pageUrl: String?, pageTitle: String? = null) {
        if (activity.isFinishing || activity.isDestroyed) return
        val cleanUrl = pageUrl?.trim().orEmpty()
        if (cleanUrl.isBlank() || cleanUrl.startsWith("file:///")) {
            showShareDialog(activity)
            return
        }

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_share_qr, null)
        val ivQr = dialogView.findViewById<ImageView>(R.id.ivQrCode)

        val message = if (!pageTitle.isNullOrBlank()) {
            "${activity.getString(R.string.qr_open_on_phone_msg)}\n\n$pageTitle"
        } else {
            activity.getString(R.string.qr_open_on_phone_msg)
        }

        val dialog = AlertDialog.Builder(activity, ThemeHelper.getDialogTheme(activity))
            .setTitle(R.string.qr_open_on_phone_title)
            .setMessage(message)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .create()

        bindAsyncQr(activity, dialog, ivQr, cleanUrl)
        dialog.show()
    }

    private fun bindAsyncQr(activity: Activity, dialog: AlertDialog, imageView: ImageView, url: String) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val job: Job = scope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                QrCodeGenerator.generateQrCode(url, 480, 480)
            }
            if (!activity.isFinishing && !activity.isDestroyed && dialog.isShowing && bitmap != null) {
                imageView.setImageBitmap(bitmap)
            }
        }
        dialog.setOnDismissListener {
            job.cancel()
            scope.cancel()
        }
    }
}

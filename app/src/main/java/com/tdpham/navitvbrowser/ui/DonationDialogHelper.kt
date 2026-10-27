package com.tdpham.navitvbrowser.ui

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tdpham.navitvbrowser.R
import com.tdpham.navitvbrowser.util.DonationManager
import com.tdpham.navitvbrowser.util.QrCodeGenerator

object DonationDialogHelper {

    private const val PAYPAL_USERNAME = "navitvbrowser"
    private const val KOFI_URL = "https://ko-fi.com/navitvbrowser"

    fun showDonationDialog(activity: Activity, onSupporterStateChanged: (() -> Unit)? = null) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_donation, null)
        dialog.setContentView(view)

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseDonation)
        
        val btnTabPlay = view.findViewById<Button>(R.id.btnTabPlay)
        val btnTabPaypal = view.findViewById<Button>(R.id.btnTabPaypal)
        val btnTabKofi = view.findViewById<Button>(R.id.btnTabKofi)

        val layoutPlay = view.findViewById<LinearLayout>(R.id.layoutPlayContent)
        val layoutPaypal = view.findViewById<LinearLayout>(R.id.layoutPaypalContent)
        val layoutKofi = view.findViewById<LinearLayout>(R.id.layoutKofiContent)

        // Active Supporter Banner inside Dialog
        val layoutSupporterBadge = view.findViewById<LinearLayout>(R.id.layoutSupporterBadge)
        val tvSupporterBadgeStatus = view.findViewById<TextView>(R.id.tvSupporterBadgeStatus)

        fun refreshSupporterStatus() {
            if (DonationManager.isSupporter(activity)) {
                layoutSupporterBadge.visibility = View.VISIBLE
                tvSupporterBadgeStatus.text = activity.getString(
                    R.string.donation_active_supporter_desc
                )
            } else {
                layoutSupporterBadge.visibility = View.GONE
            }
        }
        refreshSupporterStatus()

        // Google Play Tiers
        val btnTier1 = view.findViewById<Button>(R.id.btnTier1)
        val btnTier2 = view.findViewById<Button>(R.id.btnTier2)
        val btnTier3 = view.findViewById<Button>(R.id.btnTier3)

        val handlePlayDonation = { tierName: String ->
            DonationManager.setSupporter(activity, true, tierName)
            Toast.makeText(activity, R.string.donation_simulation_success, Toast.LENGTH_LONG).show()
            refreshSupporterStatus()
            onSupporterStateChanged?.invoke()
            dialog.dismiss()
        }

        btnTier1.setOnClickListener { handlePlayDonation("Coffee Supporter ($1.99)") }
        btnTier2.setOnClickListener { handlePlayDonation("Fan Supporter ($4.99)") }
        btnTier3.setOnClickListener { handlePlayDonation("VIP Supporter ($19.99)") }

        // PayPal Section
        val btnPreset3 = view.findViewById<Button>(R.id.btnPreset3)
        val btnPreset5 = view.findViewById<Button>(R.id.btnPreset5)
        val btnPreset10 = view.findViewById<Button>(R.id.btnPreset10)
        val ivPaypalQr = view.findViewById<ImageView>(R.id.ivPaypalQr)
        val btnOpenPaypal = view.findViewById<Button>(R.id.btnOpenPaypal)

        var selectedAmount = "5"
        fun updatePaypalQr(amount: String) {
            selectedAmount = amount
            val paypalUrl = "https://paypal.me/$PAYPAL_USERNAME/${amount}USD"
            val qrBitmap = QrCodeGenerator.generateQrCode(paypalUrl, 320, 320)
            if (qrBitmap != null) {
                ivPaypalQr.setImageBitmap(qrBitmap)
            }
        }
        updatePaypalQr(selectedAmount)

        btnPreset3.setOnClickListener { updatePaypalQr("3") }
        btnPreset5.setOnClickListener { updatePaypalQr("5") }
        btnPreset10.setOnClickListener { updatePaypalQr("10") }

        btnOpenPaypal.setOnClickListener {
            val paypalUrl = "https://paypal.me/$PAYPAL_USERNAME/${selectedAmount}USD"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paypalUrl))
            try {
                activity.startActivity(intent)
            } catch (_: Exception) {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("PayPal Link", paypalUrl))
                Toast.makeText(activity, R.string.donation_address_copied, Toast.LENGTH_SHORT).show()
            }
        }

        // Ko-fi Section
        val btnOpenKofi = view.findViewById<Button>(R.id.btnOpenKofi)
        val ivKofiQr = view.findViewById<ImageView>(R.id.ivKofiQr)
        val kofiQrBitmap = QrCodeGenerator.generateQrCode(KOFI_URL, 320, 320)
        if (kofiQrBitmap != null) ivKofiQr.setImageBitmap(kofiQrBitmap)

        btnOpenKofi.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL))
            try {
                activity.startActivity(intent)
            } catch (_: Exception) {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Ko-fi Link", KOFI_URL))
                Toast.makeText(activity, R.string.donation_address_copied, Toast.LENGTH_SHORT).show()
            }
        }

        // Tab Switching Logic
        fun selectTab(selectedBtn: Button, targetLayout: LinearLayout) {
            val tabs = listOf(btnTabPlay, btnTabPaypal, btnTabKofi)
            val layouts = listOf(layoutPlay, layoutPaypal, layoutKofi)

            layouts.forEach { it.visibility = View.GONE }
            tabs.forEach { button ->
                button.alpha = 0.65f
            }

            targetLayout.visibility = View.VISIBLE
            selectedBtn.alpha = 1.0f
        }

        btnTabPlay.setOnClickListener { selectTab(btnTabPlay, layoutPlay) }
        btnTabPaypal.setOnClickListener { selectTab(btnTabPaypal, layoutPaypal) }
        btnTabKofi.setOnClickListener { selectTab(btnTabKofi, layoutKofi) }

        btnClose.setOnClickListener { dialog.dismiss() }

        // Default tab: Google Play
        selectTab(btnTabPlay, layoutPlay)

        dialog.show()
    }
}

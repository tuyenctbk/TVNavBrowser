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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DonationDialogHelper {

    private const val PAYPAL_USERNAME = "navitvbrowser"
    private const val KOFI_URL = "https://ko-fi.com/navitvbrowser"

    fun showDonationDialog(activity: Activity, onSupporterStateChanged: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_donation, null)
        dialog.setContentView(view)

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        dialog.setOnDismissListener {
            dialogScope.cancel()
        }

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
            com.tdpham.navitvbrowser.util.UsageQuotaManager.refreshSupporterAllowance(activity)
            Toast.makeText(activity, R.string.donation_simulation_success, Toast.LENGTH_LONG).show()
            refreshSupporterStatus()
            onSupporterStateChanged?.invoke()
            dialog.dismiss()
        }

        btnTier1.setOnClickListener { handlePlayDonation(activity.getString(R.string.donation_tier_1)) }
        btnTier2.setOnClickListener { handlePlayDonation(activity.getString(R.string.donation_tier_2)) }
        btnTier3.setOnClickListener { handlePlayDonation(activity.getString(R.string.donation_tier_3)) }

        // PayPal Section
        val btnPreset3 = view.findViewById<Button>(R.id.btnPreset3)
        val btnPreset5 = view.findViewById<Button>(R.id.btnPreset5)
        val btnPreset10 = view.findViewById<Button>(R.id.btnPreset10)
        val ivPaypalQr = view.findViewById<ImageView>(R.id.ivPaypalQr)
        val btnOpenPaypal = view.findViewById<Button>(R.id.btnOpenPaypal)

        var selectedAmount = "5"
        var paypalQrJob: Job? = null
        fun updatePaypalQr(amount: String) {
            selectedAmount = amount
            val paypalUrl = "https://paypal.me/$PAYPAL_USERNAME/${amount}USD"
            paypalQrJob?.cancel()
            paypalQrJob = dialogScope.launch {
                val qrBitmap = withContext(Dispatchers.Default) {
                    QrCodeGenerator.generateQrCode(paypalUrl, 320, 320)
                }
                if (!activity.isFinishing && !activity.isDestroyed && dialog.isShowing && qrBitmap != null) {
                    ivPaypalQr.setImageBitmap(qrBitmap)
                }
            }
            val accentColor = ThemeHelper.getAccentColor(activity)
            val primaryColor = ThemeHelper.getTextPrimaryColor(activity)
            mapOf("3" to btnPreset3, "5" to btnPreset5, "10" to btnPreset10).forEach { (value, btn) ->
                val active = value == amount
                btn.alpha = if (active) 1.0f else 0.6f
                btn.setTextColor(if (active) accentColor else primaryColor)
            }
        }

        btnPreset3.setOnClickListener { updatePaypalQr("3") }
        btnPreset5.setOnClickListener { updatePaypalQr("5") }
        btnPreset10.setOnClickListener { updatePaypalQr("10") }

        btnOpenPaypal.setOnClickListener {
            val paypalUrl = "https://paypal.me/$PAYPAL_USERNAME/${selectedAmount}USD"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paypalUrl))
            try {
                activity.startActivity(intent)
            } catch (_: Exception) {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.donation_method_paypal), paypalUrl))
                Toast.makeText(activity, R.string.donation_address_copied, Toast.LENGTH_SHORT).show()
            }
        }

        // Ko-fi Section
        val btnOpenKofi = view.findViewById<Button>(R.id.btnOpenKofi)
        val ivKofiQr = view.findViewById<ImageView>(R.id.ivKofiQr)
        var kofiQrLoaded = false
        fun ensureKofiQrLoaded() {
            if (kofiQrLoaded) return
            kofiQrLoaded = true
            dialogScope.launch {
                val kofiQrBitmap = withContext(Dispatchers.Default) {
                    QrCodeGenerator.generateQrCode(KOFI_URL, 320, 320)
                }
                if (!activity.isFinishing && !activity.isDestroyed && dialog.isShowing && kofiQrBitmap != null) {
                    ivKofiQr.setImageBitmap(kofiQrBitmap)
                }
            }
        }

        btnOpenKofi.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL))
            try {
                activity.startActivity(intent)
            } catch (_: Exception) {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.donation_method_kofi), KOFI_URL))
                Toast.makeText(activity, R.string.donation_address_copied, Toast.LENGTH_SHORT).show()
            }
        }

        FocusAnimationHelper.applyAll(
            btnClose, btnTabPlay, btnTabPaypal, btnTabKofi,
            btnTier1, btnTier2, btnTier3,
            btnPreset3, btnPreset5, btnPreset10,
            btnOpenPaypal, btnOpenKofi
        )

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

            if (targetLayout === layoutPaypal) {
                updatePaypalQr(selectedAmount)
            } else if (targetLayout === layoutKofi) {
                ensureKofiQrLoaded()
            }
        }

        btnTabPlay.setOnClickListener { selectTab(btnTabPlay, layoutPlay) }
        btnTabPaypal.setOnClickListener { selectTab(btnTabPaypal, layoutPaypal) }
        btnTabKofi.setOnClickListener { selectTab(btnTabKofi, layoutKofi) }

        btnClose.setOnClickListener { dialog.dismiss() }

        // Default tab: Google Play (QR codes generated lazily only when PayPal / Ko-fi tab is selected)
        selectTab(btnTabPlay, layoutPlay)

        dialog.show()
    }
}

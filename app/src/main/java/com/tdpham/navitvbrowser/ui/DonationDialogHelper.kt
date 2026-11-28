package com.tdpham.navitvbrowser.ui

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageButton
import com.tdpham.navitvbrowser.R
import com.tdpham.navitvbrowser.util.RatingHelper
import com.tdpham.navitvbrowser.util.ShareHelper

/**
 * Opt-in community support dialog opened only when the user explicitly taps
 * "Support Development" inside Settings. Contains no simulated billing or
 * external payment steering so it stays strictly compliant with Google Play
 * Payments Policy.
 */
object DonationDialogHelper {

    fun showDonationDialog(activity: Activity, onSupporterStateChanged: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_donation, null)
        dialog.setContentView(view)

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseDonation)
        val btnRate = view.findViewById<Button>(R.id.btnSupportRate)
        val btnShare = view.findViewById<Button>(R.id.btnSupportShare)

        FocusAnimationHelper.applyAll(btnClose, btnRate, btnShare)

        btnRate.setOnClickListener {
            dialog.dismiss()
            RatingHelper.launchInAppReview(activity)
            onSupporterStateChanged?.invoke()
        }

        btnShare.setOnClickListener {
            dialog.dismiss()
            ShareHelper.showShareDialog(activity)
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
        btnRate.requestFocus()
    }
}

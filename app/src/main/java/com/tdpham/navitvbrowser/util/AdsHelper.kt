package com.tdpham.navitvbrowser.util

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.tdpham.navitvbrowser.R
import kotlin.random.Random

object AdsHelper {

    private val TIPS = arrayOf(
        "Plug in a USB or Bluetooth mouse for easy web navigation!",
        "Click the Mic button and speak to search or dictate URLs!",
        "Long-press OK on dashboard shortcuts to remove them!",
        "Enable Web Ad Blocker in Settings to speed up page loads!",
        "Press Back on remote to clear focus and dismiss keypads."
    )

    private val TIP_ICONS = arrayOf(
        R.drawable.ic_nav_mouse,
        R.drawable.ic_nav_mic,
        R.drawable.ic_nav_bookmark,
        R.drawable.ic_nav_settings,
        R.drawable.ic_nav_back
    )

    fun createBannerAd(activity: Activity, container: FrameLayout): AdView {
        container.removeAllViews()

        // Inflate visual placeholder tip layout
        val inflater = LayoutInflater.from(activity)
        val placeholder = inflater.inflate(R.layout.layout_ad_placeholder, container, false)
        val tvAdTip = placeholder.findViewById<TextView>(R.id.tvAdTip)
        val ivTipIcon = placeholder.findViewById<ImageView>(R.id.ivTipIcon)

        // Select a random tip
        val randomIndex = Random.nextInt(TIPS.size)
        tvAdTip.text = TIPS[randomIndex]
        ivTipIcon.setImageResource(TIP_ICONS[randomIndex])

        container.addView(placeholder)

        // Setup real AdMob AdView
        val adView = AdView(activity).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = activity.getString(R.string.admob_banner_ad_unit_id)
        }

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                // Ad loaded successfully: show AdView, hide placeholder
                placeholder.visibility = View.GONE
                adView.visibility = View.VISIBLE
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                // Failed to load: keep visual placeholder, hide AdView
                placeholder.visibility = View.VISIBLE
                adView.visibility = View.GONE
            }
        }

        // Hide AdView initially so placeholder is visible while loading
        adView.visibility = View.GONE

        container.addView(
            adView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        adView.loadAd(AdRequest.Builder().build())
        return adView
    }
}

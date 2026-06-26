package com.tdpham.navitvbrowser.util

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.tdpham.navitvbrowser.R
import kotlin.random.Random

import com.tdpham.navitvbrowser.ui.FocusAnimationHelper

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

    fun loadNativeAd(activity: Activity, container: FrameLayout) {
        container.removeAllViews()
        
        // Setup visual placeholder (Tips) while ad loads or if it fails
        val inflater = LayoutInflater.from(activity)
        val placeholder = inflater.inflate(R.layout.layout_ad_placeholder, container, false)
        val tvAdTip = placeholder.findViewById<TextView>(R.id.tvAdTip)
        val ivTipIcon = placeholder.findViewById<ImageView>(R.id.ivTipIcon)
        
        val randomIndex = Random.nextInt(TIPS.size)
        tvAdTip.text = TIPS[randomIndex]
        ivTipIcon.setImageResource(TIP_ICONS[randomIndex])
        container.addView(placeholder)

        val adLoader = AdLoader.Builder(activity, activity.getString(R.string.admob_native_ad_unit_id))
            .forNativeAd { nativeAd ->
                val adView = inflater.inflate(R.layout.layout_native_ad_tv, null) as NativeAdView
                populateNativeAdView(nativeAd, adView)
                container.removeAllViews()
                container.addView(adView)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    // Stay with placeholder
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.mediaView = adView.findViewById(R.id.ad_media)

        (adView.headlineView as TextView).text = nativeAd.headline
        nativeAd.mediaContent?.let { adView.mediaView?.setMediaContent(it) }

        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.INVISIBLE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView).text = nativeAd.body
        }

        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            val ctaButton = adView.callToActionView as Button
            ctaButton.text = nativeAd.callToAction
            FocusAnimationHelper.apply(ctaButton)
        }

        adView.setNativeAd(nativeAd)
    }

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

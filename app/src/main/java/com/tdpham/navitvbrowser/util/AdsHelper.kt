package com.tdpham.navitvbrowser.util

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.DisplayMetrics
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
import com.tdpham.navitvbrowser.BuildConfig
import com.tdpham.navitvbrowser.R
import com.tdpham.navitvbrowser.ui.FocusAnimationHelper
import kotlin.random.Random

object AdsHelper {

    // Official Google AdMob Demo Ad Units
    private const val TEST_NATIVE_AD_UNIT = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_BANNER_AD_UNIT = "ca-app-pub-3940256099942544/9214589741"

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

    private var currentNativeAd: NativeAd? = null
    private var currentAdView: AdView? = null

    /**
     * Checks if the app is running on an Android TV device.
     */
    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isUiModeTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasTvFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return isUiModeTv || hasTvFeature
    }

    /**
     * Entry point to initialize ads based on device type.
     */
    fun initAds(activity: Activity, container: FrameLayout) {
        container.removeAllViews()
        if (isTvDevice(activity)) {
            loadNativeAd(activity, container)
        } else {
            loadAdaptiveBannerAd(activity, container)
        }
    }

    /**
     * Lifecycle hooks to handle Banner ads and prevent background CPU/network usage.
     */
    fun onPause() {
        currentAdView?.pause()
    }

    fun onResume() {
        currentAdView?.resume()
    }

    /**
     * Cleans up the active ad units to prevent memory leaks.
     */
    fun onDestroy() {
        currentNativeAd?.destroy()
        currentNativeAd = null
        currentAdView?.destroy()
        currentAdView = null
    }

    /**
     * Loads a Native Ad optimized for Android TV UI/navigation.
     */
    private fun loadNativeAd(activity: Activity, container: FrameLayout) {
        // Setup visual placeholder (Tips) while ad loads or if it fails
        val inflater = LayoutInflater.from(activity)
        val placeholder = inflater.inflate(R.layout.layout_ad_placeholder, container, false)
        val tvAdTip = placeholder.findViewById<TextView>(R.id.tvAdTip)
        val ivTipIcon = placeholder.findViewById<ImageView>(R.id.ivTipIcon)
        
        val randomIndex = Random.nextInt(TIPS.size)
        tvAdTip.text = TIPS[randomIndex]
        ivTipIcon.setImageResource(TIP_ICONS[randomIndex])
        container.addView(placeholder)

        val adUnitId = if (BuildConfig.DEBUG) TEST_NATIVE_AD_UNIT else activity.getString(R.string.admob_native_ad_unit_id)

        val adLoader = AdLoader.Builder(activity, adUnitId)
            .forNativeAd { nativeAd ->
                if (activity.isFinishing || activity.isDestroyed) {
                    nativeAd.destroy()
                    return@forNativeAd
                }
                // Release previous native ad to avoid memory leaks
                currentNativeAd?.destroy()
                currentNativeAd = nativeAd

                val adView = inflater.inflate(R.layout.layout_native_ad_tv, null) as NativeAdView
                populateNativeAdView(nativeAd, adView)
                container.removeAllViews()
                container.addView(adView)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    // Stay with placeholder on failure
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

    /**
     * Loads an Anchored Adaptive Banner optimized for Mobile and Tablet devices.
     */
    private fun loadAdaptiveBannerAd(activity: Activity, container: FrameLayout) {
        val inflater = LayoutInflater.from(activity)
        val placeholder = inflater.inflate(R.layout.layout_ad_placeholder, container, false)
        val tvAdTip = placeholder.findViewById<TextView>(R.id.tvAdTip)
        val ivTipIcon = placeholder.findViewById<ImageView>(R.id.ivTipIcon)

        val randomIndex = Random.nextInt(TIPS.size)
        tvAdTip.text = TIPS[randomIndex]
        ivTipIcon.setImageResource(TIP_ICONS[randomIndex])

        container.addView(placeholder)

        val adUnitId = if (BuildConfig.DEBUG) TEST_BANNER_AD_UNIT else activity.getString(R.string.admob_banner_ad_unit_id)
        val adSize = getAdaptiveAdSize(activity, container)

        val adView = AdView(activity).apply {
            setAdSize(adSize)
            this.adUnitId = adUnitId
        }

        // Clean up previous banner
        currentAdView?.destroy()
        currentAdView = adView

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                if (activity.isFinishing || activity.isDestroyed) {
                    adView.destroy()
                    return
                }
                placeholder.visibility = View.GONE
                adView.visibility = View.VISIBLE
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                placeholder.visibility = View.VISIBLE
                adView.visibility = View.GONE
            }
        }

        adView.visibility = View.GONE
        container.addView(
            adView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        adView.loadAd(AdRequest.Builder().build())
    }

    /**
     * Dynamically calculates the screen width in DP to configure an adaptive banner size.
     */
    private fun getAdaptiveAdSize(activity: Activity, container: FrameLayout): AdSize {
        val windowMetrics = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            activity.windowManager.currentWindowMetrics
        } else {
            null
        }
        val widthPixels = if (windowMetrics != null) {
            windowMetrics.bounds.width()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels
        }
        val density = activity.resources.displayMetrics.density
        var adWidthPixels = container.width.toFloat()
        if (adWidthPixels == 0f) {
            adWidthPixels = widthPixels.toFloat()
        }
        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }
}

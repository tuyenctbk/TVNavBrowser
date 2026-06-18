package com.tdpham.tvnavbrowser.util

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.tdpham.tvnavbrowser.R

object AdsHelper {

    fun createBannerAd(activity: Activity, container: FrameLayout): AdView {
        val adView = AdView(activity).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = activity.getString(R.string.admob_banner_ad_unit_id)
        }
        container.removeAllViews()
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

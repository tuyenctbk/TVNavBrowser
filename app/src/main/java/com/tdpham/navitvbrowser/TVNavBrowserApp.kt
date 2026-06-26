package com.tdpham.navitvbrowser

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.webkit.WebView
import com.google.firebase.ktx.Firebase
import com.tdpham.navitvbrowser.util.RemoteConfigHelper

class TVNavBrowserApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Warm up WebView process to reduce MainActivity startup time
        try {
            WebView(this).destroy()
        } catch (e: Exception) {}

        FirebaseApp.initializeApp(this)
        MobileAds.initialize(this) {}
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        Firebase.analytics.logEvent("app_open", null)
        RemoteConfigHelper.fetchAndActivate()
    }
}

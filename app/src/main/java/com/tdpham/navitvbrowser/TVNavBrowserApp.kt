package com.tdpham.navitvbrowser

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.tdpham.navitvbrowser.util.RemoteConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TVNavBrowserApp : Application() {

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        Firebase.analytics.logEvent("app_open", null)

        // Offload background maintenance to IO thread to prevent cold start ANRs
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RemoteConfigHelper.fetchAndActivate()
            } catch (_: Exception) {}
            try {
                com.tdpham.navitvbrowser.util.WebViewCacheManager.initialize(this@TVNavBrowserApp)
            } catch (_: Exception) {}
        }
    }
}

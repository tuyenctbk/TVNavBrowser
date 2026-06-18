package com.tdpham.tvnavbrowser.util

import android.os.Bundle
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase

object FirebaseInitializer {

    fun logEvent(eventName: String, params: Map<String, Any>? = null) {
        val bundle = Bundle().apply {
            params?.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }
        Firebase.analytics.logEvent(eventName, bundle)
    }

    fun recordException(exception: Exception) {
        FirebaseCrashlytics.getInstance().recordException(exception)
    }
}

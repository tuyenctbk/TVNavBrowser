package com.tdpham.navitvbrowser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tdpham.navitvbrowser.util.AppPreferences
import com.tdpham.navitvbrowser.util.RatingHelper

class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val onboardingComplete = AppPreferences.isOnboardingComplete(this)

        val target = if (onboardingComplete) {
            MainActivity::class.java
        } else {
            OnboardingActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }
}

package com.tdpham.tvnavbrowser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tdpham.tvnavbrowser.util.AppPreferences
import com.tdpham.tvnavbrowser.util.RatingHelper

class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val onboardingComplete = AppPreferences.isOnboardingComplete(this)
        if (onboardingComplete) {
            RatingHelper.maybeShowRating(this)
        }

        val target = if (onboardingComplete) {
            MainActivity::class.java
        } else {
            OnboardingActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }
}

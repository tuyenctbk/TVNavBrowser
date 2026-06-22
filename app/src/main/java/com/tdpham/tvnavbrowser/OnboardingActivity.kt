package com.tdpham.tvnavbrowser

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.tdpham.tvnavbrowser.ui.FocusAnimationHelper
import com.tdpham.tvnavbrowser.util.AppPreferences

class OnboardingActivity : ComponentActivity() {

    private var currentStep = 0

    private val titles by lazy {
        intArrayOf(
            R.string.onboarding_title_1,
            R.string.onboarding_title_2,
            R.string.onboarding_title_3,
            R.string.onboarding_title_4
        )
    }

    private val messages by lazy {
        intArrayOf(
            R.string.onboarding_message_1,
            R.string.onboarding_message_2,
            R.string.onboarding_message_3,
            R.string.onboarding_message_4
        )
    }

    private val icons = intArrayOf(
        R.mipmap.ic_launcher,
        R.drawable.ic_onboarding_mouse,
        R.drawable.ic_onboarding_bookmark,
        R.drawable.ic_onboarding_settings
    )

    private lateinit var tvTitle: TextView
    private lateinit var tvMessage: TextView
    private lateinit var tvStep: TextView
    private lateinit var ivOnboarding: ImageView
    private lateinit var btnNext: Button
    private lateinit var btnSkip: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)

        tvTitle = findViewById(R.id.tvTitle)
        tvMessage = findViewById(R.id.tvMessage)
        tvStep = findViewById(R.id.tvStep)
        ivOnboarding = findViewById(R.id.ivOnboarding)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)

        FocusAnimationHelper.apply(btnNext, 1.08f)
        FocusAnimationHelper.apply(btnSkip, 1.08f)

        btnNext.setOnClickListener {
            if (currentStep < titles.lastIndex) {
                currentStep++
                updateStep(animate = true)
            } else {
                completeOnboarding()
            }
        }
        btnSkip.setOnClickListener { completeOnboarding() }

        if (savedInstanceState != null) {
            currentStep = savedInstanceState.getInt(KEY_STEP, 0)
        }
        updateStep(animate = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, currentStep)
    }

    private fun updateStep(animate: Boolean) {
        val applyContent = {
            tvTitle.setText(titles[currentStep])
            tvMessage.setText(messages[currentStep])
            ivOnboarding.setImageResource(icons[currentStep])
            tvStep.text = getString(R.string.onboarding_step, currentStep + 1, titles.size)
            btnNext.text = if (currentStep == titles.lastIndex) {
                getString(R.string.onboarding_get_started)
            } else {
                getString(R.string.onboarding_next)
            }
        }

        if (animate) {
            tvTitle.animate().alpha(0f).translationX(-50f).setDuration(140).start()
            tvMessage.animate().alpha(0f).translationX(-50f).setDuration(140).start()
            ivOnboarding.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(140).withEndAction {
                applyContent()
                tvTitle.translationX = 50f
                tvMessage.translationX = 50f
                ivOnboarding.scaleX = 0.9f
                ivOnboarding.scaleY = 0.9f
                tvTitle.animate().alpha(1f).translationX(0f).setDuration(220).start()
                tvMessage.animate().alpha(1f).translationX(0f).setDuration(220).start()
                ivOnboarding.animate().alpha(1f).scaleX(1.0f).scaleY(1.0f).setDuration(220).start()
            }.start()
        } else {
            applyContent()
            tvTitle.translationX = 0f
            tvMessage.translationX = 0f
            ivOnboarding.scaleX = 1f
            ivOnboarding.scaleY = 1f
            ivOnboarding.alpha = 1f
        }
    }

    private fun completeOnboarding() {
        AppPreferences.setOnboardingComplete(this, true)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val KEY_STEP = "onboarding_step"
    }
}

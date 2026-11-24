package com.tdpham.navitvbrowser.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import com.tdpham.navitvbrowser.R
import java.util.Locale

class TtsHelper(
    private val context: Context,
    private val onStateChanged: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    @Volatile
    private var isReady = false
    @Volatile
    private var initFailed = false
    @Volatile
    private var pendingText: String? = null

    @Volatile
    var isSpeaking: Boolean = false
        private set

    init {
        try {
            tts = TextToSpeech(appContext, this)
        } catch (_: Exception) {
            isReady = false
            initFailed = true
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            isReady = true
            initFailed = false
            val defaultLocale = Locale.getDefault()
            val langResult = engine.setLanguage(defaultLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    notifyState(true)
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    notifyState(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    notifyState(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    isSpeaking = false
                    notifyState(false)
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    isSpeaking = false
                    notifyState(false)
                }
            })
            val queued = pendingText
            pendingText = null
            if (!queued.isNullOrBlank()) {
                mainHandler.post { speak(queued) }
            }
        } else {
            isReady = false
            initFailed = true
            val hadPending = !pendingText.isNullOrBlank()
            pendingText = null
            if (hadPending) {
                showToast(R.string.tts_unavailable)
            }
        }
    }

    fun toggleSpeak(text: String) {
        if (isSpeaking || tts?.isSpeaking == true) {
            stop()
        } else {
            speak(text)
        }
    }

    fun speak(text: String) {
        val cleanText = text
            .replace(Regex("[\\p{So}\\p{Cn}•]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleanText.isBlank()) return

        if (initFailed || tts == null) {
            showToast(R.string.tts_unavailable)
            return
        }

        if (!isReady) {
            pendingText = cleanText
            showToast(R.string.tts_reading_aloud)
            return
        }

        val engine = tts ?: run {
            showToast(R.string.tts_unavailable)
            return
        }

        val maxLen = TextToSpeech.getMaxSpeechInputLength().coerceAtMost(3900)
        val clipped = if (cleanText.length > maxLen) cleanText.take(maxLen) else cleanText
        val result = try {
            engine.speak(clipped, TextToSpeech.QUEUE_FLUSH, null, "navitv_tts_utterance")
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }

        if (result == TextToSpeech.ERROR) {
            isSpeaking = false
            notifyState(false)
            showToast(R.string.tts_unavailable)
        } else {
            isSpeaking = true
            notifyState(true)
            showToast(R.string.tts_reading_aloud)
        }
    }

    fun stop() {
        pendingText = null
        try {
            if (tts?.isSpeaking == true || isSpeaking) {
                tts?.stop()
            }
        } catch (_: Exception) {}
        if (isSpeaking) {
            isSpeaking = false
            notifyState(false)
        }
    }

    fun shutdown() {
        stop()
        mainHandler.removeCallbacksAndMessages(null)
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isReady = false
    }

    private fun showToast(resId: Int) {
        mainHandler.post {
            Toast.makeText(appContext, resId, Toast.LENGTH_SHORT).show()
        }
    }

    private fun notifyState(speaking: Boolean) {
        mainHandler.post {
            onStateChanged?.invoke(speaking)
        }
    }
}

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
    context: Context,
    private val onStateChanged: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isInitializing = false
    private var isReady = false
    private var isShutdown = false
    private var pendingText: String? = null

    var isSpeaking: Boolean = false
        private set

    private fun ensureInitialized() {
        if (isShutdown || isReady || isInitializing) return
        isInitializing = true
        try {
            tts = TextToSpeech(appContext, this)
        } catch (_: Exception) {
            isInitializing = false
            isReady = false
            Toast.makeText(appContext, R.string.tts_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInit(status: Int) {
        isInitializing = false
        if (isShutdown) {
            try { tts?.shutdown() } catch (_: Exception) {}
            tts = null
            return
        }
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            val engine = tts ?: return
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
            })
            pendingText?.let { textToSpeak ->
                pendingText = null
                speak(textToSpeak)
            }
        } else {
            isReady = false
            pendingText = null
            mainHandler.post {
                if (!isShutdown) {
                    Toast.makeText(appContext, R.string.tts_unavailable, Toast.LENGTH_SHORT).show()
                }
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
        if (isShutdown) return
        val cleanText = text
            .replace(Regex("[\\p{So}\\p{Cn}•]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleanText.isBlank()) return

        if (!isReady) {
            pendingText = cleanText
            ensureInitialized()
            Toast.makeText(appContext, R.string.tts_reading_aloud, Toast.LENGTH_SHORT).show()
            return
        }

        val engine = tts
        if (engine == null) {
            Toast.makeText(appContext, R.string.tts_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        isSpeaking = true
        notifyState(true)
        Toast.makeText(appContext, R.string.tts_reading_aloud, Toast.LENGTH_SHORT).show()
        val maxLen = TextToSpeech.getMaxSpeechInputLength().coerceAtMost(3900)
        val clipped = if (cleanText.length > maxLen) cleanText.take(maxLen) else cleanText
        engine.speak(clipped, TextToSpeech.QUEUE_FLUSH, null, "navitv_tts_utterance")
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
        isShutdown = true
        stop()
        mainHandler.removeCallbacksAndMessages(null)
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isReady = false
        isInitializing = false
    }

    private fun notifyState(speaking: Boolean) {
        if (isShutdown) return
        mainHandler.post {
            if (!isShutdown) {
                onStateChanged?.invoke(speaking)
            }
        }
    }
}

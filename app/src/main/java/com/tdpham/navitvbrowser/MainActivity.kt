package com.tdpham.navitvbrowser

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.text.TextUtils
import android.webkit.RenderProcessGoneDetail
import androidx.annotation.RequiresApi
import org.json.JSONTokener
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.content.res.ColorStateList
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import android.view.LayoutInflater
import android.widget.LinearLayout
import org.json.JSONObject
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tdpham.navitvbrowser.ui.ThemeHelper
import com.tdpham.navitvbrowser.data.entity.BookmarkEntity
import kotlinx.coroutines.flow.collectLatest
import com.tdpham.navitvbrowser.data.db.BrowserDatabase
import com.tdpham.navitvbrowser.data.repository.BookmarkRepository
import com.tdpham.navitvbrowser.data.repository.HistoryRepository
import com.tdpham.navitvbrowser.data.repository.ReadLaterRepository
import com.tdpham.navitvbrowser.data.sync.BookmarkSyncManager
import com.tdpham.navitvbrowser.ui.FocusAnimationHelper
import com.tdpham.navitvbrowser.ui.InputHelper
import com.tdpham.navitvbrowser.ui.VirtualCursorController
import com.tdpham.navitvbrowser.util.AdsHelper
import com.tdpham.navitvbrowser.util.WebViewMemoryManager
import com.tdpham.navitvbrowser.util.AiHelper
import com.tdpham.navitvbrowser.util.AppPreferences
import com.tdpham.navitvbrowser.util.EmbeddedAdBlocker
import com.tdpham.navitvbrowser.util.EngagementHelper
import com.tdpham.navitvbrowser.util.FirebaseInitializer
import com.tdpham.navitvbrowser.util.RatingHelper
import com.tdpham.navitvbrowser.util.RemoteConfigHelper
import com.tdpham.navitvbrowser.util.SmartPromptHelper
import com.tdpham.navitvbrowser.util.UpdateHelper
import com.tdpham.navitvbrowser.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        private const val KEY_SAVED_URL = "saved_url"
        private const val KEY_MOUSE_MODE = "mouse_mode"
        private const val VOICE_SEARCH_REQUEST_CODE = 999
        private const val RECORD_AUDIO_PERMISSION_CODE = 1001
        private const val MAX_OFFLINE_TEXT_CHARS = 100_000
        private const val TRUSTED_ASSET_PREFIX = "file:///android_asset/"

        /**
         * Set when the WebView renderer dies so the recreated Activity starts on the homepage
         * instead of reloading the page that just crashed (which would cause a crash loop).
         */
        @Volatile
        private var recoverToHomepage = false
    }

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnBookmark: ImageButton
    private lateinit var btnReadLater: ImageButton
    private lateinit var btnDesktopMode: ImageButton
    private lateinit var btnDarkMode: ImageButton
    private lateinit var btnVoiceSearch: ImageButton
    private lateinit var btnSummarize: ImageButton
    private lateinit var ivCursor: ImageView
    private lateinit var webViewContainer: View
    private lateinit var progressBar: ProgressBar
    private lateinit var tvInputHint: TextView
    private lateinit var bookmarkBar: View
    private lateinit var bookmarkBarContainer: LinearLayout
    private lateinit var btnFullscreen: ImageButton
    private lateinit var toolbarContainer: View
    private lateinit var rootLayout: View
    private lateinit var customViewContainer: FrameLayout

    // Dedicated AI Summary Overlay Views
    private lateinit var summaryOverlayContainer: FrameLayout
    private lateinit var tvSummaryTitle: TextView
    private lateinit var tvSummaryContent: TextView
    private lateinit var btnCopySummary: Button
    private lateinit var btnCloseSummary: Button
    private lateinit var btnCloseSummaryIcon: ImageButton

    // Voice Overlay Views
    private lateinit var voiceOverlay: FrameLayout
    private lateinit var tvVoiceStatus: TextView
    private var speechRecognizer: SpeechRecognizer? = null

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private var realBookmarks: List<BookmarkEntity> = emptyList()
    private lateinit var virtualCursor: VirtualCursorController
    private lateinit var historyRepo: HistoryRepository
    private lateinit var bookmarkRepo: BookmarkRepository
    private lateinit var readLaterRepo: ReadLaterRepository
    private lateinit var webViewMemoryManager: WebViewMemoryManager
    private var isWebViewInputFocused = false
    private var isFullscreenMode = false
    private var isDarkModeActive = false
    private var currentAppliedTheme: String = ""
    private val autoFullscreenRunnable = Runnable {
        if (!isFinishing && !isDestroyed && !isFullscreenMode && !urlInput.hasFocus() && summaryOverlayContainer.visibility != View.VISIBLE) {
            toggleFullscreen(enabled = true)
        }
    }
    private val autoFullscreenDelayMs = 4500L
    private var modeToast: Toast? = null
    private var isSwitchingMode = false

    private fun showModeNotice(message: String) {
        modeToast?.cancel()
        modeToast = Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT)
        modeToast?.show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        currentAppliedTheme = AppPreferences.getAppTheme(this)
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            val msg = e.message ?: ""
            if (msg.contains("WebView", ignoreCase = true) || msg.contains("Browser", ignoreCase = true)) {
                Toast.makeText(this, getString(R.string.error_webview_missing), Toast.LENGTH_LONG).show()
                finish()
                return
            } else {
                // If it's not a clear WebView error, show a general error
                Toast.makeText(this, getString(R.string.error_generic_app, e.message), Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)
        }

        val db = BrowserDatabase.getInstance(applicationContext)
        historyRepo = HistoryRepository(db.historyDao())
        bookmarkRepo = BookmarkRepository(db.bookmarkDao())
        readLaterRepo = ReadLaterRepository(db.readLaterDao())

        webView = findViewById(R.id.webView)
        webViewMemoryManager = WebViewMemoryManager(this, webView)
        urlInput = findViewById(R.id.urlInput)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnHome = findViewById(R.id.btnHome)
        btnBookmark = findViewById(R.id.btnBookmark)
        btnReadLater = findViewById(R.id.btnReadLater)
        btnDesktopMode = findViewById(R.id.btnDesktopMode)
        btnDarkMode = findViewById(R.id.btnDarkMode)
        btnVoiceSearch = findViewById(R.id.btnVoiceSearch)
        btnSummarize = findViewById(R.id.btnSummarize)
        ivCursor = findViewById(R.id.ivCursor)
        webViewContainer = findViewById(R.id.webViewContainer)
        progressBar = findViewById(R.id.progressBar)
        tvInputHint = findViewById(R.id.tvInputHint)
        bookmarkBar = findViewById(R.id.bookmarkBar)
        bookmarkBarContainer = findViewById(R.id.bookmarkBarContainer)
        btnFullscreen = findViewById(R.id.btnFullscreen)
        toolbarContainer = findViewById(R.id.toolbarContainer)
        rootLayout = findViewById(R.id.rootLayout)
        customViewContainer = findViewById(R.id.customViewContainer)

        // Summary Overlay
        summaryOverlayContainer = findViewById(R.id.summaryOverlayContainer)
        tvSummaryTitle = findViewById(R.id.tvSummaryTitle)
        tvSummaryContent = findViewById(R.id.tvSummaryContent)
        btnCopySummary = findViewById(R.id.btnCopySummary)
        btnCloseSummary = findViewById(R.id.btnCloseSummary)
        btnCloseSummaryIcon = findViewById(R.id.btnCloseSummaryIcon)

        btnCopySummary.setOnClickListener {
            val text = tvSummaryContent.text.toString()
            if (text.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.ai_summary_heading), text))
                Toast.makeText(this, R.string.ai_summary_copied, Toast.LENGTH_SHORT).show()
            }
        }
        val closeOverlayAction = {
            summaryOverlayContainer.visibility = View.GONE
            webView.requestFocus()
        }
        btnCloseSummary.setOnClickListener { closeOverlayAction() }
        btnCloseSummaryIcon.setOnClickListener { closeOverlayAction() }

        // Voice Overlay
        voiceOverlay = findViewById(R.id.voiceOverlay)
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus)

        // Initial setup for custom ad rules and background bookmark sync
        EmbeddedAdBlocker.updateCustomRules(AppPreferences.getCustomAdRules(this))
        lifecycleScope.launch(Dispatchers.IO) {
            BookmarkSyncManager.syncBookmarks(this@MainActivity, bookmarkRepo)
        }

        virtualCursor = VirtualCursorController(rootLayout, ivCursor, webView)
        virtualCursor.onCursorActivity = {
            if (isFullscreenMode) {
                if (ivCursor.y <= 10f) {
                    toggleFullscreen(false)
                }
            }
            resetInactivityTimer()
        }

        webView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (isSwitchingMode) return@OnFocusChangeListener
            if (hasFocus) {
                setMouseMode(true)
            } else {
                setMouseMode(false)
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(webView.windowToken, 0)
            }
        }

        configureWebView()
        setupNavigationButtons()
        setupToolbarButtons()
        setupFocusAnimations()
        setupBackHandler()
        updateInputHint()
        updateNavButtonsState()
        setupBookmarkBarFlow()
        setupBookmarkBarAutoHide()

        // Init Dark Mode from preferences
        isDarkModeActive = AppPreferences.isForceDarkModeEnabled(this)
        applyDarkMode(isDarkModeActive)

        val recovering = recoverToHomepage
        recoverToHomepage = false
        val restoreUrl = savedInstanceState?.getString(KEY_SAVED_URL)
        when {
            recovering -> {
                val home = resolveHomepage()
                webView.loadUrl(home)
                urlInput.setText(home)
            }
            !restoreUrl.isNullOrBlank() -> {
                webView.loadUrl(restoreUrl)
                urlInput.setText(restoreUrl)
            }
            else -> loadInitialUrl(intent)
        }
        setMouseMode(savedInstanceState?.getBoolean(KEY_MOUSE_MODE, true) ?: true)

        webView.post {
            if (virtualCursor.isEnabled && !urlInput.hasFocus()) {
                webView.requestFocus()
            }
        }

        findViewById<FrameLayout>(R.id.adContainer)?.let {
            AdsHelper.initAds(this, it)
        }

        if (AppPreferences.isOnboardingComplete(this)) {
            EngagementHelper.recordLaunch(this)
            SmartPromptHelper.maybeShowNextPrompt(this)
        }

        applyInterfaceTheme()
    }

    private fun applyInterfaceTheme() {
        val isLight = ThemeHelper.isLightTheme(this)
        rootLayout.setBackgroundResource(ThemeHelper.getScreenBackground(this))
        toolbarContainer.setBackgroundResource(ThemeHelper.getToolbarBackground(this))
        bookmarkBarContainer.setBackgroundResource(ThemeHelper.getToolbarBackground(this))
        urlInput.setBackgroundResource(ThemeHelper.getUrlBarBackground(this))
        urlInput.setTextColor(ThemeHelper.getTextPrimaryColor(this))
        urlInput.setHintTextColor(ThemeHelper.getTextSecondaryColor(this))
        tvInputHint.setTextColor(ThemeHelper.getTextSecondaryColor(this))

        val btnBg = ThemeHelper.getToolbarButtonBackground(this)
        btnBack.setBackgroundResource(btnBg)
        btnForward.setBackgroundResource(btnBg)
        btnRefresh.setBackgroundResource(btnBg)
        btnHome.setBackgroundResource(btnBg)
        btnBookmark.setBackgroundResource(btnBg)
        btnReadLater.setBackgroundResource(btnBg)
        btnDesktopMode.setBackgroundResource(btnBg)
        btnDarkMode.setBackgroundResource(btnBg)
        btnVoiceSearch.setBackgroundResource(btnBg)
        btnSummarize.setBackgroundResource(btnBg)
        btnFullscreen.setBackgroundResource(btnBg)

        summaryOverlayContainer.findViewById<View>(R.id.cardSummary)?.setBackgroundResource(ThemeHelper.getOverlayCardBackground(this))
        tvSummaryTitle.setTextColor(ThemeHelper.getAccentColor(this))
        tvSummaryContent.setTextColor(ThemeHelper.getTextPrimaryColor(this))
    }

    override fun onResume() {
        super.onResume()
        val latestTheme = AppPreferences.getAppTheme(this)
        if (currentAppliedTheme.isNotEmpty() && latestTheme != currentAppliedTheme) {
            currentAppliedTheme = latestTheme
            recreate()
            return
        }
        applyInterfaceTheme()
        // Keep the toolbar toggle in sync with the Settings switch (both write the same preference).
        if (::webView.isInitialized) {
            val darkPref = AppPreferences.isForceDarkModeEnabled(this)
            if (darkPref != isDarkModeActive) {
                applyDarkMode(darkPref)
            }
        }
        if (::webViewMemoryManager.isInitialized) {
            webViewMemoryManager.onResume()
        } else if (::webView.isInitialized) {
            webView.onResume()
        }
        resetInactivityTimer()
        AdsHelper.onResume()
    }

    override fun onPause() {
        if (::webViewMemoryManager.isInitialized) {
            webViewMemoryManager.onPause()
        } else if (::webView.isInitialized) {
            webView.onPause()
        }
        handler.removeCallbacks(autoFullscreenRunnable)
        AdsHelper.onPause()
        super.onPause()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::webViewMemoryManager.isInitialized) {
            webViewMemoryManager.onTrimMemory(level)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUrlIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) {
            val currentUrl = webView.url
            if (!currentUrl.isNullOrBlank()) {
                outState.putString(KEY_SAVED_URL, currentUrl)
            }
        }
        if (::virtualCursor.isInitialized) {
            outState.putBoolean(KEY_MOUSE_MODE, virtualCursor.isEnabled)
        }
    }

    override fun onDestroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}

        if (::webViewMemoryManager.isInitialized) {
            webViewMemoryManager.destroy()
        } else if (::webView.isInitialized) {
            webView.loadUrl("about:blank")
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        AdsHelper.onDestroy()
        super.onDestroy()
    }

    @Suppress("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        resetInactivityTimer()
        
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (isFullscreenMode && (webView.scrollY <= 10 || !virtualCursor.isEnabled)) {
                        toggleFullscreen(false)
                        urlInput.requestFocus()
                        return true
                    }
                }
                KeyEvent.KEYCODE_MENU -> {
                    if (isFullscreenMode) {
                        toggleFullscreen(false)
                    }
                    urlInput.requestFocus()
                    urlInput.selectAll()
                    return true
                }
            }
        }

        val isOverlayActive = (summaryOverlayContainer.visibility == View.VISIBLE) || (voiceOverlay.visibility == View.VISIBLE)
        val isEditingUrl = urlInput.hasFocus()
        val isFocusedOnToolbar = isViewInToolbarOrBookmarkBar(currentFocus) && !webView.hasFocus()

        // While a page video is shown fullscreen, keys must reach the player controls, not the pointer.
        val isCustomViewShown = customView != null

        if (virtualCursor.isEnabled && !isOverlayActive && !isEditingUrl && !isFocusedOnToolbar && !isCustomViewShown) {
            if (!webView.hasFocus()) {
                webView.requestFocus()
            }
            if (virtualCursor.handleKey(event)) {
                return true
            }
        } else if (event.action == KeyEvent.ACTION_DOWN && (event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
            // Smooth accelerated scrolling when holding D-pad in spatial navigation mode
            val holdDurationMs = (event.eventTime - event.downTime).coerceAtLeast(0)
            if (event.repeatCount > 1 || holdDurationMs > 280L) {
                val scrollMultiplier = (1.5f + (holdDurationMs / 250f)).coerceIn(1.5f, 6.0f)
                val deltaY = (if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) 45f else -45f) * scrollMultiplier
                webView.scrollBy(0, deltaY.toInt())
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @SuppressLint("MissingSuperCall")
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val homepage = resolveHomepage()
            if (webView.url != homepage) {
                webView.loadUrl(homepage)
                Toast.makeText(this, getString(R.string.nav_returning_home), Toast.LENGTH_SHORT).show()
            }
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        resetInactivityTimer()
        if (InputHelper.isMouseEvent(event)) {
            virtualCursor.hideForPhysicalPointer()
            tvInputHint.text = getString(R.string.input_hint_mouse)
            tvInputHint.isVisible = true
            if (webView.dispatchGenericMotionEvent(event)) return true
        } else if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK &&
            event.action == MotionEvent.ACTION_MOVE && virtualCursor.isEnabled
        ) {
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            if (Math.abs(x) > 0.15f || Math.abs(y) > 0.15f) {
                virtualCursor.move(x * 32f, y * 32f)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        resetInactivityTimer()
        if (InputHelper.isTouchEvent(event)) {
            virtualCursor.hideForPhysicalPointer()
            tvInputHint.text = getString(R.string.input_hint_touch)
            tvInputHint.isVisible = true
        }
        return super.dispatchTouchEvent(event)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        webView.addJavascriptInterface(WebAppInterface(), "AndroidApp")
        settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.setNeedInitialFocus(true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = RemoteConfigHelper.isSafeBrowsingEnabled()
        }
        settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return false
                if (scheme != "http" && scheme != "https" && scheme != "file" && scheme != "about") {
                    try {
                        val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                        if (intent != null) {
                            intent.addCategory(Intent.CATEGORY_BROWSABLE)
                            intent.component = null
                            intent.selector = null
                            startActivity(intent)
                            return true
                        }
                    } catch (_: Exception) {
                        return true
                    }
                }
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (AppPreferences.isBlockEmbeddedAdsEnabled(this@MainActivity) &&
                    EmbeddedAdBlocker.shouldBlockRequest(request)
                ) {
                    return EmbeddedAdBlocker.blockedResponse()
                }
                return super.shouldInterceptRequest(view, request)
            }

            @RequiresApi(android.os.Build.VERSION_CODES.O)
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                // Without this override a renderer crash/OOM-kill (common on low-RAM TVs) takes down the whole app.
                FirebaseCrashlytics.getInstance().log(
                    "WebView renderer gone (didCrash=${detail?.didCrash()}, priority=${detail?.rendererPriorityAtExit()})"
                )
                recoverToHomepage = true
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) recreate()
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.isVisible = true
                progressBar.progress = 0
                isWebViewInputFocused = false
                
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
                if (view != null) {
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }
                
                updateAdContainerVisibility()

                view?.evaluateJavascript("window.__tvnavAdBlockerApplied = false;", null)
                injectFocusStyle(view)
                updateNavButtonsState()

                if (AppPreferences.isBlockEmbeddedAdsEnabled(this@MainActivity)) {
                    view?.let { EmbeddedAdBlocker.applyDomCleanup(it) }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.isVisible = false
                updateAdContainerVisibility()
                injectFocusStyle(view)
                if (AppPreferences.isForceDarkModeEnabled(this@MainActivity)) {
                    injectDarkMode(view)
                }
                injectInputFocusListeners(view)

                // Only move focus to WebView if the user is NOT currently navigating the toolbar
                if (!isViewInToolbarOrBookmarkBar(currentFocus)) {
                    view?.requestFocus()
                }

                virtualCursor.showIfEnabled()
                if (!UrlUtils.isBrowsableUrl(url.orEmpty())) return
                urlInput.setText(url)
                if (AppPreferences.isBlockEmbeddedAdsEnabled(this@MainActivity)) {
                    view?.let { EmbeddedAdBlocker.applyDomCleanup(it) }
                }
                val title = view?.title
                val pageUrl = url
                if (!title.isNullOrBlank() && pageUrl != null) {
                    EngagementHelper.recordPageLoaded(this@MainActivity)
                    lifecycleScope.launch(Dispatchers.IO) {
                        historyRepo.addToHistory(title = title, url = pageUrl)
                    }
                    FirebaseInitializer.logEvent("page_view", mapOf("host" to UrlUtils.hostOf(pageUrl)))
                }
                updateNavButtonsState()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    onHideCustomView()
                    return
                }
                customView = view
                customViewCallback = callback
                if (view != null) {
                    customViewContainer.addView(view)
                }
                customViewContainer.visibility = View.VISIBLE
                toolbarContainer.visibility = View.GONE
                bookmarkBar.visibility = View.GONE
                webViewContainer.visibility = View.GONE
                ivCursor.visibility = View.GONE
            }

            override fun onHideCustomView() {
                if (customView == null) return
                customViewContainer.removeView(customView)
                customView = null
                customViewContainer.visibility = View.GONE
                toolbarContainer.visibility = if (isFullscreenMode) View.GONE else View.VISIBLE
                webViewContainer.visibility = View.VISIBLE
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                virtualCursor.showIfEnabled()
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.isVisible = newProgress in 1..99
                updateNavButtonsState()

                if (newProgress >= 30 && AppPreferences.isBlockEmbeddedAdsEnabled(this@MainActivity)) {
                    view?.let { EmbeddedAdBlocker.applyDomCleanup(it) }
                }
            }
        }
    }

    private fun setupNavigationButtons() {
        btnFullscreen.setOnClickListener { toggleFullscreen(!isFullscreenMode) }
        btnVoiceSearch.setOnClickListener { startVoiceControl() }
        btnSummarize.setOnClickListener { onSummarizeClicked() }
        btnDesktopMode.setOnClickListener { toggleDesktopMode() }
        btnDarkMode.setOnClickListener { applyDarkMode(!isDarkModeActive) }

        btnBack.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        btnForward.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        btnRefresh.setOnClickListener { webView.reload() }
        btnHome.setOnClickListener {
            val home = resolveHomepage()
            webView.loadUrl(home)
            urlInput.setText(home)
        }
        btnReadLater.setOnClickListener {
            startActivity(Intent(this, ReadLaterActivity::class.java))
        }
        btnReadLater.setOnLongClickListener {
            saveCurrentPageForOfflineReading()
            true
        }
        btnBookmark.setOnClickListener {
            val title = webView.title ?: ""
            val url = webView.url ?: urlInput.text.toString()
            if (!UrlUtils.isBrowsableUrl(url)) return@setOnClickListener
            lifecycleScope.launch {
                val added = withContext(Dispatchers.IO) {
                    bookmarkRepo.addBookmark(title = title, url = url)
                }
                val message = if (added) R.string.bookmark_saved else R.string.bookmark_exists
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                if (added) {
                    BookmarkSyncManager.pushBookmark(this@MainActivity, BookmarkEntity(title = title, url = url))
                    EngagementHelper.recordBookmarkAdded(this@MainActivity)
                    FirebaseInitializer.logEvent("bookmark_added", mapOf("host" to UrlUtils.hostOf(url)))
                }
            }
        }
        btnBookmark.setOnLongClickListener {
            saveCurrentPageForOfflineReading()
            true
        }

        urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                loadTypedUrl(urlInput.text.toString().trim())
                true
            } else {
                false
            }
        }

        urlInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSuggestions(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun saveCurrentPageForOfflineReading() {
        val url = webView.url ?: urlInput.text.toString()
        if (!UrlUtils.isBrowsableUrl(url)) return

        webView.evaluateJavascript(
            "(function() { return JSON.stringify({title: document.title || '', text: document.body ? document.body.innerText : ''}); })();"
        ) { jsonResult ->
            lifecycleScope.launch {
                var title = webView.title.orEmpty().ifBlank { url }
                var fullText = ""

                try {
                    if (!jsonResult.isNullOrBlank() && jsonResult != "null") {
                        // evaluateJavascript() JSON-encodes the script result. Because the script already returns a
                        // JSON string, the value arrives as an escaped string literal that must be decoded once
                        // (handling escaped quotes, backslashes and \uXXXX correctly) before parsing it as an object.
                        val obj = when (val decoded = JSONTokener(jsonResult).nextValue()) {
                            is String -> JSONObject(decoded)
                            is JSONObject -> decoded
                            else -> null
                        }
                        if (obj != null) {
                            val pageTitle = obj.optString("title")
                            if (pageTitle.isNotBlank()) title = pageTitle
                            fullText = obj.optString("text")
                                .replace("\\n{3,}".toRegex(), "\n\n")
                                .trim()
                                .take(MAX_OFFLINE_TEXT_CHARS)
                        }
                    }
                } catch (_: Exception) {
                    fullText = ""
                }

                val excerpt = fullText.take(250).replace("\\s+".toRegex(), " ").trim().ifBlank { title }
                // The reader renders this through Html.fromHtml(), so plain text must be escaped.
                val contentHtml = if (fullText.isBlank()) "" else TextUtils.htmlEncode(fullText).replace("\n", "<br>")

                val savedId = withContext(Dispatchers.IO) {
                    readLaterRepo.saveArticle(title, url, contentHtml, excerpt)
                }
                if (savedId > 0) {
                    Toast.makeText(this@MainActivity, R.string.read_later_saved, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private enum class SuggestionType {
        BOOKMARK,
        HISTORY,
        FREQUENT,
        WEB
    }

    private data class SearchSuggestion(
        val title: String,
        val url: String,
        val type: SuggestionType
    )

    private val SUGGESTED_SITES = listOf(
        "google.com", "youtube.com", "facebook.com", "netflix.com", 
        "amazon.com", "twitter.com", "instagram.com", "wikipedia.org",
        "reddit.com", "twitch.tv", "pluto.tv", "disneyplus.com",
        "github.com", "yahoo.com", "bing.com", "spotify.com"
    )

    private var autocompleteJob: Job? = null

    private fun updateSuggestions(input: String) {
        val query = input.trim()
        autocompleteJob?.cancel()

        // Only suggest while the user is actually typing; programmatic updates (page loads, voice,
        // intents) must not run DB queries or replace the bookmark chips with URL look-alikes.
        if (!urlInput.hasFocus() || query.isBlank() || query.startsWith("file://")) {
            populateBookmarkBar(realBookmarks)
            return
        }

        autocompleteJob = lifecycleScope.launch {
            delay(120) // Debounce typing

            val suggestions = withContext(Dispatchers.IO) {
                val results = mutableListOf<SearchSuggestion>()
                val seenUrls = mutableSetOf<String>()

                fun normalize(url: String): String =
                    url.lowercase()
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .removePrefix("www.")
                        .removeSuffix("/")

                // 1. Frequently visited sites from History database
                val frequentHistory = historyRepo.searchFrequentSites(query, 6)
                frequentHistory.forEach { h ->
                    val norm = normalize(h.url)
                    if (seenUrls.add(norm)) {
                        results.add(SearchSuggestion(h.title.ifBlank { h.url }, h.url, SuggestionType.FREQUENT))
                    }
                }

                // 2. Bookmarks from database
                val matchingBookmarks = bookmarkRepo.searchBookmarks(query, 6)
                matchingBookmarks.forEach { b ->
                    val norm = normalize(b.url)
                    if (seenUrls.add(norm)) {
                        results.add(SearchSuggestion(b.title.ifBlank { b.url }, b.url, SuggestionType.BOOKMARK))
                    }
                }

                // 3. Search history from database
                val matchingHistory = historyRepo.searchHistory(query, 8)
                matchingHistory.forEach { h ->
                    val norm = normalize(h.url)
                    if (seenUrls.add(norm)) {
                        results.add(SearchSuggestion(h.title.ifBlank { h.url }, h.url, SuggestionType.HISTORY))
                    }
                }

                // 4. Fallback matching web domains
                if (results.size < 6) {
                    val matchingSites = SUGGESTED_SITES.filter { it.contains(query.lowercase()) }
                    matchingSites.forEach { site ->
                        val url = "https://$site"
                        val norm = normalize(url)
                        if (seenUrls.add(norm)) {
                            results.add(SearchSuggestion(site, url, SuggestionType.WEB))
                        }
                    }
                }

                results.take(12)
            }

            if (suggestions.isNotEmpty()) {
                populateSuggestionChips(suggestions)
            } else {
                populateBookmarkBar(realBookmarks)
            }
        }
    }

    private fun populateSuggestionChips(suggestions: List<SearchSuggestion>) {
        bookmarkBarContainer.removeAllViews()
        if (suggestions.isEmpty()) {
            bookmarkBar.visibility = View.GONE
            return
        }

        val inflater = LayoutInflater.from(this)
        suggestions.forEach { item ->
            val chip = inflater.inflate(R.layout.item_bookmark_chip, bookmarkBarContainer, false)
            val tvChipTitle = chip.findViewById<TextView>(R.id.tvChipTitle)
            val ivChipIcon = chip.findViewById<ImageView>(R.id.ivChipIcon)
            tvChipTitle.text = item.title

            when (item.type) {
                SuggestionType.BOOKMARK -> {
                    ivChipIcon?.setImageResource(R.drawable.ic_nav_bookmark)
                    ivChipIcon?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent))
                }
                SuggestionType.HISTORY, SuggestionType.FREQUENT -> {
                    ivChipIcon?.setImageResource(R.drawable.ic_nav_history)
                    ivChipIcon?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent))
                }
                SuggestionType.WEB -> {
                    ivChipIcon?.setImageResource(R.drawable.ic_nav_search)
                    ivChipIcon?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_secondary))
                }
            }

            FocusAnimationHelper.apply(chip)
            chip.setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
                urlInput.clearFocus()
                urlInput.setText(item.url)
                loadTypedUrl(item.url)
            }
            bookmarkBarContainer.addView(chip)
        }

        showBookmarkBar()
    }

    private fun toggleDesktopMode() {
        val settings = webView.settings
        val isDesktop = settings.userAgentString.contains("Windows NT")
        
        if (isDesktop) {
            // Switch to Mobile
            settings.userAgentString = null // Default mobile UA
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
            btnDesktopMode.imageTintList = ColorStateList.valueOf(getColor(R.color.text_primary))
            Toast.makeText(this, getString(R.string.nav_mobile_site_requested), Toast.LENGTH_SHORT).show()
        } else {
            // Switch to Desktop
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            btnDesktopMode.imageTintList = ColorStateList.valueOf(getColor(R.color.accent))
            Toast.makeText(this, getString(R.string.nav_desktop_site_requested), Toast.LENGTH_SHORT).show()
        }
        webView.reload()
    }

    private fun setMouseMode(enabled: Boolean) {
        if (isSwitchingMode) return
        if (virtualCursor.isEnabled == enabled) return
        isSwitchingMode = true
        try {
            virtualCursor.setEnabled(enabled)
            updateInputHint()

            if (enabled && !webView.hasFocus()) {
                webView.requestFocus()
            } else if (!enabled && webView.hasFocus()) {
                urlInput.requestFocus()
            }
            updateAdContainerVisibility()

            FirebaseInitializer.logEvent(
                if (enabled) "mouse_mode_on" else "mouse_mode_off",
                null
            )
        } finally {
            isSwitchingMode = false
        }
    }

    private val hideInputHintRunnable = Runnable { tvInputHint.isVisible = virtualCursor.isEnabled }

    private fun updateInputHint() {
        tvInputHint.removeCallbacks(hideInputHintRunnable)
        tvInputHint.text = when {
            virtualCursor.isEnabled -> getString(R.string.input_hint_virtual_mouse)
            hasConnectedMouse() -> getString(R.string.input_hint_mouse)
            else -> getString(R.string.input_hint_default)
        }
        tvInputHint.isVisible = true
        tvInputHint.postDelayed(hideInputHintRunnable, 3500)
    }

    private var lastMouseCheckTime = 0L
    private var cachedMouseConnected = false

    private fun hasConnectedMouse(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastMouseCheckTime < 4000L) {
            return cachedMouseConnected
        }
        lastMouseCheckTime = now
        cachedMouseConnected = try {
            InputDevice.getDeviceIds().any { id ->
                val device = InputDevice.getDevice(id) ?: return@any false
                device.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE
            }
        } catch (_: Exception) {
            false
        }
        return cachedMouseConnected
    }

    private fun setupToolbarButtons() {
        findViewById<ImageButton>(R.id.btnShowBookmarks).setOnClickListener {
            startActivity(Intent(this, BookmarksActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnShowHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupFocusAnimations() {
        FocusAnimationHelper.applyAll(
            btnBack, btnForward, btnRefresh, btnHome, btnBookmark, btnReadLater,
            btnFullscreen, btnDesktopMode, btnDarkMode, btnVoiceSearch, btnSummarize,
            findViewById(R.id.btnShowBookmarks),
            findViewById(R.id.btnShowHistory),
            findViewById(R.id.btnSettings),
            btnCopySummary, btnCloseSummary, btnCloseSummaryIcon
        )
        FocusAnimationHelper.apply(urlInput) { _, hasFocus ->
            if (!hasFocus) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
            }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (summaryOverlayContainer.visibility == View.VISIBLE) {
                    summaryOverlayContainer.visibility = View.GONE
                    webView.requestFocus()
                    return
                }
                if (voiceOverlay.visibility == View.VISIBLE) {
                    speechRecognizer?.stopListening()
                    voiceOverlay.visibility = View.GONE
                    webView.requestFocus()
                    return
                }
                if (customView != null) {
                    webView.webChromeClient?.onHideCustomView()
                    return
                }
                if (isFullscreenMode) {
                    toggleFullscreen(false)
                    resetInactivityTimer()
                    btnFullscreen.requestFocus()
                    return
                }
                if (isWebViewInputFocused || isKeyboardVisible()) {
                    webView.evaluateJavascript("document.activeElement.blur();", null)
                    isWebViewInputFocused = false
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(webView.windowToken, 0)
                    webView.requestFocus()
                    return
                }
                if (urlInput.hasFocus()) {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
                    urlInput.clearFocus()
                    webView.requestFocus()
                    return
                }
                if (webView.canGoBack()) {
                    webView.goBack()
                    return
                }
                val homepage = resolveHomepage()
                val currentUrl = webView.url.orEmpty()
                if (currentUrl.isNotBlank() && currentUrl != homepage && !currentUrl.endsWith("dashboard.html")) {
                    webView.loadUrl(homepage)
                    Toast.makeText(this@MainActivity, getString(R.string.nav_returning_home), Toast.LENGTH_SHORT).show()
                    return
                }
                finish()
            }
        })
    }

    private fun loadInitialUrl(intent: Intent) {
        if (handleUrlIntent(intent)) return
        val homepage = resolveHomepage()
        webView.loadUrl(homepage)
        urlInput.setText(homepage)
    }

    private fun handleUrlIntent(intent: Intent): Boolean {
        val url = intent.getStringExtra(EXTRA_URL)?.trim().orEmpty()
        if (!UrlUtils.isBrowsableUrl(url)) return false
        webView.loadUrl(url)
        urlInput.setText(url)
        return true
    }

    private fun resolveHomepage(): String {
        if (AppPreferences.hasCustomHomepage(this)) {
            val custom = AppPreferences.getHomepage(this)
            if (custom.isNotBlank()) return custom
        }
        val remoteUrl = RemoteConfigHelper.getHomepageUrl()
        return if (remoteUrl.isNotBlank() && remoteUrl != "https://www.google.com") {
            remoteUrl
        } else {
            "file:///android_asset/dashboard.html"
        }
    }

    private fun loadTypedUrl(input: String) {
        if (input.isBlank()) return
        val url = UrlUtils.resolveInput(input)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
        urlInput.clearFocus()
        webView.loadUrl(url)
        webView.requestFocus()
        FirebaseInitializer.logEvent("search", mapOf("query_length" to input.length))
    }

    private fun injectFocusStyle(view: WebView?) {
        val css = """
            *:focus, a:focus, button:focus, input:focus, select:focus, textarea:focus, [tabindex]:focus, [role="button"]:focus {
                outline: 3px solid #FFC857 !important;
                outline-offset: 3px !important;
                box-shadow: 0 0 16px rgba(255, 200, 87, 0.9), 0 0 28px rgba(255, 200, 87, 0.4) !important;
                transform: scale(1.04) !important;
                transition: transform 0.16s cubic-bezier(0.175, 0.885, 0.32, 1.275), box-shadow 0.16s ease !important;
                z-index: 99999 !important;
            }
        """.trimIndent().replace("\n", " ")
        val js = "(function() {" +
            "var style = document.getElementById('tvnav-focus-style');" +
            "if (!style) {" +
            "style = document.createElement('style');" +
            "style.id = 'tvnav-focus-style';" +
            "document.head.appendChild(style);" +
            "}" +
            "style.innerHTML = '$css';" +
            "})();"
        view?.evaluateJavascript(js, null)
    }

    private fun injectDarkMode(view: WebView?) {
        view?.evaluateJavascript(darkModeScript(true), null)
    }

    /**
     * Single source of truth for the injected dark stylesheet. Page loads and the toolbar toggle
     * previously injected two different stylesheets with different ids, so turning dark mode off
     * only removed one of them and the page stayed dark.
     */
    private fun darkModeScript(enabled: Boolean): String = if (enabled) {
        """
        (function() {
            var legacy = document.getElementById('tvnav-dark-mode');
            if (legacy) legacy.remove();
            var style = document.getElementById('__tvnav_dark_style__');
            if (!style) {
                style = document.createElement('style');
                style.id = '__tvnav_dark_style__';
                style.innerHTML = 'html, body { background-color: #121212 !important; color: #e6e6e6 !important; } * { border-color: #2e2e2e !important; } p, span, h1, h2, h3, h4, h5, h6, li, td, th { color: #e6e6e6 !important; } a { color: #82b1ff !important; } input, textarea, select { background: #242424 !important; color: #ffffff !important; } img, video { filter: brightness(0.88) contrast(1.05) !important; }';
                (document.head || document.documentElement).appendChild(style);
            }
        })();
        """.trimIndent()
    } else {
        """
        (function() {
            ['__tvnav_dark_style__', 'tvnav-dark-mode'].forEach(function(id) {
                var style = document.getElementById(id);
                if (style) style.remove();
            });
        })();
        """.trimIndent()
    }

    private fun updateNavButtonsState() {
        val canGoBack = webView.canGoBack()
        btnBack.isEnabled = canGoBack
        btnBack.alpha = if (canGoBack) 1.0f else 0.4f

        val canGoForward = webView.canGoForward()
        btnForward.isEnabled = canGoForward
        btnForward.alpha = if (canGoForward) 1.0f else 0.4f
    }

    private fun setupBookmarkBarFlow() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bookmarkRepo.getAllBookmarks().collectLatest { list ->
                    realBookmarks = list
                    if (urlInput.text.isNullOrBlank() || !urlInput.hasFocus()) {
                        populateBookmarkBar(list)
                    }
                }
            }
        }
    }

    private fun populateBookmarkBar(list: List<BookmarkEntity>) {
        bookmarkBarContainer.removeAllViews()
        if (list.isEmpty()) {
            bookmarkBar.visibility = View.GONE
            return
        }

        val inflater = LayoutInflater.from(this)
        list.forEach { bookmark ->
            val chip = inflater.inflate(R.layout.item_bookmark_chip, bookmarkBarContainer, false)
            val tvChipTitle = chip.findViewById<TextView>(R.id.tvChipTitle)
            tvChipTitle.text = bookmark.title

            FocusAnimationHelper.apply(chip)
            chip.setOnClickListener {
                webView.loadUrl(bookmark.url)
                webView.requestFocus()
            }
            bookmarkBarContainer.addView(chip)
        }

        if (isViewInToolbarOrBookmarkBar(currentFocus)) {
            showBookmarkBar()
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideBookmarkRunnable = Runnable { animateBookmarkBarVisibility(false) }

    private fun showBookmarkBar() {
        handler.removeCallbacks(hideBookmarkRunnable)
        animateBookmarkBarVisibility(true)
    }

    private fun scheduleHideBookmarkBar(delayMs: Long = 2500L) {
        handler.removeCallbacks(hideBookmarkRunnable)
        handler.postDelayed(hideBookmarkRunnable, delayMs)
    }

    private fun animateBookmarkBarVisibility(show: Boolean) {
        val containerChildCount = bookmarkBarContainer.childCount
        if (containerChildCount == 0) {
            bookmarkBar.visibility = View.GONE
            return
        }

        val targetVisibility = if (show) View.VISIBLE else View.GONE
        if (bookmarkBar.visibility == targetVisibility) return

        android.transition.TransitionManager.beginDelayedTransition(findViewById(android.R.id.content))
        bookmarkBar.visibility = targetVisibility
    }

    private fun setupBookmarkBarAutoHide() {
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus != null) {
                if (isViewInToolbarOrBookmarkBar(newFocus)) {
                    showBookmarkBar()
                } else {
                    scheduleHideBookmarkBar()
                }
            } else {
                scheduleHideBookmarkBar()
            }
        }
    }

    private fun isViewInToolbarOrBookmarkBar(view: View?): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.id == R.id.toolbarContainer || current.id == R.id.bookmarkBar) {
                return true
            }
            val parent = current.parent
            current = if (parent is View) parent else null
        }
        return false
    }

    private fun applyDarkMode(enabled: Boolean) {
        isDarkModeActive = enabled
        AppPreferences.setForceDarkModeEnabled(this, enabled)
        btnDarkMode.imageTintList = ColorStateList.valueOf(
            if (enabled) ContextCompat.getColor(this, R.color.accent)
            else ContextCompat.getColor(this, R.color.text_primary)
        )

        // WebSettingsCompat Force Dark if supported
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            try {
                WebSettingsCompat.setForceDark(
                    webView.settings,
                    if (enabled) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                )
            } catch (_: Exception) {}
        }

        // Comprehensive TV-friendly Dark Mode CSS injection / removal
        webView.evaluateJavascript(darkModeScript(enabled), null)
    }

    private fun startVoiceControl() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            // Fallback to standard recognizer intent
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_search_prompt))
            }
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(intent, VOICE_SEARCH_REQUEST_CODE)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.error_voice_search_unsupported), Toast.LENGTH_SHORT).show()
            }
            return
        }

        voiceOverlay.visibility = View.VISIBLE
        tvVoiceStatus.text = getString(R.string.voice_search_prompt)

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        tvVoiceStatus.text = getString(R.string.voice_status_listening)
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        tvVoiceStatus.text = getString(R.string.voice_status_processing)
                    }
                    override fun onError(error: Int) {
                        voiceOverlay.visibility = View.GONE
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> getString(R.string.voice_error_no_match)
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> getString(R.string.voice_error_timeout)
                            else -> getString(R.string.voice_error_generic)
                        }
                        Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                    override fun onResults(results: Bundle?) {
                        voiceOverlay.visibility = View.GONE
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull()?.trim()
                        if (!spokenText.isNullOrBlank()) {
                            handleVoiceCommandOrSearch(spokenText)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            voiceOverlay.visibility = View.GONE
            Toast.makeText(this, getString(R.string.voice_error_speech, e.message.orEmpty()), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleVoiceCommandOrSearch(command: String) {
        val lower = command.lowercase().trim()
        when {
            lower == "back" || lower == "go back" -> {
                Toast.makeText(this, getString(R.string.voice_command_executed, getString(R.string.nav_back)), Toast.LENGTH_SHORT).show()
                if (webView.canGoBack()) webView.goBack()
            }
            lower == "forward" || lower == "go forward" -> {
                Toast.makeText(this, getString(R.string.voice_command_executed, getString(R.string.nav_forward)), Toast.LENGTH_SHORT).show()
                if (webView.canGoForward()) webView.goForward()
            }
            lower == "refresh" || lower == "reload" -> {
                Toast.makeText(this, getString(R.string.voice_command_executed, getString(R.string.nav_refresh)), Toast.LENGTH_SHORT).show()
                webView.reload()
            }
            lower == "home" || lower == "go home" -> {
                Toast.makeText(this, getString(R.string.voice_command_executed, getString(R.string.nav_home)), Toast.LENGTH_SHORT).show()
                val home = resolveHomepage()
                webView.loadUrl(home)
                urlInput.setText(home)
            }
            lower == "dark mode" || lower == "night mode" || lower == "toggle dark mode" -> {
                applyDarkMode(!isDarkModeActive)
                Toast.makeText(this, getString(R.string.voice_command_executed, getString(R.string.nav_dark_mode)), Toast.LENGTH_SHORT).show()
            }
            lower == "fullscreen" || lower == "toggle fullscreen" -> {
                toggleFullscreen(!isFullscreenMode)
                Toast.makeText(this, getString(R.string.voice_command_executed, getString(R.string.nav_fullscreen)), Toast.LENGTH_SHORT).show()
            }
            lower == "bookmarks" || lower == "open bookmarks" || lower == "show bookmarks" -> {
                startActivity(Intent(this, BookmarksActivity::class.java))
            }
            lower == "history" || lower == "open history" || lower == "show history" -> {
                startActivity(Intent(this, HistoryActivity::class.java))
            }
            lower == "settings" || lower == "open settings" -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            lower == "summarize" || lower == "summarize page" || lower == "ai summary" -> {
                onSummarizeClicked()
            }
            else -> {
                urlInput.setText(command)
                loadTypedUrl(command)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceControl()
            } else {
                Toast.makeText(this, R.string.voice_perm_required, Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_SEARCH_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val query = results[0].trim()
                handleVoiceCommandOrSearch(query)
            }
        }
    }

    private fun isKeyboardVisible(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val insets = ViewCompat.getRootWindowInsets(window.decorView)
            if (insets != null) {
                return insets.isVisible(WindowInsetsCompat.Type.ime())
            }
        }
        return urlInput.hasFocus() || isWebViewInputFocused
    }

    private fun injectInputFocusListeners(view: WebView?) {
        val js = """
            (function() {
                function addFocusListeners() {
                    var inputs = document.querySelectorAll('input, textarea, [contenteditable]');
                    inputs.forEach(function(input) {
                        if (!input.dataset.hasTvnavListeners) {
                            input.dataset.hasTvnavListeners = 'true';
                            input.addEventListener('focus', function() {
                                if (window.AndroidApp) window.AndroidApp.onInputFocused();
                            });
                            input.addEventListener('blur', function() {
                                if (window.AndroidApp) window.AndroidApp.onInputBlurred();
                            });
                        }
                    });
                }
                addFocusListeners();
                if (window.MutationObserver) {
                    var observer = new MutationObserver(addFocusListeners);
                    observer.observe(document.body, { childList: true, subtree: true });
                }
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    private fun updateAdContainerVisibility() {
        val container = findViewById<View>(R.id.adContainer) ?: return
        if (isFullscreenMode) {
            container.visibility = View.GONE
            return
        }
        val url = webView.url
        val isDashboard = url == "file:///android_asset/dashboard.html" || url.isNullOrEmpty()
        
        // Auto-hide guides row if virtual mouse is active or not on dashboard
        if (isDashboard && !virtualCursor.isEnabled) {
            container.visibility = View.VISIBLE
        } else {
            container.visibility = View.GONE
        }
    }

    private fun toggleFullscreen(enabled: Boolean) {
        if (isFullscreenMode == enabled) return
        isFullscreenMode = enabled

        toolbarContainer.visibility = if (enabled) View.GONE else View.VISIBLE
        updateAdContainerVisibility()

        val iconRes = if (enabled) R.drawable.ic_nav_fullscreen_exit else R.drawable.ic_nav_fullscreen
        btnFullscreen.setImageResource(iconRes)
        btnFullscreen.animate()
            .scaleX(if (enabled) 1.08f else 1f)
            .scaleY(if (enabled) 1.08f else 1f)
            .setDuration(140)
            .start()

        FirebaseInitializer.logEvent(
            if (enabled) "fullscreen_on" else "fullscreen_off",
            null
        )
    }

    private fun resetInactivityTimer() {
        handler.removeCallbacks(autoFullscreenRunnable)
        if (AppPreferences.isAutoFullscreenEnabled(this)) {
            val isEditing = urlInput.hasFocus()
            val isSummaryOpen = ::summaryOverlayContainer.isInitialized && summaryOverlayContainer.visibility == View.VISIBLE
            val isVoiceOpen = ::voiceOverlay.isInitialized && voiceOverlay.visibility == View.VISIBLE
            if (!isFullscreenMode && !isEditing && !isSummaryOpen && !isVoiceOpen) {
                handler.postDelayed(autoFullscreenRunnable, autoFullscreenDelayMs)
            }
        }
    }

    inner class WebAppInterface {
        @android.webkit.JavascriptInterface
        fun onInputFocused() {
            runOnUiThread {
                isWebViewInputFocused = true
            }
        }

        @android.webkit.JavascriptInterface
        fun onInputBlurred() {
            runOnUiThread {
                isWebViewInputFocused = false
            }
        }

        /**
         * The bridge is registered for every page, so any website could call the navigation methods.
         * Only the bundled dashboard (loaded from app assets) is allowed to trigger them.
         */
        private fun runIfTrustedPage(action: () -> Unit) {
            runOnUiThread {
                if (webView.url.orEmpty().startsWith(TRUSTED_ASSET_PREFIX)) action()
            }
        }

        @android.webkit.JavascriptInterface
        fun openBookmarks() {
            runIfTrustedPage {
                startActivity(Intent(this@MainActivity, BookmarksActivity::class.java))
            }
        }

        @android.webkit.JavascriptInterface
        fun openReadLater() {
            runIfTrustedPage {
                startActivity(Intent(this@MainActivity, ReadLaterActivity::class.java))
            }
        }

        @android.webkit.JavascriptInterface
        fun openHistory() {
            runIfTrustedPage {
                startActivity(Intent(this@MainActivity, HistoryActivity::class.java))
            }
        }

        @android.webkit.JavascriptInterface
        fun openSettings() {
            runIfTrustedPage {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }

        @android.webkit.JavascriptInterface
        fun openUrl(url: String) {
            runIfTrustedPage {
                loadTypedUrl(url)
            }
        }
    }

    private fun onSummarizeClicked() {
        val url = webView.url
        if (url == null || !UrlUtils.isBrowsableUrl(url) || url.startsWith("file:///")) {
            Toast.makeText(this, getString(R.string.ai_error_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val js = """
            (function() {
                var text = "";
                var elements = document.querySelectorAll('p, h1, h2, h3, article');
                for (var i = 0; i < elements.length; i++) {
                    text += elements[i].innerText + " ";
                }
                return text.trim();
            })();
        """.trimIndent()

        val progressToast = Toast.makeText(this, getString(R.string.ai_summarizing), Toast.LENGTH_LONG)
        progressToast.show()

        webView.evaluateJavascript(js) { extractedText ->
            val cleanText = try {
                if (extractedText == null || extractedText == "null") null
                else JSONObject("{\"text\":$extractedText}").getString("text")
            } catch (e: Exception) {
                extractedText?.replace("^\"|\"$".toRegex(), "")
            }?.trim()

            if (cleanText.isNullOrBlank()) {
                progressToast.cancel()
                Toast.makeText(this, getString(R.string.ai_error_empty), Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }

            val truncatedText = if (cleanText.length > 4000) cleanText.take(4000) + "..." else cleanText

            lifecycleScope.launch {
                val result = AiHelper.getSummaryWithFailover(this@MainActivity, truncatedText)
                progressToast.cancel()
                
                result.onSuccess { summary ->
                    EngagementHelper.recordAiSummary(this@MainActivity)
                    showSummaryDialog(summary)
                }.onFailure { error ->
                    val message = when (error.message) {
                        "API_KEY_MISSING" -> getString(R.string.ai_error_key_missing)
                        "EMPTY_TEXT" -> getString(R.string.ai_error_empty)
                        else -> getString(R.string.ai_error_generic)
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSummaryDialog(summary: String) {
        tvSummaryContent.text = summary
        summaryOverlayContainer.visibility = View.VISIBLE
        btnCopySummary.requestFocus()
    }
}

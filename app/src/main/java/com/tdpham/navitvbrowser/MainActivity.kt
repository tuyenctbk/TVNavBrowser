package com.tdpham.navitvbrowser

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
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
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdView
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.tdpham.navitvbrowser.data.entity.BookmarkEntity
import kotlinx.coroutines.flow.collectLatest
import com.tdpham.navitvbrowser.data.db.BrowserDatabase
import com.tdpham.navitvbrowser.data.repository.BookmarkRepository
import com.tdpham.navitvbrowser.data.repository.HistoryRepository
import com.tdpham.navitvbrowser.ui.FocusAnimationHelper
import com.tdpham.navitvbrowser.ui.InputHelper
import com.tdpham.navitvbrowser.ui.VirtualCursorController
import com.tdpham.navitvbrowser.util.AdsHelper
import com.tdpham.navitvbrowser.util.AppPreferences
import com.tdpham.navitvbrowser.util.EmbeddedAdBlocker
import com.tdpham.navitvbrowser.util.FirebaseInitializer
import com.tdpham.navitvbrowser.util.RatingHelper
import com.tdpham.navitvbrowser.util.UpdateHelper
import com.tdpham.navitvbrowser.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        private const val KEY_MOUSE_MODE = "mouse_mode"
        private const val VOICE_SEARCH_REQUEST_CODE = 999
    }

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var btnBack: Button
    private lateinit var btnForward: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnBookmark: Button
    private lateinit var btnDesktopMode: Button
    private lateinit var btnVoiceSearch: Button
    private lateinit var ivCursor: ImageView
    private lateinit var webViewContainer: View
    private lateinit var progressBar: ProgressBar
    private lateinit var tvInputHint: TextView
    private lateinit var bookmarkBar: View
    private lateinit var bookmarkBarContainer: LinearLayout
    private lateinit var btnFullscreen: Button
    private lateinit var toolbarContainer: View
    private lateinit var rootLayout: View

    private var realBookmarks: List<BookmarkEntity> = emptyList()
    private lateinit var virtualCursor: VirtualCursorController
    private lateinit var historyRepo: HistoryRepository
    private lateinit var bookmarkRepo: BookmarkRepository
    private var isWebViewInputFocused = false
    private var isFullscreenMode = false
    private val autoFullscreenRunnable = Runnable { toggleFullscreen(true) }
    private val AUTO_FULLSCREEN_DELAY_MS = 4000L
    private var modeToast: Toast? = null
    private var isSwitchingMode = false

    private fun showModeNotice(message: String) {
        modeToast?.cancel()
        modeToast = Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT)
        modeToast?.show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)

        val db = BrowserDatabase.getInstance(applicationContext)
        historyRepo = HistoryRepository(db.historyDao())
        bookmarkRepo = BookmarkRepository(db.bookmarkDao())

        webView = findViewById(R.id.webView)
        urlInput = findViewById(R.id.urlInput)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnBookmark = findViewById(R.id.btnBookmark)
        btnDesktopMode = findViewById(R.id.btnDesktopMode)
        btnVoiceSearch = findViewById(R.id.btnVoiceSearch)
        ivCursor = findViewById(R.id.ivCursor)
        webViewContainer = findViewById(R.id.webViewContainer)
        progressBar = findViewById(R.id.progressBar)
        tvInputHint = findViewById(R.id.tvInputHint)
        bookmarkBar = findViewById(R.id.bookmarkBar)
        bookmarkBarContainer = findViewById(R.id.bookmarkBarContainer)
        btnFullscreen = findViewById(R.id.btnFullscreen)
        toolbarContainer = findViewById(R.id.toolbarContainer)
        rootLayout = findViewById(R.id.rootLayout)

        virtualCursor = VirtualCursorController(rootLayout, ivCursor, webView)
        virtualCursor.onCursorActivity = {
            if (isFullscreenMode) {
                if (ivCursor.y <= 0f) {
                    toggleFullscreen(false)
                }
            }
            resetInactivityTimer()
        }
        virtualCursor.onExitUp = {
            urlInput.requestFocus()
        }

        urlInput.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
            }
        }

        webView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (isSwitchingMode) return@OnFocusChangeListener
            if (hasFocus) {
                setMouseMode(true)
                showModeNotice(getString(R.string.mode_virtual_mouse))
                ivCursor.post {
                    ivCursor.x = rootLayout.width / 2f - ivCursor.width / 2f
                    ivCursor.y = webViewContainer.y + 10f
                }
            } else {
                setMouseMode(false)
                showModeNotice(getString(R.string.mode_dpad))
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
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

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            setMouseMode(savedInstanceState.getBoolean(KEY_MOUSE_MODE, true))
        } else {
            loadInitialUrl(intent)
            setMouseMode(true)
        }

        findViewById<FrameLayout>(R.id.adContainer)?.let {
            AdsHelper.initAds(this, it)
        }

        if (AppPreferences.isOnboardingComplete(this)) {
            RatingHelper.maybeShowRating(this)
            UpdateHelper.maybeShowUpdateDialog(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
        resetInactivityTimer()
        AdsHelper.onResume()
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        handler.removeCallbacks(autoFullscreenRunnable)
        AdsHelper.onPause()
        super.onPause()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE && ::webView.isInitialized) {
            webView.clearCache(false) // Clear memory cache but keep disk cache
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUrlIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) webView.saveState(outState)
        outState.putBoolean(KEY_MOUSE_MODE, virtualCursor.isEnabled)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.loadUrl("about:blank")
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        AdsHelper.onDestroy()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        resetInactivityTimer()
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                event.startTracking()
                return true
            } else if (event.action == KeyEvent.ACTION_UP && event.isTracking && !event.isCanceled) {
                onBackPressedDispatcher.onBackPressed()
                return true
            } else if (event.action == KeyEvent.ACTION_DOWN && event.isLongPress) {
                val homepage = resolveHomepage()
                if (webView.url != homepage) {
                    webView.loadUrl(homepage)
                    Toast.makeText(this, getString(R.string.nav_returning_home), Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU -> {
                    if (isFullscreenMode) {
                        toggleFullscreen(false)
                    }
                    urlInput.requestFocus()
                    urlInput.selectAll()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    webView.reload()
                    return true
                }
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN && webView.hasFocus() && virtualCursor.isEnabled && !isKeyboardVisible()) {
            if (virtualCursor.handleKey(event)) {
                updateInputHint()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
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
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.setNeedInitialFocus(true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
        settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                false

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

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.isVisible = true
                progressBar.progress = 0
                isWebViewInputFocused = false
                
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
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
                    lifecycleScope.launch(Dispatchers.IO) {
                        historyRepo.addToHistory(title = title, url = pageUrl)
                    }
                    FirebaseInitializer.logEvent("page_view", mapOf("url" to pageUrl))
                }
                updateNavButtonsState()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
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
        btnVoiceSearch.setOnClickListener { startVoiceSearch() }
        btnDesktopMode.setOnClickListener { toggleDesktopMode() }

        btnBack.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        btnForward.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        btnRefresh.setOnClickListener { webView.reload() }
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
                if (added) FirebaseInitializer.logEvent("bookmark_added", mapOf("url" to url))
            }
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

    private val SUGGESTED_SITES = listOf(
        "google.com", "youtube.com", "facebook.com", "netflix.com", 
        "amazon.com", "twitter.com", "instagram.com", "wikipedia.org",
        "reddit.com", "twitch.tv", "pluto.tv", "disneyplus.com"
    )

    private fun updateSuggestions(input: String) {
        if (input.isBlank() || input.startsWith("http") || input.startsWith("file")) {
            populateBookmarkBar(realBookmarks)
            return
        }

        val filtered = SUGGESTED_SITES.filter { it.contains(input.lowercase()) }
        if (filtered.isNotEmpty()) {
            val suggestions = filtered.map { BookmarkEntity(title = it, url = "https://$it") }
            populateBookmarkBar(suggestions)
            showBookmarkBar()
        }
    }

    private fun toggleDesktopMode() {
        val settings = webView.settings
        val isDesktop = settings.userAgentString.contains("Windows NT")
        
        if (isDesktop) {
            // Switch to Mobile
            settings.userAgentString = null // Default mobile UA
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
            btnDesktopMode.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_primary))
            Toast.makeText(this, getString(R.string.nav_mobile_site_requested), Toast.LENGTH_SHORT).show()
        } else {
            // Switch to Desktop
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            btnDesktopMode.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.accent))
            Toast.makeText(this, getString(R.string.nav_desktop_site_requested), Toast.LENGTH_SHORT).show()
        }
        webView.reload()
    }

    private fun setMouseMode(enabled: Boolean) {
        if (isSwitchingMode) return
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

    private fun updateInputHint() {
        tvInputHint.text = when {
            virtualCursor.isEnabled -> getString(R.string.input_hint_virtual_mouse)
            hasConnectedMouse() -> getString(R.string.input_hint_mouse)
            else -> getString(R.string.input_hint_default)
        }
        tvInputHint.isVisible = true
        tvInputHint.postDelayed({ tvInputHint.isVisible = virtualCursor.isEnabled }, 3500)
    }

    private fun hasConnectedMouse(): Boolean {
        return InputDevice.getDeviceIds().any { id ->
            val device = InputDevice.getDevice(id) ?: return@any false
            device.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE
        }
    }

    private fun setupToolbarButtons() {
        findViewById<Button>(R.id.btnShowBookmarks).setOnClickListener {
            startActivity(Intent(this, BookmarksActivity::class.java))
        }
        findViewById<Button>(R.id.btnShowHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupFocusAnimations() {
        FocusAnimationHelper.applyAll(
            btnBack, btnForward, btnRefresh, btnBookmark,
            btnFullscreen, btnDesktopMode, btnVoiceSearch,
            findViewById(R.id.btnShowBookmarks),
            findViewById(R.id.btnShowHistory),
            findViewById(R.id.btnSettings),
            urlInput
        )
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullscreenMode) {
                    toggleFullscreen(false)
                    resetInactivityTimer()
                    btnFullscreen.requestFocus()
                    return
                }
                if (isKeyboardVisible() || isWebViewInputFocused) {
                    webView.evaluateJavascript("document.activeElement.blur();", null)
                    isWebViewInputFocused = false
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(webView.windowToken, 0)
                    webView.requestFocus()
                    return
                }
                if (urlInput.hasFocus()) {
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
                    urlInput.clearFocus()
                    webView.requestFocus()
                    return
                }
                if (webView.hasFocus() && virtualCursor.isEnabled) {
                    urlInput.requestFocus()
                    return
                }
                if (webView.canGoBack()) {
                    webView.goBack()
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
        val homepage = AppPreferences.getHomepage(this)
        return if (homepage.isEmpty() || homepage == "https://www.google.com") {
            "file:///android_asset/dashboard.html"
        } else {
            homepage
        }
    }

    private fun loadTypedUrl(input: String) {
        if (input.isEmpty()) return
        var url = input
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(input, "UTF-8")
        }
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
        urlInput.clearFocus()
        webView.loadUrl(url)
        webView.requestFocus()
        FirebaseInitializer.logEvent("search", mapOf("query" to input))
    }

    private fun injectFocusStyle(view: WebView?) {
        val css = "*:focus { outline: 3px solid #FFC857 !important; outline-offset: 2px !important; }"
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
        val js = """
            (function() {
                var style = document.getElementById('tvnav-dark-mode');
                if (style) return;
                style = document.createElement('style');
                style.id = 'tvnav-dark-mode';
                style.innerHTML = 'html, body { background-color: #0b1020 !important; color: #ffffff !important; } ' +
                                  '* { border-color: #333 !important; } ' +
                                  'a { color: #8ab4f8 !important; } ' +
                                  'input, textarea { background-color: #1c2640 !important; color: #fff !important; }';
                document.head.appendChild(style);
                
                // Intelligent inversion for images if they are too bright (optional/complex)
                // document.documentElement.style.filter = 'invert(1) hue-rotate(180deg)';
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
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
            bookmarkRepo.getAllBookmarks().collectLatest { list ->
                realBookmarks = list
                if (urlInput.text.isNullOrBlank()) {
                    populateBookmarkBar(list)
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
            if (current.id == R.id.toolbar || current.id == R.id.bookmarkBar) {
                return true
            }
            val parent = current.parent
            current = if (parent is View) parent else null
        }
        return false
    }

    private fun startVoiceSearch() {
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak a website URL or search query...")
        }
        try {
            startActivityForResult(intent, VOICE_SEARCH_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice search is not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_SEARCH_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val query = results[0].trim()
                urlInput.setText(query)
                loadTypedUrl(query)
            }
        }
    }

    private fun isKeyboardVisible(): Boolean {
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        if (insets != null) {
            return insets.isVisible(WindowInsetsCompat.Type.ime())
        }
        val rect = android.graphics.Rect()
        window.decorView.getWindowVisibleDisplayFrame(rect)
        val screenHeight = window.decorView.rootView.height
        val keypadHeight = screenHeight - rect.bottom
        return keypadHeight > screenHeight * 0.15
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
        btnFullscreen.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
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
            if (isFullscreenMode) {
                toggleFullscreen(false)
            }
            handler.postDelayed(autoFullscreenRunnable, AUTO_FULLSCREEN_DELAY_MS)
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
    }
}

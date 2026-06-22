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
    private lateinit var btnMouseMode: Button
    private lateinit var btnVoiceSearch: Button
    private lateinit var ivCursor: ImageView
    private lateinit var webViewContainer: View
    private lateinit var progressBar: ProgressBar
    private lateinit var tvInputHint: TextView
    private lateinit var bookmarkBar: View
    private lateinit var bookmarkBarContainer: LinearLayout

    private lateinit var virtualCursor: VirtualCursorController
    private lateinit var historyRepo: HistoryRepository
    private lateinit var bookmarkRepo: BookmarkRepository
    private var bannerAdView: AdView? = null
    private var isWebViewInputFocused = false

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
        btnMouseMode = findViewById(R.id.btnMouseMode)
        btnVoiceSearch = findViewById(R.id.btnVoiceSearch)
        ivCursor = findViewById(R.id.ivCursor)
        webViewContainer = findViewById(R.id.webViewContainer)
        progressBar = findViewById(R.id.progressBar)
        tvInputHint = findViewById(R.id.tvInputHint)
        bookmarkBar = findViewById(R.id.bookmarkBar)
        bookmarkBarContainer = findViewById(R.id.bookmarkBarContainer)

        virtualCursor = VirtualCursorController(webViewContainer, ivCursor, webView)

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
            if (savedInstanceState.getBoolean(KEY_MOUSE_MODE, false)) {
                setMouseMode(true)
            }
        } else {
            loadInitialUrl(intent)
        }

        findViewById<FrameLayout>(R.id.adContainer)?.let {
            bannerAdView = AdsHelper.createBannerAd(this, it)
        }

        if (AppPreferences.isOnboardingComplete(this)) {
            RatingHelper.maybeShowRating(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
        bannerAdView?.resume()
    }

    override fun onPause() {
        bannerAdView?.pause()
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
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
        bannerAdView?.destroy()
        bannerAdView = null
        if (::webView.isInitialized) {
            webView.loadUrl("about:blank")
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && virtualCursor.isEnabled && !isKeyboardVisible()) {
            val focused = currentFocus
            if (focused != urlInput && virtualCursor.handleKey(event.keyCode)) {
                updateInputHint()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
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
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.setNeedInitialFocus(true)
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
                view?.evaluateJavascript("window.__tvnavAdBlockerApplied = false;", null)
                injectFocusStyle(view)
                updateNavButtonsState()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.isVisible = false
                injectFocusStyle(view)
                injectInputFocusListeners(view)
                view?.requestFocus()
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
            }
        }
    }

    private fun setupNavigationButtons() {
        btnMouseMode.setOnClickListener { setMouseMode(!virtualCursor.isEnabled) }
        btnVoiceSearch.setOnClickListener { startVoiceSearch() }

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
    }

    private fun setMouseMode(enabled: Boolean) {
        virtualCursor.setEnabled(enabled)
        val tintColor = if (enabled) {
            getColor(R.color.accent)
        } else {
            getColor(R.color.text_primary)
        }
        btnMouseMode.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(tintColor)
        btnMouseMode.animate()
            .scaleX(if (enabled) 1.08f else 1f)
            .scaleY(if (enabled) 1.08f else 1f)
            .setDuration(140)
            .start()
        updateInputHint()
        FirebaseInitializer.logEvent(
            if (enabled) "mouse_mode_on" else "mouse_mode_off",
            null
        )
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
            btnMouseMode, btnVoiceSearch,
            findViewById(R.id.btnShowBookmarks),
            findViewById(R.id.btnShowHistory),
            findViewById(R.id.btnSettings),
            urlInput
        )
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isKeyboardVisible() || isWebViewInputFocused) {
                    webView.evaluateJavascript("document.activeElement.blur();", null)
                    isWebViewInputFocused = false
                    btnBack.requestFocus()
                    return
                }
                if (urlInput.hasFocus()) {
                    urlInput.clearFocus()
                    btnBack.requestFocus()
                    return
                }
                if (webView.canGoBack()) {
                    webView.goBack()
                    return
                }
                if (virtualCursor.isEnabled) {
                    setMouseMode(false)
                } else {
                    finish()
                }
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
                populateBookmarkBar(list)
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

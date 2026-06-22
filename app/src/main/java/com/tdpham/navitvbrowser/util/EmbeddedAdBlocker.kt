package com.tdpham.navitvbrowser.util

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.io.ByteArrayInputStream

/**
 * Blocks embedded website ads via network filtering and DOM cleanup.
 * Does not affect the app's own AdMob banner.
 */
object EmbeddedAdBlocker {

    private val blockedHostSuffixes = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adservice.google.com",
        "adnxs.com",
        "adsafeprotected.com",
        "amazon-adsystem.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "criteo.net",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "media.net",
        "advertising.com",
        "moatads.com",
        "imrworldwide.com",
        "adsrvr.org",
        "2mdn.net",
        "adform.net",
        "smartadserver.com",
        "lijit.com",
        "casalemedia.com",
        "bidswitch.net",
        "adroll.com",
        "quantserve.com",
        "zergnet.com",
        "spotxchange.com",
        "adition.com",
        "contextweb.com",
        "sharethrough.com",
        "teads.tv",
        "exoclick.com",
        "popads.net",
        "propellerads.com",
        "mgid.com",
        "revcontent.com",
        "yieldmo.com",
        "serving-sys.com",
        "adcolony.com",
        "unityads.unity3d.com",
        "applovin.com",
        "chartboost.com",
        "facebook.net",
        "scorecardresearch.com"
    )

    private val blockedHostKeywords = setOf(
        "adserver",
        "adservice",
        "adtech",
        "advert",
        "banner-ads",
        "sponsor",
        "tracking",
        "tracker",
        "analytics",
        "metrics."
    )

    private val blockedPathKeywords = setOf(
        "/ads/",
        "/ad/",
        "/ads?",
        "/ad?",
        "/advert",
        "/banner",
        "/sponsor",
        "/tracking",
        "/pixel",
        "adsystem",
        "adservice",
        "pagead",
        "show_ads"
    )

    private val emptyResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        ByteArrayInputStream(ByteArray(0))
    )

    fun shouldBlockRequest(request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) {
            return shouldBlockUrl(request.url.toString())
        }
        return false
    }

    fun shouldBlockUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path?.lowercase().orEmpty()

        if (blockedHostSuffixes.any { host == it || host.endsWith(".$it") }) {
            return true
        }
        if (host.startsWith("ads.") || host.contains(".ads.")) {
            return true
        }
        if (blockedHostKeywords.any { host.contains(it) }) {
            return true
        }
        if (blockedPathKeywords.any { path.contains(it) }) {
            return true
        }
        return false
    }

    fun blockedResponse(): WebResourceResponse = emptyResponse

    fun applyDomCleanup(webView: WebView) {
        webView.evaluateJavascript(DOM_CLEANUP_SCRIPT, null)
    }

    private const val DOM_CLEANUP_SCRIPT = """
(function() {
  if (window.__tvnavAdBlockerApplied) return;
  window.__tvnavAdBlockerApplied = true;

  var css = [
    'ins.adsbygoogle,',
    '[class*="ad-container"], [class*="ad_container"], [class*="ad-wrapper"],',
    '[class*="ad_banner"], [class*="ad-banner"], [class*="adsbox"],',
    '[class*="advert"], [class*="sponsored"], [class*="promo-ad"],',
    '[id*="ad-container"], [id*="ad_container"], [id*="ad-slot"], [id*="ad_slot"],',
    '[id*="google_ads"], [id*="div-gpt-ad"],',
    '[data-ad], [data-ad-slot], [data-ad-client], [data-google-query-id],',
    'iframe[src*="doubleclick"], iframe[src*="googlesyndication"],',
    'iframe[src*="adnxs"], iframe[src*="taboola"], iframe[src*="outbrain"],',
    'iframe[src*="criteo"], iframe[src*="adservice"], iframe[src*="advertising"],',
    '.google-auto-placed, .adsbygoogle, .ad-placement, .ad-slot,',
    '#ad, #ads, #ad-container, #ad_wrapper, #banner-ad, #top-ad, #bottom-ad',
    '{ display: none !important; visibility: hidden !important;',
    '  height: 0 !important; max-height: 0 !important; overflow: hidden !important;',
    '  opacity: 0 !important; pointer-events: none !important; }'
  ].join(' ');

  var style = document.getElementById('tvnav-adblock-style');
  if (!style) {
    style = document.createElement('style');
    style.id = 'tvnav-adblock-style';
    style.type = 'text/css';
    style.appendChild(document.createTextNode(css));
    (document.head || document.documentElement).appendChild(style);
  }

  var selectors = [
    'ins.adsbygoogle',
    '[class*="ad-container"]', '[class*="ad_container"]', '[class*="ad-wrapper"]',
    '[class*="advert"]', '[class*="sponsored"]', '[id*="ad-slot"]', '[id*="ad_slot"]',
    '[id*="google_ads"]', '[id*="div-gpt-ad"]', '[data-ad]', '[data-ad-slot]',
    'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]',
    'iframe[src*="taboola"]', 'iframe[src*="outbrain"]', '.google-auto-placed'
  ];

  function hideAds() {
    selectors.forEach(function(selector) {
      try {
        document.querySelectorAll(selector).forEach(function(node) {
          node.style.setProperty('display', 'none', 'important');
          node.style.setProperty('visibility', 'hidden', 'important');
          node.style.setProperty('height', '0', 'important');
          node.style.setProperty('max-height', '0', 'important');
          node.style.setProperty('overflow', 'hidden', 'important');
          node.style.setProperty('opacity', '0', 'important');
          node.style.setProperty('pointer-events', 'none', 'important');
        });
      } catch (e) {}
    });
  }

  hideAds();

  if (window.MutationObserver) {
    new MutationObserver(function() { hideAds(); })
      .observe(document.documentElement, { childList: true, subtree: true });
  }

  setInterval(hideAds, 2000);
})();
"""
}

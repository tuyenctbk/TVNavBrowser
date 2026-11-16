package com.tdpham.navitvbrowser.util

import java.net.URI
import java.net.URLEncoder

object UrlUtils {

    private const val SEARCH_URL = "https://www.google.com/search?q="
    private const val ASSET_PREFIX = "file:///android_asset/"

    private val HOSTNAME_REGEX = Regex(
        "^([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}(:\\d+)?([/?#]\\S*)?$",
        RegexOption.IGNORE_CASE
    )
    private val IPV4_REGEX = Regex(
        "^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?([/?#]\\S*)?$"
    )

    fun isBrowsableUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val trimmed = url.trim()
        val lower = trimmed.lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith(ASSET_PREFIX)
    }

    /** Returns the host of [url], or an empty string if it cannot be parsed. */
    fun hostOf(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val uriHost = runCatching { URI(trimmed).host }.getOrNull()
        if (!uriHost.isNullOrBlank()) return uriHost
        // Fallback for real-world URLs containing unencoded query characters (e.g. spaces or pipes)
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return ""
        val afterScheme = trimmed.substring(schemeEnd + 3)
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val hostPart = authority.substringAfterLast('@').substringBefore(':')
        return if (hostPart.contains('.') && hostPart.none { it.isWhitespace() }) hostPart else ""
    }

    /**
     * Turns raw user input from the URL bar into a loadable URL: explicit http(s) URLs and
     * bundled assets are kept, host-like input gets https://, everything else (including
     * `javascript:` pseudo-URLs) becomes a web search.
     */
    fun resolveInput(input: String): String {
        val text = input.trim()
        val lower = text.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> text
            lower.startsWith(ASSET_PREFIX) -> text
            text.isNotEmpty() && text.none { it.isWhitespace() } &&
                (HOSTNAME_REGEX.matches(text) || IPV4_REGEX.matches(text)) -> "https://$text"
            else -> SEARCH_URL + URLEncoder.encode(text, "UTF-8")
        }
    }
}

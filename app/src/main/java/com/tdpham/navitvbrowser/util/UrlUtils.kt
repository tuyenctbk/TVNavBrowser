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
        val lower = url.lowercase()
        return !lower.startsWith("about:") &&
            !lower.startsWith("chrome-error:") &&
            !lower.startsWith("data:") &&
            !lower.startsWith("javascript:") &&
            !lower.startsWith("blob:")
    }

    /** Returns the host of [url], or an empty string if it cannot be parsed. */
    fun hostOf(url: String): String =
        runCatching { URI(url).host }.getOrNull().orEmpty()

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

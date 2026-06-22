package com.tdpham.navitvbrowser.util

object UrlUtils {

    fun isBrowsableUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return !lower.startsWith("about:") &&
            !lower.startsWith("chrome-error:") &&
            !lower.startsWith("data:") &&
            !lower.startsWith("javascript:") &&
            !lower.startsWith("blob:")
    }
}

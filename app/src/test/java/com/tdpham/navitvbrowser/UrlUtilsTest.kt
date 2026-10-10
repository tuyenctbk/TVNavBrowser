package com.tdpham.navitvbrowser

import com.tdpham.navitvbrowser.util.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun testIsBrowsableUrl() {
        assertTrue(UrlUtils.isBrowsableUrl("https://example.com"))
        assertTrue(UrlUtils.isBrowsableUrl("http://example.com/test"))
        assertTrue(UrlUtils.isBrowsableUrl("file:///android_asset/homepage.html"))

        assertFalse(UrlUtils.isBrowsableUrl(""))
        assertFalse(UrlUtils.isBrowsableUrl("   "))
        assertFalse(UrlUtils.isBrowsableUrl("javascript:alert(1)"))
        assertFalse(UrlUtils.isBrowsableUrl("data:text/html,<h1>Hi</h1>"))
        assertFalse(UrlUtils.isBrowsableUrl("about:blank"))
        assertFalse(UrlUtils.isBrowsableUrl("blob:https://example.com/uuid"))
    }

    @Test
    fun testHostOf() {
        assertEquals("google.com", UrlUtils.hostOf("https://google.com/search?q=android"))
        assertEquals("sub.domain.co.uk", UrlUtils.hostOf("http://sub.domain.co.uk:8080/path"))
        assertEquals("", UrlUtils.hostOf("not a valid url"))
        assertEquals("", UrlUtils.hostOf(""))
    }

    @Test
    fun testResolveInput() {
        assertEquals("https://google.com", UrlUtils.resolveInput("https://google.com"))
        assertEquals("http://example.com", UrlUtils.resolveInput("http://example.com"))
        assertEquals("file:///android_asset/homepage.html", UrlUtils.resolveInput("file:///android_asset/homepage.html"))

        assertEquals("https://example.com", UrlUtils.resolveInput("example.com"))
        assertEquals("https://news.ycombinator.com", UrlUtils.resolveInput("news.ycombinator.com"))
        assertEquals("https://192.168.1.1:8080", UrlUtils.resolveInput("192.168.1.1:8080"))

        assertEquals("https://www.google.com/search?q=weather+today", UrlUtils.resolveInput("weather today"))
        assertEquals("https://www.google.com/search?q=javascript%3Aalert%281%29", UrlUtils.resolveInput("javascript:alert(1)"))
    }
}

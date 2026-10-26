package com.tdpham.navitvbrowser.data

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object PortalSuggestionsManager {

    @Volatile
    private var cachedSuggestionsJson: String? = null

    /**
     * Loads the suggestion portals JSON. Uses cached in-memory string if available,
     * otherwise reads from the bundled portal_suggestions.json asset.
     */
    fun getSuggestionsJson(context: Context): String {
        cachedSuggestionsJson?.let { return it }
        return try {
            context.assets.open("portal_suggestions.json").use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val json = reader.readText()
                cachedSuggestionsJson = json
                json
            }
        } catch (_: Exception) {
            "{}"
        }
    }

    /**
     * Clears the in-memory cache if suggestion portals are ever refreshed or customized.
     */
    fun invalidateCache() {
        cachedSuggestionsJson = null
    }
}

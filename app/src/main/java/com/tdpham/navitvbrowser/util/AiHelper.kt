package com.tdpham.navitvbrowser.util

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AiHelper {

    private var generativeModel: GenerativeModel? = null
    private var lastUsedKey: String? = null

    private fun getModel(): GenerativeModel? {
        val apiKey = RemoteConfigHelper.getGeminiApiKey()
        if (apiKey.isBlank()) {
            generativeModel = null
            lastUsedKey = null
            return null
        }

        if (generativeModel == null || lastUsedKey != apiKey) {
            generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )
            lastUsedKey = apiKey
        }
        return generativeModel
    }

    suspend fun summarizeText(text: String): Result<String> = withContext(Dispatchers.IO) {
        val model = getModel() ?: return@withContext Result.failure(Exception("API_KEY_MISSING"))
        
        if (text.isBlank()) {
            return@withContext Result.failure(Exception("EMPTY_TEXT"))
        }

        try {
            val response = model.generateContent(
                content {
                    text("Summarize the following web page content concisely for an Android TV user. Focus on the main points and keep it under 150 words: \n\n$text")
                }
            )
            val summary = response.text
            if (summary != null) {
                Result.success(summary)
            } else {
                Result.failure(Exception("NO_RESPONSE"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

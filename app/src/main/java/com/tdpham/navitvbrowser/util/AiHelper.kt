package com.tdpham.navitvbrowser.util

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tdpham.navitvbrowser.R

interface AiProvider {
    val name: String
    suspend fun summarize(text: String): Result<String>
}

class FirebaseVertexAiProvider : AiProvider {
    override val name: String = "FIREBASE_GEMINI"
    
    override suspend fun summarize(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val model = Firebase.vertexAI.generativeModel("gemini-1.5-flash")
            val response = model.generateContent(content {
                text("${AiHelper.SYSTEM_PROMPT} \n\n$text")
            })
            response.text?.let { Result.success(it) } ?: Result.failure(Exception("EMPTY_RESPONSE"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class LocalExtractiveProvider : AiProvider {
    override val name: String = "LOCAL"
    
    override suspend fun summarize(text: String): Result<String> {
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val extracted = if (sentences.isNotEmpty()) {
            sentences.take(3).joinToString(" ")
        } else {
            text.take(300)
        }
        return Result.success(extracted)
    }
}

object AiHelper {
    const val SYSTEM_PROMPT = "Summarize the following web page content concisely for an Android TV user. Focus on the main points and keep it under 150 words."

    suspend fun getSummaryWithFailover(context: Context, text: String): Result<String> {
        android.util.Log.d("TVNAV_DEBUG", "getSummaryWithFailover started. Text length: ${text.length}")
        // 1. Try Firebase Vertex AI
        val vertexResult = FirebaseVertexAiProvider().summarize(text)
        if (vertexResult.isSuccess) {
            android.util.Log.d("TVNAV_DEBUG", "Firebase Vertex AI Success")
            return vertexResult
        }

        // 2. Fallback: Local Extraction
        android.util.Log.d("TVNAV_DEBUG", "Firebase Vertex AI failed: ${vertexResult.exceptionOrNull()?.message}, falling back")
        return LocalExtractiveProvider().summarize(text).map { summary ->
            "LITE SUMMARY: $summary...\n\n${context.getString(R.string.ai_local_summary_note)}"
        }
    }
}

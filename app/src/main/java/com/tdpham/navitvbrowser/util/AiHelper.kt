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
            val modelName = RemoteConfigHelper.getAiModelName()
            val model = Firebase.vertexAI.generativeModel(modelName)
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
            val taken = sentences.take(3).joinToString(" ")
            if (sentences.size > 3) "$taken..." else taken
        } else {
            val taken = text.take(300)
            if (text.length > 300) "$taken..." else taken
        }
        return Result.success(extracted)
    }
}

object AiHelper {
    const val SYSTEM_PROMPT = "Summarize the following web page content concisely for an Android TV user. Respond in the same language as the page content. Focus on the main points and keep it under 150 words."

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
        val prefix = context.getString(R.string.ai_local_summary_prefix)
        return LocalExtractiveProvider().summarize(text).map { summary ->
            "$prefix $summary\n\n${context.getString(R.string.ai_local_summary_note)}"
        }
    }
}

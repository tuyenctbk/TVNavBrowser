package com.tdpham.navitvbrowser.util

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tdpham.navitvbrowser.R
import kotlin.math.max

interface AiProvider {
    val name: String
    suspend fun summarize(context: Context, text: String): Result<String>
}

class FirebaseVertexAiProvider : AiProvider {
    override val name: String = "FIREBASE_GEMINI"

    override suspend fun summarize(context: Context, text: String): Result<String> = withContext(Dispatchers.IO) {
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

/**
 * Zero-cost on-device TF-IDF / TextRank-inspired extractive summarizer.
 * Works 100% offline with zero API fees and zero quota limits.
 */
class LocalExtractiveProvider : AiProvider {
    override val name: String = "LOCAL_SMART_EXTRACTOR"

    override suspend fun summarize(context: Context, text: String): Result<String> = withContext(Dispatchers.Default) {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        val wordCount = clean.split(" ").count { it.isNotBlank() }
        val readMinutes = max(1, wordCount / 200)
        val readingTimeHeader = context.getString(R.string.ai_reading_time_format, readMinutes, wordCount)

        val rawSentences = clean.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length in 35..260 && !it.contains("cookie", ignoreCase = true) && !it.contains("subscribe", ignoreCase = true) }

        if (rawSentences.isEmpty()) {
            val fallback = if (clean.length > 360) "${clean.take(360)}..." else clean
            return@withContext Result.success("$readingTimeHeader\n\n• $fallback")
        }

        // Compute word frequency for keyword weighting
        val stopWords = setOf(
            "the", "and", "for", "that", "this", "with", "from", "your", "have", "more",
            "will", "are", "was", "were", "has", "had", "but", "not", "you", "all", "can",
            "about", "into", "their", "they", "what", "when", "which", "who", "would", "there"
        )
        val freq = mutableMapOf<String, Int>()
        clean.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).forEach { w ->
            if (w.length >= 4 && w !in stopWords) {
                freq[w] = (freq[w] ?: 0) + 1
            }
        }

        // Score sentences by keyword density + position bonus (lead sentences carry high signal)
        val scored = rawSentences.mapIndexed { index, sentence ->
            val words = sentence.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 }
            val keywordScore = words.sumOf { freq[it] ?: 0 }.toDouble() / max(1, words.size)
            val positionBonus = when {
                index == 0 -> 2.5
                index < 3 -> 1.5
                else -> 1.0
            }
            Triple(index, sentence, keywordScore * positionBonus)
        }

        val topSentences = scored
            .sortedByDescending { it.third }
            .take(4)
            .sortedBy { it.first }
            .map { "• ${it.second}" }

        val formatted = buildString {
            append("$readingTimeHeader\n\n")
            append(topSentences.joinToString("\n\n"))
        }
        Result.success(formatted)
    }
}

object AiHelper {
    const val SYSTEM_PROMPT = "Summarize the following web page content concisely for an Android TV user. Respond in the same language as the page content. Focus on the main points and keep it under 150 words."

    suspend fun getSummaryWithFailover(context: Context, text: String): Result<String> {
        // 1. Try Firebase Vertex AI if configured and reachable
        val vertexResult = FirebaseVertexAiProvider().summarize(context, text)
        if (vertexResult.isSuccess) {
            return vertexResult
        }

        // 2. Zero-Cost On-Device Smart Extractive Summarizer (100% Free & Offline)
        val prefix = context.getString(R.string.ai_local_summary_prefix)
        return LocalExtractiveProvider().summarize(context, text).map { summary ->
            "$prefix\n$summary\n\n${context.getString(R.string.ai_local_summary_note)}"
        }
    }
}

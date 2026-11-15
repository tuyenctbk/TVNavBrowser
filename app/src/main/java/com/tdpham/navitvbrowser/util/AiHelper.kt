package com.tdpham.navitvbrowser.util

import android.content.Context
import android.os.SystemClock
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.tdpham.navitvbrowser.R
import kotlin.math.max

data class AiGenerationResponse(
    val text: String,
    val usedTier: AiModelConfig.CapabilityTier,
    val fromCache: Boolean = false,
    val rateLimitedCascaded: Boolean = false
)

interface AiProvider {
    val name: String
    suspend fun summarize(context: Context, text: String): Result<String>
}

class FirebaseVertexAiProvider(
    private val modelNameProvider: () -> String = { AiModelConfig.getPrimaryReasoningModel() }
) : AiProvider {
    override val name: String = "FIREBASE_GEMINI"

    override suspend fun summarize(context: Context, text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val modelName = modelNameProvider()
            val model = Firebase.vertexAI.generativeModel(modelName)
            val response = withTimeout(35_000L) {
                model.generateContent(content {
                    text("${AiHelper.SYSTEM_PROMPT}\n\n$text")
                })
            }
            response.text?.takeIf { it.isNotBlank() }?.let { Result.success(it) }
                ?: Result.failure(Exception("EMPTY_RESPONSE"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun answerQuestion(question: String, pageContext: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val modelName = modelNameProvider()
            val model = Firebase.vertexAI.generativeModel(modelName)
            val response = withTimeout(35_000L) {
                model.generateContent(content {
                    text(
                        "You are a concise TV browser assistant. Answer the user's question in 2-4 bullet points " +
                        "using the provided web page text. Respond in the same language as the question.\n\n" +
                        "Question: $question\n\nPage Content:\n$pageContext"
                    )
                })
            }
            response.text?.takeIf { it.isNotBlank() }?.let { Result.success(it) }
                ?: Result.failure(Exception("EMPTY_RESPONSE"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Tier 3: Zero-cost on-device TF-IDF / TextRank-inspired extractive summarizer & Reflex Q&A Engine.
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

    /**
     * Instant Local Reflex Engine (Playbook Section 2):
     * Finds the sentences on the page that best match the user's question keywords using 0 cloud tokens.
     */
    suspend fun answerQuestionLocally(context: Context, question: String, pageText: String): Result<String> = withContext(Dispatchers.Default) {
        val clean = pageText.replace(Regex("\\s+"), " ").trim()
        val queryTokens = question.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }
            .toSet()

        val sentences = clean.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length in 25..280 }

        if (sentences.isEmpty()) {
            return@withContext summarize(context, pageText)
        }

        val matched = sentences.mapIndexed { idx, s ->
            val words = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            val overlap = words.count { it in queryTokens }
            Triple(idx, s, overlap)
        }.filter { it.third > 0 }
            .sortedWith(compareByDescending<Triple<Int, String, Int>> { it.third }.thenBy { it.first })
            .take(3)
            .map { "• ${it.second}" }

        if (matched.isNotEmpty()) {
            Result.success(matched.joinToString("\n\n"))
        } else {
            summarize(context, pageText)
        }
    }
}

object AiHelper {
    const val SYSTEM_PROMPT = "Summarize the following web page content concisely for an Android TV user. Respond in the same language as the page content. Focus on the main points and keep it under 150 words."

    /** Fingerprint Deduplication Cache (45s TTL) — Playbook Section 1.C */
    private const val CACHE_TTL_MS = 45_000L
    private data class CacheEntry(
        val fingerprint: Int,
        val response: AiGenerationResponse,
        val timestampMs: Long
    )
    @Volatile
    private var lastCacheEntry: CacheEntry? = null

    suspend fun getSummaryDetailed(context: Context, text: String): Result<AiGenerationResponse> {
        val cleanInput = text.trim()
        if (cleanInput.isEmpty()) return Result.failure(Exception("EMPTY_TEXT"))

        // 1. Check 45-second Fingerprint Deduplication Cache (0 tokens on double-tap)
        val fingerprint = cleanInput.hashCode()
        val now = SystemClock.elapsedRealtime()
        lastCacheEntry?.let { cached ->
            if (cached.fingerprint == fingerprint && (now - cached.timestampMs) <= CACHE_TTL_MS) {
                return Result.success(cached.response.copy(fromCache = true))
            }
        }

        val preferredTier = UsageQuotaManager.resolvePreferredTier(context)
        var cascadedFromPrimary = false

        // 2. Tier 1: Primary Standard Model (when quota < 80% and Eco Lite is off)
        if (preferredTier == AiModelConfig.CapabilityTier.TIER_1_PRIMARY_CLOUD) {
            val primaryProvider = FirebaseVertexAiProvider { AiModelConfig.getPrimaryReasoningModel() }
            val primaryResult = primaryProvider.summarize(context, cleanInput)
            if (primaryResult.isSuccess) {
                val out = AiGenerationResponse(
                    text = primaryResult.getOrThrow(),
                    usedTier = AiModelConfig.CapabilityTier.TIER_1_PRIMARY_CLOUD
                )
                UsageQuotaManager.recordSuccess(context, UsageQuotaManager.AiFeature.SUMMARY, out.usedTier)
                lastCacheEntry = CacheEntry(fingerprint, out, SystemClock.elapsedRealtime())
                return Result.success(out)
            }
            // Primary failed or hit 429 rate-limit -> cascade to Tier 2 Economy Lite
            cascadedFromPrimary = true
            UsageQuotaManager.recordFallbackCascade(context)
        }

        // 3. Tier 2: High-Efficiency Economy Lite Model (when Eco Lite is on, >= 80% quota, or cascaded from Tier 1)
        if (preferredTier != AiModelConfig.CapabilityTier.TIER_3_LOCAL_ZERO_COST) {
            val liteProvider = FirebaseVertexAiProvider { AiModelConfig.getEconomyLiteModel() }
            val liteResult = liteProvider.summarize(context, cleanInput)
            if (liteResult.isSuccess) {
                val out = AiGenerationResponse(
                    text = liteResult.getOrThrow(),
                    usedTier = AiModelConfig.CapabilityTier.TIER_2_ECONOMY_LITE,
                    rateLimitedCascaded = cascadedFromPrimary
                )
                UsageQuotaManager.recordSuccess(context, UsageQuotaManager.AiFeature.SUMMARY, out.usedTier)
                lastCacheEntry = CacheEntry(fingerprint, out, SystemClock.elapsedRealtime())
                return Result.success(out)
            }
            UsageQuotaManager.recordFallbackCascade(context)
        }

        // 4. Tier 3: Zero-Cost On-Device Smart Extractive Summarizer (100% Free & Offline, 0 cloud credits charged)
        val prefix = context.getString(R.string.ai_local_summary_prefix)
        return LocalExtractiveProvider().summarize(context, cleanInput).map { summary ->
            val formatted = "$prefix\n$summary\n\n${context.getString(R.string.ai_local_summary_note)}"
            val out = AiGenerationResponse(
                text = formatted,
                usedTier = AiModelConfig.CapabilityTier.TIER_3_LOCAL_ZERO_COST,
                rateLimitedCascaded = cascadedFromPrimary
            )
            lastCacheEntry = CacheEntry(fingerprint, out, SystemClock.elapsedRealtime())
            out
        }
    }

    suspend fun getSummaryWithFailover(context: Context, text: String): Result<String> {
        return getSummaryDetailed(context, text).map { it.text }
    }

    /**
     * Context-Aware "Ask This Page" Q&A Engine with 45-second deduplication and 3-Tier Free-Tier Cascade:
     * Tier 1 (Primary Cloud) -> Tier 2 (Economy Lite) -> Tier 3 (Zero-Cost Local Extractive Q&A).
     */
    suspend fun askPageQuestionDetailed(
        context: Context,
        question: String,
        pageText: String
    ): Result<AiGenerationResponse> {
        val cleanQuestion = question.trim()
        val cleanPage = pageText.trim()
        if (cleanQuestion.isEmpty() || cleanPage.isEmpty()) {
            return Result.failure(Exception("EMPTY_TEXT"))
        }

        val fingerprint = (cleanQuestion.lowercase() + "||" + cleanPage.take(1500)).hashCode()
        val now = SystemClock.elapsedRealtime()
        lastCacheEntry?.let { cached ->
            if (cached.fingerprint == fingerprint && (now - cached.timestampMs) <= CACHE_TTL_MS) {
                return Result.success(cached.response.copy(fromCache = true))
            }
        }

        val preferredTier = UsageQuotaManager.resolvePreferredTier(context)
        var cascadedFromPrimary = false

        // Tier 1: Primary Standard Model
        if (preferredTier == AiModelConfig.CapabilityTier.TIER_1_PRIMARY_CLOUD) {
            val primaryProvider = FirebaseVertexAiProvider { AiModelConfig.getPrimaryReasoningModel() }
            val res = primaryProvider.answerQuestion(cleanQuestion, cleanPage)
            if (res.isSuccess) {
                val out = AiGenerationResponse(
                    text = res.getOrThrow(),
                    usedTier = AiModelConfig.CapabilityTier.TIER_1_PRIMARY_CLOUD
                )
                UsageQuotaManager.recordSuccess(context, UsageQuotaManager.AiFeature.CHAT, out.usedTier)
                lastCacheEntry = CacheEntry(fingerprint, out, SystemClock.elapsedRealtime())
                return Result.success(out)
            }
            cascadedFromPrimary = true
            UsageQuotaManager.recordFallbackCascade(context)
        }

        // Tier 2: High-Efficiency Economy Lite Model
        if (preferredTier != AiModelConfig.CapabilityTier.TIER_3_LOCAL_ZERO_COST) {
            val liteProvider = FirebaseVertexAiProvider { AiModelConfig.getEconomyLiteModel() }
            val liteRes = liteProvider.answerQuestion(cleanQuestion, cleanPage)
            if (liteRes.isSuccess) {
                val out = AiGenerationResponse(
                    text = liteRes.getOrThrow(),
                    usedTier = AiModelConfig.CapabilityTier.TIER_2_ECONOMY_LITE,
                    rateLimitedCascaded = cascadedFromPrimary
                )
                UsageQuotaManager.recordSuccess(context, UsageQuotaManager.AiFeature.CHAT, out.usedTier)
                lastCacheEntry = CacheEntry(fingerprint, out, SystemClock.elapsedRealtime())
                return Result.success(out)
            }
            UsageQuotaManager.recordFallbackCascade(context)
        }

        // Tier 3: Zero-Cost On-Device Reflex Q&A Engine (0 cloud credits)
        return LocalExtractiveProvider().answerQuestionLocally(context, cleanQuestion, cleanPage).map { answer ->
            val formatted = "$answer\n\n${context.getString(R.string.ai_local_summary_note)}"
            val out = AiGenerationResponse(
                text = formatted,
                usedTier = AiModelConfig.CapabilityTier.TIER_3_LOCAL_ZERO_COST,
                rateLimitedCascaded = cascadedFromPrimary
            )
            lastCacheEntry = CacheEntry(fingerprint, out, SystemClock.elapsedRealtime())
            out
        }
    }
}

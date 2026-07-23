package com.tdpham.navitvbrowser.util

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface AiProvider {
    val name: String
    suspend fun summarize(text: String): Result<String>
}

class GeminiProvider(private val apiKey: String, private val modelName: String) : AiProvider {
    override val name: String = "GEMINI"
    
    override suspend fun summarize(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val model = GenerativeModel(modelName = modelName, apiKey = apiKey)
            val response = model.generateContent(content {
                text("Summarize the following web page content concisely for an Android TV user. Focus on the main points and keep it under 150 words: \n\n$text")
            })
            response.text?.let { Result.success(it) } ?: Result.failure(Exception("EMPTY_RESPONSE"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class OpenAiCompatibleProvider(
    override val name: String,
    private val apiKey: String,
    private val endpoint: String,
    private val model: String,
    private val client: OkHttpClient
) : AiProvider {

    override suspend fun summarize(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are a concise summarizer for Android TV. Keep it under 150 words.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Summarize this: $text")
                    })
                })
            }

            val mediaType = "application/json".toMediaType()
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://navitv.browser") // Recommended for OpenRouter
                .addHeader("X-Title", "NaviTV Browser")             // Recommended for OpenRouter
                .post(payload.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    return@withContext Result.failure(Exception("HTTP_$code"))
                }
                val bodyString = response.body?.string() ?: return@withContext Result.failure(Exception("EMPTY_BODY"))
                val json = JSONObject(bodyString)
                val choices = json.optJSONArray("choices")
                if (choices == null || choices.length() == 0) return@withContext Result.failure(Exception("NO_CHOICES"))
                
                val summary = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                Result.success(summary)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class LocalExtractiveProvider : AiProvider {
    override val name: String = "LOCAL"
    
    override suspend fun summarize(text: String): Result<String> = Result.success(
        text.split(Regex("(?<=[.!?])\\s+"))
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString(" ")
            .let { "LITE SUMMARY: $it..." }
    )
}

object AiHelper {
    private val sharedClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getSummaryWithFailover(context: Context, text: String, onRetry: (String) -> Unit): Result<String> {
        // 1. Try Manual BYOK if enabled
        if (AppPreferences.getAiMode(context) == "MANUAL") {
            val engine = AppPreferences.getAiEngine(context)
            val provider = createProvider(
                engine = engine,
                key = AppPreferences.getAiCustomKey(context),
                endpoint = AppPreferences.getAiCustomEndpoint(context),
                model = AppPreferences.getAiCustomModel(context)
            )
            return provider?.summarize(text) ?: Result.failure(Exception("INVALID_CONFIG"))
        }

        // 2. Try Dev Cascade
        val cascade = RemoteConfigHelper.getAiRoutingCascade()
        cascade.forEachIndexed { index, engineName ->
            if (index > 0) {
                onRetry(engineName)
            }
            val provider = createDevProvider(engineName) ?: return@forEachIndexed
            val result = provider.summarize(text)
            if (result.isSuccess) return result
        }

        // 3. Last Resort: Local
        return LocalExtractiveProvider().summarize(text)
    }

    private fun createProvider(engine: String, key: String, endpoint: String, model: String): AiProvider? {
        if (key.isBlank()) return null
        return when (engine) {
            "GEMINI" -> GeminiProvider(key, model.ifBlank { "gemini-1.5-flash" })
            "OPENAI", "GROQ", "DEEPSEEK", "OPENROUTER", "MISTRAL", "SILICONFLOW", "TOGETHER", "CEREBRAS" -> {
                val finalEndpoint = when {
                    endpoint.isNotBlank() -> endpoint
                    engine == "OPENAI" -> "https://api.openai.com/v1/chat/completions"
                    engine == "GROQ" -> "https://api.groq.com/openai/v1/chat/completions"
                    engine == "DEEPSEEK" -> "https://api.deepseek.com/chat/completions"
                    engine == "OPENROUTER" -> "https://openrouter.ai/api/v1/chat/completions"
                    engine == "MISTRAL" -> "https://api.mistral.ai/v1/chat/completions"
                    engine == "SILICONFLOW" -> "https://api.siliconflow.cn/v1/chat/completions"
                    engine == "TOGETHER" -> "https://api.together.xyz/v1/chat/completions"
                    engine == "CEREBRAS" -> "https://api.cerebras.ai/v1/chat/completions"
                    else -> ""
                }
                val finalModel = when {
                    model.isNotBlank() -> model
                    engine == "OPENAI" -> "gpt-4o-mini"
                    engine == "GROQ" -> "llama-3.3-70b-versatile"
                    engine == "DEEPSEEK" -> "deepseek-chat"
                    engine == "OPENROUTER" -> "google/gemini-flash-1.5"
                    engine == "MISTRAL" -> "mistral-small-latest"
                    engine == "SILICONFLOW" -> "deepseek-ai/DeepSeek-V3"
                    engine == "TOGETHER" -> "meta-llama/Llama-3.3-70B-Instruct-Turbo"
                    engine == "CEREBRAS" -> "llama3.3-70b"
                    else -> "gpt-4o-mini"
                }
                if (finalEndpoint.isBlank()) null else OpenAiCompatibleProvider(engine, key, finalEndpoint, finalModel, sharedClient)
            }
            else -> null
        }
    }

    private fun createDevProvider(engine: String): AiProvider? {
        return when (engine) {
            "GEMINI" -> {
                val key = RemoteConfigHelper.getGeminiApiKey()
                if (key.isNotBlank()) GeminiProvider(key, "gemini-1.5-flash") else null
            }
            "GROQ" -> {
                val key = RemoteConfigHelper.getGroqApiKey()
                if (key.isNotBlank()) OpenAiCompatibleProvider(engine, key, "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile", sharedClient) else null
            }
            "DEEPSEEK" -> {
                val key = RemoteConfigHelper.getDeepSeekApiKey()
                if (key.isNotBlank()) OpenAiCompatibleProvider(engine, key, "https://api.deepseek.com/chat/completions", "deepseek-chat", sharedClient) else null
            }
            "OPENROUTER" -> {
                val key = RemoteConfigHelper.getOpenRouterApiKey()
                if (key.isNotBlank()) OpenAiCompatibleProvider(engine, key, "https://openrouter.ai/api/v1/chat/completions", "google/gemini-flash-1.5", sharedClient) else null
            }
            "MISTRAL" -> {
                val key = RemoteConfigHelper.getMistralApiKey()
                if (key.isNotBlank()) OpenAiCompatibleProvider(engine, key, "https://api.mistral.ai/v1/chat/completions", "mistral-small-latest", sharedClient) else null
            }
            "SILICONFLOW" -> {
                val key = RemoteConfigHelper.getSiliconFlowApiKey()
                if (key.isNotBlank()) OpenAiCompatibleProvider(engine, key, "https://api.siliconflow.cn/v1/chat/completions", "deepseek-ai/DeepSeek-V3", sharedClient) else null
            }
            "TOGETHER" -> {
                val key = RemoteConfigHelper.getTogetherApiKey()
                if (key.isNotBlank()) OpenAiCompatibleProvider(engine, key, "https://api.together.xyz/v1/chat/completions", "meta-llama/Llama-3.3-70B-Instruct-Turbo", sharedClient) else null
            }
            "CEREBRAS" -> {
                val key = RemoteConfigHelper.getCerebrasApiKey()
                if (key.isNotBlank()) OpenAiCompatibleProvider(engine, key, "https://api.cerebras.ai/v1/chat/completions", "llama3.3-70b", sharedClient) else null
            }
            else -> null
        }
    }
}

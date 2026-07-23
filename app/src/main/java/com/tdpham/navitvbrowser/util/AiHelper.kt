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
                text("${AiHelper.SYSTEM_PROMPT} \n\n$text")
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
                        put("content", AiHelper.SYSTEM_PROMPT)
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
    const val SYSTEM_PROMPT = "Summarize the following web page content concisely for an Android TV user. Focus on the main points and keep it under 150 words."

    private val sharedClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getSummaryWithFailover(context: Context, text: String, onRetry: (String) -> Unit): Result<String> {
        // 1. Try Manual BYOK if enabled
        if (AppPreferences.getAiMode(context) == "MANUAL") {
            val engineId = AppPreferences.getAiEngine(context)
            val provider = createProvider(
                engineId = engineId,
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

    private fun createProvider(engineId: String, key: String, endpoint: String, model: String): AiProvider? {
        if (key.isBlank()) return null
        val engine = AiEngine.fromId(engineId)
        
        return when (engine) {
            AiEngine.GEMINI -> GeminiProvider(key, model.ifBlank { engine.defaultModel })
            else -> {
                val finalEndpoint = endpoint.ifBlank { engine.defaultEndpoint }
                val finalModel = model.ifBlank { engine.defaultModel }
                if (finalEndpoint.isBlank()) null else OpenAiCompatibleProvider(engine.id, key, finalEndpoint, finalModel, sharedClient)
            }
        }
    }

    private fun createDevProvider(engineId: String): AiProvider? {
        val engine = AiEngine.fromId(engineId)
        val key = when (engine) {
            AiEngine.OPENAI -> RemoteConfigHelper.getOpenaiApiKey()
            AiEngine.GEMINI -> RemoteConfigHelper.getGeminiApiKey()
            AiEngine.GROQ -> RemoteConfigHelper.getGroqApiKey()
            AiEngine.DEEPSEEK -> RemoteConfigHelper.getDeepSeekApiKey()
            AiEngine.OPENROUTER -> RemoteConfigHelper.getOpenRouterApiKey()
            AiEngine.MISTRAL -> RemoteConfigHelper.getMistralApiKey()
            AiEngine.SILICONFLOW -> RemoteConfigHelper.getSiliconFlowApiKey()
            AiEngine.TOGETHER -> RemoteConfigHelper.getTogetherApiKey()
            AiEngine.CEREBRAS -> RemoteConfigHelper.getCerebrasApiKey()
        }
        
        if (key.isBlank()) return null
        
        return createProvider(engine.id, key, "", "")
    }
}


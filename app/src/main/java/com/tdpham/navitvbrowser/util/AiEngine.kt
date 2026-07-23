package com.tdpham.navitvbrowser.util

enum class AiEngine(
    val id: String,
    val defaultEndpoint: String = "",
    val defaultModel: String = ""
) {
    GEMINI("GEMINI", defaultModel = "gemini-1.5-flash"),
    OPENAI("OPENAI", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini"),
    GROQ("GROQ", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile"),
    DEEPSEEK("DEEPSEEK", "https://api.deepseek.com/chat/completions", "deepseek-chat"),
    OPENROUTER("OPENROUTER", "https://openrouter.ai/api/v1/chat/completions", "google/gemini-flash-1.5"),
    MISTRAL("MISTRAL", "https://api.mistral.ai/v1/chat/completions", "mistral-small-latest"),
    SILICONFLOW("SILICONFLOW", "https://api.siliconflow.cn/v1/chat/completions", "deepseek-ai/DeepSeek-V3"),
    TOGETHER("TOGETHER", "https://api.together.xyz/v1/chat/completions", "meta-llama/Llama-3.3-70B-Instruct-Turbo"),
    CEREBRAS("CEREBRAS", "https://api.cerebras.ai/v1/chat/completions", "llama3.3-70b");

    companion object {
        fun fromId(id: String): AiEngine = values().find { it.id == id } ?: GEMINI
        fun getAllIds(): Array<String> = values().map { it.id }.toTypedArray()
    }
}

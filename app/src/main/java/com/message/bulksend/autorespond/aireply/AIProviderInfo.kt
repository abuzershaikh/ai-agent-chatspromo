package com.message.bulksend.autorespond.aireply

data class AIProviderInfo(
    val name: String,
    val description: String,
    val learnMoreUrl: String,
    val poweredBy: String,
    val models: List<String>,
    val apiKeyUrl: String
)

object AIProviderData {
    fun getProviderInfo(provider: AIProvider): AIProviderInfo {
        return when (provider) {
            AIProvider.CHATSPROMO -> AIProviderInfo(
                name = "ChatsPromo AI",
                description = "Built-in AI assistant - no API key required. Ready to use instantly!",
                learnMoreUrl = "",
                poweredBy = "Powered by ChatsPromo",
                models = listOf("chatspromo-v1"),
                apiKeyUrl = ""
            )
            AIProvider.CHATGPT -> AIProviderInfo(
                name = "ChatGPT",
                description = "Connect an AI assistant to send an artificial intelligence-based reply.",
                learnMoreUrl = "https://platform.openai.com/docs",
                poweredBy = "Powered by OpenAI",
                models = listOf(
                    "gpt-4o-mini",
                    "gpt-4o",
                    "gpt-4.1-mini",
                    "gpt-4.1",
                    "gpt-3.5-turbo",
                    "gpt-5-mini",
                    "gpt-5"
                ),
                apiKeyUrl = "https://platform.openai.com/api-keys"
            )
            AIProvider.GEMINI -> AIProviderInfo(
                name = "Gemini",
                description = "Use Google's advanced AI model for intelligent responses.",
                learnMoreUrl = "https://ai.google.dev/gemini-api/docs/models/gemini",
                poweredBy = "Powered by Google AI",
                models = listOf(
                    "gemini-3.1-pro-preview",
                    "gemini-3-flash-preview",
                    "gemini-3.1-flash-lite-preview",
                    "gemini-2.5-pro",
                    "gemini-2.5-flash",
                    "gemini-2.5-flash-lite"
                ),
                apiKeyUrl = "https://aistudio.google.com/app/apikey"
            )
        }
    }
}

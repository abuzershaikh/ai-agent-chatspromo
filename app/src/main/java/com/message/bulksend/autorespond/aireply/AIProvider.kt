package com.message.bulksend.autorespond.aireply

enum class AIProvider(val displayName: String, val defaultModel: String) {
    CHATSPROMO("ChatsPromo AI", "chatspromo-v1"),
    CHATGPT("ChatGPT", "gpt-4o-mini"),
    GEMINI("Gemini", "gemini-2.5-flash")
}

data class AIConfig(
    val provider: AIProvider,
    val apiKey: String,
    val model: String,
    val template: String = "",
    val enableThinking: Boolean = false,
    val responseLength: ResponseLength = ResponseLength.SHORT,
    val temperature: Double = 0.7,
    val maxTokens: Int = 500
)

enum class ResponseLength(val displayName: String, val instruction: String, val tokens: Int) {
    SHORT("Short", "Keep response brief and concise (1-2 sentences max)", 150),
    MEDIUM("Medium", "Provide a balanced response (2-3 sentences)", 300),
    LONG("Long", "Give detailed response with full information", 800)
}

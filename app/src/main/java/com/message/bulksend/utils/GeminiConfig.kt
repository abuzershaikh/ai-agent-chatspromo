package com.message.bulksend.utils

object GeminiConfig {
    // API Key - to be fetched from Firebase or set dynamically
    var API_KEY: String = ""
    const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    
    // API request headers
    fun getHeaders(): Map<String, String> {
        return mapOf(
            "Content-Type" to "application/json",
            "X-goog-api-key" to API_KEY
        )
    }
}

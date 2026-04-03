package com.message.bulksend.autorespond.aireply

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class AIConfigManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_config", Context.MODE_PRIVATE)
    private val gson = Gson()

    private fun normalizeModel(provider: AIProvider, model: String): String {
        if (provider == AIProvider.CHATSPROMO) {
            return provider.defaultModel
        }

        if (provider != AIProvider.GEMINI) {
            return model
        }

        return when (model) {
            "gemini-3-pro-preview" -> "gemini-3.1-pro-preview"
            "gemini-2.0-flash-exp",
            "gemini-2.0-flash-st",
            "gemini-1.5-flash" -> "gemini-2.5-flash"
            "gemini-1.5-pro",
            "gemini-pro" -> "gemini-2.5-pro"
            "gemini-3.1-pro-preview",
            "gemini-3-flash-preview",
            "gemini-3.1-flash-lite-preview",
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite" -> model
            else -> provider.defaultModel
        }
    }
    
    fun saveConfig(
        provider: AIProvider, 
        apiKey: String, 
        model: String, 
        template: String = "",
        enableThinking: Boolean = false,
        responseLength: ResponseLength = ResponseLength.SHORT,
        temperature: Double = 0.7
    ) {
        val normalizedModel = normalizeModel(provider, model)

        prefs.edit().apply {
            putString("${provider.name}_api_key", apiKey)
            putString("${provider.name}_model", normalizedModel)
            putString("${provider.name}_template", template)
            putBoolean("${provider.name}_thinking", enableThinking)
            putString("${provider.name}_response_length", responseLength.name)
            putFloat("${provider.name}_temperature", temperature.toFloat())
            apply()
        }
    }
    
    fun getConfig(provider: AIProvider): AIConfig {
        val responseLengthName = prefs.getString("${provider.name}_response_length", ResponseLength.SHORT.name) ?: ResponseLength.SHORT.name
        val responseLength = try {
            ResponseLength.valueOf(responseLengthName)
        } catch (e: Exception) {
            ResponseLength.SHORT
        }
        val savedModel = prefs.getString("${provider.name}_model", provider.defaultModel) ?: provider.defaultModel
        val normalizedModel = normalizeModel(provider, savedModel)

        if (normalizedModel != savedModel) {
            prefs.edit().putString("${provider.name}_model", normalizedModel).apply()
        }

        val apiKey =
            if (provider == AIProvider.CHATSPROMO) {
                "server-managed"
            } else {
                prefs.getString("${provider.name}_api_key", "") ?: ""
            }

        return AIConfig(
            provider = provider,
            apiKey = apiKey,
            model = normalizedModel,
            template = prefs.getString("${provider.name}_template", "") ?: "",
            enableThinking = prefs.getBoolean("${provider.name}_thinking", false),
            responseLength = responseLength,
            temperature = prefs.getFloat("${provider.name}_temperature", 0.7f).toDouble(),
            maxTokens = responseLength.tokens
        )
    }
    
    fun getTemplate(provider: AIProvider): String {
        return prefs.getString("${provider.name}_template", "") ?: ""
    }
    
    fun saveTemplate(provider: AIProvider, template: String) {
        prefs.edit().putString("${provider.name}_template", template).apply()
    }
}

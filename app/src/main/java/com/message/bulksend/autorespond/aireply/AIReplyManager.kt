package com.message.bulksend.autorespond.aireply

import android.content.Context
import android.content.SharedPreferences

class AIReplyManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_reply_state", Context.MODE_PRIVATE)

    private fun normalizeProvider(provider: AIProvider): AIProvider {
        return when (provider) {
            AIProvider.CHATSPROMO -> AIProvider.GEMINI
            else -> provider
        }
    }
    
    fun isAIReplyEnabled(): Boolean {
        return prefs.getBoolean("ai_reply_enabled", false)
    }
    
    fun setAIReplyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("ai_reply_enabled", enabled).apply()
    }
    
    fun getSelectedProvider(): AIProvider {
        val providerName = prefs.getString("selected_provider", AIProvider.GEMINI.name)
        return try {
            normalizeProvider(AIProvider.valueOf(providerName ?: AIProvider.GEMINI.name))
        } catch (e: Exception) {
            AIProvider.GEMINI
        }
    }
    
    fun setSelectedProvider(provider: AIProvider) {
        val normalizedProvider = normalizeProvider(provider)
        prefs.edit().putString("selected_provider", normalizedProvider.name).apply()
    }
}

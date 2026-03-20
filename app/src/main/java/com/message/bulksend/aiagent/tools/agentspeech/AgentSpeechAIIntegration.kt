package com.message.bulksend.aiagent.tools.agentspeech

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * AI Integration for Agent Speech
 * Provides context to AI Agent about speech capabilities
 */
class AgentSpeechAIIntegration(private val context: Context) {
    private val speechManager = AgentSpeechManager.getInstance(context)
    
    companion object {
        private const val TAG = "AgentSpeechAI"
    }
    
    /**
     * Get speech configuration for AI context
     */
    suspend fun getSpeechContextForAI(): String {
        return try {
            withTimeoutOrNull(2000) {
                val settings = speechManager.getSettings().first()
                
                if (settings == null || !settings.isEnabled) {
                    return@withTimeoutOrNull ""
                }
                
                buildString {
                    appendLine("## 🎤 Voice Reply System")
                    appendLine()
                    appendLine("**Status**: ENABLED")
                    appendLine("**Language**: ${settings.language}")
                    appendLine()
                    appendLine("### How to Send Voice Replies:")
                    appendLine()
                    appendLine("When you want to send a voice reply instead of text:")
                    appendLine("1. Generate your text response normally")
                    appendLine("2. The system will automatically convert it to speech")
                    appendLine("3. Voice will be sent to the customer")
                    appendLine()
                    appendLine("### Voice Reply Guidelines:")
                    appendLine("- Keep responses clear and concise")
                    appendLine("- Use simple language for better speech quality")
                    appendLine("- Avoid special characters and emojis in voice replies")
                    appendLine("- Voice replies work best for short messages (under 500 characters)")
                    appendLine()
                    appendLine("### Current Configuration:")
                    appendLine("- Voice Language: ${getLanguageName(settings.language)}")
                    appendLine("- User: ${settings.userEmail}")
                    appendLine()
                    appendLine("**Note**: Voice replies are automatically queued and processed in order.")
                }
            } ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting speech context", e)
            ""
        }
    }
    
    /**
     * Check if speech is enabled
     */
    suspend fun isSpeechEnabled(): Boolean {
        return try {
            val enabled = speechManager.isEnabled()
            Log.d(TAG, "Speech enabled check: $enabled")
            enabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking speech status", e)
            false
        }
    }
    
    /**
     * Queue text for speech conversion
     * Returns queue ID or -1 if failed
     */
    suspend fun queueTextForSpeech(text: String, phoneNumber: String): Long {
        return try {
            if (!speechManager.isEnabled()) {
                Log.d(TAG, "Speech not enabled, skipping")
                return -1
            }
            
            // Clean text for speech (remove emojis, special chars)
            val cleanText = cleanTextForSpeech(text)
            
            if (cleanText.isBlank()) {
                Log.w(TAG, "Text is empty after cleaning")
                return -1
            }
            
            val queueId = speechManager.addToQueue(cleanText, phoneNumber)
            Log.d(TAG, "Text queued for speech: ID=$queueId")
            
            queueId
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing text for speech", e)
            -1
        }
    }
    
    /**
     * Clean text for speech conversion
     * Removes emojis, special characters, etc.
     */
    private fun cleanTextForSpeech(text: String): String {
        return text
            // Remove emojis
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            // Remove multiple spaces
            .replace(Regex("\\s+"), " ")
            // Remove special markdown
            .replace("**", "")
            .replace("__", "")
            .replace("~~", "")
            // Trim
            .trim()
    }
    
    /**
     * Get language display name
     */
    private fun getLanguageName(code: String): String {
        return when (code) {
            "EN-IN" -> "English (Indian Accent)"
            "en", "EN-US" -> "English (American)"
            "EN-BR" -> "English (British)"
            "EN-AU" -> "English (Australian)"
            "es" -> "Spanish"
            "fr" -> "French"
            "zh" -> "Chinese"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            else -> code
        }
    }
    
    /**
     * Get audio file path for completed queue item
     */
    suspend fun getAudioPath(queueId: Long): String? {
        return try {
            speechManager.getAudioPath(queueId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio path", e)
            null
        }
    }
}

package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import com.message.bulksend.autorespond.ai.history.AIAgentHistoryManager
import com.message.bulksend.autorespond.ai.settings.AIAgentAdvancedSettings

/**
 * Handler for logging conversations to history sheet
 */
class ConversationLoggingHandler(
    private val historyManager: AIAgentHistoryManager,
    private val advancedSettings: AIAgentAdvancedSettings
) : MessageHandler {
    
    override fun getPriority(): Int = 150 // Execute after main processing
    
    override suspend fun handle(
        context: Context,
        message: String,
        response: String,
        senderPhone: String,
        senderName: String
    ): HandlerResult {
        if (!advancedSettings.autoSaveIntentHistory) {
            return HandlerResult(success = true)
        }
        
        try {
            val intent = "UNKNOWN" // Will be set by IntentDetectionHandler if enabled
            
            historyManager.logConversation(
                phoneNumber = senderPhone,
                userName = senderName,
                userMessage = message,
                aiReply = response,
                intent = intent
            )
            
            android.util.Log.d("ConversationLoggingHandler", "✅ Logged conversation")
            return HandlerResult(success = true)
            
        } catch (e: Exception) {
            android.util.Log.e("ConversationLoggingHandler", "❌ Error: ${e.message}")
            return HandlerResult(success = false)
        }
    }
}

package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context

/**
 * Base interface for cross-cutting concern handlers
 * Handlers process messages for features like reminders, lead scoring, etc.
 */
interface MessageHandler {
    
    /**
     * Handle the message and response
     * Returns result indicating if handler processed successfully
     */
    suspend fun handle(
        context: Context,
        message: String,
        response: String,
        senderPhone: String,
        senderName: String
    ): HandlerResult
    
    /**
     * Priority of handler execution (lower = earlier)
     */
    fun getPriority(): Int = 100
}

/**
 * Result of handler processing
 */
data class HandlerResult(
    val success: Boolean,
    val modifiedResponse: String? = null, // If handler wants to modify response
    val shouldStopChain: Boolean = false, // If true, stop executing remaining handlers
    val metadata: Map<String, Any> = emptyMap()
)

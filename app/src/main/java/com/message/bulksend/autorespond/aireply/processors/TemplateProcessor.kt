package com.message.bulksend.autorespond.aireply.processors

import android.content.Context

/**
 * Base interface for template-specific processors
 * Each template (CLINIC, ECOMMERCE, GENERAL) implements this interface
 */
interface TemplateProcessor {
    
    /**
     * Generate template-specific context for AI prompt
     */
    suspend fun generateContext(senderPhone: String): String
    
    /**
     * Process AI response and execute template-specific commands
     * Returns modified response after processing
     */
    suspend fun processResponse(
        response: String,
        message: String,
        senderPhone: String,
        senderName: String
    ): String
    
    /**
     * Get template identifier
     */
    fun getTemplateType(): String
}

/**
 * Result of response processing
 */
data class ProcessingResult(
    val modifiedResponse: String,
    val commandsExecuted: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)

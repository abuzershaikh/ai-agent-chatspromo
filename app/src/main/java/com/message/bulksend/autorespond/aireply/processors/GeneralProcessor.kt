package com.message.bulksend.autorespond.aireply.processors

import android.content.Context

/**
 * Processor for GENERAL template (fallback)
 * Handles basic conversation without template-specific logic
 */
class GeneralProcessor(private val context: Context) : TemplateProcessor {
    
    override fun getTemplateType(): String = "GENERAL"
    
    override suspend fun generateContext(senderPhone: String): String {
        // No additional context for general template
        return ""
    }
    
    override suspend fun processResponse(
        response: String,
        message: String,
        senderPhone: String,
        senderName: String
    ): String {
        // No special processing for general template
        return response
    }
}

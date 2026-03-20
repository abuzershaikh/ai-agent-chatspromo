package com.message.bulksend.autorespond.aireply.processors

import android.content.Context

/**
 * Processor for CUSTOM template.
 *
 * Custom behavior is primarily driven via AIAgentContextBuilder and
 * custom-template settings. This processor keeps runtime processing stable.
 */
class CustomTemplateProcessor(private val context: Context) : TemplateProcessor {

    override fun getTemplateType(): String = "CUSTOM"

    override suspend fun generateContext(senderPhone: String): String {
        // Context is injected centrally by AIAgentContextBuilder.
        return ""
    }

    override suspend fun processResponse(
        response: String,
        message: String,
        senderPhone: String,
        senderName: String
    ): String {
        // No template-specific command post-processing required here.
        return response
    }
}

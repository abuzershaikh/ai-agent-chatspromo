package com.message.bulksend.aiagent.tools.globalsender

import android.content.Context
import com.message.bulksend.autorespond.documentreply.DocumentFile
import com.message.bulksend.autorespond.documentreply.DocumentType

/**
 * AI-facing wrapper around GlobalSenderManager.
 * Lets AI tools trigger text/media sends through one shared pipeline.
 */
class GlobalSenderAIIntegration(context: Context) {

    private val senderManager = GlobalSenderManager(context)

    suspend fun sendText(
        phoneNumber: String,
        message: String,
        preferredPackage: String? = null
    ): GlobalSenderResponse {
        val result = senderManager.sendTextViaAccessibility(
            phoneNumber = phoneNumber,
            message = message,
            preferredPackage = preferredPackage
        )
        return GlobalSenderResponse(
            success = result.success,
            status = result.status,
            message = result.message,
            phoneNumber = result.phoneNumber
        )
    }

    fun queueDocuments(
        phoneNumber: String,
        senderName: String,
        documentPaths: List<String> = emptyList(),
        documentType: DocumentType? = null,
        documents: List<DocumentFile> = emptyList()
    ): GlobalSenderResponse {
        val result = senderManager.queueDocumentsForAccessibility(
            phoneNumber = phoneNumber,
            senderName = senderName,
            keyword = "AI_GLOBAL_SENDER",
            documentPaths = documentPaths,
            documentType = documentType,
            documents = documents
        )
        return GlobalSenderResponse(
            success = result.success,
            status = result.status,
            message = result.message,
            phoneNumber = result.phoneNumber
        )
    }

    fun isAccessibilityReady(): Boolean = senderManager.isAccessibilityEnabled()

    fun getFunctionCallSchema(): String {
        return """
            Global Sender Tool:
            1) send_text(phone_number, message)
            2) queue_documents(phone_number, sender_name, document_paths/documents)
            Use this for reliable WhatsApp send with accessibility fallback.
        """.trimIndent()
    }
}

data class GlobalSenderResponse(
    val success: Boolean,
    val status: String,
    val message: String,
    val phoneNumber: String
)


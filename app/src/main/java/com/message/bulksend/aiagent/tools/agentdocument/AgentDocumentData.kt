package com.message.bulksend.aiagent.tools.agentdocument

import android.net.Uri

/**
 * Data class for Agent Document
 * Stores media file information that AI Agent can send via WhatsApp
 */
data class AgentDocument(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val tags: String = "",
    val mediaType: MediaType = MediaType.IMAGE,
    val filePath: String = "",
    val fileUri: Uri? = null,
    val fileSize: Long = 0,
    val mimeType: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

/**
 * Media types supported by Agent Document
 */
enum class MediaType(val displayName: String, val mimeTypes: List<String>) {
    IMAGE(
        "Image",
        listOf("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp")
    ),
    PDF(
        "PDF Document",
        listOf("application/pdf")
    ),
    VIDEO(
        "Video",
        listOf("video/mp4", "video/3gpp", "video/avi", "video/mkv")
    ),
    AUDIO(
        "Audio",
        listOf("audio/mpeg", "audio/mp3", "audio/wav", "audio/ogg", "audio/aac")
    );

    companion object {
        fun fromMimeType(mimeType: String): MediaType {
            return values().find { type ->
                type.mimeTypes.any { it.equals(mimeType, ignoreCase = true) }
            } ?: IMAGE
        }
    }
}

/**
 * Function call data for AI Agent
 * This is what AI Agent will use to send documents
 */
data class AgentDocumentFunctionCall(
    val documentId: String,
    val documentName: String,
    val description: String,
    val tags: String,
    val mediaType: String,
    val filePath: String
)

/**
 * Response from AI Agent after sending document
 */
data class AgentDocumentResponse(
    val success: Boolean,
    val message: String,
    val documentId: String,
    val sentAt: Long = System.currentTimeMillis()
)

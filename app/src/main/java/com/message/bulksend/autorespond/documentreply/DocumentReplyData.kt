package com.message.bulksend.autorespond.documentreply

/**
 * Document types supported by the system
 */
enum class DocumentType {
    IMAGE,
    VIDEO,
    PDF,
    AUDIO
}

/**
 * Data class for document reply configuration
 */
data class DocumentReplyData(
    val id: String = System.currentTimeMillis().toString(),
    val keyword: String,
    val documentType: DocumentType? = null, // Keep for backward compatibility, but will be deprecated
    val documentPaths: List<String> = emptyList(), // Keep for backward compatibility
    val documents: List<DocumentFile> = emptyList(), // New field for mixed document types
    val matchOption: String = "exact", // "exact" or "contains"
    val minWordMatch: Int = 1, // Minimum words to match in contains mode
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    // Helper function to get all document paths (for backward compatibility)
    fun getAllDocumentPaths(): List<String> {
        return if (!documents.isNullOrEmpty()) {
            documents.map { it.savedPath }
        } else {
            documentPaths
        }
    }
    
    // Helper function to get document count by type
    fun getDocumentCountByType(type: DocumentType): Int {
        return documents?.count { it.documentType == type } ?: 0
    }
    
    // Helper function to get total document count
    fun getTotalDocumentCount(): Int {
        return if (!documents.isNullOrEmpty()) {
            documents.size
        } else {
            documentPaths.size
        }
    }
    
    // Helper function to get mixed document types summary
    fun getDocumentTypesSummary(): String {
        if (documents.isNullOrEmpty()) {
            return documentType?.name ?: "No documents"
        }
        
        val imageCount = getDocumentCountByType(DocumentType.IMAGE)
        val videoCount = getDocumentCountByType(DocumentType.VIDEO)
        val pdfCount = getDocumentCountByType(DocumentType.PDF)
        val audioCount = getDocumentCountByType(DocumentType.AUDIO)
        
        val parts = mutableListOf<String>()
        if (imageCount > 0) parts.add("${imageCount} Image${if (imageCount > 1) "s" else ""}")
        if (videoCount > 0) parts.add("${videoCount} Video${if (videoCount > 1) "s" else ""}")
        if (pdfCount > 0) parts.add("${pdfCount} PDF${if (pdfCount > 1) "s" else ""}")
        if (audioCount > 0) parts.add("${audioCount} Audio${if (audioCount > 1) "s" else ""}")
        
        return if (parts.isNotEmpty()) parts.joinToString(", ") else "No documents"
    }
}

/**
 * Data class for document file information
 */
data class DocumentFile(
    val id: String = System.currentTimeMillis().toString(),
    val originalName: String,
    val savedPath: String,
    val documentType: DocumentType,
    val fileSize: Long,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Data class for document send queue
 */
data class DocumentSendTask(
    val id: String = System.currentTimeMillis().toString(),
    val phoneNumber: String,
    val senderName: String,
    val keyword: String,
    val documentPaths: List<String> = emptyList(), // Keep for backward compatibility
    val documentType: DocumentType? = null, // Keep for backward compatibility
    val documents: List<DocumentFile> = emptyList(), // New field for mixed documents
    val status: DocumentSendStatus = DocumentSendStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null,
    val errorMessage: String? = null
) {
    // Helper function to get all document paths
    fun getAllDocumentPaths(): List<String> {
        return if (!documents.isNullOrEmpty()) {
            documents.map { it.savedPath }
        } else {
            documentPaths
        }
    }
    
    // Helper function to get all documents
    fun getAllDocuments(): List<DocumentFile> {
        return documents ?: emptyList()
    }
}

/**
 * Status of document sending task
 */
enum class DocumentSendStatus {
    PENDING,    // Waiting in queue
    SENDING,    // Currently being sent
    SENT,       // Successfully sent
    FAILED,     // Failed to send
    CANCELLED   // Cancelled by user
}

/**
 * Result of document reply processing
 */
sealed class DocumentReplyResult {
    object NoMatch : DocumentReplyResult()
    object Disabled : DocumentReplyResult()
    data class Match(val reply: DocumentReplyData) : DocumentReplyResult()
    data class Error(val message: String) : DocumentReplyResult()
}
package com.message.bulksend.autorespond.ai.document

import android.content.Context
import android.util.Log
import com.message.bulksend.aiagent.tools.globalsender.GlobalSenderManager
import com.message.bulksend.autorespond.documentreply.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI Agent Document Manager
 * Gives AI Agent full control over document reply system
 * 
 * Features:
 * - Send documents (images, videos, PDFs, audio)
 * - Schedule follow-up messages with documents
 * - Use accessibility features for document sending
 * - No limitations - full power
 */
class AIAgentDocumentManager(private val context: Context) {
    
    companion object {
        const val TAG = "AIAgentDocumentManager"
    }
    
    private val documentReplyManager = DocumentReplyManager(context)
    private val documentSendService = DocumentSendService.getInstance()
    private val globalSenderManager = GlobalSenderManager(context)
    
    /**
     * Send document to user immediately
     * AI Agent can call this to send any document type
     */
    suspend fun sendDocumentToUser(
        phoneNumber: String,
        userName: String,
        documentPaths: List<String>,
        documentType: DocumentType? = null,
        documents: List<DocumentFile> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📤 AI Agent sending document to $userName ($phoneNumber)")
            Log.d(TAG, "📁 Document count: ${documentPaths.size + documents.size}")
            if (!globalSenderManager.isAccessibilityEnabled()) {
                Log.e(TAG, "Accessibility service is disabled. Cannot auto-send media.")
                return@withContext false
            }
            
            // Enable document send service
            val queueResult = globalSenderManager.queueDocumentsForAccessibility(
                phoneNumber = phoneNumber,
                senderName = userName,
                keyword = "AI_AGENT_SEND",
                documentPaths = documentPaths,
                documentType = documentType,
                documents = documents
            )
            
            // Add to send queue
            if (queueResult.success) {
                Log.d(TAG, "Document send task added successfully")
                true
            } else {
                Log.e(TAG, "Global sender queue failed: ${queueResult.status} - ${queueResult.message}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending document: ${e.message}")
            false
        }
    }
    
    /**
     * Send single image
     */
    suspend fun sendImage(
        phoneNumber: String,
        userName: String,
        imagePath: String
    ): Boolean {
        return sendDocumentToUser(
            phoneNumber = phoneNumber,
            userName = userName,
            documentPaths = listOf(imagePath),
            documentType = DocumentType.IMAGE
        )
    }
    
    /**
     * Send single video
     */
    suspend fun sendVideo(
        phoneNumber: String,
        userName: String,
        videoPath: String
    ): Boolean {
        return sendDocumentToUser(
            phoneNumber = phoneNumber,
            userName = userName,
            documentPaths = listOf(videoPath),
            documentType = DocumentType.VIDEO
        )
    }
    
    /**
     * Send single PDF
     */
    suspend fun sendPDF(
        phoneNumber: String,
        userName: String,
        pdfPath: String
    ): Boolean {
        return sendDocumentToUser(
            phoneNumber = phoneNumber,
            userName = userName,
            documentPaths = listOf(pdfPath),
            documentType = DocumentType.PDF
        )
    }
    
    /**
     * Send single audio
     */
    suspend fun sendAudio(
        phoneNumber: String,
        userName: String,
        audioPath: String
    ): Boolean {
        return sendDocumentToUser(
            phoneNumber = phoneNumber,
            userName = userName,
            documentPaths = listOf(audioPath),
            documentType = DocumentType.AUDIO
        )
    }
    
    /**
     * Send multiple documents (mixed types)
     */
    suspend fun sendMultipleDocuments(
        phoneNumber: String,
        userName: String,
        documents: List<DocumentFile>
    ): Boolean {
        return sendDocumentToUser(
            phoneNumber = phoneNumber,
            userName = userName,
            documentPaths = emptyList(),
            documents = documents
        )
    }
    
    /**
     * Search for documents by keyword
     * AI can search existing document replies
     */
    fun searchDocumentsByKeyword(keyword: String): List<DocumentReplyData> {
        val allReplies = documentReplyManager.getAllDocumentReplies()
        return allReplies.filter { reply ->
            reply.keyword.contains(keyword, ignoreCase = true) && reply.isEnabled
        }
    }
    
    /**
     * Get all available documents
     */
    fun getAllAvailableDocuments(): List<DocumentFile> {
        return documentReplyManager.getAllDocumentFiles()
    }
    
    /**
     * Get documents by type
     */
    fun getDocumentsByType(documentType: DocumentType): List<DocumentFile> {
        return documentReplyManager.getDocumentsByType(documentType)
    }
    
    /**
     * Get all images
     */
    fun getAllImages(): List<DocumentFile> {
        return getDocumentsByType(DocumentType.IMAGE)
    }
    
    /**
     * Get all videos
     */
    fun getAllVideos(): List<DocumentFile> {
        return getDocumentsByType(DocumentType.VIDEO)
    }
    
    /**
     * Get all PDFs
     */
    fun getAllPDFs(): List<DocumentFile> {
        return getDocumentsByType(DocumentType.PDF)
    }
    
    /**
     * Get all audio files
     */
    fun getAllAudios(): List<DocumentFile> {
        return getDocumentsByType(DocumentType.AUDIO)
    }
    
    /**
     * Check if document exists
     */
    fun documentExists(documentPath: String): Boolean {
        return java.io.File(documentPath).exists()
    }
    
    /**
     * Get document info
     */
    fun getDocumentInfo(documentPath: String): DocumentFile? {
        return getAllAvailableDocuments().find { it.savedPath == documentPath }
    }
    
    /**
     * Schedule follow-up document send
     * AI can schedule documents to be sent later
     */
    suspend fun scheduleDocumentSend(
        phoneNumber: String,
        userName: String,
        documentPaths: List<String>,
        delayMillis: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "⏰ Scheduling document send for $userName in ${delayMillis}ms")
            
            // Wait for delay
            kotlinx.coroutines.delay(delayMillis)
            
            // Send document
            sendDocumentToUser(
                phoneNumber = phoneNumber,
                userName = userName,
                documentPaths = documentPaths
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scheduling document: ${e.message}")
            false
        }
    }
    
    /**
     * Get document send queue status
     */
    fun getQueueStatus(): Pair<Int, DocumentSendTask?> {
        return documentSendService.getQueueStatus()
    }
    
    /**
     * Check if document send is enabled
     */
    fun isDocumentSendEnabled(): Boolean {
        return DocumentSendService.isDocumentSendEnabled()
    }
    
    /**
     * Enable document send
     */
    fun enableDocumentSend() {
        DocumentSendService.enableDocumentSend()
        Log.d(TAG, "✅ Document send enabled by AI Agent")
    }
    
    /**
     * Disable document send
     */
    fun disableDocumentSend() {
        DocumentSendService.disableDocumentSend()
        Log.d(TAG, "❌ Document send disabled by AI Agent")
    }
    
    /**
     * Get document statistics
     */
    fun getDocumentStatistics(): DocumentStatistics {
        val allDocuments = getAllAvailableDocuments()
        return DocumentStatistics(
            totalDocuments = allDocuments.size,
            imageCount = allDocuments.count { it.documentType == DocumentType.IMAGE },
            videoCount = allDocuments.count { it.documentType == DocumentType.VIDEO },
            pdfCount = allDocuments.count { it.documentType == DocumentType.PDF },
            audioCount = allDocuments.count { it.documentType == DocumentType.AUDIO },
            totalStorageUsed = documentReplyManager.getTotalStorageUsed()
        )
    }
    
    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
        return documentReplyManager.formatFileSize(bytes)
    }
}

/**
 * Document statistics data class
 */
data class DocumentStatistics(
    val totalDocuments: Int,
    val imageCount: Int,
    val videoCount: Int,
    val pdfCount: Int,
    val audioCount: Int,
    val totalStorageUsed: Long
)



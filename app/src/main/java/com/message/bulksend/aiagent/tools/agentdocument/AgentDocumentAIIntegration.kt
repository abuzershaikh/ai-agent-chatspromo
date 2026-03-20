package com.message.bulksend.aiagent.tools.agentdocument

import android.content.Context
import android.util.Log
import com.message.bulksend.autorespond.ai.document.AIAgentDocumentManager
import com.message.bulksend.autorespond.documentreply.DocumentFile
import com.message.bulksend.autorespond.documentreply.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * AI Agent Document Integration
 * Connects Agent Document system with AI Agent for function calling
 * 
 * This allows AI Agent to:
 * - List available documents
 * - Search documents by name/description
 * - Send documents to users via WhatsApp
 * - Get document information
 */
class AgentDocumentAIIntegration(private val context: Context) {
    
    companion object {
        const val TAG = "AgentDocumentAI"
    }
    
    private val documentManager = AgentDocumentManager(context)
    private val aiDocumentManager = AIAgentDocumentManager(context)
    
    /**
     * Get all available documents for AI Agent
     * Returns list of documents with their details
     */
    suspend fun getAllDocuments(): List<AgentDocument> = withContext(Dispatchers.IO) {
        try {
            // Use first() with timeout instead of collect to avoid hanging
            kotlinx.coroutines.withTimeoutOrNull(1500) {
                documentManager.getAllDocuments().first()
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting documents: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Search documents by name or description
     * AI Agent can use this to find relevant documents
     */
    suspend fun searchDocuments(query: String): List<AgentDocument> = withContext(Dispatchers.IO) {
        try {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isBlank()) return@withContext emptyList()
            val allDocs = getAllDocuments()
            allDocs.filter { doc ->
                doc.name.contains(normalizedQuery, ignoreCase = true) ||
                doc.description.contains(normalizedQuery, ignoreCase = true) ||
                parseTags(doc.tags).any { tag -> tag.contains(normalizedQuery, ignoreCase = true) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching documents: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get documents by media type
     */
    suspend fun getDocumentsByType(mediaType: MediaType): List<AgentDocument> = withContext(Dispatchers.IO) {
        try {
            kotlinx.coroutines.withTimeoutOrNull(1500) {
                documentManager.getDocumentsByType(mediaType).first()
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting documents by type: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Send document to user via WhatsApp
     * This is the main function AI Agent will call
     */
    suspend fun sendDocumentToUser(
        phoneNumber: String,
        userName: String,
        documentId: String
    ): AgentDocumentResponse = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🤖 AI Agent sending document: $documentId to $userName")
            
            // Get document details
            val document = documentManager.getDocumentById(documentId)
            if (document == null) {
                Log.e(TAG, "❌ Document not found: $documentId")
                return@withContext AgentDocumentResponse(
                    success = false,
                    message = "Document not found",
                    documentId = documentId
                )
            }
            
            // Check if file exists
            val file = java.io.File(document.filePath)
            if (!file.exists()) {
                Log.e(TAG, "❌ File not found: ${document.filePath}")
                return@withContext AgentDocumentResponse(
                    success = false,
                    message = "File not found",
                    documentId = documentId
                )
            }
            
            // Convert to DocumentFile for sending
            val documentFile = DocumentFile(
                originalName = document.name,
                savedPath = document.filePath,
                documentType = when (document.mediaType) {
                    MediaType.IMAGE -> DocumentType.IMAGE
                    MediaType.PDF -> DocumentType.PDF
                    MediaType.VIDEO -> DocumentType.VIDEO
                    MediaType.AUDIO -> DocumentType.AUDIO
                },
                fileSize = document.fileSize
            )
            
            // Send via AI Agent Document Manager
            val success = when (document.mediaType) {
                MediaType.IMAGE -> aiDocumentManager.sendImage(phoneNumber, userName, document.filePath)
                MediaType.PDF -> aiDocumentManager.sendPDF(phoneNumber, userName, document.filePath)
                MediaType.VIDEO -> aiDocumentManager.sendVideo(phoneNumber, userName, document.filePath)
                MediaType.AUDIO -> aiDocumentManager.sendAudio(phoneNumber, userName, document.filePath)
            }
            
            if (success) {
                Log.d(TAG, "✅ Document sent successfully: ${document.name}")
                AgentDocumentResponse(
                    success = true,
                    message = "Document sent: ${document.name}",
                    documentId = documentId
                )
            } else {
                Log.e(TAG, "❌ Failed to send document: ${document.name}")
                AgentDocumentResponse(
                    success = false,
                    message = "Failed to send document",
                    documentId = documentId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending document: ${e.message}", e)
            AgentDocumentResponse(
                success = false,
                message = "Error: ${e.message}",
                documentId = documentId
            )
        }
    }
    
    /**
     * Send multiple documents to user
     */
    suspend fun sendMultipleDocuments(
        phoneNumber: String,
        userName: String,
        documentIds: List<String>
    ): List<AgentDocumentResponse> = withContext(Dispatchers.IO) {
        documentIds.map { documentId ->
            sendDocumentToUser(phoneNumber, userName, documentId)
        }
    }

    /**
     * Match document by tags/name/description and send best match.
     * Useful when AI has intent but not exact document ID.
     */
    suspend fun sendDocumentByTagMatch(
        phoneNumber: String,
        userName: String,
        query: String
    ): AgentDocumentResponse = withContext(Dispatchers.IO) {
        try {
            val bestMatch = findBestDocumentForQuery(query)
            if (bestMatch == null) {
                return@withContext AgentDocumentResponse(
                    success = false,
                    message = "No document matched query: $query",
                    documentId = ""
                )
            }
            sendDocumentToUser(phoneNumber, userName, bestMatch.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending document by tag match: ${e.message}", e)
            AgentDocumentResponse(
                success = false,
                message = "Error: ${e.message}",
                documentId = ""
            )
        }
    }
    
    /**
     * Get document information for AI context
     * Returns formatted string with document details
     */
    suspend fun getDocumentInfo(documentId: String): String? = withContext(Dispatchers.IO) {
        try {
            val document = documentManager.getDocumentById(documentId)
            if (document == null) return@withContext null
            
            buildString {
                append("Document: ${document.name}\n")
                append("Type: ${document.mediaType.displayName}\n")
                append("Description: ${document.description}\n")
                append("Size: ${formatFileSize(document.fileSize)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting document info: ${e.message}")
            null
        }
    }
    
    /**
     * Get all documents as formatted text for AI context
     * AI Agent can use this to know what documents are available
     */
    suspend fun getDocumentsListForAI(): String = withContext(Dispatchers.IO) {
        try {
            // Add timeout to prevent hanging on database operations
            val documents = kotlinx.coroutines.withTimeoutOrNull(2000) {
                getAllDocuments()
            }
            
            if (documents == null) {
                Log.w(TAG, "⚠️ Document fetch timed out")
                return@withContext "No documents available."
            }
            
            if (documents.isEmpty()) {
                return@withContext "No documents available."
            }
            
            buildString {
                appendLine("Available Documents (${documents.size}):")
                appendLine()
                documents.forEachIndexed { index, doc ->
                    appendLine("${index + 1}. ${doc.name}")
                    appendLine("   ID: ${doc.id}")
                    appendLine("   Type: ${doc.mediaType.displayName} (${doc.mediaType.name})")
                    appendLine("   MIME: ${doc.mimeType}")
                    appendLine("   Description: ${doc.description}")
                    if (doc.tags.isNotBlank()) {
                        appendLine("   Tags: ${doc.tags}")
                    }
                    appendLine("   Size: ${formatFileSize(doc.fileSize)}")
                    appendLine()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting documents list: ${e.message}")
            "Error loading documents."
        }
    }
    
    /**
     * Get document count by type
     */
    suspend fun getDocumentCountByType(): Map<MediaType, Int> = withContext(Dispatchers.IO) {
        try {
            val documents = getAllDocuments()
            MediaType.values().associateWith { type ->
                documents.count { it.mediaType == type }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting document count: ${e.message}")
            emptyMap()
        }
    }
    
    /**
     * Get total document count
     */
    suspend fun getTotalDocumentCount(): Int = withContext(Dispatchers.IO) {
        try {
            documentManager.getDocumentCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total count: ${e.message}")
            0
        }
    }
    
    /**
     * Check if document exists
     */
    suspend fun documentExists(documentId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            documentManager.getDocumentById(documentId) != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Format file size for display
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
    
    /**
     * Get function call schema for AI Agent
     * This tells AI Agent how to use the document system
     */
    fun getFunctionCallSchema(): String {
        return """
            Agent Document System Functions:
            
            1. send_document(phone_number, user_name, document_id)
               - Send a document to user via WhatsApp
               - Parameters:
                 * phone_number: User's phone number
                 * user_name: User's name
                 * document_id: ID of document to send
               - Returns: Success/failure response
            
            2. search_documents(query)
               - Search documents by name or description
               - Parameters:
                 * query: Search term
               - Returns: List of matching documents
            
            3. list_documents()
               - Get all available documents
               - Returns: List of all documents with details
            
            4. get_document_info(document_id)
               - Get detailed information about a document
               - Parameters:
                 * document_id: ID of document
               - Returns: Document details

            5. send_document_by_tag(phone_number, user_name, query)
               - Match document by tags/name/description and send best match
               - Parameters:
                 * phone_number: User's phone number
                 * user_name: User's name
                 * query: Intent keywords (example: brochure, pricing pdf, clinic form)
               - Returns: Success/failure response
             
            Usage Example:
            When user asks for a brochure, search for "brochure" and send the matching document.
        """.trimIndent()
    }

    private suspend fun findBestDocumentForQuery(query: String): AgentDocument? {
        val normalizedQuery = normalizeText(query)
        if (normalizedQuery.isBlank()) return null

        val words = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        val docs = getAllDocuments()

        val scored = docs.mapNotNull { doc ->
            val tags = parseTags(doc.tags).map { normalizeText(it) }.filter { it.isNotBlank() }
            val name = normalizeText(doc.name)
            val description = normalizeText(doc.description)

            var score = 0

            // Strongest signal: explicit tag match
            val exactTagHits = tags.count { tag ->
                tag == normalizedQuery || words.contains(tag)
            }
            score += exactTagHits * 10

            // Partial tag match
            val partialTagHits = tags.count { tag ->
                normalizedQuery.contains(tag) || words.any { w -> tag.contains(w) || w.contains(tag) }
            }
            score += partialTagHits * 5

            // Name/description fallback signals
            if (name.contains(normalizedQuery)) score += 4
            if (description.contains(normalizedQuery)) score += 3
            score += words.count { w -> name.contains(w) } * 2
            score += words.count { w -> description.contains(w) }

            if (score > 0) doc to score else null
        }

        return scored.maxByOrNull { it.second }?.first
    }

    private fun parseTags(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun normalizeText(value: String): String {
        return value.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

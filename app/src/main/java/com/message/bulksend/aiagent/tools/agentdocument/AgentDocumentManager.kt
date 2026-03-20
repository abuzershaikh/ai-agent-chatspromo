package com.message.bulksend.aiagent.tools.agentdocument

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manager class for Agent Document operations
 * Handles CRUD operations and file management
 */
class AgentDocumentManager(private val context: Context) {
    
    private val database = AgentDocumentDatabase.getDatabase(context)
    private val dao = database.agentDocumentDao()
    
    companion object {
        private const val TAG = "AgentDocumentManager"
        private const val DOCUMENTS_FOLDER = "agent_documents"
    }
    
    /**
     * Get all active documents as Flow
     */
    fun getAllDocuments(): Flow<List<AgentDocument>> {
        return dao.getAllDocuments().map { entities ->
            entities.map { it.toAgentDocument() }
        }
    }
    
    /**
     * Get documents by media type
     */
    fun getDocumentsByType(mediaType: MediaType): Flow<List<AgentDocument>> {
        return dao.getDocumentsByType(mediaType.name).map { entities ->
            entities.map { it.toAgentDocument() }
        }
    }
    
    /**
     * Get document by ID
     */
    suspend fun getDocumentById(documentId: String): AgentDocument? {
        return withContext(Dispatchers.IO) {
            dao.getDocumentById(documentId)?.toAgentDocument()
        }
    }
    
    /**
     * Add new document
     * Copies file to app storage and saves to database
     */
    suspend fun addDocument(
        name: String,
        description: String,
        tags: String,
        sourceUri: Uri,
        mimeType: String
    ): Result<AgentDocument> {
        return withContext(Dispatchers.IO) {
            try {
                // Generate unique ID
                val documentId = UUID.randomUUID().toString()
                
                // Determine media type
                val mediaType = MediaType.fromMimeType(mimeType)
                
                // Copy file to app storage
                val destinationFile = copyFileToAppStorage(sourceUri, documentId, mediaType)
                
                // Get file size
                val fileSize = destinationFile.length()
                
                // Create document entity
                val document = AgentDocument(
                    id = documentId,
                    name = name,
                    description = description,
                    tags = normalizeTags(tags),
                    mediaType = mediaType,
                    filePath = destinationFile.absolutePath,
                    fileUri = Uri.fromFile(destinationFile),
                    fileSize = fileSize,
                    mimeType = mimeType,
                    createdAt = System.currentTimeMillis(),
                    isActive = true
                )
                
                // Save to database
                dao.insertDocument(document.toEntity())
                
                Log.d(TAG, "Document added successfully: $name")
                Result.success(document)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error adding document", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Update document details (name, description)
     */
    suspend fun updateDocument(
        documentId: String,
        name: String,
        description: String,
        tags: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val existing = dao.getDocumentById(documentId)
                if (existing != null) {
                    val updated = existing.copy(
                        name = name,
                        description = description,
                        tags = normalizeTags(tags)
                    )
                    dao.updateDocument(updated)
                    Log.d(TAG, "Document updated: $documentId")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Document not found"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating document", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Delete document (removes file and database entry)
     */
    suspend fun deleteDocument(documentId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val document = dao.getDocumentById(documentId)
                if (document != null) {
                    // Delete file
                    val file = File(document.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                    
                    // Delete from database
                    dao.deleteDocument(documentId)
                    
                    Log.d(TAG, "Document deleted: $documentId")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Document not found"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting document", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get document count
     */
    suspend fun getDocumentCount(): Int {
        return withContext(Dispatchers.IO) {
            dao.getDocumentCount()
        }
    }
    
    /**
     * Prepare function call data for AI Agent
     * This is what AI will use to send the document
     */
    fun prepareFunctionCall(document: AgentDocument): AgentDocumentFunctionCall {
        return AgentDocumentFunctionCall(
            documentId = document.id,
            documentName = document.name,
            description = document.description,
            tags = document.tags,
            mediaType = document.mediaType.name,
            filePath = document.filePath
        )
    }

    private fun normalizeTags(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .joinToString(",")
    }
    
    /**
     * Copy file from source URI to app storage
     */
    private fun copyFileToAppStorage(
        sourceUri: Uri,
        documentId: String,
        mediaType: MediaType
    ): File {
        // Create documents directory
        val documentsDir = File(context.filesDir, DOCUMENTS_FOLDER)
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }
        
        // Create subdirectory for media type
        val typeDir = File(documentsDir, mediaType.name.lowercase())
        if (!typeDir.exists()) {
            typeDir.mkdirs()
        }
        
        // Get file extension from source
        val extension = context.contentResolver.getType(sourceUri)?.let { mimeType ->
            when {
                mimeType.startsWith("image/") -> ".${mimeType.substringAfter("image/")}"
                mimeType.startsWith("video/") -> ".${mimeType.substringAfter("video/")}"
                mimeType.startsWith("audio/") -> ".${mimeType.substringAfter("audio/")}"
                mimeType == "application/pdf" -> ".pdf"
                else -> ""
            }
        } ?: ""
        
        // Create destination file
        val destinationFile = File(typeDir, "$documentId$extension")
        
        // Copy file
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destinationFile).use { output ->
                input.copyTo(output)
            }
        }
        
        return destinationFile
    }
}

/**
 * Extension functions for conversion
 */
private fun AgentDocumentEntity.toAgentDocument(): AgentDocument {
    return AgentDocument(
        id = id,
        name = name,
        description = description,
        tags = tags,
        mediaType = MediaType.valueOf(mediaType),
        filePath = filePath,
        fileUri = Uri.parse(filePath),
        fileSize = fileSize,
        mimeType = mimeType,
        createdAt = createdAt,
        isActive = isActive
    )
}

private fun AgentDocument.toEntity(): AgentDocumentEntity {
    return AgentDocumentEntity(
        id = id,
        name = name,
        description = description,
        tags = tags,
        mediaType = mediaType.name,
        filePath = filePath,
        fileSize = fileSize,
        mimeType = mimeType,
        createdAt = createdAt,
        isActive = isActive
    )
}

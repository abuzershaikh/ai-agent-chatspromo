package com.message.bulksend.autorespond.documentreply

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Manager class for document replies
 */
class DocumentReplyManager(private val context: Context) {

    companion object {
        const val TAG = "DocumentReplyManager"
        private const val PREFS_NAME = "document_reply_prefs"
        private const val KEY_DOCUMENT_REPLIES = "document_replies"
        private const val KEY_DOCUMENT_FILES = "document_files"
        
        // Folder structure in app's internal storage
        private const val DOCUMENTS_FOLDER = "documents"
        private const val IMAGES_FOLDER = "images"
        private const val VIDEOS_FOLDER = "videos"
        private const val PDFS_FOLDER = "pdfs"
        private const val AUDIOS_FOLDER = "audios"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        // Create folder structure on initialization
        createFolderStructure()
    }

    /**
     * Create folder structure for documents
     */
    private fun createFolderStructure() {
        try {
            val documentsDir = File(context.filesDir, DOCUMENTS_FOLDER)
            val imagesDir = File(documentsDir, IMAGES_FOLDER)
            val videosDir = File(documentsDir, VIDEOS_FOLDER)
            val pdfsDir = File(documentsDir, PDFS_FOLDER)
            val audiosDir = File(documentsDir, AUDIOS_FOLDER)
            
            documentsDir.mkdirs()
            imagesDir.mkdirs()
            videosDir.mkdirs()
            pdfsDir.mkdirs()
            audiosDir.mkdirs()
            
            Log.d(TAG, "✅ Folder structure created successfully")
            Log.d(TAG, "Documents path: ${documentsDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating folder structure: ${e.message}")
        }
    }

    /**
     * Get folder path for document type
     */
    private fun getFolderForType(documentType: DocumentType): File {
        val documentsDir = File(context.filesDir, DOCUMENTS_FOLDER)
        return when (documentType) {
            DocumentType.IMAGE -> File(documentsDir, IMAGES_FOLDER)
            DocumentType.VIDEO -> File(documentsDir, VIDEOS_FOLDER)
            DocumentType.PDF -> File(documentsDir, PDFS_FOLDER)
            DocumentType.AUDIO -> File(documentsDir, AUDIOS_FOLDER)
        }
    }

    /**
     * Save document from URI to app folder
     */
    suspend fun saveDocument(uri: Uri, documentType: DocumentType): Boolean {
        return try {
            Log.d(TAG, "📁 Starting to save document: $uri, type: $documentType")
            
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "❌ Cannot open input stream for URI: $uri")
                return false
            }

            // Get original filename
            val originalName = getFileName(uri) ?: "document_${System.currentTimeMillis()}"
            Log.d(TAG, "📝 Original filename: $originalName")
            
            // Create unique filename to avoid conflicts
            val timestamp = System.currentTimeMillis()
            val extension = getFileExtension(originalName)
            val uniqueFileName = "${timestamp}_$originalName"
            
            // Get target folder
            val targetFolder = getFolderForType(documentType)
            val targetFile = File(targetFolder, uniqueFileName)
            
            Log.d(TAG, "💾 Saving to: ${targetFile.absolutePath}")
            
            // Copy file
            val outputStream = FileOutputStream(targetFile)
            val bytesWritten = inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            
            Log.d(TAG, "✅ File copied successfully: $bytesWritten bytes")
            
            // Save file info
            val documentFile = DocumentFile(
                originalName = originalName,
                savedPath = targetFile.absolutePath,
                documentType = documentType,
                fileSize = targetFile.length()
            )
            saveDocumentFile(documentFile)
            
            Log.d(TAG, "✅ Document saved successfully: ${targetFile.absolutePath}")
            Log.d(TAG, "📊 File size: ${formatFileSize(targetFile.length())}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving document: ${e.message}", e)
            false
        }
    }

    /**
     * Get filename from URI
     */
    private fun getFileName(uri: Uri): String? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        return it.getString(nameIndex)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting filename: ${e.message}")
            null
        }
    }

    /**
     * Get file extension
     */
    private fun getFileExtension(filename: String): String {
        return filename.substringAfterLast('.', "")
    }

    /**
     * Save document file info to preferences
     */
    private fun saveDocumentFile(documentFile: DocumentFile) {
        val files = getAllDocumentFiles().toMutableList()
        files.add(documentFile)
        val json = gson.toJson(files)
        prefs.edit().putString(KEY_DOCUMENT_FILES, json).apply()
    }

    /**
     * Get all document files
     */
    fun getAllDocumentFiles(): List<DocumentFile> {
        val json = prefs.getString(KEY_DOCUMENT_FILES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DocumentFile>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing document files: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get document count by type
     */
    fun getDocumentCount(documentType: DocumentType): Int {
        return getAllDocumentFiles().count { it.documentType == documentType }
    }

    /**
     * Get documents by type
     */
    fun getDocumentsByType(documentType: DocumentType): List<DocumentFile> {
        return getAllDocumentFiles().filter { it.documentType == documentType }
    }

    /**
     * Save document reply configuration
     */
    fun saveDocumentReply(reply: DocumentReplyData) {
        val replies = getAllDocumentReplies().toMutableList()
        
        // Check if updating existing reply
        val existingIndex = replies.indexOfFirst { it.id == reply.id }
        if (existingIndex != -1) {
            replies[existingIndex] = reply
        } else {
            replies.add(0, reply) // Add to beginning
        }
        
        val json = gson.toJson(replies)
        prefs.edit().putString(KEY_DOCUMENT_REPLIES, json).apply()
        Log.d(TAG, "Document reply saved: ${reply.keyword}")
    }

    /**
     * Get all document replies
     */
    fun getAllDocumentReplies(): List<DocumentReplyData> {
        val json = prefs.getString(KEY_DOCUMENT_REPLIES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DocumentReplyData>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing document replies: ${e.message}")
            emptyList()
        }
    }

    /**
     * Delete document reply
     */
    fun deleteDocumentReply(id: String) {
        val replies = getAllDocumentReplies().toMutableList()
        replies.removeAll { it.id == id }
        val json = gson.toJson(replies)
        prefs.edit().putString(KEY_DOCUMENT_REPLIES, json).apply()
        Log.d(TAG, "Document reply deleted: $id")
    }

    /**
     * Toggle reply enabled state
     */
    fun toggleReplyEnabled(id: String) {
        val replies = getAllDocumentReplies().toMutableList()
        val index = replies.indexOfFirst { it.id == id }
        if (index != -1) {
            replies[index] = replies[index].copy(isEnabled = !replies[index].isEnabled)
            val json = gson.toJson(replies)
            prefs.edit().putString(KEY_DOCUMENT_REPLIES, json).apply()
        }
    }

    /**
     * Find matching document reply for incoming message
     */
    fun findMatchingDocumentReply(incomingMessage: String): DocumentReplyResult {
        Log.d(TAG, "🔍 Searching for document reply match...")
        Log.d(TAG, "📨 Incoming message: '$incomingMessage'")
        
        // First check if phone is locked
        if (LockScreenDetector.isPhoneLocked(context)) {
            Log.w(TAG, "🔒 Phone is locked - Document Reply cannot work on lock screen")
            Log.w(TAG, "⚠️ Lock status: ${LockScreenDetector.getLockStatusMessage(context)}")
            
            // Show simple warning for background scenario
            LockScreenWarningDialog.showSimpleLockWarning(context)
            
            return DocumentReplyResult.Error("Phone is locked - Document Reply requires unlocked screen")
        }
        
        Log.d(TAG, "✅ Phone is unlocked - proceeding with document reply check")
        
        val replies = getAllDocumentReplies().filter { it.isEnabled }
        Log.d(TAG, "📋 Found ${replies.size} enabled document replies")
        
        for (reply in replies) {
            Log.d(TAG, "🔍 Checking reply: keyword='${reply.keyword}', type=${reply.documentType}, match=${reply.matchOption}")
            Log.d(TAG, "📁 Document paths: ${reply.documentPaths}")
            
            val matches = when (reply.matchOption) {
                "exact" -> {
                    val result = incomingMessage.trim().equals(reply.keyword.trim(), ignoreCase = true)
                    Log.d(TAG, "🎯 Exact match check: '$incomingMessage' == '${reply.keyword}' = $result")
                    result
                }
                "contains" -> {
                    // Split keyword into individual words and count matches
                    val keywords = reply.keyword.trim().split("\\s+".toRegex())
                    val matchCount = keywords.count { keyword ->
                        incomingMessage.contains(keyword, ignoreCase = true)
                    }
                    
                    Log.d(TAG, "🔍 Contains match check:")
                    Log.d(TAG, "  📨 Incoming: '$incomingMessage'")
                    Log.d(TAG, "  🔑 Keywords: $keywords")
                    Log.d(TAG, "  ✅ Match count: $matchCount")
                    Log.d(TAG, "  📊 Required: ${reply.minWordMatch}")
                    
                    // Check if match count meets minimum requirement
                    val result = matchCount >= reply.minWordMatch
                    Log.d(TAG, "  🎯 Result: $result")
                    result
                }
                else -> {
                    Log.d(TAG, "❌ Unknown match option: ${reply.matchOption}")
                    false
                }
            }
            
            if (matches) {
                Log.d(TAG, "✅ Document reply match found!")
                Log.d(TAG, "🔑 Keyword: '${reply.keyword}'")
                Log.d(TAG, "📁 Document type: ${reply.documentType}")
                
                // Get document paths from both old and new structure
                val allDocumentPaths = reply.getAllDocumentPaths()
                Log.d(TAG, "📄 Document count: ${allDocumentPaths.size}")
                
                // Verify all document files exist
                val existingPaths = allDocumentPaths.filter { path ->
                    val exists = File(path).exists()
                    Log.d(TAG, "📁 File check: $path -> $exists")
                    exists
                }
                
                if (existingPaths.isEmpty()) {
                    Log.e(TAG, "❌ No valid document files found for keyword: ${reply.keyword}")
                    continue
                }
                
                Log.d(TAG, "✅ Found ${existingPaths.size} valid document files")
                
                // Create a compatible reply for backward compatibility
                val compatibleReply = if (reply.documents.isNullOrEmpty()) {
                    // Old structure - use as is
                    reply.copy(documentPaths = existingPaths)
                } else {
                    // New structure - update both fields
                    reply.copy(
                        documentPaths = existingPaths,
                        documents = reply.documents.filter { doc ->
                            existingPaths.contains(doc.savedPath)
                        }
                    )
                }
                
                return DocumentReplyResult.Match(compatibleReply)
            }
        }
        
        Log.d(TAG, "❌ No document reply match found for: '$incomingMessage'")
        return DocumentReplyResult.NoMatch
    }

    /**
     * Check if document reply is enabled
     */
    fun isDocumentReplyEnabled(): Boolean {
        return prefs.getBoolean("document_reply_enabled", true)
    }

    /**
     * Enable/disable document reply
     */
    fun setDocumentReplyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("document_reply_enabled", enabled).apply()
        Log.d(TAG, "Document reply ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Get documents folder path (for external access)
     */
    fun getDocumentsFolderPath(): String {
        return File(context.filesDir, DOCUMENTS_FOLDER).absolutePath
    }

    /**
     * Delete document file
     */
    fun deleteDocumentFile(documentFile: DocumentFile): Boolean {
        return try {
            val file = File(documentFile.savedPath)
            val deleted = file.delete()
            
            if (deleted) {
                // Remove from saved files list
                val files = getAllDocumentFiles().toMutableList()
                files.removeAll { it.id == documentFile.id }
                val json = gson.toJson(files)
                prefs.edit().putString(KEY_DOCUMENT_FILES, json).apply()
                Log.d(TAG, "✅ Document file deleted: ${documentFile.originalName}")
            }
            
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting document file: ${e.message}")
            false
        }
    }

    /**
     * Get total storage used by documents
     */
    fun getTotalStorageUsed(): Long {
        return getAllDocumentFiles().sumOf { it.fileSize }
    }

    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
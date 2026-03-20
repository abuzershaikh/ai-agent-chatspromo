package com.message.bulksend.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Helper class to manage media storage for campaigns
 * Saves media to app's internal storage so it persists for resume functionality
 */
object MediaStorageHelper {
    
    private const val TAG = "MediaStorageHelper"
    private const val MEDIA_FOLDER = "CampaignMedia"
    
    /**
     * Get the media storage directory
     */
    fun getMediaDirectory(context: Context): File {
        val mediaDir = File(context.filesDir, MEDIA_FOLDER)
        if (!mediaDir.exists()) {
            mediaDir.mkdirs()
            Log.d(TAG, "Created media directory: ${mediaDir.absolutePath}")
        }
        return mediaDir
    }
    
    /**
     * Save media from URI to local storage
     * Returns the local file path or null if failed
     */
    fun saveMediaToLocal(context: Context, uri: Uri, campaignId: String): String? {
        return try {
            val mediaDir = getMediaDirectory(context)
            
            // Get file extension from URI
            val extension = getFileExtension(context, uri)
            val fileName = "${campaignId}_media.$extension"
            val localFile = File(mediaDir, fileName)
            
            // Copy content from URI to local file
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(localFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            Log.d(TAG, "Media saved to: ${localFile.absolutePath}")
            localFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving media: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get file extension from URI
     */
    private fun getFileExtension(context: Context, uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return when {
            mimeType?.contains("image/jpeg") == true -> "jpg"
            mimeType?.contains("image/png") == true -> "png"
            mimeType?.contains("image/gif") == true -> "gif"
            mimeType?.contains("video/mp4") == true -> "mp4"
            mimeType?.contains("video/3gpp") == true -> "3gp"
            mimeType?.contains("video") == true -> "mp4"
            mimeType?.contains("audio/mpeg") == true -> "mp3"
            mimeType?.contains("audio") == true -> "mp3"
            mimeType?.contains("application/pdf") == true -> "pdf"
            mimeType?.contains("document") == true -> "doc"
            else -> "media"
        }
    }
    
    /**
     * Get local media file for a campaign
     * Returns the File if exists, null otherwise
     */
    fun getLocalMediaFile(context: Context, mediaPath: String?): File? {
        if (mediaPath.isNullOrEmpty()) return null
        
        val file = File(mediaPath)
        return if (file.exists()) {
            Log.d(TAG, "Local media found: $mediaPath")
            file
        } else {
            Log.d(TAG, "Local media not found: $mediaPath")
            null
        }
    }
    
    /**
     * Get URI for local media file
     */
    fun getLocalMediaUri(context: Context, mediaPath: String?): Uri? {
        val file = getLocalMediaFile(context, mediaPath)
        return file?.let { Uri.fromFile(it) }
    }
    
    /**
     * Delete media file for a campaign
     */
    fun deleteMedia(mediaPath: String?): Boolean {
        if (mediaPath.isNullOrEmpty()) return false
        
        return try {
            val file = File(mediaPath)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Media deleted: $mediaPath, success: $deleted")
                deleted
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting media: ${e.message}", e)
            false
        }
    }
    
    /**
     * Delete all media for a campaign (by campaign ID prefix)
     */
    fun deleteAllCampaignMedia(context: Context, campaignId: String) {
        try {
            val mediaDir = getMediaDirectory(context)
            mediaDir.listFiles()?.filter { it.name.startsWith(campaignId) }?.forEach { file ->
                file.delete()
                Log.d(TAG, "Deleted: ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting campaign media: ${e.message}", e)
        }
    }
    
    /**
     * Get media type from file path
     */
    fun getMediaType(mediaPath: String?): MediaType {
        if (mediaPath.isNullOrEmpty()) return MediaType.NONE
        
        val extension = mediaPath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg", "png", "gif", "webp" -> MediaType.IMAGE
            "mp4", "3gp", "mkv", "avi", "mov" -> MediaType.VIDEO
            "mp3", "wav", "ogg", "m4a" -> MediaType.AUDIO
            "pdf", "doc", "docx", "xls", "xlsx" -> MediaType.DOCUMENT
            else -> MediaType.OTHER
        }
    }
    
    /**
     * Clean up old media files (older than 30 days)
     */
    fun cleanupOldMedia(context: Context, daysOld: Int = 30) {
        try {
            val mediaDir = getMediaDirectory(context)
            val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
            
            mediaDir.listFiles()?.filter { it.lastModified() < cutoffTime }?.forEach { file ->
                file.delete()
                Log.d(TAG, "Cleaned up old media: ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up media: ${e.message}", e)
        }
    }
    
    enum class MediaType {
        NONE, IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER
    }
}
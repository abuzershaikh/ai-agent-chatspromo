package com.message.bulksend.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Manages media files for campaigns
 * Copies media to app's internal storage so it persists across app restarts
 */
object MediaManager {
    
    private const val TAG = "MediaManager"
    private const val MEDIA_FOLDER = "campaign_media"
    
    /**
     * Copy media from external URI to app's internal storage
     * Returns the internal file path or null if failed
     */
    fun copyMediaToInternal(context: Context, sourceUri: Uri, campaignId: String): String? {
        return try {
            val mediaDir = getMediaDirectory(context)
            val fileName = "${campaignId}_${System.currentTimeMillis()}_${getFileExtension(context, sourceUri)}"
            val destFile = File(mediaDir, fileName)
            
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            Log.d(TAG, "Media copied to: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy media: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get URI for internal media file (for sharing with other apps)
     */
    fun getMediaUri(context: Context, filePath: String): Uri? {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            } else {
                Log.e(TAG, "Media file not found: $filePath")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get media URI: ${e.message}", e)
            null
        }
    }
    
    /**
     * Check if media file exists
     */
    fun mediaExists(filePath: String?): Boolean {
        if (filePath.isNullOrEmpty()) return false
        return File(filePath).exists()
    }
    
    /**
     * Delete media file for a campaign
     */
    fun deleteMedia(filePath: String?): Boolean {
        if (filePath.isNullOrEmpty()) return false
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete media: ${e.message}", e)
            false
        }
    }
    
    /**
     * Delete all media for a campaign (by campaign ID prefix)
     */
    fun deleteCampaignMedia(context: Context, campaignId: String) {
        try {
            val mediaDir = getMediaDirectory(context)
            mediaDir.listFiles()?.filter { it.name.startsWith(campaignId) }?.forEach { file ->
                file.delete()
                Log.d(TAG, "Deleted: ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete campaign media: ${e.message}", e)
        }
    }
    
    /**
     * Clean up old media files (older than 30 days)
     */
    fun cleanupOldMedia(context: Context, maxAgeDays: Int = 30) {
        try {
            val mediaDir = getMediaDirectory(context)
            val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
            
            mediaDir.listFiles()?.filter { it.lastModified() < cutoffTime }?.forEach { file ->
                file.delete()
                Log.d(TAG, "Cleaned up old media: ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old media: ${e.message}", e)
        }
    }
    
    /**
     * Get total size of media folder
     */
    fun getMediaFolderSize(context: Context): Long {
        return try {
            val mediaDir = getMediaDirectory(context)
            mediaDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Get media directory, create if not exists
     */
    private fun getMediaDirectory(context: Context): File {
        val mediaDir = File(context.filesDir, MEDIA_FOLDER)
        if (!mediaDir.exists()) {
            mediaDir.mkdirs()
        }
        return mediaDir
    }
    
    /**
     * Get file extension from URI
     */
    private fun getFileExtension(context: Context, uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return when {
            mimeType?.contains("video") == true -> "mp4"
            mimeType?.contains("image/jpeg") == true -> "jpg"
            mimeType?.contains("image/png") == true -> "png"
            mimeType?.contains("image/gif") == true -> "gif"
            mimeType?.contains("pdf") == true -> "pdf"
            mimeType?.contains("document") == true -> "doc"
            else -> "media"
        }
    }
    
    /**
     * Get media type from file path
     */
    fun getMediaType(filePath: String?): String {
        if (filePath.isNullOrEmpty()) return "unknown"
        return when {
            filePath.endsWith(".mp4", true) || filePath.endsWith(".mov", true) || 
            filePath.endsWith(".avi", true) || filePath.endsWith(".mkv", true) -> "video"
            filePath.endsWith(".jpg", true) || filePath.endsWith(".jpeg", true) || 
            filePath.endsWith(".png", true) || filePath.endsWith(".gif", true) -> "image"
            filePath.endsWith(".pdf", true) -> "pdf"
            filePath.endsWith(".doc", true) || filePath.endsWith(".docx", true) -> "document"
            else -> "file"
        }
    }
}
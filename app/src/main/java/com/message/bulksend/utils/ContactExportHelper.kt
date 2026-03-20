package com.message.bulksend.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.message.bulksend.contactmanager.Contact
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Helper class to export contacts to CSV and VCF formats
 */
object ContactExportHelper {
    
    private const val TAG = "ContactExportHelper"
    
    /**
     * Export contacts to CSV format
     * @return File path if successful, null otherwise
     */
    fun exportToCSV(
        context: Context,
        contacts: List<Contact>,
        fileName: String
    ): String? {
        return try {
            val csvContent = buildString {
                // Header
                appendLine("Name,Phone Number")
                // Data
                contacts.forEach { contact ->
                    // Escape commas and quotes in name
                    val escapedName = contact.name.replace("\"", "\"\"")
                    appendLine("\"$escapedName\",\"${contact.number}\"")
                }
            }
            
            saveToDownloads(context, csvContent, "$fileName.csv", "text/csv")
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to CSV", e)
            null
        }
    }
    

    
    /**
     * Save content to Downloads folder
     */
    private fun saveToDownloads(
        context: Context,
        content: String,
        fileName: String,
        mimeType: String
    ): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ use MediaStore
            saveUsingMediaStore(context, content, fileName, mimeType)
        } else {
            // Older Android versions
            saveToExternalStorage(context, content, fileName)
        }
    }
    
    /**
     * Save using MediaStore API (Android 10+)
     */
    private fun saveUsingMediaStore(
        context: Context,
        content: String,
        fileName: String,
        mimeType: String
    ): String? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BulkSend")
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }
                Log.d(TAG, "File saved: $fileName")
                "Downloads/BulkSend/$fileName"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving with MediaStore", e)
            null
        }
    }
    
    /**
     * Save to external storage (Android 9 and below)
     */
    @Suppress("DEPRECATION")
    private fun saveToExternalStorage(
        context: Context,
        content: String,
        fileName: String
    ): String? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val bulkSendDir = File(downloadsDir, "BulkSend")
            
            if (!bulkSendDir.exists()) {
                bulkSendDir.mkdirs()
            }
            
            val file = File(bulkSendDir, fileName)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(content.toByteArray())
            }
            
            Log.d(TAG, "File saved: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to external storage", e)
            null
        }
    }
    

}

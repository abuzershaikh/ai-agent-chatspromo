package com.message.bulksend.pdfviewer

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Manager for storing and retrieving recently opened PDFs
 * PDFs are copied to app's chatspromo folder for persistent access
 */
object RecentPdfManager {
    
    private const val PREFS_NAME = "pdf_recent"
    private const val KEY_RECENT_PDFS = "recent_pdfs"
    private const val MAX_RECENT = 20
    private const val PDF_FOLDER = "chatspromo/pdfs"
    
    data class RecentPdf(
        val filePath: String,  // Local file path in app storage
        val name: String,
        val timestamp: Long
    )
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private fun getPdfFolder(context: Context): File {
        val folder = File(context.filesDir, PDF_FOLDER)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }
    
    /**
     * Add PDF to recent list by copying it to app storage
     */
    fun addRecentPdf(context: Context, uri: Uri, name: String): String? {
        try {
            // Create unique filename
            val timestamp = System.currentTimeMillis()
            val sanitizedName = name.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val fileName = "${timestamp}_$sanitizedName"
            
            // Copy PDF to app storage
            val pdfFolder = getPdfFolder(context)
            val destFile = File(pdfFolder, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Save to recent list
            val prefs = getPrefs(context)
            val recentList = getRecentPdfs(context).toMutableList()
            
            // Remove if already exists (by name)
            recentList.removeAll { it.name == name }
            
            // Add to top
            recentList.add(0, RecentPdf(destFile.absolutePath, name, timestamp))
            
            // Keep only MAX_RECENT items
            if (recentList.size > MAX_RECENT) {
                // Delete old PDF files
                for (i in MAX_RECENT until recentList.size) {
                    try {
                        File(recentList[i].filePath).delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                recentList.subList(MAX_RECENT, recentList.size).clear()
            }
            
            // Save to preferences
            val jsonArray = JSONArray()
            recentList.forEach { pdf ->
                val jsonObject = JSONObject().apply {
                    put("filePath", pdf.filePath)
                    put("name", pdf.name)
                    put("timestamp", pdf.timestamp)
                }
                jsonArray.put(jsonObject)
            }
            
            prefs.edit().putString(KEY_RECENT_PDFS, jsonArray.toString()).apply()
            
            return destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    fun getRecentPdfs(context: Context): List<RecentPdf> {
        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_RECENT_PDFS, null) ?: return emptyList()
        
        val recentList = mutableListOf<RecentPdf>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val filePath = jsonObject.getString("filePath")
                
                // Check if file still exists
                if (File(filePath).exists()) {
                    recentList.add(
                        RecentPdf(
                            filePath = filePath,
                            name = jsonObject.getString("name"),
                            timestamp = jsonObject.getLong("timestamp")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return recentList
    }
    
    fun clearRecentPdfs(context: Context) {
        // Delete all PDF files
        val pdfFolder = getPdfFolder(context)
        pdfFolder.listFiles()?.forEach { it.delete() }
        
        // Clear preferences
        getPrefs(context).edit().remove(KEY_RECENT_PDFS).apply()
    }
    
    fun deletePdf(context: Context, filePath: String) {
        try {
            // Delete file
            File(filePath).delete()
            
            // Remove from recent list
            val prefs = getPrefs(context)
            val recentList = getRecentPdfs(context).toMutableList()
            recentList.removeAll { it.filePath == filePath }
            
            // Save updated list
            val jsonArray = JSONArray()
            recentList.forEach { pdf ->
                val jsonObject = JSONObject().apply {
                    put("filePath", pdf.filePath)
                    put("name", pdf.name)
                    put("timestamp", pdf.timestamp)
                }
                jsonArray.put(jsonObject)
            }
            
            prefs.edit().putString(KEY_RECENT_PDFS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

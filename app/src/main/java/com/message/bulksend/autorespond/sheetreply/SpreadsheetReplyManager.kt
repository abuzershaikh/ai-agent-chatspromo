package com.message.bulksend.autorespond.sheetreply

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class SpreadsheetReplyManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "spreadsheet_reply_prefs",
        Context.MODE_PRIVATE
    )
    
    private val gson = Gson()
    
    companion object {
        private const val KEY_SPREADSHEETS = "spreadsheets_list"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    }
    
    fun getAllSpreadsheets(): List<SpreadsheetData> {
        val json = prefs.getString(KEY_SPREADSHEETS, "[]") ?: "[]"
        val type = object : TypeToken<List<SpreadsheetData>>() {}.type
        return gson.fromJson(json, type)
    }
    
    fun addSpreadsheet(name: String, url: String, type: String) {
        val spreadsheets = getAllSpreadsheets().toMutableList()
        val newSheet = SpreadsheetData(
            id = UUID.randomUUID().toString(),
            name = name,
            url = url,
            type = type
        )
        spreadsheets.add(newSheet)
        saveSpreadsheets(spreadsheets)
    }
    
    fun deleteSpreadsheet(id: String) {
        val spreadsheets = getAllSpreadsheets().toMutableList()
        spreadsheets.removeAll { it.id == id }
        saveSpreadsheets(spreadsheets)
    }

    fun replaceAllSpreadsheets(spreadsheets: List<SpreadsheetData>) {
        saveSpreadsheets(spreadsheets)
    }
    
    private fun saveSpreadsheets(spreadsheets: List<SpreadsheetData>) {
        val json = gson.toJson(spreadsheets)
        prefs.edit().putString(KEY_SPREADSHEETS, json).apply()
    }
    
    fun getLastSyncTime(): String {
        return prefs.getString(KEY_LAST_SYNC_TIME, "") ?: ""
    }
    
    fun setLastSyncTime(time: String) {
        prefs.edit().putString(KEY_LAST_SYNC_TIME, time).apply()
    }
    
    /**
     * Find matching reply from spreadsheet data
     */
    fun findMatchingReply(incomingMessage: String): String? {
        val spreadsheets = getAllSpreadsheets()
        if (spreadsheets.isEmpty()) return null
        
        // Try to read data from all spreadsheets
        for (sheet in spreadsheets) {
            try {
                val data = com.message.bulksend.autorespond.sheetreply.SpreadsheetReader.readSpreadsheetData(
                    context,
                    sheet.url
                )
                
                // Find matching row
                val matchingRow = data.find { row ->
                    row.incomingMessage.equals(incomingMessage, ignoreCase = true) ||
                    incomingMessage.contains(row.incomingMessage, ignoreCase = true)
                }
                
                if (matchingRow != null) {
                    return matchingRow.outgoingMessage
                }
            } catch (e: Exception) {
                // Continue to next spreadsheet if this one fails
                continue
            }
        }
        
        return null
    }
}

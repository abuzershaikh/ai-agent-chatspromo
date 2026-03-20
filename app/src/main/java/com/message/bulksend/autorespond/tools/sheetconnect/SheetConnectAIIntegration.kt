package com.message.bulksend.autorespond.tools.sheetconnect

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI Integration for Google Sheets
 * Exposes Google Sheets mapping capabilities as tools to the AI Agent.
 */
class SheetConnectAIIntegration(private val context: Context) {
    private val tag = "SheetConnectAI"
    private val manager = SheetConnectManager(context)

    fun getToolNames(): List<String> = listOf(
        "check_google_sheet_setup",
        "google_sheet_add_row"
    )

    suspend fun checkSetup(): String = withContext(Dispatchers.IO) {
        try {
            val isSetup = manager.isSetupDone()
            if (!isSetup) {
                return@withContext "Google Sheets is not connected. Open AI Agent -> Google Sheets Setup to connect."
            }
            
            val config = manager.getMappingConfig()
            if (config == null || config.spreadsheetId.isBlank()) {
                return@withContext "Google Sheets is connected, but no mapping is configured. Open the app and 'Configure Sheet Mappings'."
            }

            "Google Sheets is fully connected and mapped!\n" +
            "Spreadsheet: ${config.spreadsheetUrlId}\n" +
            "Active Sheet: ${config.sheetName}\n" +
            "Mapped Columns: Name -> ${config.nameColumn}, Phone -> ${config.phoneColumn}, Email -> ${config.emailColumn}, Notes -> ${config.notesColumn}"
        } catch (e: Exception) {
            Log.e(tag, "checkSetup error: ${e.message}", e)
            "Error while checking Google Sheets status: ${e.message}"
        }
    }

    suspend fun addRow(args: Map<String, String>): String = withContext(Dispatchers.IO) {
        try {
            val config = manager.getMappingConfig()
            if (config == null || config.spreadsheetId.isBlank()) {
                return@withContext "Google Sheets mapping is not configured. Cannot add row."
            }

            // Args from AI
            val name = args["name"] ?: ""
            val phone = args["phone"] ?: ""
            val email = args["email"] ?: ""
            val notes = args["notes"] ?: ""

            // We need to fetch the column headers to know exactly WHICH index to place the values in.
            // A1:Z1 contains the headers.
            val metaResult = manager.fetchSheetMetadata(config.spreadsheetUrlId)
            if (metaResult.isFailure) {
                return@withContext "Failed to fetch sheet columns: ${metaResult.exceptionOrNull()?.message}"
            }

            val sheets = metaResult.getOrNull()?.sheets ?: emptyList()
            val sheetInfo = sheets.find { it.sheetName == config.sheetName }
                ?: return@withContext "Configured sheet '${config.sheetName}' not found in the spreadsheet."

            val columns = sheetInfo.columns
            if (columns.isEmpty()) {
                return@withContext "No columns found in the first row of sheet '${config.sheetName}'."
            }

            // Create an array matching the size of columns, filled with empty strings
            val rowData = MutableList(columns.size) { "" }

            // Map the parsed AI values to the exact column indexes
            if (config.nameColumn.isNotBlank()) {
                val idx = columns.indexOf(config.nameColumn)
                if (idx != -1) rowData[idx] = name
            }
            if (config.phoneColumn.isNotBlank()) {
                val idx = columns.indexOf(config.phoneColumn)
                if (idx != -1) rowData[idx] = phone
            }
            if (config.emailColumn.isNotBlank()) {
                val idx = columns.indexOf(config.emailColumn)
                if (idx != -1) rowData[idx] = email
            }
            if (config.notesColumn.isNotBlank()) {
                val idx = columns.indexOf(config.notesColumn)
                if (idx != -1) rowData[idx] = notes
            }

            // Push to Google Sheets via Cloudflare worker
            val range = "${config.sheetName}!A:Z"
            val writeResult = manager.writeSheetData(config.spreadsheetId, range, listOf(rowData))
            
            if (writeResult.isSuccess) {
                "Successfully added row to Google Sheets!"
            } else {
                "Failed to write to Google Sheets: ${writeResult.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            Log.e(tag, "addRow error: ${e.message}", e)
            "Error while adding row: ${e.message}"
        }
    }

    suspend fun executeTool(toolName: String, args: Map<String, String> = emptyMap()): String {
        return when (toolName) {
            "check_google_sheet_setup" -> checkSetup()
            "google_sheet_add_row" -> addRow(args)
            else -> "Unknown Google Sheet tool: $toolName"
        }
    }
}

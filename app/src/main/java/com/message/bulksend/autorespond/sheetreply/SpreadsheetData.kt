package com.message.bulksend.autorespond.sheetreply

data class SpreadsheetData(
    val id: String,
    val name: String,
    val url: String,
    val type: String, // "google_sheets_link", "csv_file", "csv_link", "excel_file", "excel_link", "spreadsheet_link"
    val addedTime: Long = System.currentTimeMillis()
)

data class SpreadsheetRow(
    val incomingMessage: String,
    val outgoingMessage: String
)

fun spreadsheetTypeLabel(type: String): String {
    return when (type) {
        "google_sheets_link" -> "Google Sheets Link"
        "csv_file" -> "CSV File"
        "csv_link" -> "CSV Link"
        "excel_file" -> "Excel File"
        "excel_link" -> "Excel Link"
        "spreadsheet_link" -> "Spreadsheet Link"
        else -> type.replace("_", " ").replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }
    }
}

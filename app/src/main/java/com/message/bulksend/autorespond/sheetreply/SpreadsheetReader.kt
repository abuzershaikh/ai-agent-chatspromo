package com.message.bulksend.autorespond.sheetreply

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.util.Locale

object SpreadsheetReader {

    private const val TIMEOUT_CONNECT = 10000
    private const val TIMEOUT_READ = 15000

    private data class SourcePayload(
        val bytes: ByteArray,
        val sourceHint: String,
        val contentType: String?
    )

    private enum class SpreadsheetFormat {
        CSV,
        EXCEL
    }
    
    /**
     * Read spreadsheet data from URI (CSV, Excel, etc)
     */
    fun readSpreadsheetData(context: Context, uri: String): List<SpreadsheetRow> {
        return readSpreadsheetDataInternal(context, uri, requireReplyColumn = true)
    }

    /**
     * Read spreadsheet data for preview screen.
     * Unlike auto-reply mode, this allows rows that don't have an outgoing reply.
     */
    fun readSpreadsheetPreviewData(context: Context, uri: String): List<SpreadsheetRow> {
        return readSpreadsheetDataInternal(context, uri, requireReplyColumn = false)
    }

    private fun readSpreadsheetDataInternal(
        context: Context,
        uri: String,
        requireReplyColumn: Boolean
    ): List<SpreadsheetRow> {
        try {
            val cleanUri = uri.trim()
            if (cleanUri.isBlank()) {
                throw Exception("Spreadsheet link or file is empty.")
            }

            val finalUri = normalizeInputUrl(cleanUri)
            val source = if (isHttpUrl(finalUri)) {
                openHttpSource(finalUri)
            } else {
                openLocalSource(context, finalUri)
            }

            return parseSpreadsheetRows(
                bytes = source.bytes,
                sourceHint = source.sourceHint,
                contentType = source.contentType,
                requireReplyColumn = requireReplyColumn
            )
        } catch (e: SecurityException) {
            throw Exception("Permission denied. Please select the file again.")
        } catch (e: Exception) {
            throw Exception("Error reading file: ${readableMessage(e)}")
        }
    }

    fun detectLinkType(url: String): String? {
        val cleanUrl = url.trim()
        if (!isHttpUrl(cleanUrl)) {
            return null
        }

        val lowerUrl = cleanUrl.lowercase(Locale.US)
        return when {
            lowerUrl.contains("docs.google.com/spreadsheets") -> "google_sheets_link"
            lowerUrl.contains("format=csv") || lowerUrl.endsWith(".csv") -> "csv_link"
            lowerUrl.endsWith(".xlsx") ||
                lowerUrl.endsWith(".xls") ||
                lowerUrl.contains("1drv.ms") ||
                lowerUrl.contains("onedrive.live.com") ||
                lowerUrl.contains("sharepoint.com") -> "excel_link"
            lowerUrl.contains("drive.google.com") -> "spreadsheet_link"
            else -> "spreadsheet_link"
        }
    }

    /**
     * Normalize supported link formats to direct downloadable URLs.
     */
    private fun normalizeInputUrl(rawUrl: String): String {
        return when {
            rawUrl.contains("docs.google.com/spreadsheets") -> convertGoogleSheetsToCSV(rawUrl)
            rawUrl.contains("drive.google.com") -> convertGoogleDriveToDirectDownload(rawUrl)
            rawUrl.contains("1drv.ms") ||
                rawUrl.contains("onedrive.live.com") ||
                rawUrl.contains("sharepoint.com") -> convertExcelWebLink(rawUrl)
            else -> rawUrl
        }
    }

    private fun isHttpUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun openHttpSource(url: String): SourcePayload {
        var connection: HttpURLConnection? = null
        return try {
            connection = java.net.URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = TIMEOUT_CONNECT
            connection.readTimeout = TIMEOUT_READ
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            connection.setRequestProperty(
                "Accept",
                "text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,*/*"
            )

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error: $responseCode")
            }

            val contentType = connection.contentType?.substringBefore(";")?.lowercase(Locale.US).orEmpty()
            if (contentType.contains("text/html")) {
                throw Exception(
                    "Link is not directly downloadable. Make sure the file/sheet is shared as 'Anyone with the link (Viewer)'."
                )
            }

            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) {
                throw Exception("Sheet is empty.")
            }

            SourcePayload(
                bytes = bytes,
                sourceHint = listOfNotNull(
                    connection.url?.toString(),
                    connection.getHeaderField("Content-Disposition")
                ).joinToString(" "),
                contentType = contentType
            )
        } catch (e: java.net.UnknownHostException) {
            throw Exception("No internet connection. Please check your network.")
        } catch (e: java.net.SocketTimeoutException) {
            throw Exception("Connection timeout. Please try again.")
        } catch (e: java.io.FileNotFoundException) {
            throw Exception("File not found or access denied. Make sure the sheet is publicly accessible.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun openLocalSource(context: Context, uri: String): SourcePayload {
        val contentUri = Uri.parse(uri)
        val contentType = context.contentResolver.getType(contentUri)
            ?.substringBefore(";")
            ?.lowercase(Locale.US)
        val displayName = context.contentResolver.query(contentUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } else {
                null
            }
        } ?: contentUri.lastPathSegment

        val bytes = context.contentResolver.openInputStream(contentUri)?.use { stream ->
            stream.readBytes()
        } ?: throw Exception("Cannot open file. Permission denied.")

        if (bytes.isEmpty()) {
            throw Exception("Sheet is empty.")
        }

        return SourcePayload(
            bytes = bytes,
            sourceHint = listOfNotNull(displayName, uri).joinToString(" "),
            contentType = contentType
        )
    }

    private fun parseSpreadsheetRows(
        bytes: ByteArray,
        sourceHint: String,
        contentType: String?,
        requireReplyColumn: Boolean
    ): List<SpreadsheetRow> {
        val preferredFormat = detectSpreadsheetFormat(bytes, sourceHint, contentType)
        val primaryParser: () -> List<SpreadsheetRow> = {
            when (preferredFormat) {
                SpreadsheetFormat.CSV -> parseCsvRows(bytes, requireReplyColumn)
                SpreadsheetFormat.EXCEL -> parseExcelRows(bytes, requireReplyColumn)
            }
        }
        val fallbackParser: () -> List<SpreadsheetRow> = {
            when (preferredFormat) {
                SpreadsheetFormat.CSV -> parseExcelRows(bytes, requireReplyColumn)
                SpreadsheetFormat.EXCEL -> parseCsvRows(bytes, requireReplyColumn)
            }
        }

        return try {
            primaryParser()
        } catch (primaryError: Exception) {
            try {
                fallbackParser()
            } catch (fallbackError: Exception) {
                val message = readableMessage(primaryError).takeIf { it.isNotBlank() }
                    ?: readableMessage(fallbackError)
                throw Exception(message)
            }
        }
    }

    private fun readableMessage(error: Throwable): String {
        return error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    }

    private fun detectSpreadsheetFormat(
        bytes: ByteArray,
        sourceHint: String,
        contentType: String?
    ): SpreadsheetFormat {
        val hint = buildString {
            append(sourceHint.lowercase(Locale.US))
            append(' ')
            append(contentType?.lowercase(Locale.US).orEmpty())
        }

        return when {
            hint.contains("format=csv") ||
                hint.contains(".csv") ||
                hint.contains("text/csv") ||
                hint.contains("application/csv") ||
                hint.contains("comma-separated") -> SpreadsheetFormat.CSV
            hint.contains(".xlsx") ||
                hint.contains(".xls") ||
                hint.contains("spreadsheetml") ||
                hint.contains("ms-excel") -> SpreadsheetFormat.EXCEL
            looksLikeZip(bytes) || looksLikeOleWorkbook(bytes) -> SpreadsheetFormat.EXCEL
            else -> SpreadsheetFormat.CSV
        }
    }

    private fun parseCsvRows(
        bytes: ByteArray,
        requireReplyColumn: Boolean
    ): List<SpreadsheetRow> {
        val lines = BufferedReader(
            InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8)
        ).use { reader ->
            reader.readLines().filter { it.isNotBlank() }
        }
        if (lines.isEmpty()) {
            return emptyList()
        }

        val firstLine = lines.first().removePrefix("\uFEFF")
        val firstColumns = parseCsvLine(firstLine)
        validateSheetShape(firstColumns, requireReplyColumn)

        val hasHeader = looksLikeHeader(firstColumns)
        val (incomingIndex, outgoingIndex) = detectColumnIndexes(firstColumns)
        val rowColumns = if (hasHeader) {
            lines.drop(1).map(::parseCsvLine)
        } else {
            listOf(firstColumns) + lines.drop(1).map(::parseCsvLine)
        }

        return buildSpreadsheetRows(rowColumns, incomingIndex, outgoingIndex, requireReplyColumn)
    }

    private fun parseExcelRows(
        bytes: ByteArray,
        requireReplyColumn: Boolean
    ): List<SpreadsheetRow> {
        return WorkbookFactory.create(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0) ?: throw Exception("Sheet is empty.")
            val formatter = DataFormatter()
            val extractedRows = mutableListOf<List<String>>()

            for (row in sheet) {
                val values = extractRowValues(row, formatter)
                if (values.any { it.isNotBlank() }) {
                    extractedRows.add(values)
                }
            }

            if (extractedRows.isEmpty()) {
                return@use emptyList()
            }

            val firstRow = extractedRows.first()
            validateSheetShape(firstRow, requireReplyColumn)

            val hasHeader = looksLikeHeader(firstRow)
            val (incomingIndex, outgoingIndex) = detectColumnIndexes(firstRow)
            val rowColumns = if (hasHeader) extractedRows.drop(1) else extractedRows

            buildSpreadsheetRows(rowColumns, incomingIndex, outgoingIndex, requireReplyColumn)
        }
    }

    private fun validateSheetShape(header: List<String>, requireReplyColumn: Boolean) {
        if (header.isEmpty()) {
            throw Exception("Sheet is empty.")
        }
        if (requireReplyColumn && header.size < 2) {
            throw Exception("Sheet must contain at least 2 columns (incoming, reply).")
        }
    }

    private fun buildSpreadsheetRows(
        rowColumns: List<List<String>>,
        incomingIndex: Int,
        outgoingIndex: Int,
        requireReplyColumn: Boolean
    ): List<SpreadsheetRow> {
        return rowColumns.mapNotNull { columns ->
            if (columns.size <= incomingIndex) {
                return@mapNotNull null
            }

            val incoming = columns[incomingIndex].trim()
            val outgoing = columns.getOrNull(outgoingIndex)?.trim().orEmpty()
            if (incoming.isBlank()) {
                return@mapNotNull null
            }
            if (requireReplyColumn && outgoing.isBlank()) {
                return@mapNotNull null
            }

            SpreadsheetRow(
                incomingMessage = incoming,
                outgoingMessage = outgoing
            )
        }
    }

    private fun extractRowValues(
        row: org.apache.poi.ss.usermodel.Row,
        formatter: DataFormatter
    ): List<String> {
        val lastCell = row.lastCellNum.toInt()
        if (lastCell <= 0) {
            return emptyList()
        }

        val values = MutableList(lastCell) { index ->
            formatter.formatCellValue(row.getCell(index)).trim()
        }
        while (values.isNotEmpty() && values.last().isBlank()) {
            values.removeAt(values.lastIndex)
        }
        return values
    }

    private fun looksLikeZip(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte()
    }

    private fun looksLikeOleWorkbook(bytes: ByteArray): Boolean {
        val oleHeader = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
        )
        return bytes.size >= oleHeader.size && bytes.copyOfRange(0, oleHeader.size).contentEquals(oleHeader)
    }
    
    /**
     * Parse CSV line handling quotes and commas
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && !inQuotes -> {
                    inQuotes = true
                }
                char == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
            i++
        }
        result.add(current.toString().trim())

        return result
    }

    private fun looksLikeHeader(columns: List<String>): Boolean {
        val normalized = columns.map { it.trim().lowercase() }
        val hasIncomingHint = normalized.any {
            it.contains("incoming") ||
                it.contains("keyword") ||
                it.contains("trigger") ||
                it.contains("question") ||
                it.contains("mobile") ||
                it.contains("phone") ||
                it.contains("number")
        }
        val hasReplyHint = normalized.any {
            it.contains("reply") || it.contains("response") || it.contains("outgoing") || it.contains("answer")
        }
        return hasIncomingHint || hasReplyHint
    }

    private fun detectColumnIndexes(header: List<String>): Pair<Int, Int> {
        if (header.size < 2) return 0 to 1

        val normalized = header.map { it.trim().lowercase() }
        val incomingHints = listOf(
            "incoming",
            "keyword",
            "trigger",
            "question",
            "query",
            "prompt",
            "mobile",
            "phone",
            "number"
        )
        val outgoingHints = listOf("reply", "response", "outgoing", "answer")

        val incomingIndex = normalized.indexOfFirst { text ->
            incomingHints.any { hint -> text.contains(hint) }
        }.takeIf { it >= 0 } ?: 0

        val outgoingIndex = normalized.indexOfFirst { text ->
            outgoingHints.any { hint -> text.contains(hint) }
        }.takeIf { it >= 0 } ?: if (incomingIndex == 0) 1 else 0

        return incomingIndex to outgoingIndex
    }
    
    /**
     * Convert Google Sheets URL to CSV export URL
     */
    private fun convertGoogleSheetsToCSV(url: String): String {
        // Extract spreadsheet ID from various Google Sheets URL formats
        val spreadsheetId = when {
            url.contains("/d/") -> {
                // Format: https://docs.google.com/spreadsheets/d/SPREADSHEET_ID/edit
                val startIndex = url.indexOf("/d/") + 3
                val endIndex = url.indexOf("/", startIndex).takeIf { it > 0 } ?: url.length
                url.substring(startIndex, endIndex)
            }
            url.contains("id=") -> {
                // Format: https://docs.google.com/spreadsheets/u/0/?id=SPREADSHEET_ID
                val startIndex = url.indexOf("id=") + 3
                val endIndex = url.indexOf("&", startIndex).takeIf { it > 0 } ?: url.length
                url.substring(startIndex, endIndex)
            }
            else -> throw Exception("Invalid Google Sheets URL format")
        }
        
        val gid = extractGid(url)
        return if (gid.isNullOrBlank()) {
            "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?format=csv"
        } else {
            "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?format=csv&gid=$gid"
        }
    }

    private fun extractGid(url: String): String? {
        val parsed = Uri.parse(url)
        parsed.getQueryParameter("gid")?.takeIf { it.isNotBlank() }?.let { return it }

        val fragment = parsed.fragment ?: return null
        val match = Regex("(?:^|&)gid=(\\d+)").find(fragment)
        return match?.groupValues?.getOrNull(1)
    }

    /**
     * Convert Google Drive share link to direct download URL.
     */
    private fun convertGoogleDriveToDirectDownload(url: String): String {
        val fileId = when {
            url.contains("/file/d/") -> {
                val startIndex = url.indexOf("/file/d/") + 8
                val endIndex = url.indexOf("/", startIndex).takeIf { it > 0 } ?: url.length
                url.substring(startIndex, endIndex)
            }
            url.contains("id=") -> {
                Uri.parse(url).getQueryParameter("id") ?: ""
            }
            else -> ""
        }.trim()

        if (fileId.isBlank()) {
            throw Exception("Invalid Google Drive file URL format")
        }

        return "https://drive.google.com/uc?export=download&id=$fileId"
    }

    private fun convertExcelWebLink(url: String): String {
        val parsed = Uri.parse(url)
        if (parsed.getQueryParameter("download") == "1") {
            return url
        }
        return if (url.contains("?")) {
            "$url&download=1"
        } else {
            "$url?download=1"
        }
    }
    
    /**
     * Get sample data for demo/fallback
     */
    fun getSampleData(): List<SpreadsheetRow> {
        return listOf(
            SpreadsheetRow("hi", "Hello! How can I help you?"),
            SpreadsheetRow("price", "Our prices start at ₹199"),
            SpreadsheetRow("help", "I'm here to assist you!"),
            SpreadsheetRow("thanks", "You're welcome! 😊"),
            SpreadsheetRow("bye", "Goodbye! Have a great day!"),
            SpreadsheetRow("info", "For more information, visit our website"),
            SpreadsheetRow("contact", "You can reach us at support@example.com"),
            SpreadsheetRow("hours", "We're open Monday-Friday, 9 AM - 6 PM")
        )
    }
}

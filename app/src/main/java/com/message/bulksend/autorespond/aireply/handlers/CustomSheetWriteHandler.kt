package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import com.message.bulksend.autorespond.ai.customsheet.CustomTemplateSheetManager
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.autorespond.tools.sheetconnect.SheetConnectManager
import com.message.bulksend.autorespond.tools.sheetconnect.SheetMappingConfig
import java.util.Locale

/**
 * Executes [WRITE_SHEET: key=value; key2=value2] command emitted by AI in CUSTOM template mode.
 */
class CustomSheetWriteHandler(
    private val appContext: Context
) : MessageHandler {

    private val settings = AIAgentSettingsManager(appContext)
    private val sheetManager = CustomTemplateSheetManager(appContext)
    private val sheetConnectManager = SheetConnectManager(appContext)

    override fun getPriority(): Int = 60

    override suspend fun handle(
        context: Context,
        message: String,
        response: String,
        senderPhone: String,
        senderName: String
    ): HandlerResult {
        if (!settings.activeTemplate.equals("CUSTOM", ignoreCase = true)) {
            return HandlerResult(success = true)
        }
        if (!settings.customTemplateEnableSheetWriteTool) {
            return HandlerResult(success = true)
        }

        return try {
            val commandPattern = Regex("\\[WRITE_SHEET:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            val matches = commandPattern.findAll(response).toList()
            if (matches.isEmpty()) {
                return HandlerResult(success = true)
            }

            var modifiedResponse = response
            var writeSuccess = false
            val toolActions = mutableListOf<String>()
            val templateName = settings.customTemplateName.trim().ifBlank { "Custom AI Template" }
            val writeMode = settings.customTemplateWriteStorageMode

            matches.forEach { match ->
                val payload = match.groupValues.getOrNull(1).orEmpty()
                val parsed = parsePayload(payload).toMutableMap()
                val targetSheet = parsed.remove("__sheet__")
                val resolvedGoogleTarget = targetSheet ?: settings.customTemplateGoogleWriteSheetName

                if (parsed.isNotEmpty()) {
                    val ok =
                        when (writeMode) {
                            AIAgentSettingsManager.SHEET_WRITE_MODE_GOOGLE ->
                                writeToGoogleSheet(
                                    payload = parsed,
                                    targetSheet = resolvedGoogleTarget,
                                    senderPhone = senderPhone
                                )

                            else ->
                                sheetManager.upsertDataForPhone(
                                    templateName = templateName,
                                    sheetName = targetSheet,
                                    phoneNumber = senderPhone,
                                    userName = senderName,
                                    fields = parsed,
                                    sourceMessage = message
                                )
                        }
                    writeSuccess = writeSuccess || ok
                    val sheetLabel = resolvedGoogleTarget?.trim().orEmpty().ifBlank { "default" }
                    toolActions +=
                        "WRITE_SHEET:$sheetLabel:${if (ok) "SUCCESS" else "FAILED"}"
                }

                modifiedResponse = modifiedResponse.replace(match.value, "").trim()
            }

            modifiedResponse = modifiedResponse.replace(Regex("\\n{3,}"), "\n\n").trim()
            if (writeSuccess && modifiedResponse.isBlank()) {
                modifiedResponse = "Details saved to sheet."
            }

            HandlerResult(
                success = true,
                modifiedResponse = modifiedResponse,
                metadata =
                    mapOf(
                        "tool_actions" to toolActions,
                        "tool_action_count" to toolActions.size
                    )
            )
        } catch (e: Exception) {
            android.util.Log.e("CustomSheetWrite", "Failed to process write command: ${e.message}", e)
            HandlerResult(success = false)
        }
    }

    private suspend fun writeToGoogleSheet(
        payload: Map<String, String>,
        targetSheet: String?,
        senderPhone: String
    ): Boolean {
        val cleaned = payload.mapNotNull { (key, value) ->
            val safeKey = key.trim()
            val safeValue = value.trim()
            if (safeKey.isBlank() || safeValue.isBlank()) null else safeKey to safeValue
        }
            .toMap()

        if (cleaned.isEmpty()) return false

        val existingConfig = sheetConnectManager.getMappingConfig()
        val manualSheetRef = settings.customTemplateGoogleSheetId.trim()
        val resolvedConfig =
            if (manualSheetRef.isNotBlank() ||
                settings.customTemplateGoogleSheetName.trim().isNotBlank()
            ) {
                val base = existingConfig ?: SheetMappingConfig()
                base.copy(
                    spreadsheetUrlId = manualSheetRef.ifBlank { base.spreadsheetUrlId },
                    spreadsheetId = manualSheetRef.ifBlank { base.spreadsheetId },
                    sheetName = settings.customTemplateGoogleSheetName.trim().ifBlank { base.sheetName }
                )
            } else {
                existingConfig
            } ?: return false

        val spreadsheetRef =
            manualSheetRef.ifBlank {
                resolvedConfig.spreadsheetId.trim().ifBlank { resolvedConfig.spreadsheetUrlId.trim() }
            }

        if (spreadsheetRef.isBlank()) {
            return false
        }

        val metadata = sheetConnectManager.fetchSheetMetadata(spreadsheetRef)
        if (metadata.isFailure) return false
        val sheets = metadata.getOrNull()?.sheets.orEmpty()
        val preferredSheetName =
            when {
                targetSheet != null -> targetSheet.trim()
                settings.customTemplateGoogleWriteSheetName.isNotBlank() ->
                    settings.customTemplateGoogleWriteSheetName
                resolvedConfig.sheetName.isNotBlank() ->
                    resolvedConfig.sheetName
                else -> ""
            }
        val selectedSheet =
            sheets.find { it.sheetName.trim().equals(preferredSheetName?.trim().orEmpty(), ignoreCase = true) }
                ?: sheets.firstOrNull()
        if (selectedSheet == null) return false
        val columns = selectedSheet.columns.map { it.trim() }
        if (columns.isEmpty()) return false

        val columnIndexes =
            columns.withIndex().associateBy({ normalizeFieldName(it.value) }, { it.index })
        val compactIndexes =
            columns.withIndex().associateBy({ normalizeFieldForMatch(it.value) }, { it.index })
        val mapped = mutableMapOf<Int, String>()

        cleaned.forEach { (fieldName, fieldValue) ->
            val idx = resolveWriteColumnIndex(
                fieldName = fieldName,
                existingConfig = existingConfig,
                columnIndexes = columnIndexes,
                compactIndexes = compactIndexes
            )
            if (idx != null) {
                mapped[idx] = fieldValue
            }
        }

        val phoneColumnIndex = resolvePhoneColumnIndex(existingConfig, columnIndexes, compactIndexes)
        if (mapped.isEmpty() && senderPhone.isBlank()) return false

        val appendRow = MutableList(columns.size) { "" }
        mapped.forEach { (index, value) ->
            appendRow[index] = value
        }
        if (phoneColumnIndex != null && senderPhone.isNotBlank() && appendRow[phoneColumnIndex].isBlank()) {
            appendRow[phoneColumnIndex] = senderPhone
        }
        if (appendRow.all { it.isBlank() }) return false

        val readRange = buildSheetRange(selectedSheet.sheetName, "A:Z")
        var existingRowsForMatch: List<List<String>>? = null
        val existingRowNumber =
            if (phoneColumnIndex != null && senderPhone.isNotBlank()) {
                val existingRows = sheetConnectManager.readSheetData(spreadsheetRef, readRange).getOrElse {
                    return false
                }
                existingRowsForMatch = existingRows
                findExistingRowNumber(existingRows, phoneColumnIndex, senderPhone)
            } else {
                null
            }

        if (existingRowNumber != null) {
            val existingRows = existingRowsForMatch ?: return false
            val existingRow = existingRows.getOrNull(existingRowNumber - 1).orEmpty()
            val mergedRow =
                MutableList(columns.size) { index ->
                    existingRow.getOrNull(index).orEmpty()
                }
            mapped.forEach { (index, value) ->
                mergedRow[index] = value
            }
            if (phoneColumnIndex != null && senderPhone.isNotBlank() && mergedRow[phoneColumnIndex].isBlank()) {
                mergedRow[phoneColumnIndex] = senderPhone
            }
            return sheetConnectManager
                .updateSheetData(
                    spreadsheetRef,
                    buildSheetRange(
                        selectedSheet.sheetName,
                        "A$existingRowNumber:${columnLabel(columns.lastIndex)}$existingRowNumber"
                    ),
                    listOf(mergedRow)
                )
                .isSuccess
        }

        return sheetConnectManager
            .writeSheetData(
                spreadsheetRef,
                readRange,
                listOf(appendRow)
            )
            .isSuccess
    }

    private fun parsePayload(raw: String): Map<String, String> {
        val normalized = raw.replace("|", ";").replace("\n", ";")
        var parts =
            normalized.split(";")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        if (parts.size <= 1 && raw.contains(",")) {
            parts =
                raw.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
        }

        val map = linkedMapOf<String, String>()
        parts.forEach { part ->
            val idx = part.indexOf('=')
            if (idx <= 0 || idx >= part.length - 1) return@forEach
            val key = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (key.isBlank() || value.isBlank()) return@forEach

            if (key.equals("sheet", ignoreCase = true) || key.equals("table", ignoreCase = true)) {
                map["__sheet__"] = value
            } else {
                map[key] = value
            }
            }
        return map
    }

    private fun resolveColumnAlias(
        normalizedAlias: String,
        config: SheetMappingConfig,
        columnIndexes: Map<String, Int>,
        compactIndexes: Map<String, Int>
    ): Int? {
        val aliasColumn = when (normalizedAlias) {
            "name", "fullname", "customer name" -> config.nameColumn
            "phone", "mobile", "mobile number", "phonenumber", "contact" -> config.phoneColumn
            "email", "mail", "e-mail" -> config.emailColumn
            "notes", "note", "remarks", "details" -> config.notesColumn
            else -> ""
        }.trim()
        if (aliasColumn.isBlank()) return null
        return columnIndexes[normalizeFieldName(aliasColumn)] ?: compactIndexes[normalizeFieldForMatch(aliasColumn)]
    }

    private fun resolveWriteColumnIndex(
        fieldName: String,
        existingConfig: SheetMappingConfig?,
        columnIndexes: Map<String, Int>,
        compactIndexes: Map<String, Int>
    ): Int? {
        val normalizedField = normalizeFieldName(fieldName)
        val compactField = normalizeFieldForMatch(fieldName)
        return columnIndexes[normalizedField]
            ?: compactIndexes[compactField]
            ?: if (existingConfig != null) {
                resolveColumnAlias(normalizedField, existingConfig, columnIndexes, compactIndexes)
            } else {
                null
            }
    }

    private fun normalizeFieldName(raw: String): String {
        return raw.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }

    private fun normalizeFieldForMatch(raw: String): String {
        return normalizeFieldName(raw).replace(Regex("[^a-z0-9]"), "")
    }

    private fun resolvePhoneColumnIndex(
        existingConfig: SheetMappingConfig?,
        columnIndexes: Map<String, Int>,
        compactIndexes: Map<String, Int>
    ): Int? {
        val aliases =
            listOf(
                "phone",
                "mobile",
                "mobile number",
                "phone number",
                "customer phone",
                "customer mobile",
                "chat phone",
                "contact",
                "contact number",
                "whatsapp",
                "whatsapp number",
                "wa number"
            )
        aliases.forEach { alias ->
            columnIndexes[normalizeFieldName(alias)]?.let { return it }
            compactIndexes[normalizeFieldForMatch(alias)]?.let { return it }
        }
        return existingConfig?.let {
            resolveColumnAlias("phone", it, columnIndexes, compactIndexes)
        }
    }

    private fun findExistingRowNumber(
        rows: List<List<String>>,
        phoneColumnIndex: Int,
        senderPhone: String
    ): Int? {
        for (rowIndex in 1 until rows.size) {
            val existingPhone = rows[rowIndex].getOrNull(phoneColumnIndex).orEmpty()
            if (phonesMatch(existingPhone, senderPhone)) {
                return rowIndex + 1
            }
        }
        return null
    }

    private fun phonesMatch(left: String, right: String): Boolean {
        val normalizedLeft = normalizePhoneForMatch(left)
        val normalizedRight = normalizePhoneForMatch(right)
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) return false
        if (normalizedLeft == normalizedRight) return true
        if (normalizedLeft.length >= 10 && normalizedRight.length >= 10) {
            return normalizedLeft.takeLast(10) == normalizedRight.takeLast(10)
        }
        return normalizedLeft.endsWith(normalizedRight) || normalizedRight.endsWith(normalizedLeft)
    }

    private fun normalizePhoneForMatch(raw: String): String {
        return raw.filter { it.isDigit() }
    }

    private fun buildSheetRange(sheetName: String, cellRange: String): String {
        val escapedName = sheetName.replace("'", "''")
        return "'$escapedName'!$cellRange"
    }

    private fun columnLabel(columnIndex: Int): String {
        var value = columnIndex
        val builder = StringBuilder()
        while (value >= 0) {
            builder.append(('A'.code + (value % 26)).toChar())
            value = (value / 26) - 1
        }
        return builder.reverse().toString()
    }
}

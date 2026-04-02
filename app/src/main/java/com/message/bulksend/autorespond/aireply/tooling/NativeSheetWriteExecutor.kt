package com.message.bulksend.autorespond.aireply.tooling

import android.content.Context
import com.message.bulksend.autorespond.ai.customsheet.CustomTemplateSheetManager
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.autorespond.tools.sheetconnect.SheetConnectManager
import com.message.bulksend.autorespond.tools.sheetconnect.SheetMappingConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class NativeSheetWriteExecutor(
    context: Context
) {
    private val appContext = context.applicationContext
    private val settings = AIAgentSettingsManager(appContext)
    private val sheetManager = CustomTemplateSheetManager(appContext)
    private val sheetConnectManager = SheetConnectManager(appContext)

    suspend fun write(
        args: JSONObject,
        senderPhone: String,
        senderName: String,
        sourceMessage: String
    ): SkillExecutionResult {
        if (!settings.customTemplateEnableSheetWriteTool) {
            return SkillExecutionResult.ignored("Sheet write tool disabled")
        }

        val targetSheet = args.optString("sheet").trim().ifBlank { null }
        val fieldsObj = args.optJSONObject("fields")
        val payload =
            if (fieldsObj != null) {
                flattenJsonObject(fieldsObj)
            } else {
                flattenJsonObject(args, excluded = setOf("sheet", "fields"))
            }

        val cleaned = payload.mapNotNull { (k, v) ->
            val key = k.trim()
            val value = v.trim()
            if (key.isBlank() || value.isBlank()) null else key to value
        }.toMap()

        if (cleaned.isEmpty()) {
            return SkillExecutionResult.ignored("No sheet fields provided")
        }

        val resolvedLocalTarget = resolveLocalTableTarget(targetSheet)
        val ok =
            when (settings.customTemplateWriteStorageMode) {
                AIAgentSettingsManager.SHEET_WRITE_MODE_GOOGLE ->
                    writeToGoogleSheet(
                        payload = cleaned,
                        targetSheet = targetSheet,
                        senderPhone = senderPhone
                    )

                else ->
                    writeToTableSheet(
                        payload = cleaned,
                        targetSheet = resolvedLocalTarget.sheetName,
                        useLinkedSheetMapping = resolvedLocalTarget.useLinkedSheetMapping,
                        senderPhone = senderPhone,
                        senderName = senderName,
                        sourceMessage = sourceMessage
                    )
            }

        val sheetLabel =
            when (settings.customTemplateWriteStorageMode) {
                AIAgentSettingsManager.SHEET_WRITE_MODE_GOOGLE ->
                    targetSheet?.ifBlank { "default" } ?: "default"
                else -> resolvedLocalTarget.sheetName?.ifBlank { "default" } ?: "default"
            }
        val action = "WRITE_SHEET:$sheetLabel:${if (ok) "SUCCESS" else "FAILED"}"

        return if (ok) {
            SkillExecutionResult.success(
                message = "Sheet write completed",
                payload = JSONObject()
                    .put("sheet", sheetLabel)
                    .put("saved_fields", JSONObject(cleaned)),
                toolActions = listOf(action)
            )
        } else {
            SkillExecutionResult.error(
                message = "Sheet write failed",
                retryable = true,
                payload = JSONObject()
                    .put("sheet", sheetLabel)
                    .put("attempted_fields", JSONObject(cleaned)),
                attempts = 1
            ).copy(toolActions = listOf(action))
        }
    }

    private suspend fun writeToTableSheet(
        payload: Map<String, String>,
        targetSheet: String?,
        useLinkedSheetMapping: Boolean,
        senderPhone: String,
        senderName: String,
        sourceMessage: String
    ): Boolean {
        if (useLinkedSheetMapping && !targetSheet.isNullOrBlank()) {
            return sheetManager.upsertMappedDataForPhone(
                folderName = settings.customTemplateSheetFolderName,
                sheetName = targetSheet,
                phoneNumber = senderPhone,
                fields = payload,
                allowedFields = resolveConfiguredWriteFields()
            )
        }

        val templateName = settings.customTemplateName.trim().ifBlank { "Custom AI Template" }
        return sheetManager.upsertDataForPhone(
            templateName = templateName,
            sheetName = targetSheet,
            phoneNumber = senderPhone,
            userName = senderName,
            fields = payload,
            sourceMessage = sourceMessage,
            folderNameOverride = settings.customTemplateSheetFolderName
        )
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
        }.toMap()

        if (cleaned.isEmpty()) return false

        val existingConfig = sheetConnectManager.getMappingConfig()
        val manualSheetRef = settings.customTemplateGoogleSheetId.trim()
        val resolvedConfig =
            if (manualSheetRef.isNotBlank() || settings.customTemplateGoogleSheetName.trim().isNotBlank()) {
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
            sheets.find { it.sheetName.trim().equals(preferredSheetName.trim(), ignoreCase = true) }
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

    private fun flattenJsonObject(
        obj: JSONObject,
        excluded: Set<String> = emptySet()
    ): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next().trim()
            if (key.isBlank() || key in excluded) continue
            val value = obj.opt(key)
            if (value == null || value == JSONObject.NULL) continue
            val text = value.toString().trim()
            if (text.isBlank()) continue
            out[key] = text
        }
        return out
    }

    private fun resolveLocalTableTarget(targetSheet: String?): LocalTableWriteTarget {
        val explicitTarget = targetSheet?.trim().orEmpty()
        val linkedSheet = settings.customTemplateLinkedWriteSheetName.trim()
        val linkedFolder = settings.customTemplateSheetFolderName.trim()
        val resolvedSheet = explicitTarget.ifBlank { linkedSheet }.ifBlank { null }
        val useLinkedSheetMapping =
            resolvedSheet != null &&
                linkedSheet.isNotBlank() &&
                linkedFolder.isNotBlank() &&
                resolvedSheet.equals(linkedSheet, ignoreCase = true)
        return LocalTableWriteTarget(
            sheetName = resolvedSheet,
            useLinkedSheetMapping = useLinkedSheetMapping
        )
    }

    private fun resolveConfiguredWriteFields(): List<String> {
        val rawSchema = settings.customTemplateWriteFieldSchema.trim()
        if (rawSchema.isNotBlank()) {
            runCatching {
                val parsed = mutableListOf<String>()
                val arr = JSONArray(rawSchema)
                for (index in 0 until arr.length()) {
                    val obj = arr.optJSONObject(index) ?: continue
                    val name = obj.optString("name").trim()
                    if (name.isNotBlank()) {
                        parsed += name
                    }
                }
                if (parsed.isNotEmpty()) {
                    return parsed
                }
            }
        }

        return settings.customTemplateWriteSheetColumns
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun resolveColumnAlias(
        normalizedAlias: String,
        config: SheetMappingConfig,
        columnIndexes: Map<String, Int>,
        compactIndexes: Map<String, Int>
    ): Int? {
        val aliasColumn =
            when (normalizedAlias) {
                "name", "fullname", "customer name" -> config.nameColumn
                "phone", "mobile", "mobile number", "phonenumber", "contact" -> config.phoneColumn
                "email", "mail", "e-mail" -> config.emailColumn
                "notes", "note", "remarks", "details" -> config.notesColumn
                else -> ""
            }.trim()
        if (aliasColumn.isBlank()) return null
        return columnIndexes[normalizeFieldName(aliasColumn)]
            ?: compactIndexes[normalizeFieldForMatch(aliasColumn)]
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
                "contact",
                "whatsapp",
                "wa number"
            )

        existingConfig?.phoneColumn?.trim()?.takeIf { it.isNotBlank() }?.let { columnName ->
            columnIndexes[normalizeFieldName(columnName)]
                ?.let { return it }
            compactIndexes[normalizeFieldForMatch(columnName)]
                ?.let { return it }
        }

        aliases.forEach { alias ->
            columnIndexes[normalizeFieldName(alias)]
                ?.let { return it }
            compactIndexes[normalizeFieldForMatch(alias)]
                ?.let { return it }
        }

        return null
    }

    private fun findExistingRowNumber(
        rows: List<List<String>>,
        phoneColumnIndex: Int,
        phoneNumber: String
    ): Int? {
        val target = normalizePhone(phoneNumber)
        if (target.isBlank()) return null

        rows.forEachIndexed { index, row ->
            val candidate = row.getOrNull(phoneColumnIndex).orEmpty()
            if (isSamePhone(target, candidate)) {
                return index + 1
            }
        }
        return null
    }

    private fun normalizePhone(phone: String): String {
        return phone.replace(Regex("[^0-9]"), "")
    }

    private fun isSamePhone(target: String, candidate: String): Boolean {
        val normalized = normalizePhone(candidate)
        if (normalized.isBlank()) return false
        if (target == normalized) return true
        return target.takeLast(10) == normalized.takeLast(10)
    }

    private fun buildSheetRange(sheetName: String, range: String): String {
        val safeName = sheetName.replace("'", "''")
        return "'$safeName'!$range"
    }

    private fun columnLabel(index: Int): String {
        var value = index
        val chars = StringBuilder()
        do {
            chars.append(('A'.code + (value % 26)).toChar())
            value = value / 26 - 1
        } while (value >= 0)
        return chars.reverse().toString()
    }

    private data class LocalTableWriteTarget(
        val sheetName: String?,
        val useLinkedSheetMapping: Boolean
    )
}

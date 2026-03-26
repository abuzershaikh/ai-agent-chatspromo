package com.message.bulksend.aiagent.tools.ownerassist

import android.content.Context
import com.message.bulksend.autorespond.aireply.AIProvider
import com.message.bulksend.autorespond.aireply.AIService
import com.message.bulksend.tablesheet.data.agent.SheetCommandExecutor
import org.json.JSONObject
import java.util.Locale

class OwnerAssistSheetOperationEngine(
    context: Context,
    private val contextBuilder: OwnerAssistContextBuilder = OwnerAssistContextBuilder(context)
) {
    private val appContext = context.applicationContext
    private val sheetCommandExecutor = SheetCommandExecutor(appContext)
    private val aiService = AIService(appContext)

    suspend fun maybeHandleSheetInstruction(
        instruction: String,
        provider: AIProvider
    ): String? {
        val text = instruction.trim()
        if (text.isBlank()) return null

        if (isListTablesRequest(text)) {
            return contextBuilder.buildSheetContext(maxTables = 20, maxColumnsPerTable = 15)
        }

        val direct = parseStructuredSheetCommand(text)
        val looksLikeSheetIntent = direct != null || looksLikeSheetInstruction(text)
        if (!looksLikeSheetIntent) return null

        val parsed = direct ?: parseCommandWithAi(text, provider)
        if (parsed == null || parsed.command == COMMAND_NONE) {
            return buildSheetHelpMessage()
        }

        if (parsed.command !in SUPPORTED_COMMANDS) {
            return buildSheetHelpMessage()
        }

        val result =
            runCatching {
                sheetCommandExecutor.execute(parsed.command, parsed.payload)
            }.getOrElse { error ->
                return "Sheet command failed: ${error.message ?: "unknown error"}"
            }

        return buildExecutionMessage(parsed, result)
    }

    private suspend fun parseCommandWithAi(
        instruction: String,
        provider: AIProvider
    ): ParsedSheetCommand? {
        val sheetContext = contextBuilder.buildSheetContext()
        val prompt = buildCommandParsingPrompt(instruction, sheetContext)
        val raw =
            aiService.generateReply(
                provider = provider,
                message = prompt,
                senderName = "OwnerAssist",
                senderPhone = ""
            )

        val parsedJson = parseJsonObject(raw) ?: return null
        val command = parsedJson.optString("command").trim().uppercase(Locale.ROOT)
        if (command.isBlank()) return null

        val payload = parsedJson.optJSONObject("payload") ?: JSONObject()
        val reason = parsedJson.optString("reason").trim()
        return ParsedSheetCommand(command = command, payload = payload, source = "ai", reason = reason)
    }

    private fun parseStructuredSheetCommand(text: String): ParsedSheetCommand? {
        val upper = text.uppercase(Locale.ROOT)
        val command = SUPPORTED_COMMANDS.firstOrNull { token ->
            upper.contains("[$token:")
        } ?: return null

        val start = upper.indexOf("[$command:")
        if (start < 0) return null
        val jsonStart = text.indexOf('{', start)
        if (jsonStart < 0) return null

        val jsonEnd = findJsonEnd(text, jsonStart)
        if (jsonEnd < 0) return null

        val payloadText = text.substring(jsonStart, jsonEnd + 1)
        val payload = runCatching { JSONObject(payloadText) }.getOrNull() ?: return null
        return ParsedSheetCommand(command = command, payload = payload, source = "direct", reason = "")
    }

    private fun findJsonEnd(text: String, jsonStart: Int): Int {
        var depth = 0
        var inQuotes = false
        var escaping = false

        for (index in jsonStart until text.length) {
            val char = text[index]
            if (inQuotes) {
                if (escaping) {
                    escaping = false
                } else if (char == '\\') {
                    escaping = true
                } else if (char == '"') {
                    inQuotes = false
                }
                continue
            }

            when (char) {
                '"' -> inQuotes = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        return -1
    }

    private fun parseJsonObject(raw: String): JSONObject? {
        val cleaned = raw.trim().removePrefix("```").removeSuffix("```").trim()
        runCatching { return JSONObject(cleaned) }

        val extracted = extractJsonObject(cleaned) ?: extractJsonObject(raw) ?: return null
        return runCatching { JSONObject(extracted) }.getOrNull()
    }

    private fun extractJsonObject(value: String): String? {
        val start = value.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inQuotes = false
        var escaping = false
        for (index in start until value.length) {
            val char = value[index]
            if (inQuotes) {
                if (escaping) {
                    escaping = false
                } else if (char == '\\') {
                    escaping = true
                } else if (char == '"') {
                    inQuotes = false
                }
                continue
            }

            when (char) {
                '"' -> inQuotes = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return value.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun looksLikeSheetInstruction(text: String): Boolean {
        val normalized = text.lowercase(Locale.getDefault())
        val sheetWords = listOf("sheet", "table", "rows", "columns", "column", "data")
        val actionWords = listOf("show", "find", "select", "count", "sum", "avg", "pivot", "update", "upsert", "add", "insert", "bulk")
        return sheetWords.any { normalized.contains(it) } && actionWords.any { normalized.contains(it) }
    }

    private fun isListTablesRequest(text: String): Boolean {
        val normalized = text.lowercase(Locale.getDefault())
        val asksList = normalized.contains("list tables") || normalized.contains("show tables") || normalized.contains("available tables")
        val mentionsSheet = normalized.contains("sheet") || normalized.contains("table")
        return asksList && mentionsSheet
    }

    private fun buildCommandParsingPrompt(
        instruction: String,
        sheetContext: String
    ): String {
        return """
            You are Owner Assist Sheet Parser.
            Convert owner instruction into one sheet command JSON.

            Supported commands:
            1) SHEET_SELECT payload keys: table/tableId, columns, where, contains, orderBy, order, limit
            2) SHEET_AGG payload keys: table/tableId, operation(COUNT|SUM|AVG|MIN|MAX|COUNTIF), column, criteria, where, contains
            3) SHEET_PIVOT payload keys: table/tableId, groupBy, operation, valueColumn, where, contains
            4) SHEET_UPSERT payload keys: table/tableId, key(object), values(object)
            5) SHEET_BULK_UPSERT payload keys: table/tableId, rows(array), keyColumns(array), maxRows

            If instruction is not a sheet operation, return command as NONE.

            Sheet context:
            $sheetContext

            Owner instruction:
            "$instruction"

            Output STRICT JSON only, no markdown:
            {
              "command": "SHEET_SELECT|SHEET_AGG|SHEET_PIVOT|SHEET_UPSERT|SHEET_BULK_UPSERT|NONE",
              "payload": {},
              "reason": "short reason"
            }
        """.trimIndent()
    }

    private fun buildExecutionMessage(
        parsed: ParsedSheetCommand,
        result: SheetCommandExecutor.CommandResult
    ): String {
        if (!result.success) {
            return """
                Owner Assist Sheet Action Failed
                Command: ${parsed.command}
                Reason: ${result.message}
            """.trimIndent()
        }

        val rowsPreview =
            if (result.rows.isNotEmpty()) {
                result.rows.take(5).joinToString("\n") { row ->
                    row.entries.joinToString(", ") { (key, value) -> "$key=$value" }
                }
            } else {
                ""
            }

        val aggregatePreview =
            if (result.aggregate.isNotEmpty()) {
                result.aggregate.entries.joinToString(", ") { (key, value) -> "$key=$value" }
            } else {
                ""
            }

        return buildString {
            append("Owner Assist Sheet Action Executed\n")
            append("Command: ${parsed.command}\n")
            append("Status: SUCCESS\n")
            append("Summary: ${result.message}\n")
            if (result.affectedRows > 0) {
                append("Affected Rows: ${result.affectedRows}\n")
            }
            if (aggregatePreview.isNotBlank()) {
                append("Aggregate: $aggregatePreview\n")
            }
            if (rowsPreview.isNotBlank()) {
                append("Rows (top 5):\n$rowsPreview\n")
            }
            if (parsed.reason.isNotBlank()) {
                append("Reason: ${parsed.reason}\n")
            }
        }.trim()
    }

    private fun buildSheetHelpMessage(): String {
        return """
            Owner Assist sheet command samajh nahi aaya.

            Try examples:
            1) Show top 5 rows from table Leads where city=Mumbai
            2) Count rows in table Orders where status=Pending
            3) Upsert table Leads key phone=+919137167857 values status=Hot
            4) List tables

            You can also send direct structured command:
            [SHEET_SELECT: {"table":"Leads","limit":5}]
        """.trimIndent()
    }

    private data class ParsedSheetCommand(
        val command: String,
        val payload: JSONObject,
        val source: String,
        val reason: String
    )

    private companion object {
        const val COMMAND_NONE = "NONE"
        val SUPPORTED_COMMANDS =
            setOf("SHEET_SELECT", "SHEET_AGG", "SHEET_PIVOT", "SHEET_UPSERT", "SHEET_BULK_UPSERT")
    }
}

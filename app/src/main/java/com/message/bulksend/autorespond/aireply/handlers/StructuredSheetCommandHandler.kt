package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.tablesheet.data.agent.SheetCommandExecutor
import org.json.JSONObject
import java.util.Locale

class StructuredSheetCommandHandler(
    private val appContext: Context
) : MessageHandler {
    private val settings = AIAgentSettingsManager(appContext)
    private val executor = SheetCommandExecutor(appContext)

    override fun getPriority(): Int = 58

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
        if (!settings.customTemplateEnableSheetReadTool && !settings.customTemplateEnableSheetWriteTool) {
            return HandlerResult(success = true)
        }

        val commands = extractCommands(response)
        if (commands.isEmpty()) {
            return HandlerResult(success = true)
        }

        var modifiedResponse = response
        val toolActions = mutableListOf<String>()
        val commandSummaries = mutableListOf<String>()

        commands.sortedByDescending { it.startIndex }.forEach { parsed ->
            val result =
                if (!isCommandAllowedBySettings(parsed.command)) {
                    SheetCommandExecutor.CommandResult(
                        success = false,
                        command = parsed.command,
                        message = "Command is not allowed by current sheet read/write settings"
                    )
                } else {
                    runCatching {
                        executor.execute(parsed.command, JSONObject(parsed.payloadJson))
                    }.getOrElse { error ->
                        SheetCommandExecutor.CommandResult(
                            success = false,
                            command = parsed.command,
                            message = error.message ?: "Invalid JSON payload"
                        )
                    }
                }

            modifiedResponse =
                modifiedResponse.removeRange(
                    parsed.startIndex,
                    parsed.endIndexExclusive
                )

            val actionStatus = if (result.success) "SUCCESS" else "FAILED"
            toolActions += "${parsed.command}:$actionStatus"
            commandSummaries += result.summaryText()
        }

        modifiedResponse = modifiedResponse.trim()
        modifiedResponse = modifiedResponse.replace(Regex("\\n{3,}"), "\n\n")
        if (modifiedResponse.isBlank()) {
            modifiedResponse = commandSummaries.joinToString("\n")
        }

        return HandlerResult(
            success = true,
            modifiedResponse = modifiedResponse,
            metadata =
                mapOf(
                    "tool_actions" to toolActions,
                    "sheet_command_summary" to commandSummaries
                )
        )
    }

    private fun extractCommands(response: String): List<ParsedCommand> {
        val parsed = mutableListOf<ParsedCommand>()
        var searchIndex = 0
        while (searchIndex < response.length) {
            val openBracket = response.indexOf('[', searchIndex)
            if (openBracket < 0) break

            val colonIndex = response.indexOf(':', openBracket + 1)
            if (colonIndex < 0) break

            val command =
                response.substring(openBracket + 1, colonIndex)
                    .trim()
                    .uppercase(Locale.ROOT)
            if (command !in SUPPORTED_COMMANDS) {
                searchIndex = openBracket + 1
                continue
            }

            var cursor = colonIndex + 1
            while (cursor < response.length && response[cursor].isWhitespace()) {
                cursor += 1
            }
            if (cursor >= response.length || response[cursor] != '{') {
                searchIndex = openBracket + 1
                continue
            }

            val jsonStart = cursor
            var jsonEnd = -1
            var depth = 0
            var inQuotes = false
            var escaping = false

            for (index in jsonStart until response.length) {
                val char = response[index]
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
                            jsonEnd = index
                            break
                        }
                    }
                }
            }

            if (jsonEnd < 0) {
                searchIndex = openBracket + 1
                continue
            }

            cursor = jsonEnd + 1
            while (cursor < response.length && response[cursor].isWhitespace()) {
                cursor += 1
            }
            if (cursor >= response.length || response[cursor] != ']') {
                searchIndex = openBracket + 1
                continue
            }

            parsed +=
                ParsedCommand(
                    command = command,
                    payloadJson = response.substring(jsonStart, jsonEnd + 1),
                    startIndex = openBracket,
                    endIndexExclusive = cursor + 1
                )
            searchIndex = cursor + 1
        }

        return parsed
    }

    private fun isCommandAllowedBySettings(command: String): Boolean {
        val normalized = command.trim().uppercase(Locale.ROOT)
        return when (normalized) {
            "SHEET_SELECT", "SHEET_AGG", "SHEET_PIVOT" ->
                settings.customTemplateEnableSheetReadTool || settings.customTemplateEnableSheetWriteTool
            "SHEET_UPSERT", "SHEET_BULK_UPSERT" ->
                settings.customTemplateEnableSheetWriteTool
            else -> false
        }
    }

    private data class ParsedCommand(
        val command: String,
        val payloadJson: String,
        val startIndex: Int,
        val endIndexExclusive: Int
    )

    private companion object {
        val SUPPORTED_COMMANDS =
            setOf("SHEET_SELECT", "SHEET_AGG", "SHEET_UPSERT", "SHEET_BULK_UPSERT", "SHEET_PIVOT")
    }
}

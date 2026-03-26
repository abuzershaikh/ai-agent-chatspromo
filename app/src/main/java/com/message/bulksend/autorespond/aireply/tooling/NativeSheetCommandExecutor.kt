package com.message.bulksend.autorespond.aireply.tooling

import android.content.Context
import com.message.bulksend.tablesheet.data.agent.SheetCommandExecutor
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class NativeSheetCommandExecutor(
    context: Context
) {
    private val commandExecutor = SheetCommandExecutor(context.applicationContext)

    suspend fun execute(
        command: String,
        args: JSONObject
    ): SkillExecutionResult {
        val normalizedCommand = command.trim().uppercase(Locale.ROOT)
        if (normalizedCommand.isBlank()) {
            return SkillExecutionResult.ignored("Sheet command is required")
        }

        val payload = runCatching { JSONObject(args.toString()) }.getOrDefault(JSONObject())
        val result =
            runCatching {
                commandExecutor.execute(normalizedCommand, payload)
            }.getOrElse { error ->
                val fallbackPayload = JSONObject().put("command", normalizedCommand)
                return SkillExecutionResult.error(
                    message = error.message ?: "Sheet command execution failed",
                    retryable = false,
                    payload = fallbackPayload
                ).copy(toolActions = listOf("$normalizedCommand:FAILED"))
            }

        val resultPayload = JSONObject()
            .put("command", result.command)
            .put("message", result.message)
            .put("affected_rows", result.affectedRows)

        if (result.rowId != null) {
            resultPayload.put("row_id", result.rowId)
        }
        if (result.rows.isNotEmpty()) {
            resultPayload.put("rows", rowsToJson(result.rows))
        }
        if (result.aggregate.isNotEmpty()) {
            resultPayload.put("aggregate", JSONObject(result.aggregate))
        }

        val action = "${result.command}:${if (result.success) "SUCCESS" else "FAILED"}"
        return if (result.success) {
            SkillExecutionResult.success(
                message = result.message,
                payload = resultPayload,
                toolActions = listOf(action)
            )
        } else {
            SkillExecutionResult.error(
                message = result.message,
                retryable = false,
                payload = resultPayload
            ).copy(toolActions = listOf(action))
        }
    }

    private fun rowsToJson(rows: List<Map<String, String>>): JSONArray {
        val json = JSONArray()
        rows.forEach { row ->
            val rowObject = JSONObject()
            row.forEach { (key, value) ->
                rowObject.put(key, value)
            }
            json.put(rowObject)
        }
        return json
    }
}


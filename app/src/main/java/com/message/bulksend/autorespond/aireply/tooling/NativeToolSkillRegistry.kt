package com.message.bulksend.autorespond.aireply.tooling

import com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class SkillDefinition(
    val functionName: String,
    val toolId: String?,
    val description: String,
    val parameters: JSONObject,
    val isEnabled: (AIAgentSettingsManager) -> Boolean,
    val commandBuilder: (JSONObject) -> String?
)

class NativeToolSkillRegistry(
    private val settings: AIAgentSettingsManager
) {

    private val definitions = listOf(
        SkillDefinition(
            functionName = "send_document",
            toolId = AgentTaskToolRegistry.SEND_DOCUMENT,
            description = "Send a document by exact document id.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put("document_id", JSONObject().put("type", "string"))
                )
                .put("required", JSONArray().put("document_id")),
            isEnabled = { it.customTemplateEnableDocumentTool },
            commandBuilder = { args ->
                val id = args.optString("document_id").ifBlank { args.optString("id") }.trim()
                if (id.isBlank()) null else "[SEND_DOCUMENT: $id]"
            }
        ),
        SkillDefinition(
            functionName = "send_document_by_tag",
            toolId = AgentTaskToolRegistry.SEND_DOCUMENT,
            description = "Send a document by tag or semantic query.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put("query", JSONObject().put("type", "string"))
                )
                .put("required", JSONArray().put("query")),
            isEnabled = { it.customTemplateEnableDocumentTool },
            commandBuilder = { args ->
                val query = args.optString("query").ifBlank { args.optString("tag") }.trim()
                if (query.isBlank()) null else "[SEND_DOCUMENT_BY_TAG: $query]"
            }
        ),
        SkillDefinition(
            functionName = "send_payment",
            toolId = AgentTaskToolRegistry.SEND_PAYMENT,
            description = "Send payment method details or QR using configured payment method id.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put("method_id", JSONObject().put("type", "string"))
                )
                .put("required", JSONArray().put("method_id")),
            isEnabled = { it.customTemplateEnablePaymentTool },
            commandBuilder = { args ->
                val methodId = args.optString("method_id").ifBlank { args.optString("id") }.trim()
                if (methodId.isBlank()) null else "[SEND_PAYMENT: $methodId]"
            }
        ),
        SkillDefinition(
            functionName = "generate_payment_link",
            toolId = AgentTaskToolRegistry.GENERATE_PAYMENT_LINK,
            description = "Generate Razorpay payment link with amount and description.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("amount", JSONObject().put("type", "number"))
                        .put("description", JSONObject().put("type", "string"))
                )
                .put("required", JSONArray().put("amount").put("description")),
            isEnabled = { it.customTemplateEnablePaymentTool },
            commandBuilder = { args ->
                val amountRaw = if (args.has("amount")) sanitizeValue(args.opt("amount")) else ""
                val description = args.optString("description").trim()
                if (amountRaw.isBlank() || description.isBlank()) null
                else "[GENERATE-PAYMENT-LINK: $amountRaw, $description]"
            }
        ),
        SkillDefinition(
            functionName = "write_sheet",
            toolId = AgentTaskToolRegistry.WRITE_SHEET,
            description = "Write structured fields to sheet storage.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("sheet", JSONObject().put("type", "string"))
                        .put("fields", JSONObject().put("type", "object"))
                ),
            isEnabled = { it.customTemplateEnableSheetWriteTool },
            commandBuilder = { args ->
                val pairs = mutableListOf<String>()
                val sheet = args.optString("sheet").trim()
                if (sheet.isNotBlank()) {
                    pairs += "sheet=${sanitizeValue(sheet)}"
                }

                val fieldsObj = args.optJSONObject("fields")
                if (fieldsObj != null) {
                    pairs += flattenObject(fieldsObj)
                } else {
                    pairs += flattenObject(args, excluded = setOf("sheet"))
                }

                val hasRealField = pairs.any { !it.startsWith("sheet=") }
                if (!hasRealField) null else "[WRITE_SHEET: ${pairs.joinToString("; ")}]"
            }
        ),
        SkillDefinition(
            functionName = "calendar_action",
            toolId = AgentTaskToolRegistry.GOOGLE_CALENDAR,
            description = "Execute a Google Calendar action.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("action", JSONObject().put("type", "string"))
                        .put("params", JSONObject().put("type", "object"))
                )
                .put("required", JSONArray().put("action")),
            isEnabled = { it.customTemplateEnableGoogleCalendarTool },
            commandBuilder = { args ->
                val action = args.optString("action").ifBlank { args.optString("command") }.trim()
                if (action.isBlank()) {
                    null
                } else {
                    val tag = normalizePrefixTag(action, "CALENDAR_")
                    val params = args.optJSONObject("params")
                    val payload =
                        if (params != null) flattenObject(params)
                        else flattenObject(args, excluded = setOf("action", "command"))
                    if (payload.isEmpty()) "[$tag]" else "[$tag: ${payload.joinToString("; ")}]"
                }
            }
        ),
        SkillDefinition(
            functionName = "gmail_action",
            toolId = AgentTaskToolRegistry.GOOGLE_GMAIL,
            description = "Execute a Gmail action.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("action", JSONObject().put("type", "string"))
                        .put("params", JSONObject().put("type", "object"))
                )
                .put("required", JSONArray().put("action")),
            isEnabled = { it.customTemplateEnableGoogleGmailTool },
            commandBuilder = { args ->
                val action = args.optString("action").ifBlank { args.optString("command") }.trim()
                if (action.isBlank()) {
                    null
                } else {
                    val tag = normalizePrefixTag(action, "GMAIL_")
                    val params = args.optJSONObject("params")
                    val payload =
                        if (params != null) flattenObject(params)
                        else flattenObject(args, excluded = setOf("action", "command"))
                    if (payload.isEmpty()) "[$tag]" else "[$tag: ${payload.joinToString("; ")}]"
                }
            }
        ),
        SkillDefinition(
            functionName = "task_step_complete",
            toolId = null,
            description = "Mark current task step complete when current step criteria are met.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put("step", JSONObject().put("type", "integer"))
                )
                .put("required", JSONArray().put("step")),
            isEnabled = { it.customTemplateTaskModeEnabled },
            commandBuilder = { args ->
                val step =
                    when {
                        args.has("step") -> args.optInt("step", -1)
                        args.has("step_number") -> args.optInt("step_number", -1)
                        else -> -1
                    }
                if (step <= 0) null else "[TASK_STEP_COMPLETE: $step]"
            }
        ),
        SkillDefinition(
            functionName = "customer_need_probe",
            toolId = null,
            description = "Inspect known customer needs, identify missing required fields, and suggest next question.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("latest_message", JSONObject().put("type", "string"))
                ),
            isEnabled = { true },
            commandBuilder = { null }
        )
    )

    fun enabledSkills(stepAllowlist: Set<String>? = null): List<SkillDefinition> {
        return definitions.filter { definition ->
            if (!definition.isEnabled(settings)) return@filter false

            val allowlist = stepAllowlist
            if (
                allowlist != null &&
                    settings.customTemplatePromptMode == AIAgentSettingsManager.PROMPT_MODE_STEP_FLOW &&
                    settings.customTemplateTaskModeEnabled
            ) {
                val toolId = definition.toolId
                if (toolId != null && toolId !in allowlist) return@filter false
            }
            true
        }
    }

    fun buildOpenAITools(stepAllowlist: Set<String>? = null): JSONArray {
        val tools = JSONArray()
        enabledSkills(stepAllowlist).forEach { definition ->
            tools.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", definition.functionName)
                            .put("description", definition.description)
                            .put("parameters", definition.parameters)
                    )
            )
        }
        return tools
    }

    fun buildGeminiFunctionDeclarations(stepAllowlist: Set<String>? = null): JSONArray {
        val declarations = JSONArray()
        enabledSkills(stepAllowlist).forEach { definition ->
            declarations.put(
                JSONObject()
                    .put("name", definition.functionName)
                    .put("description", definition.description)
                    .put("parameters", definition.parameters)
            )
        }
        return declarations
    }

    fun buildCommandForCall(
        functionName: String,
        args: JSONObject,
        stepAllowlist: Set<String>? = null
    ): String? {
        val definition =
            enabledSkills(stepAllowlist).firstOrNull {
                it.functionName.equals(functionName.trim(), ignoreCase = true)
            } ?: return null

        return definition.commandBuilder(args)?.trim()?.takeIf { it.isNotBlank() }
    }

    companion object {
        private fun normalizePrefixTag(rawAction: String, prefix: String): String {
            val normalized =
                rawAction.trim()
                    .uppercase(Locale.ROOT)
                    .replace(Regex("[^A-Z0-9_]+"), "_")
                    .trim('_')
            return if (normalized.startsWith(prefix)) normalized else "$prefix$normalized"
        }

        private fun flattenObject(obj: JSONObject, excluded: Set<String> = emptySet()): List<String> {
            val result = mutableListOf<String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next().trim()
                if (key.isBlank() || key in excluded) continue
                val value = sanitizeValue(obj.opt(key))
                if (value.isBlank()) continue
                result += "$key=$value"
            }
            return result
        }

        private fun sanitizeValue(value: Any?): String {
            if (value == null || value == JSONObject.NULL) return ""
            return when (value) {
                is JSONArray -> {
                    val values = mutableListOf<String>()
                    for (i in 0 until value.length()) {
                        val item = sanitizeValue(value.opt(i))
                        if (item.isNotBlank()) values += item
                    }
                    values.joinToString(",")
                }
                is JSONObject -> {
                    flattenObject(value).joinToString(",")
                }
                else -> value.toString()
            }
                .replace('\n', ' ')
                .replace(';', ',')
                .trim()
        }
    }
}

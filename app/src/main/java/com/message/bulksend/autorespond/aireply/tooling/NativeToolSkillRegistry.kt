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
    val executionPolicy: SkillExecutionPolicy = SkillExecutionPolicy(),
    val commandBuilder: (JSONObject) -> String? = { null }
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
            executionPolicy = SkillExecutionPolicy(timeoutMs = 20_000L, maxRetries = 1),
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
            executionPolicy = SkillExecutionPolicy(timeoutMs = 20_000L, maxRetries = 1),
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
            executionPolicy = SkillExecutionPolicy(timeoutMs = 18_000L, maxRetries = 1),
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
            executionPolicy = SkillExecutionPolicy(timeoutMs = 18_000L, maxRetries = 1),
            commandBuilder = { args ->
                val amountRaw = if (args.has("amount")) sanitizeValue(args.opt("amount")) else ""
                val description = args.optString("description").trim()
                if (amountRaw.isBlank() || description.isBlank()) null
                else "[GENERATE-PAYMENT-LINK: $amountRaw, $description]"
            }
        ),
        SkillDefinition(
            functionName = "payment_verification_status",
            toolId = AgentTaskToolRegistry.PAYMENT_VERIFICATION_STATUS,
            description = "Check latest payment verification/payment-link status for the current customer.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("customer_phone", JSONObject().put("type", "string"))
                        .put("plink_id", JSONObject().put("type", "string"))
                        .put("order_id", JSONObject().put("type", "string"))
                ),
            isEnabled = {
                it.customTemplateEnablePaymentTool &&
                    it.customTemplateEnablePaymentVerificationTool
            },
            executionPolicy = SkillExecutionPolicy(timeoutMs = 25_000L, maxRetries = 1)
        ),
        SkillDefinition(
            functionName = "send_agent_form",
            toolId = AgentTaskToolRegistry.SEND_AGENT_FORM,
            description = "Generate and send an Agent Form link to customer using a template key.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("template_key", JSONObject().put("type", "string"))
                )
                .put("required", JSONArray().put("template_key")),
            isEnabled = { it.customTemplateEnableAgentFormTool },
            executionPolicy = SkillExecutionPolicy(timeoutMs = 25_000L, maxRetries = 1),
            commandBuilder = { args ->
                val templateKey = args.optString("template_key").ifBlank { args.optString("template") }.trim()
                if (templateKey.isBlank()) null else "[SEND_AGENT_FORM: $templateKey]"
            }
        ),
        SkillDefinition(
            functionName = "check_agent_form_response",
            toolId = AgentTaskToolRegistry.CHECK_AGENT_FORM_RESPONSE,
            description = "Check latest Agent Form response for the current customer.",
            parameters = JSONObject().put("type", "object").put("properties", JSONObject()),
            isEnabled = { it.customTemplateEnableAgentFormTool },
            executionPolicy = SkillExecutionPolicy(timeoutMs = 12_000L, maxRetries = 0),
            commandBuilder = { "[CHECK_AGENT_FORM_RESPONSE]" }
        ),
        SkillDefinition(
            functionName = "send_catalogue",
            toolId = AgentTaskToolRegistry.CATALOGUE_SEND,
            description = "Send product catalogue media by product id or product name query.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("product_id", JSONObject().put("type", "integer"))
                        .put("product_name", JSONObject().put("type", "string"))
                        .put("query", JSONObject().put("type", "string"))
                ),
            isEnabled = { it.customTemplateEnableAutonomousCatalogueSend },
            executionPolicy = SkillExecutionPolicy(timeoutMs = 35_000L, maxRetries = 1),
            commandBuilder = { args ->
                val name =
                    args.optString("product_name")
                        .ifBlank { args.optString("query") }
                        .trim()
                if (name.isBlank()) {
                    "sending catalogue"
                } else {
                    "sending $name catalogue"
                }
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
            executionPolicy = SkillExecutionPolicy(timeoutMs = 25_000L, maxRetries = 1),
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
            functionName = "sheet_select",
            toolId = AgentTaskToolRegistry.WRITE_SHEET,
            description = "Read rows from local TableSheet using filters/sort/limit.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("table", JSONObject().put("type", "string"))
                        .put("tableId", JSONObject().put("type", "integer"))
                        .put("columns", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")))
                        .put("where", JSONObject().put("type", "object"))
                        .put("contains", JSONObject().put("type", "object"))
                        .put("orderBy", JSONObject().put("type", "string"))
                        .put("order", JSONObject().put("type", "string"))
                        .put("limit", JSONObject().put("type", "integer"))
                ),
            isEnabled = {
                it.customTemplateEnableSheetReadTool || it.customTemplateEnableSheetWriteTool
            },
            executionPolicy = SkillExecutionPolicy(timeoutMs = 25_000L, maxRetries = 0),
            commandBuilder = { args -> buildStructuredJsonCommand("SHEET_SELECT", args) }
        ),
        SkillDefinition(
            functionName = "sheet_aggregate",
            toolId = AgentTaskToolRegistry.WRITE_SHEET,
            description = "Run aggregate query (COUNT/SUM/AVG/MIN/MAX/COUNTIF) on local TableSheet.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("table", JSONObject().put("type", "string"))
                        .put("tableId", JSONObject().put("type", "integer"))
                        .put("operation", JSONObject().put("type", "string"))
                        .put("agg", JSONObject().put("type", "string"))
                        .put("column", JSONObject().put("type", "string"))
                        .put("criteria", JSONObject().put("type", "string"))
                        .put("where", JSONObject().put("type", "object"))
                        .put("contains", JSONObject().put("type", "object"))
                ),
            isEnabled = {
                it.customTemplateEnableSheetReadTool || it.customTemplateEnableSheetWriteTool
            },
            executionPolicy = SkillExecutionPolicy(timeoutMs = 25_000L, maxRetries = 0),
            commandBuilder = { args -> buildStructuredJsonCommand("SHEET_AGG", args) }
        ),
        SkillDefinition(
            functionName = "sheet_pivot",
            toolId = AgentTaskToolRegistry.WRITE_SHEET,
            description = "Run pivot query on local TableSheet grouped by a column.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("table", JSONObject().put("type", "string"))
                        .put("tableId", JSONObject().put("type", "integer"))
                        .put("groupBy", JSONObject().put("type", "string"))
                        .put("operation", JSONObject().put("type", "string"))
                        .put("valueColumn", JSONObject().put("type", "string"))
                        .put("column", JSONObject().put("type", "string"))
                        .put("where", JSONObject().put("type", "object"))
                        .put("contains", JSONObject().put("type", "object"))
                ),
            isEnabled = {
                it.customTemplateEnableSheetReadTool || it.customTemplateEnableSheetWriteTool
            },
            executionPolicy = SkillExecutionPolicy(timeoutMs = 25_000L, maxRetries = 0),
            commandBuilder = { args -> buildStructuredJsonCommand("SHEET_PIVOT", args) }
        ),
        SkillDefinition(
            functionName = "sheet_upsert",
            toolId = AgentTaskToolRegistry.WRITE_SHEET,
            description = "Upsert one row in local TableSheet using key and values objects.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("table", JSONObject().put("type", "string"))
                        .put("tableId", JSONObject().put("type", "integer"))
                        .put("key", JSONObject().put("type", "object"))
                        .put("values", JSONObject().put("type", "object"))
                )
                .put("required", JSONArray().put("key").put("values")),
            isEnabled = { it.customTemplateEnableSheetWriteTool },
            executionPolicy = SkillExecutionPolicy(timeoutMs = 30_000L, maxRetries = 0),
            commandBuilder = { args -> buildStructuredJsonCommand("SHEET_UPSERT", args) }
        ),
        SkillDefinition(
            functionName = "sheet_bulk_upsert",
            toolId = AgentTaskToolRegistry.WRITE_SHEET,
            description = "Bulk upsert multiple rows into local TableSheet.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("table", JSONObject().put("type", "string"))
                        .put("tableId", JSONObject().put("type", "integer"))
                        .put("rows", JSONObject().put("type", "array").put("items", JSONObject().put("type", "object")))
                        .put("keyColumns", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")))
                        .put("maxRows", JSONObject().put("type", "integer"))
                )
                .put("required", JSONArray().put("rows")),
            isEnabled = { it.customTemplateEnableSheetWriteTool },
            executionPolicy = SkillExecutionPolicy(timeoutMs = 45_000L, maxRetries = 0),
            commandBuilder = { args -> buildStructuredJsonCommand("SHEET_BULK_UPSERT", args) }
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
            executionPolicy = SkillExecutionPolicy(timeoutMs = 30_000L, maxRetries = 1),
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
            executionPolicy = SkillExecutionPolicy(timeoutMs = 30_000L, maxRetries = 1),
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
            executionPolicy = SkillExecutionPolicy(timeoutMs = 8_000L, maxRetries = 0),
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
            executionPolicy = SkillExecutionPolicy(timeoutMs = 8_000L, maxRetries = 0)
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

    fun findEnabledSkill(
        functionName: String,
        stepAllowlist: Set<String>? = null
    ): SkillDefinition? {
        val normalized = functionName.trim()
        if (normalized.isBlank()) return null
        return enabledSkills(stepAllowlist).firstOrNull {
            it.functionName.equals(normalized, ignoreCase = true)
        }
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
        private fun buildStructuredJsonCommand(command: String, args: JSONObject): String? {
            if (command.isBlank() || args.length() == 0) return null
            val payload = runCatching { JSONObject(args.toString()) }.getOrNull() ?: return null
            return "[$command: $payload]"
        }
    }
}


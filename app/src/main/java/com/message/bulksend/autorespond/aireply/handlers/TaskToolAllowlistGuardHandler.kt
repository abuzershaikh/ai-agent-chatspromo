package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import com.message.bulksend.autorespond.ai.customtask.manager.AgentTaskManager
import com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager

/**
 * Enforces step-level tool allowlist by stripping disallowed tool commands from AI response
 * before feature handlers execute.
 */
class TaskToolAllowlistGuardHandler(
    private val settingsManager: AIAgentSettingsManager,
    private val taskManager: AgentTaskManager
) : MessageHandler {

    override fun getPriority(): Int = 2

    override suspend fun handle(
        context: Context,
        message: String,
        response: String,
        senderPhone: String,
        senderName: String
    ): HandlerResult {
        if (!settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true)) {
            return HandlerResult(success = true)
        }
        if (settingsManager.customTemplatePromptMode != AIAgentSettingsManager.PROMPT_MODE_STEP_FLOW) {
            return HandlerResult(success = true)
        }
        if (!settingsManager.customTemplateTaskModeEnabled) {
            return HandlerResult(success = true)
        }

        val phoneKey = senderPhone.ifBlank { senderName.ifBlank { "unknown_user" } }
        val currentTask = taskManager.getCurrentTask(phoneKey) ?: return HandlerResult(success = true)

        val selectedTools = AgentTaskToolRegistry.normalizeToolIds(currentTask.allowedTools.orEmpty())
        val allowedTools = selectedTools
            .filter { AgentTaskToolRegistry.isEnabledForTemplate(it, settingsManager) }
            .toMutableSet()

        // WRITE_SHEET is default capability when global sheet-write switch is ON.
        if (settingsManager.customTemplateEnableSheetWriteTool) {
            allowedTools.add(AgentTaskToolRegistry.WRITE_SHEET)
        }

        var sanitized = response
        AgentTaskToolRegistry.allTools().forEach { tool ->
            if (tool.id !in allowedTools) {
                sanitized = stripToolCommands(sanitized, tool.id)
            }
        }
        sanitized = cleanupResponse(sanitized)

        return if (sanitized == response) {
            HandlerResult(success = true)
        } else {
            HandlerResult(success = true, modifiedResponse = sanitized)
        }
    }

    private fun stripToolCommands(text: String, toolId: String): String {
        return when (toolId) {
            AgentTaskToolRegistry.SEND_DOCUMENT -> {
                text.replace(Regex("\\[SEND_DOCUMENT:\\s*.+?\\]", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\[SEND_DOCUMENT_BY_TAG:\\s*.+?\\]", RegexOption.IGNORE_CASE), "")
            }

            AgentTaskToolRegistry.SEND_PAYMENT -> {
                text.replace(Regex("\\[SEND_PAYMENT:\\s*.+?\\]", RegexOption.IGNORE_CASE), "")
            }

            AgentTaskToolRegistry.GENERATE_PAYMENT_LINK -> {
                text.replace(
                    Regex("\\[GENERATE-PAYMENT-LINK:\\s*.+?\\]", RegexOption.IGNORE_CASE),
                    ""
                )
            }

            AgentTaskToolRegistry.PAYMENT_VERIFICATION_STATUS -> {
                text.replace(
                    Regex("\\[(PAYMENT_VERIFICATION_STATUS|CHECK_PAYMENT_STATUS):\\s*.+?\\]", RegexOption.IGNORE_CASE),
                    ""
                )
            }

            AgentTaskToolRegistry.SEND_AGENT_FORM -> {
                text.replace(
                    Regex("\\[(?:SEND[_\\s-]*AGENT[_\\s-]*FORM)\\s*:\\s*[^\\]]+\\]", RegexOption.IGNORE_CASE),
                    ""
                )
            }

            AgentTaskToolRegistry.CHECK_AGENT_FORM_RESPONSE -> {
                text.replace(
                    Regex("\\[(?:CHECK[_\\s-]*AGENT[_\\s-]*FORM[_\\s-]*RESPONSE)\\]", RegexOption.IGNORE_CASE),
                    ""
                )
            }

            AgentTaskToolRegistry.WRITE_SHEET -> {
                text.replace(Regex("\\[WRITE_SHEET:\\s*.+?\\]", RegexOption.IGNORE_CASE), "")
            }

            AgentTaskToolRegistry.CATALOGUE_SEND -> {
                text.lines()
                    .filterNot {
                        Regex(
                            "\\b(send|sending|share|bhej|bhejo|dikha|dikh)\\b.*\\bcatalog(?:ue)?\\b",
                            RegexOption.IGNORE_CASE
                        ).containsMatchIn(it)
                    }
                    .joinToString("\n")
            }

            else -> text
        }
    }

    private fun cleanupResponse(text: String): String {
        return text
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}

fun createTaskToolAllowlistGuardHandler(context: Context): TaskToolAllowlistGuardHandler {
    return TaskToolAllowlistGuardHandler(
        settingsManager = AIAgentSettingsManager(context),
        taskManager = AgentTaskManager(context)
    )
}

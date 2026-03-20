package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import com.message.bulksend.autorespond.ai.customtask.engine.AgentTaskEngine
import com.message.bulksend.autorespond.ai.customtask.manager.AgentTaskManager
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager

/**
 * Detects task completion tags and advances step-flow session.
 * Expected tag format: [TASK_STEP_COMPLETE: stepNumber]
 */
class TaskStepCompletionHandler(
    private val settingsManager: AIAgentSettingsManager,
    private val taskEngine: AgentTaskEngine
) : MessageHandler {

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
        val pattern = Regex("\\[TASK_STEP_COMPLETE:\\s*(\\d+)\\]", RegexOption.IGNORE_CASE)
        val matches = pattern.findAll(response).toList()
        if (matches.isEmpty()) return HandlerResult(success = true)

        val actions = mutableListOf<String>()
        var workflowCompleted = false

        matches.forEach { match ->
            val step = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val result = taskEngine.completeStep(phoneKey, step)
            actions.add(
                if (result.isWorkflowCompleted) {
                    "TASK_STEP_COMPLETE:$step:WORKFLOW_COMPLETED"
                } else {
                    "TASK_STEP_COMPLETE:$step:NEXT_${result.movedToStep ?: "NA"}"
                }
            )
            workflowCompleted = workflowCompleted || result.isWorkflowCompleted
        }

        var cleaned = response.replace(pattern, "").replace(Regex("\\n{3,}"), "\n\n").trim()
        if (workflowCompleted && cleaned.isNotBlank()) {
            cleaned = "$cleaned\n\nAll configured steps are completed."
        }

        return HandlerResult(
            success = true,
            modifiedResponse = cleaned,
            metadata = mapOf("tool_actions" to actions)
        )
    }

    override fun getPriority(): Int = 55
}

fun createTaskStepCompletionHandler(context: Context): TaskStepCompletionHandler {
    val settings = AIAgentSettingsManager(context)
    val manager = AgentTaskManager(context)
    val engine = AgentTaskEngine(manager)
    return TaskStepCompletionHandler(
        settingsManager = settings,
        taskEngine = engine
    )
}


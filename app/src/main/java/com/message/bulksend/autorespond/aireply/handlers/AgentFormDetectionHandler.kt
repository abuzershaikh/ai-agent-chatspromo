package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import com.message.bulksend.aiagent.tools.agentform.AgentFormAutomationStateManager
import com.message.bulksend.aiagent.tools.agentform.AgentFormAIIntegration

/**
 * Detects AgentForm tool commands in AI response.
 *
 * Commands:
 * - [SEND_AGENT_FORM: TEMPLATE_KEY]
 * - [SENDAGENTFORM: TEMPLATE_KEY]
 * - [CHECK_AGENT_FORM_RESPONSE]
 */
class AgentFormDetectionHandler(
    private val agentFormIntegration: AgentFormAIIntegration
) : MessageHandler {

    override fun getPriority(): Int = 42

    override suspend fun handle(
        context: Context,
        message: String,
        response: String,
        senderPhone: String,
        senderName: String
    ): HandlerResult {
        return try {
            var updated = response
            var changed = false
            val toolActions = mutableListOf<String>()

            val sendPattern = Regex(
                "\\[(?:SEND[_\\s-]*AGENT[_\\s-]*FORM)\\s*:\\s*([^\\]]+?)\\]",
                RegexOption.IGNORE_CASE
            )
            val sendFailureTailPattern = Regex(
                "^\\s*(?:link\\s+nahi\\s+bhej\\s+paya\\.?|unable\\s+to\\s+send\\s+link\\.?)\\s*$",
                RegexOption.IGNORE_CASE
            )

            while (true) {
                val match = sendPattern.find(updated) ?: break
                val templateKey = match.groupValues[1].trim()
                val linkResult = agentFormIntegration.createFormLinkForRecipient(templateKey, senderPhone)
                toolActions +=
                    "SEND_AGENT_FORM:$templateKey:${if (linkResult.success) "SUCCESS" else "FAILED"}"
                if (linkResult.success) {
                    val automationState = AgentFormAutomationStateManager(context)
                    if (linkResult.requiresContactVerification && linkResult.url.isNotBlank()) {
                        automationState.setPendingLink(
                            phoneRaw = senderPhone,
                            campaign = linkResult.campaign,
                            formId = linkResult.formId,
                            templateKey = linkResult.templateKey,
                            link = linkResult.url
                        )
                    } else {
                        // Contact automation applies only to contact-picker templates.
                        automationState.clearState(senderPhone)
                    }
                }
                val replacement = linkResult.message.ifBlank {
                    if (linkResult.success) {
                        "Please fill this form."
                    } else {
                        "AgentForm link could not be generated."
                    }
                }
                val before = updated.substring(0, match.range.first)
                val after = updated.substring(match.range.last + 1)
                updated = if (before.isBlank() && sendFailureTailPattern.matches(after)) {
                    replacement
                } else {
                    updated.replaceRange(match.range, replacement)
                }
                changed = true
            }

            val checkPattern = Regex(
                "\\[(?:CHECK[_\\s-]*AGENT[_\\s-]*FORM[_\\s-]*RESPONSE)\\]",
                RegexOption.IGNORE_CASE
            )
            while (true) {
                val match = checkPattern.find(updated) ?: break
                val statusText = agentFormIntegration.buildLatestResponseMessage(senderPhone)
                updated = updated.replaceRange(match.range, statusText)
                toolActions += "CHECK_AGENT_FORM_RESPONSE:SUCCESS"
                changed = true
            }

            if (!changed) {
                return HandlerResult(success = true)
            }

            updated = updated.replace(Regex("\\n{3,}"), "\n\n").trim()
            HandlerResult(
                success = true,
                modifiedResponse = updated,
                metadata =
                    mapOf(
                        "tool_actions" to toolActions,
                        "tool_action_count" to toolActions.size
                    )
            )
        } catch (e: Exception) {
            android.util.Log.e("AgentFormHandler", "Error: ${e.message}", e)
            HandlerResult(success = false)
        }
    }
}

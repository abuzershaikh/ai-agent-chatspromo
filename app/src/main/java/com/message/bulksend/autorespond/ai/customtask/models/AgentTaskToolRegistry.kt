package com.message.bulksend.autorespond.ai.customtask.models

import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import java.util.Locale

/**
 * Central registry for step-flow tool options used in task editor and runtime prompt rules.
 */
object AgentTaskToolRegistry {

    data class ToolDefinition(
        val id: String,
        val label: String,
        val commandHint: String
    )

    const val SEND_DOCUMENT = "SEND_DOCUMENT"
    const val SEND_PAYMENT = "SEND_PAYMENT"
    const val GENERATE_PAYMENT_LINK = "GENERATE-PAYMENT-LINK"
    const val PAYMENT_VERIFICATION_STATUS = "PAYMENT_VERIFICATION_STATUS"
    const val SEND_AGENT_FORM = "SEND_AGENT_FORM"
    const val CHECK_AGENT_FORM_RESPONSE = "CHECK_AGENT_FORM_RESPONSE"
    // Sheet write remains a default capability (when enabled in template settings),
    // so it is intentionally excluded from per-step dropdown definitions.
    const val WRITE_SHEET = "WRITE_SHEET"
    const val CATALOGUE_SEND = "CATALOGUE_SEND"
    const val GOOGLE_CALENDAR = "GOOGLE_CALENDAR"
    const val GOOGLE_GMAIL = "GOOGLE_GMAIL"

    private val definitions = listOf(
        ToolDefinition(
            id = SEND_DOCUMENT,
            label = "Send Document",
            commandHint = "[SEND_DOCUMENT: id] or [SEND_DOCUMENT_BY_TAG: query]"
        ),
        ToolDefinition(
            id = SEND_PAYMENT,
            label = "Send Payment Method",
            commandHint = "[SEND_PAYMENT: method_id]"
        ),
        ToolDefinition(
            id = GENERATE_PAYMENT_LINK,
            label = "Generate Payment Link",
            commandHint = "[GENERATE-PAYMENT-LINK: amount, description]"
        ),
        ToolDefinition(
            id = PAYMENT_VERIFICATION_STATUS,
            label = "Payment Verification",
            commandHint = "Use payment verification status capability"
        ),
        ToolDefinition(
            id = SEND_AGENT_FORM,
            label = "Send Agent Form",
            commandHint = "[SEND_AGENT_FORM: TEMPLATE_KEY]"
        ),
        ToolDefinition(
            id = CHECK_AGENT_FORM_RESPONSE,
            label = "Check Form Response",
            commandHint = "[CHECK_AGENT_FORM_RESPONSE]"
        ),
        ToolDefinition(
            id = CATALOGUE_SEND,
            label = "Catalogue Send",
            commandHint = "Use catalogue send flow for product media"
        ),
        ToolDefinition(
            id = GOOGLE_CALENDAR,
            label = "Google Calendar",
            commandHint = "Use [CALENDAR_...] commands for events, Meet links, tasks, task lists, and calendar reminder settings"
        ),
        ToolDefinition(
            id = GOOGLE_GMAIL,
            label = "Google Gmail",
            commandHint = "Use [GMAIL_...] commands for emails, threads, drafts, labels, auto-tracking, and Gmail history"
        )
    )

    private val byId: Map<String, ToolDefinition> = definitions.associateBy { it.id }

    fun allTools(): List<ToolDefinition> = definitions

    fun getDefinition(toolId: String): ToolDefinition? = byId[normalizeToolId(toolId)]

    fun normalizeToolIds(rawIds: Iterable<String>): List<String> {
        val requested = rawIds.map(::normalizeToolId).filter { it.isNotBlank() }.toSet()
        return definitions.map { it.id }.filter { it in requested }
    }

    fun labelFor(toolId: String): String = getDefinition(toolId)?.label ?: normalizeToolId(toolId)

    fun labelsFor(toolIds: Iterable<String>): List<String> {
        return normalizeToolIds(toolIds).map(::labelFor)
    }

    fun enabledTools(settings: AIAgentSettingsManager): List<ToolDefinition> {
        return definitions.filter { isEnabledForTemplate(it.id, settings) }
    }

    fun enabledToolIds(settings: AIAgentSettingsManager): List<String> {
        return enabledTools(settings).map { it.id }
    }

    fun isEnabledForTemplate(toolId: String, settings: AIAgentSettingsManager): Boolean {
        return when (normalizeToolId(toolId)) {
            SEND_DOCUMENT -> settings.customTemplateEnableDocumentTool
            SEND_PAYMENT,
            GENERATE_PAYMENT_LINK -> settings.customTemplateEnablePaymentTool
            PAYMENT_VERIFICATION_STATUS -> {
                settings.customTemplateEnablePaymentTool &&
                    settings.customTemplateEnablePaymentVerificationTool
            }
            SEND_AGENT_FORM,
            CHECK_AGENT_FORM_RESPONSE -> settings.customTemplateEnableAgentFormTool
            WRITE_SHEET -> settings.customTemplateEnableSheetWriteTool
            CATALOGUE_SEND -> settings.customTemplateEnableAutonomousCatalogueSend
            GOOGLE_CALENDAR -> settings.customTemplateEnableGoogleCalendarTool
            GOOGLE_GMAIL -> settings.customTemplateEnableGoogleGmailTool
            else -> false
        }
    }

    private fun normalizeToolId(value: String): String {
        return value.trim().uppercase(Locale.ROOT)
    }
}

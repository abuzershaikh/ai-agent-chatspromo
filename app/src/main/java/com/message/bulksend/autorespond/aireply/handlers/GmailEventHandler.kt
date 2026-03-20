package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import com.message.bulksend.aiagent.tools.gmail.GmailTrackingTableSheetManager
import com.message.bulksend.aiagent.tools.gmail.GoogleGmailAgentTool
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import org.json.JSONArray
import org.json.JSONObject

class GmailEventHandler(
    private val appContext: Context
) : MessageHandler {

    private val settings = AIAgentSettingsManager(appContext)
    private val trackingSheetManager = GmailTrackingTableSheetManager(appContext)

    override fun getPriority(): Int = 62

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
        if (!settings.customTemplateEnableGoogleGmailTool) {
            return HandlerResult(success = true)
        }

        return try {
            var modifiedResponse = response
            val toolActions = mutableListOf<String>()

            suspend fun run(
                actionName: String,
                regex: Regex,
                optionalPayload: Boolean = false,
                block: suspend (Map<String, String>) -> CommandResult
            ) {
                modifiedResponse =
                    applyPattern(
                        input = modifiedResponse,
                        regex = regex,
                        actionName = actionName,
                        toolActions = toolActions,
                        optionalPayload = optionalPayload,
                        block = block
                    )
            }

            run(
                actionName = "GMAIL_LIST_EMAILS",
                regex = Regex("\\[GMAIL_LIST_EMAILS(?:(.*))?\\]", RegexOption.IGNORE_CASE),
                optionalPayload = true
            ) { parsed ->
                val result = GoogleGmailAgentTool.listEmails(parsed)
                syncHistory(result)
                if (isSuccess(result)) {
                    CommandResult("SUCCESS", "Emails retrieved:\n${formatMessages(result.optJSONArray("messages"))}")
                } else {
                    jsonResult(result, failurePrefix = "Failed to list emails")
                }
            }

            run(
                actionName = "GMAIL_READ_EMAIL",
                regex = Regex("\\[GMAIL_READ_EMAIL:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val id = parsed.valueOf("messageId", "id")
                if (id.isNullOrBlank()) {
                    CommandResult("FAILED_MISSING_ID", "Failed: Message ID is required")
                } else {
                    val result = GoogleGmailAgentTool.readEmail(id, parsed.valueOf("format"))
                    if (isSuccess(result)) {
                        CommandResult("SUCCESS", "Email details:\n${formatMessage(result.optJSONObject("message"))}")
                    } else {
                        jsonResult(result, failurePrefix = "Failed to read email")
                    }
                }
            }

            run(
                actionName = "GMAIL_SEND_EMAIL",
                regex = Regex("\\[GMAIL_SEND_EMAIL:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.sendEmail(parsed.withConversationDefaults(senderPhone, senderName))
                syncHistory(result)
                jsonResult(
                    result = result,
                    successText = "Email sent with automatic tracking enabled",
                    failurePrefix = "Failed to send email"
                )
            }

            run(
                actionName = "GMAIL_REPLY_EMAIL",
                regex = Regex("\\[GMAIL_REPLY_EMAIL:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.replyEmail(parsed.withConversationDefaults(senderPhone, senderName))
                syncHistory(result)
                jsonResult(
                    result = result,
                    successText = "Email reply sent with automatic tracking enabled",
                    failurePrefix = "Failed to reply to email"
                )
            }

            run(
                actionName = "GMAIL_TRASH_EMAIL",
                regex = Regex("\\[GMAIL_TRASH_EMAIL:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.trashEmail(parsed.valueOf("messageId", "id").orEmpty())
                jsonResult(result, successText = "Email moved to trash", failurePrefix = "Failed to trash email")
            }

            run(
                actionName = "GMAIL_UNTRASH_EMAIL",
                regex = Regex("\\[GMAIL_UNTRASH_EMAIL:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.untrashEmail(parsed.valueOf("messageId", "id").orEmpty())
                jsonResult(result, successText = "Email restored from trash", failurePrefix = "Failed to untrash email")
            }

            run(
                actionName = "GMAIL_DELETE_EMAIL",
                regex = Regex("\\[GMAIL_DELETE_EMAIL:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.deleteEmail(parsed.valueOf("messageId", "id").orEmpty())
                jsonResult(result, successText = "Email permanently deleted", failurePrefix = "Failed to delete email")
            }

            run(
                actionName = "GMAIL_MODIFY_EMAIL",
                regex = Regex("\\[GMAIL_MODIFY_EMAIL:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.modifyEmail(parsed)
                jsonResult(result, successText = "Email labels updated", failurePrefix = "Failed to modify email")
            }

            run(
                actionName = "GMAIL_LIST_THREADS",
                regex = Regex("\\[GMAIL_LIST_THREADS(?:(.*))?\\]", RegexOption.IGNORE_CASE),
                optionalPayload = true
            ) { parsed ->
                val result = GoogleGmailAgentTool.listThreads(parsed)
                if (isSuccess(result)) {
                    CommandResult("SUCCESS", "Threads:\n${formatThreads(result.optJSONArray("threads"))}")
                } else {
                    jsonResult(result, failurePrefix = "Failed to list threads")
                }
            }

            run(
                actionName = "GMAIL_READ_THREAD",
                regex = Regex("\\[GMAIL_READ_THREAD:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.readThread(
                    parsed.valueOf("threadId", "id").orEmpty(),
                    parsed.valueOf("format")
                )
                if (isSuccess(result)) {
                    CommandResult("SUCCESS", "Thread details:\n${formatThread(result.optJSONObject("thread"))}")
                } else {
                    jsonResult(result, failurePrefix = "Failed to read thread")
                }
            }

            run(
                actionName = "GMAIL_MODIFY_THREAD",
                regex = Regex("\\[GMAIL_MODIFY_THREAD:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.modifyThread(parsed)
                jsonResult(result, successText = "Thread labels updated", failurePrefix = "Failed to modify thread")
            }

            run(
                actionName = "GMAIL_TRASH_THREAD",
                regex = Regex("\\[GMAIL_TRASH_THREAD:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.trashThread(parsed.valueOf("threadId", "id").orEmpty())
                jsonResult(result, successText = "Thread moved to trash", failurePrefix = "Failed to trash thread")
            }

            run(
                actionName = "GMAIL_UNTRASH_THREAD",
                regex = Regex("\\[GMAIL_UNTRASH_THREAD:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.untrashThread(parsed.valueOf("threadId", "id").orEmpty())
                jsonResult(result, successText = "Thread restored from trash", failurePrefix = "Failed to untrash thread")
            }

            run(
                actionName = "GMAIL_DELETE_THREAD",
                regex = Regex("\\[GMAIL_DELETE_THREAD:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.deleteThread(parsed.valueOf("threadId", "id").orEmpty())
                jsonResult(result, successText = "Thread permanently deleted", failurePrefix = "Failed to delete thread")
            }

            run(
                actionName = "GMAIL_LIST_DRAFTS",
                regex = Regex("\\[GMAIL_LIST_DRAFTS(?:(.*))?\\]", RegexOption.IGNORE_CASE),
                optionalPayload = true
            ) { parsed ->
                val result = GoogleGmailAgentTool.listDrafts(parsed)
                syncHistory(result)
                if (isSuccess(result)) {
                    CommandResult("SUCCESS", "Drafts:\n${formatDrafts(result.optJSONArray("drafts"))}")
                } else {
                    jsonResult(result, failurePrefix = "Failed to list drafts")
                }
            }

            run(
                actionName = "GMAIL_READ_DRAFT",
                regex = Regex("\\[GMAIL_READ_DRAFT:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.readDraft(
                    parsed.valueOf("draftId", "id").orEmpty(),
                    parsed.valueOf("format")
                )
                if (isSuccess(result)) {
                    CommandResult("SUCCESS", "Draft details:\n${formatDraft(result.optJSONObject("draft"))}")
                } else {
                    jsonResult(result, failurePrefix = "Failed to read draft")
                }
            }

            run(
                actionName = "GMAIL_CREATE_DRAFT",
                regex = Regex("\\[GMAIL_CREATE_DRAFT:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.createDraft(parsed.withConversationDefaults(senderPhone, senderName))
                syncHistory(result)
                jsonResult(result, successText = "Draft created with tracking", failurePrefix = "Failed to create draft")
            }

            run(
                actionName = "GMAIL_UPDATE_DRAFT",
                regex = Regex("\\[GMAIL_UPDATE_DRAFT:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.updateDraft(parsed.withConversationDefaults(senderPhone, senderName))
                syncHistory(result)
                jsonResult(result, successText = "Draft updated with tracking", failurePrefix = "Failed to update draft")
            }

            run(
                actionName = "GMAIL_SEND_DRAFT",
                regex = Regex("\\[GMAIL_SEND_DRAFT:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.sendDraft(parsed.valueOf("draftId", "id").orEmpty())
                syncHistory(result)
                jsonResult(result, successText = "Draft sent with tracking", failurePrefix = "Failed to send draft")
            }

            run(
                actionName = "GMAIL_DELETE_DRAFT",
                regex = Regex("\\[GMAIL_DELETE_DRAFT:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.deleteDraft(parsed.valueOf("draftId", "id").orEmpty())
                jsonResult(result, successText = "Draft deleted", failurePrefix = "Failed to delete draft")
            }

            run(
                actionName = "GMAIL_LIST_LABELS",
                regex = Regex("\\[GMAIL_LIST_LABELS(?:(.*))?\\]", RegexOption.IGNORE_CASE),
                optionalPayload = true
            ) {
                val result = GoogleGmailAgentTool.listLabels()
                if (isSuccess(result)) {
                    CommandResult("SUCCESS", "Labels:\n${formatLabels(result.optJSONArray("labels"))}")
                } else {
                    jsonResult(result, failurePrefix = "Failed to list labels")
                }
            }

            run(
                actionName = "GMAIL_CREATE_LABEL",
                regex = Regex("\\[GMAIL_CREATE_LABEL:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.createLabel(parsed)
                jsonResult(result, successText = "Label created", failurePrefix = "Failed to create label")
            }

            run(
                actionName = "GMAIL_UPDATE_LABEL",
                regex = Regex("\\[GMAIL_UPDATE_LABEL:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.updateLabel(parsed)
                jsonResult(result, successText = "Label updated", failurePrefix = "Failed to update label")
            }

            run(
                actionName = "GMAIL_DELETE_LABEL",
                regex = Regex("\\[GMAIL_DELETE_LABEL:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.deleteLabel(parsed.valueOf("labelId", "id").orEmpty())
                jsonResult(result, successText = "Label deleted", failurePrefix = "Failed to delete label")
            }

            run(
                actionName = "GMAIL_READ_ATTACHMENT",
                regex = Regex("\\[GMAIL_READ_ATTACHMENT:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.readAttachment(
                    parsed.valueOf("messageId").orEmpty(),
                    parsed.valueOf("attachmentId", "id").orEmpty()
                )
                if (isSuccess(result)) {
                    val attachment = result.optJSONObject("attachment")
                    CommandResult(
                        "SUCCESS",
                        "Attachment loaded: id=${attachment?.optString("attachmentId").orEmpty()} size=${attachment?.optInt("size") ?: 0}"
                    )
                } else {
                    jsonResult(result, failurePrefix = "Failed to read attachment")
                }
            }

            run(
                actionName = "GMAIL_LIST_HISTORY",
                regex = Regex("\\[GMAIL_LIST_HISTORY(?:(.*))?\\]", RegexOption.IGNORE_CASE),
                optionalPayload = true
            ) { parsed ->
                val result = GoogleGmailAgentTool.listEmailHistory(parsed.withHistoryDefaults(senderPhone, senderName))
                syncHistory(result)
                if (isSuccess(result)) {
                    CommandResult("SUCCESS", "Tracking history:\n${formatHistory(result.optJSONArray("history"))}")
                } else {
                    jsonResult(result, failurePrefix = "Failed to list tracking history")
                }
            }

            run(
                actionName = "GMAIL_GET_HISTORY",
                regex = Regex("\\[GMAIL_GET_HISTORY:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            ) { parsed ->
                val result = GoogleGmailAgentTool.getEmailHistory(parsed.valueOf("trackingId", "messageId", "id").orEmpty())
                syncHistory(result)
                if (isSuccess(result)) {
                    CommandResult("SUCCESS", "Tracking record:\n${formatHistoryItem(result.optJSONObject("history"))}")
                } else {
                    jsonResult(result, failurePrefix = "Failed to get tracking history")
                }
            }

            modifiedResponse = modifiedResponse.replace(Regex("\\n{3,}"), "\n\n").trim()

            HandlerResult(
                success = true,
                modifiedResponse = modifiedResponse,
                metadata = mapOf(
                    "tool_actions" to toolActions,
                    "tool_action_count" to toolActions.size
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("GmailEventHandler", "Failed to process gmail command: ${e.message}", e)
            HandlerResult(success = false)
        }
    }

    private suspend fun syncHistory(result: JSONObject) {
        if (isSuccess(result)) {
            trackingSheetManager.initializeSheetSystem()
            trackingSheetManager.syncHistoryPayload(result)
        }
    }

    private suspend fun applyPattern(
        input: String,
        regex: Regex,
        actionName: String,
        toolActions: MutableList<String>,
        optionalPayload: Boolean = false,
        block: suspend (Map<String, String>) -> CommandResult
    ): String {
        var updated = input
        regex.findAll(updated).toList().forEach { match ->
            val rawPayload =
                if (optionalPayload) {
                    match.groupValues.getOrNull(1)?.trim()?.removePrefix(":")?.trim().orEmpty()
                } else {
                    match.groupValues.getOrNull(1).orEmpty()
                }
            val parsed = parsePayload(rawPayload)
            val result = block(parsed)
            toolActions += "$actionName:${result.status}"
            updated = updated.replace(match.value, result.replacement).trim()
        }
        return updated
    }

    private fun parsePayload(raw: String): Map<String, String> {
        val normalized = raw.replace("|", ";").replace("\n", ";")
        var parts = normalized.split(";").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size <= 1 && raw.contains(",")) {
            parts = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }

        val map = linkedMapOf<String, String>()
        parts.forEach { part ->
            val idx = part.indexOf('=')
            if (idx <= 0 || idx >= part.length - 1) return@forEach
            val key = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (key.isBlank() || value.isBlank()) return@forEach
            map[key] = value
        }
        return map
    }

    private fun Map<String, String>.valueOf(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.trim()
        }?.takeIf { it.isNotBlank() }
    }

    private fun Map<String, String>.withConversationDefaults(
        senderPhone: String,
        senderName: String
    ): Map<String, String> {
        val enriched = toMutableMap()
        if (valueOf("conversationPhone", "senderPhone").isNullOrBlank() && senderPhone.isNotBlank()) {
            enriched["conversationPhone"] = senderPhone
        }
        if (valueOf("conversationName", "senderName").isNullOrBlank() && senderName.isNotBlank()) {
            enriched["conversationName"] = senderName
        }
        if (valueOf("recipientPhone", "phone", "contactPhone").isNullOrBlank() && senderPhone.isNotBlank()) {
            enriched["recipientPhone"] = senderPhone
        }
        if (valueOf("recipientName", "name", "contactName").isNullOrBlank() && senderName.isNotBlank()) {
            enriched["recipientName"] = senderName
        }
        return enriched
    }

    private fun Map<String, String>.withHistoryDefaults(
        senderPhone: String,
        senderName: String
    ): Map<String, String> {
        val hasExplicitFilter =
            valueOf("phone", "recipientPhone", "conversationPhone", "name", "recipientName", "conversationName", "email", "recipientEmail", "trackingId", "messageId", "id") != null
        if (hasExplicitFilter) return this

        val enriched = toMutableMap()
        if (senderPhone.isNotBlank()) enriched["phone"] = senderPhone
        if (senderName.isNotBlank()) enriched["name"] = senderName
        return enriched
    }

    private fun isSuccess(result: JSONObject): Boolean {
        return result.optString("status") == "success"
    }

    private fun jsonResult(
        result: JSONObject,
        successText: String = "",
        failurePrefix: String
    ): CommandResult {
        return if (isSuccess(result)) {
            CommandResult("SUCCESS", successText.ifBlank { "Success" })
        } else {
            CommandResult("FAILED", "$failurePrefix: ${result.optString("message")}")
        }
    }

    private fun formatMessages(messages: JSONArray?): String {
        if (messages == null || messages.length() == 0) return "No emails found."
        return buildString {
            for (i in 0 until messages.length()) {
                val message = messages.optJSONObject(i) ?: continue
                append("- ${message.optString("id")} | ${message.optString("threadId")}").append('\n')
            }
        }.trim()
    }

    private fun formatMessage(message: JSONObject?): String {
        if (message == null || message.length() == 0) return "No email found."
        return buildString {
            append("- ID: ${message.optString("id")}")
            append("\n- Thread: ${message.optString("threadId")}")
            val snippet = message.optString("snippet")
            if (snippet.isNotBlank()) append("\n- Snippet: $snippet")
        }
    }

    private fun formatThreads(threads: JSONArray?): String {
        if (threads == null || threads.length() == 0) return "No threads found."
        return buildString {
            for (i in 0 until threads.length()) {
                val thread = threads.optJSONObject(i) ?: continue
                append("- ${thread.optString("id")}").append('\n')
            }
        }.trim()
    }

    private fun formatThread(thread: JSONObject?): String {
        if (thread == null || thread.length() == 0) return "No thread found."
        val count = thread.optJSONArray("messages")?.length() ?: 0
        return "- ID: ${thread.optString("id")}\n- Messages: $count"
    }

    private fun formatDrafts(drafts: JSONArray?): String {
        if (drafts == null || drafts.length() == 0) return "No drafts found."
        return buildString {
            for (i in 0 until drafts.length()) {
                val draft = drafts.optJSONObject(i) ?: continue
                append("- ${draft.optString("id")}").append('\n')
            }
        }.trim()
    }

    private fun formatDraft(draft: JSONObject?): String {
        if (draft == null || draft.length() == 0) return "No draft found."
        val message = draft.optJSONObject("message")
        return buildString {
            append("- Draft ID: ${draft.optString("id")}")
            if (message != null) {
                append("\n- Thread: ${message.optString("threadId")}")
                val snippet = message.optString("snippet")
                if (snippet.isNotBlank()) append("\n- Snippet: $snippet")
            }
        }
    }

    private fun formatLabels(labels: JSONArray?): String {
        if (labels == null || labels.length() == 0) return "No labels found."
        return buildString {
            for (i in 0 until labels.length()) {
                val label = labels.optJSONObject(i) ?: continue
                append("- ${label.optString("name")} (${label.optString("id")})").append('\n')
            }
        }.trim()
    }

    private fun formatHistory(history: JSONArray?): String {
        if (history == null || history.length() == 0) return "No tracking records found."
        return buildString {
            for (i in 0 until history.length()) {
                append(formatHistoryItem(history.optJSONObject(i))).append('\n')
            }
        }.trim()
    }

    private fun formatHistoryItem(item: JSONObject?): String {
        if (item == null || item.length() == 0) return "No tracking record found."
        val subject = item.optString("subject").ifBlank { "(no subject)" }
        val recipient = item.optString("recipientEmail").ifBlank { item.optString("recipientName").ifBlank { "unknown recipient" } }
        val status = item.optString("status").ifBlank { "UNKNOWN" }
        val opens = item.optInt("openCount")
        return "- ${item.optString("trackingId")} | $recipient | $subject | $status | opens=$opens"
    }

    private data class CommandResult(
        val status: String,
        val replacement: String
    )
}

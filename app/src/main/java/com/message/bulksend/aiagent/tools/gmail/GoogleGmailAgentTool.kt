package com.message.bulksend.aiagent.tools.gmail

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GoogleGmailAgentTool {
    const val WORKER_URL = "https://google-gmail-worker.aawuazer.workers.dev"

    private fun getUserId(): String = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private fun baseError(message: String): JSONObject {
        return JSONObject().put("status", "error").put("message", message)
    }

    private fun validateRequestContext(): JSONObject? {
        if (WORKER_URL.isBlank() || WORKER_URL.contains("YOUR_ACCOUNT")) {
            return baseError("Worker URL not configured")
        }
        if (getUserId().isBlank()) {
            return baseError("User not logged in")
        }
        return null
    }

    private suspend fun postJson(endpoint: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            try {
                validateRequestContext()?.let { return@withContext it }

                val connection = URL("$WORKER_URL$endpoint").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(body.toString())
                writer.flush()
                writer.close()

                val success = connection.responseCode in 200..299
                val stream = if (success) connection.inputStream else connection.errorStream
                val raw =
                    if (stream != null) {
                        val reader = BufferedReader(InputStreamReader(stream))
                        val text = reader.readText()
                        reader.close()
                        text
                    } else {
                        ""
                    }

                if (!success) {
                    return@withContext baseError(raw.ifBlank { "HTTP ${connection.responseCode}" })
                }

                val json = if (raw.isNotBlank()) JSONObject(raw) else JSONObject()
                json.put("status", "success")
                json
            } catch (e: Exception) {
                baseError(e.message ?: "Unknown error")
            }
        }

    private fun valueOf(params: Map<String, String>, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            params.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.trim()
        }?.takeIf { it.isNotBlank() }
    }

    private fun boolValue(params: Map<String, String>, vararg keys: String): Boolean? {
        val raw = valueOf(params, *keys) ?: return null
        return when (raw.lowercase()) {
            "true", "1", "yes", "y", "on" -> true
            "false", "0", "no", "n", "off" -> false
            else -> null
        }
    }

    private fun intValue(params: Map<String, String>, vararg keys: String): Int? {
        return valueOf(params, *keys)?.toIntOrNull()
    }

    private fun putIfNotBlank(body: JSONObject, key: String, value: String?) {
        if (!value.isNullOrBlank()) {
            body.put(key, value)
        }
    }

    private fun putIfPresent(body: JSONObject, key: String, value: Boolean?) {
        if (value != null) {
            body.put(key, value)
        }
    }

    private fun putEmailContext(body: JSONObject, params: Map<String, String>) {
        putIfNotBlank(body, "recipientName", valueOf(params, "recipientName", "name", "contactName"))
        putIfNotBlank(body, "recipientPhone", valueOf(params, "recipientPhone", "phone", "contactPhone"))
        putIfNotBlank(body, "conversationName", valueOf(params, "conversationName", "senderName"))
        putIfNotBlank(body, "conversationPhone", valueOf(params, "conversationPhone", "senderPhone"))
        putIfNotBlank(body, "replyTo", valueOf(params, "replyTo"))
        putIfPresent(body, "disableTracking", boolValue(params, "disableTracking", "trackingOff"))
    }

    suspend fun listEmails(params: Map<String, String>): JSONObject {
        val body =
            JSONObject().apply {
                put("userId", getUserId())
                intValue(params, "maxResults", "limit")?.let { put("maxResults", it) }
                putIfNotBlank(this, "q", valueOf(params, "q", "query", "search"))
                putIfNotBlank(this, "pageToken", valueOf(params, "pageToken"))
                putIfNotBlank(this, "labelIds", valueOf(params, "labelIds", "labels"))
                putIfPresent(this, "includeSpamTrash", boolValue(params, "includeSpamTrash"))
            }
        return postJson("/gmail/list", body)
    }

    suspend fun readEmail(messageId: String, format: String? = null): JSONObject {
        if (messageId.isBlank()) return baseError("Message ID is required")
        val body =
            JSONObject().apply {
                put("userId", getUserId())
                put("messageId", messageId)
                putIfNotBlank(this, "format", format)
            }
        return postJson("/gmail/read", body)
    }

    suspend fun sendEmail(params: Map<String, String>): JSONObject {
        val to = valueOf(params, "to", "recipient") ?: return baseError("Recipient (to) is required")
        val subject = valueOf(params, "subject", "title") ?: return baseError("Subject is required")
        val bodyText = valueOf(params, "body", "text", "content")
        val html = valueOf(params, "html")
        if (bodyText.isNullOrBlank() && html.isNullOrBlank()) {
            return baseError("Body is required")
        }

        val body =
            JSONObject().apply {
                put("userId", getUserId())
                put("to", to)
                put("subject", subject)
                bodyText?.let { put("text", it) }
                html?.let { put("html", it) } ?: put("html", bodyText?.replace("\n", "<br>").orEmpty())
                putIfNotBlank(this, "cc", valueOf(params, "cc"))
                putIfNotBlank(this, "bcc", valueOf(params, "bcc"))
                putEmailContext(this, params)
            }
        return postJson("/gmail/send", body)
    }

    suspend fun replyEmail(params: Map<String, String>): JSONObject {
        val to = valueOf(params, "to", "recipient") ?: return baseError("Recipient (to) is required")
        val subject = valueOf(params, "subject", "title") ?: return baseError("Subject is required")
        val bodyText = valueOf(params, "body", "text", "content")
        val html = valueOf(params, "html")
        if (bodyText.isNullOrBlank() && html.isNullOrBlank()) {
            return baseError("Body is required")
        }

        val threadId = valueOf(params, "threadId") ?: return baseError("threadId is required")
        val messageIdRef = valueOf(params, "messageIdRef", "inReplyTo") ?: return baseError("messageIdRef is required")

        val body =
            JSONObject().apply {
                put("userId", getUserId())
                put("to", to)
                put("subject", subject)
                bodyText?.let { put("text", it) }
                html?.let { put("html", it) } ?: put("html", bodyText?.replace("\n", "<br>").orEmpty())
                put("threadId", threadId)
                put("messageIdRef", messageIdRef)
                putIfNotBlank(this, "cc", valueOf(params, "cc"))
                putIfNotBlank(this, "bcc", valueOf(params, "bcc"))
                putEmailContext(this, params)
            }
        return postJson("/gmail/reply", body)
    }

    suspend fun trashEmail(messageId: String): JSONObject = postMessageId("/gmail/trash", messageId)

    suspend fun untrashEmail(messageId: String): JSONObject = postMessageId("/gmail/untrash", messageId)

    suspend fun deleteEmail(messageId: String): JSONObject = postMessageId("/gmail/delete", messageId)

    suspend fun modifyEmail(params: Map<String, String>): JSONObject {
        val messageId = valueOf(params, "messageId", "id") ?: return baseError("Message ID is required")
        val body =
            JSONObject().apply {
                put("userId", getUserId())
                put("messageId", messageId)
                putIfNotBlank(this, "addLabelIds", valueOf(params, "addLabelIds", "addLabels"))
                putIfNotBlank(this, "removeLabelIds", valueOf(params, "removeLabelIds", "removeLabels"))
            }
        return postJson("/gmail/modify", body)
    }

    suspend fun listThreads(params: Map<String, String>): JSONObject {
        val body =
            JSONObject().apply {
                put("userId", getUserId())
                intValue(params, "maxResults", "limit")?.let { put("maxResults", it) }
                putIfNotBlank(this, "q", valueOf(params, "q", "query", "search"))
                putIfNotBlank(this, "pageToken", valueOf(params, "pageToken"))
                putIfNotBlank(this, "labelIds", valueOf(params, "labelIds", "labels"))
                putIfPresent(this, "includeSpamTrash", boolValue(params, "includeSpamTrash"))
            }
        return postJson("/gmail/threads/list", body)
    }

    suspend fun readThread(threadId: String, format: String? = null): JSONObject {
        if (threadId.isBlank()) return baseError("Thread ID is required")
        val body =
            JSONObject().apply {
                put("userId", getUserId())
                put("threadId", threadId)
                putIfNotBlank(this, "format", format)
            }
        return postJson("/gmail/threads/read", body)
    }

    suspend fun modifyThread(params: Map<String, String>): JSONObject {
        val threadId = valueOf(params, "threadId", "id") ?: return baseError("Thread ID is required")
        val body =
            JSONObject().apply {
                put("userId", getUserId())
                put("threadId", threadId)
                putIfNotBlank(this, "addLabelIds", valueOf(params, "addLabelIds", "addLabels"))
                putIfNotBlank(this, "removeLabelIds", valueOf(params, "removeLabelIds", "removeLabels"))
            }
        return postJson("/gmail/threads/modify", body)
    }

    suspend fun trashThread(threadId: String): JSONObject = postThreadId("/gmail/threads/trash", threadId)

    suspend fun untrashThread(threadId: String): JSONObject = postThreadId("/gmail/threads/untrash", threadId)

    suspend fun deleteThread(threadId: String): JSONObject = postThreadId("/gmail/threads/delete", threadId)

    suspend fun listDrafts(params: Map<String, String>): JSONObject {
        val body =
            JSONObject().apply {
                put("userId", getUserId())
                intValue(params, "maxResults", "limit")?.let { put("maxResults", it) }
                putIfNotBlank(this, "pageToken", valueOf(params, "pageToken"))
                putIfPresent(this, "includeDraftBodies", boolValue(params, "includeDraftBodies", "withBodies"))
            }
        return postJson("/gmail/drafts/list", body)
    }

    suspend fun readDraft(draftId: String, format: String? = null): JSONObject {
        if (draftId.isBlank()) return baseError("Draft ID is required")
        val body =
            JSONObject().apply {
                put("userId", getUserId())
                put("draftId", draftId)
                putIfNotBlank(this, "format", format)
            }
        return postJson("/gmail/drafts/read", body)
    }

    suspend fun createDraft(params: Map<String, String>): JSONObject = postDraft("/gmail/drafts/create", params)

    suspend fun updateDraft(params: Map<String, String>): JSONObject = postDraft("/gmail/drafts/update", params, true)

    suspend fun sendDraft(draftId: String): JSONObject {
        if (draftId.isBlank()) return baseError("Draft ID is required")
        return postJson("/gmail/drafts/send", JSONObject().put("userId", getUserId()).put("draftId", draftId))
    }

    suspend fun deleteDraft(draftId: String): JSONObject {
        if (draftId.isBlank()) return baseError("Draft ID is required")
        return postJson("/gmail/drafts/delete", JSONObject().put("userId", getUserId()).put("draftId", draftId))
    }

    suspend fun listLabels(): JSONObject = postJson("/gmail/labels/list", JSONObject().put("userId", getUserId()))

    suspend fun createLabel(params: Map<String, String>): JSONObject {
        val name = valueOf(params, "name", "title") ?: return baseError("Label name is required")
        val labelDetails =
            JSONObject().apply {
                put("name", name)
                putIfNotBlank(this, "labelListVisibility", valueOf(params, "labelListVisibility"))
                putIfNotBlank(this, "messageListVisibility", valueOf(params, "messageListVisibility"))
                putIfNotBlank(this, "backgroundColor", valueOf(params, "backgroundColor"))
                putIfNotBlank(this, "textColor", valueOf(params, "textColor"))
            }
        return postJson(
            "/gmail/labels/create",
            JSONObject().put("userId", getUserId()).put("labelDetails", labelDetails)
        )
    }

    suspend fun updateLabel(params: Map<String, String>): JSONObject {
        val labelId = valueOf(params, "labelId", "id") ?: return baseError("Label ID is required")
        val labelDetails =
            JSONObject().apply {
                putIfNotBlank(this, "name", valueOf(params, "name", "title"))
                putIfNotBlank(this, "labelListVisibility", valueOf(params, "labelListVisibility"))
                putIfNotBlank(this, "messageListVisibility", valueOf(params, "messageListVisibility"))
                putIfNotBlank(this, "backgroundColor", valueOf(params, "backgroundColor"))
                putIfNotBlank(this, "textColor", valueOf(params, "textColor"))
            }
        if (labelDetails.length() == 0) return baseError("No label update fields provided")
        return postJson(
            "/gmail/labels/update",
            JSONObject().put("userId", getUserId()).put("labelId", labelId).put("labelDetails", labelDetails)
        )
    }

    suspend fun deleteLabel(labelId: String): JSONObject {
        if (labelId.isBlank()) return baseError("Label ID is required")
        return postJson("/gmail/labels/delete", JSONObject().put("userId", getUserId()).put("labelId", labelId))
    }

    suspend fun readAttachment(messageId: String, attachmentId: String): JSONObject {
        if (messageId.isBlank() || attachmentId.isBlank()) {
            return baseError("messageId and attachmentId are required")
        }
        return postJson(
            "/gmail/attachments/read",
            JSONObject()
                .put("userId", getUserId())
                .put("messageId", messageId)
                .put("attachmentId", attachmentId)
        )
    }

    suspend fun listEmailHistory(params: Map<String, String>): JSONObject {
        val body =
            JSONObject().apply {
                put("userId", getUserId())
                intValue(params, "maxResults", "limit")?.let { put("maxResults", it) }
                putIfNotBlank(this, "pageToken", valueOf(params, "pageToken"))
                putIfNotBlank(this, "phone", valueOf(params, "phone", "recipientPhone", "conversationPhone"))
                putIfNotBlank(this, "name", valueOf(params, "name", "recipientName", "conversationName"))
                putIfNotBlank(this, "email", valueOf(params, "email", "recipientEmail"))
                putIfNotBlank(this, "trackingId", valueOf(params, "trackingId", "messageId", "id"))
                putIfNotBlank(this, "status", valueOf(params, "status"))
                putIfPresent(this, "opened", boolValue(params, "opened"))
            }
        return postJson("/gmail/history/list", body)
    }

    suspend fun getEmailHistory(trackingId: String): JSONObject {
        if (trackingId.isBlank()) return baseError("Tracking ID is required")
        return postJson(
            "/gmail/history/get",
            JSONObject().put("userId", getUserId()).put("trackingId", trackingId)
        )
    }

    private suspend fun postMessageId(endpoint: String, messageId: String): JSONObject {
        if (messageId.isBlank()) return baseError("Message ID is required")
        return postJson(endpoint, JSONObject().put("userId", getUserId()).put("messageId", messageId))
    }

    private suspend fun postThreadId(endpoint: String, threadId: String): JSONObject {
        if (threadId.isBlank()) return baseError("Thread ID is required")
        return postJson(endpoint, JSONObject().put("userId", getUserId()).put("threadId", threadId))
    }

    private suspend fun postDraft(
        endpoint: String,
        params: Map<String, String>,
        requireDraftId: Boolean = false
    ): JSONObject {
        val to = valueOf(params, "to", "recipient") ?: return baseError("Recipient (to) is required")
        val subject = valueOf(params, "subject", "title") ?: return baseError("Subject is required")
        val bodyText = valueOf(params, "body", "text", "content")
        val html = valueOf(params, "html")
        if (bodyText.isNullOrBlank() && html.isNullOrBlank()) {
            return baseError("Body is required")
        }

        val body =
            JSONObject().apply {
                put("userId", getUserId())
                if (requireDraftId) {
                    val draftId = valueOf(params, "draftId", "id") ?: return baseError("Draft ID is required")
                    put("draftId", draftId)
                }
                put("to", to)
                put("subject", subject)
                bodyText?.let { put("text", it) }
                html?.let { put("html", it) } ?: put("html", bodyText?.replace("\n", "<br>").orEmpty())
                putIfNotBlank(this, "cc", valueOf(params, "cc"))
                putIfNotBlank(this, "bcc", valueOf(params, "bcc"))
                putIfNotBlank(this, "threadId", valueOf(params, "threadId"))
                putIfNotBlank(this, "messageIdRef", valueOf(params, "messageIdRef", "inReplyTo"))
                putEmailContext(this, params)
            }
        return postJson(endpoint, body)
    }
}

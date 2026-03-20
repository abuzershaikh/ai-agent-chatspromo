package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import com.message.bulksend.aiagent.tools.agentdocument.AgentDocumentAIIntegration

/**
 * Handler for detecting document send requests in AI responses
 * Detects [SEND_DOCUMENT: ID] and [SEND_DOCUMENT_BY_TAG: query] commands
 */
class DocumentDetectionHandler(
    private val documentIntegration: AgentDocumentAIIntegration
) : MessageHandler {

    override fun getPriority(): Int = 35 // Execute before Payment (40) and Catalogue (50)

    override suspend fun handle(
        context: Context,
        message: String,
        response: String,
        senderPhone: String,
        senderName: String
    ): HandlerResult {
        return try {
            android.util.Log.d("DocumentHandler", "Checking response for document command")
            android.util.Log.d("DocumentHandler", "Response: $response")

            val commandPattern = Regex("\\[SEND_DOCUMENT:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            val matches =
                commandPattern.findAll(response)
                    .toList()
                    .distinctBy { it.groupValues[1].trim().lowercase() }

            val tagCommandPattern = Regex("\\[SEND_DOCUMENT_BY_TAG:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            val tagMatches =
                tagCommandPattern.findAll(response)
                    .toList()
                    .distinctBy { it.groupValues[1].trim().lowercase() }

            if (matches.isEmpty() && tagMatches.isEmpty()) {
                android.util.Log.d("DocumentHandler", "No document command found in response")
                return HandlerResult(success = true)
            }

            var modifiedResponse = response
            val toolActions = mutableListOf<String>()

            matches.forEach { match ->
                val documentId = match.groupValues[1].trim()
                android.util.Log.d("DocumentHandler", "Document command detected for ID: $documentId")

                val result = documentIntegration.sendDocumentToUser(senderPhone, senderName, documentId)
                android.util.Log.d(
                    "DocumentHandler",
                    "Send result: success=${result.success}, message=${result.message}"
                )

                toolActions +=
                    "SEND_DOCUMENT:$documentId:${if (result.success) "SUCCESS" else "FAILED"}"

                modifiedResponse = modifiedResponse.replace(match.value, "").trim()

                if (result.success) {
                    android.util.Log.d("DocumentHandler", "Document sent successfully")
                } else {
                    android.util.Log.e("DocumentHandler", "Failed to send document: ${result.message}")
                }
            }

            tagMatches.forEach { match ->
                val query = match.groupValues[1].trim()
                android.util.Log.d("DocumentHandler", "Document-by-tag command detected: $query")

                val result = documentIntegration.sendDocumentByTagMatch(senderPhone, senderName, query)
                android.util.Log.d(
                    "DocumentHandler",
                    "Tag match send result: success=${result.success}, message=${result.message}, documentId=${result.documentId}"
                )

                val actionKey = if (result.documentId.isNotBlank()) result.documentId else query
                toolActions +=
                    "SEND_DOCUMENT_BY_TAG:$actionKey:${if (result.success) "SUCCESS" else "FAILED"}"

                modifiedResponse = modifiedResponse.replace(match.value, "").trim()
            }

            modifiedResponse = modifiedResponse.replace(Regex("\\n{3,}"), "\n\n").trim()

            HandlerResult(
                success = true,
                modifiedResponse = modifiedResponse,
                metadata =
                    mapOf(
                        "tool_actions" to toolActions,
                        "tool_action_count" to toolActions.size
                    )
            )
        } catch (e: Exception) {
            android.util.Log.e("DocumentHandler", "Error: ${e.message}", e)
            HandlerResult(success = false)
        }
    }
}

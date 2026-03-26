package com.message.bulksend.autorespond.aireply.tooling

import android.content.Context
import com.message.bulksend.aiagent.tools.agentdocument.AgentDocumentAIIntegration
import com.message.bulksend.aiagent.tools.agentform.AgentFormAIIntegration
import com.message.bulksend.aiagent.tools.calendar.GoogleCalendarAgentTool
import com.message.bulksend.aiagent.tools.ecommerce.PaymentMethodAIIntegration
import com.message.bulksend.aiagent.tools.ecommerce.RazorPaymentManager
import com.message.bulksend.aiagent.tools.paymentverification.PaymentVerificationManager
import com.message.bulksend.aiagent.tools.gmail.GmailTrackingTableSheetManager
import com.message.bulksend.aiagent.tools.gmail.GoogleGmailAgentTool
import com.message.bulksend.autorespond.ai.customtask.engine.AgentTaskEngine
import com.message.bulksend.autorespond.ai.customtask.manager.AgentTaskManager
import com.message.bulksend.autorespond.ai.needdiscovery.NeedDiscoveryManager
import com.message.bulksend.autorespond.ai.product.AIAgentProductManager
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import org.json.JSONObject
import java.util.Locale

class NativeSkillExecutor(
    context: Context,
    private val settings: AIAgentSettingsManager,
    private val needDiscoveryManager: NeedDiscoveryManager
) {
    private val appContext = context.applicationContext
    private val documentIntegration = AgentDocumentAIIntegration(appContext)
    private val paymentIntegration = PaymentMethodAIIntegration(appContext)
    private val razorPaymentManager = RazorPaymentManager(appContext)
    private val paymentVerificationManager = PaymentVerificationManager.getInstance(appContext)
    private val agentFormIntegration = AgentFormAIIntegration(appContext)
    private val productManager = AIAgentProductManager(appContext)
    private val sheetWriteExecutor = NativeSheetWriteExecutor(appContext)
    private val taskEngine = AgentTaskEngine(AgentTaskManager(appContext))
    private val gmailTrackingSheetManager = GmailTrackingTableSheetManager(appContext)

    suspend fun execute(
        skill: SkillDefinition,
        args: JSONObject,
        senderPhone: String,
        senderName: String,
        userMessage: String
    ): SkillExecutionResult {
        return when (skill.functionName.lowercase(Locale.ROOT)) {
            "send_document" -> executeSendDocument(args, senderPhone, senderName)
            "send_document_by_tag" -> executeSendDocumentByTag(args, senderPhone, senderName)
            "send_payment" -> executeSendPayment(args, senderPhone, senderName)
            "generate_payment_link" -> executeGeneratePaymentLink(args, senderPhone, senderName)
            "payment_verification_status" -> executePaymentVerificationStatus(args, senderPhone)
            "send_agent_form" -> executeSendAgentForm(args, senderPhone)
            "check_agent_form_response" -> executeCheckAgentFormResponse(senderPhone)
            "send_catalogue" -> executeSendCatalogue(args, senderPhone, senderName)
            "write_sheet" -> sheetWriteExecutor.write(args, senderPhone, senderName, userMessage)
            "calendar_action" -> executeCalendarAction(args)
            "gmail_action" -> executeGmailAction(args, senderPhone, senderName)
            "task_step_complete" -> executeTaskStepComplete(args, senderPhone, senderName)
            "customer_need_probe" -> executeNeedProbe(args, senderPhone, userMessage)
            else -> SkillExecutionResult.ignored("Unsupported skill: ${skill.functionName}")
        }
    }

    private suspend fun executeSendDocument(
        args: JSONObject,
        senderPhone: String,
        senderName: String
    ): SkillExecutionResult {
        val documentId = args.optString("document_id").ifBlank { args.optString("id") }.trim()
        if (documentId.isBlank()) {
            return SkillExecutionResult.ignored("document_id is required")
        }

        val result = documentIntegration.sendDocumentToUser(senderPhone, senderName, documentId)
        val action = "SEND_DOCUMENT:$documentId:${if (result.success) "SUCCESS" else "FAILED"}"
        return if (result.success) {
            SkillExecutionResult.success(
                message = result.message,
                payload = JSONObject()
                    .put("document_id", result.documentId)
                    .put("success", true),
                toolActions = listOf(action)
            )
        } else {
            SkillExecutionResult.error(
                message = result.message,
                retryable = true,
                payload = JSONObject().put("document_id", documentId),
                usedFallback = false
            ).copy(toolActions = listOf(action))
        }
    }

    private suspend fun executeSendDocumentByTag(
        args: JSONObject,
        senderPhone: String,
        senderName: String
    ): SkillExecutionResult {
        val query = args.optString("query").ifBlank { args.optString("tag") }.trim()
        if (query.isBlank()) {
            return SkillExecutionResult.ignored("query is required")
        }

        val result = documentIntegration.sendDocumentByTagMatch(senderPhone, senderName, query)
        val actionKey = result.documentId.ifBlank { query }
        val action = "SEND_DOCUMENT_BY_TAG:$actionKey:${if (result.success) "SUCCESS" else "FAILED"}"
        return if (result.success) {
            SkillExecutionResult.success(
                message = result.message,
                payload = JSONObject()
                    .put("query", query)
                    .put("document_id", result.documentId),
                toolActions = listOf(action)
            )
        } else {
            SkillExecutionResult.error(
                message = result.message,
                retryable = true,
                payload = JSONObject().put("query", query)
            ).copy(toolActions = listOf(action))
        }
    }

    private suspend fun executeSendPayment(
        args: JSONObject,
        senderPhone: String,
        senderName: String
    ): SkillExecutionResult {
        val methodId = args.optString("method_id").ifBlank { args.optString("id") }.trim()
        if (methodId.isBlank()) {
            return SkillExecutionResult.ignored("method_id is required")
        }

        val result = paymentIntegration.sendPaymentMethod(senderPhone, senderName, methodId)
        val action = "SEND_PAYMENT:$methodId:${if (result.success) "SUCCESS" else "FAILED"}"
        return if (result.success) {
            SkillExecutionResult.success(
                message = result.message,
                payload = JSONObject()
                    .put("method_id", methodId)
                    .put("is_media", result.isMedia)
                    .put("details", result.details ?: ""),
                toolActions = listOf(action)
            )
        } else {
            SkillExecutionResult.error(
                message = result.message,
                retryable = true,
                payload = JSONObject().put("method_id", methodId)
            ).copy(toolActions = listOf(action))
        }
    }

    private suspend fun executeGeneratePaymentLink(
        args: JSONObject,
        senderPhone: String,
        senderName: String
    ): SkillExecutionResult {
        val amount = parseDoubleArg(args, "amount")
        val description = args.optString("description").trim()

        if (amount == null || amount <= 0.0 || description.isBlank()) {
            return SkillExecutionResult.ignored("amount and description are required")
        }

        val link =
            razorPaymentManager.createPaymentLink(
                amount = amount,
                description = description,
                customerName = senderName,
                customerPhone = senderPhone
            )

        val action = "GENERATE-PAYMENT-LINK:$amount:$description:${if (link != null) "SUCCESS" else "FAILED"}"
        return if (!link.isNullOrBlank()) {
            SkillExecutionResult.success(
                message = "Payment link generated",
                payload = JSONObject()
                    .put("amount", amount)
                    .put("description", description)
                    .put("payment_link", link),
                toolActions = listOf(action)
            )
        } else {
            SkillExecutionResult.error(
                message = "Could not generate payment link",
                retryable = true,
                payload = JSONObject().put("amount", amount).put("description", description)
            ).copy(toolActions = listOf(action))
        }
    }


    private suspend fun executePaymentVerificationStatus(
        args: JSONObject,
        senderPhone: String
    ): SkillExecutionResult {
        val customerPhone = args.optString("customer_phone").ifBlank { senderPhone }.trim()
        if (customerPhone.isBlank()) {
            return SkillExecutionResult.ignored("customer_phone is required")
        }

        val explicitLinkId = args.optString("plink_id").trim()
        val latestLink =
            if (explicitLinkId.isBlank()) {
                razorPaymentManager.getLatestPaymentLinkForUser(customerPhone)
            } else {
                null
            }
        val linkId = explicitLinkId.ifBlank { latestLink?.id.orEmpty() }
        val firestoreStatus = latestLink?.status.orEmpty().trim().lowercase(Locale.ROOT)
        val apiStatus =
            if (linkId.isNotBlank()) {
                razorPaymentManager.verifyPaymentStatusFromApi(linkId)
            } else {
                "unknown"
            }

        val latestVerification = paymentVerificationManager.getLatestVerificationForCustomer(customerPhone)
        val verificationStatus = latestVerification?.status.orEmpty().trim()
        val normalizedStatus = normalizePaymentStatus(apiStatus, firestoreStatus, verificationStatus)

        val payload = JSONObject()
            .put("customer_phone", customerPhone)
            .put("plink_id", linkId)
            .put("api_status", apiStatus)
            .put("firestore_status", firestoreStatus)
            .put("verification_status", verificationStatus)
            .put("resolved_status", normalizedStatus)

        val action = "PAYMENT_VERIFICATION_STATUS:$normalizedStatus"
        return if (normalizedStatus == "unknown") {
            SkillExecutionResult.error(
                message = "Payment status could not be resolved",
                retryable = true,
                payload = payload
            ).copy(toolActions = listOf(action))
        } else {
            SkillExecutionResult.success(
                message = "Payment status: $normalizedStatus",
                payload = payload,
                toolActions = listOf(action)
            )
        }
    }

    private suspend fun executeSendAgentForm(
        args: JSONObject,
        senderPhone: String
    ): SkillExecutionResult {
        val templateKey = args.optString("template_key").ifBlank { args.optString("template") }.trim()
        if (templateKey.isBlank()) {
            return SkillExecutionResult.ignored("template_key is required")
        }

        val result = agentFormIntegration.createFormLinkForRecipient(templateKey, senderPhone)
        val action = "SEND_AGENT_FORM:$templateKey:${if (result.success) "SUCCESS" else "FAILED"}"

        return if (result.success) {
            SkillExecutionResult.success(
                message = result.message,
                payload = JSONObject()
                    .put("template_key", templateKey)
                    .put("form_id", result.formId)
                    .put("campaign", result.campaign)
                    .put("url", result.url)
                    .put("requires_contact_verification", result.requiresContactVerification),
                toolActions = listOf(action)
            )
        } else {
            SkillExecutionResult.error(
                message = result.message,
                retryable = true,
                payload = JSONObject().put("template_key", templateKey)
            ).copy(toolActions = listOf(action))
        }
    }

    private suspend fun executeCheckAgentFormResponse(
        senderPhone: String
    ): SkillExecutionResult {
        if (senderPhone.isBlank()) {
            return SkillExecutionResult.ignored("senderPhone is required")
        }

        val statusText = agentFormIntegration.buildLatestResponseMessage(senderPhone)
        return SkillExecutionResult.success(
            message = statusText,
            payload = JSONObject().put("status_text", statusText),
            toolActions = listOf("CHECK_AGENT_FORM_RESPONSE:SUCCESS")
        )
    }

    private suspend fun executeSendCatalogue(
        args: JSONObject,
        senderPhone: String,
        senderName: String
    ): SkillExecutionResult {
        val productIdArg = parseLongArg(args, "product_id")
        val productName =
            args.optString("product_name")
                .ifBlank { args.optString("query") }
                .trim()

        val selectedProduct =
            when {
                productIdArg != null && productIdArg > 0L -> {
                    productManager.getAllProducts().firstOrNull { it.id == productIdArg }
                }
                productName.isNotBlank() -> {
                    productManager.getProductByName(productName)
                        ?: productManager.searchProducts(productName).firstOrNull()
                }
                else -> null
            }

        val targetProductId = selectedProduct?.id ?: productIdArg
        if (targetProductId == null || targetProductId <= 0L) {
            return SkillExecutionResult.ignored("product_id or product_name is required")
        }

        val success =
            productManager.sendProductCatalogue(
                phoneNumber = senderPhone,
                userName = senderName,
                productId = targetProductId
            )
        val action =
            "CATALOGUE_SEND:${targetProductId}:${if (success) "SUCCESS" else "FAILED"}"

        return if (success) {
            SkillExecutionResult.success(
                message = "Catalogue sent",
                payload = JSONObject()
                    .put("product_id", targetProductId)
                    .put("product_name", selectedProduct?.name ?: productName),
                toolActions = listOf(action)
            )
        } else {
            SkillExecutionResult.error(
                message = "Failed to send catalogue",
                retryable = true,
                payload = JSONObject().put("product_id", targetProductId)
            ).copy(toolActions = listOf(action))
        }
    }
    private suspend fun executeTaskStepComplete(
        args: JSONObject,
        senderPhone: String,
        senderName: String
    ): SkillExecutionResult {
        if (!settings.customTemplateTaskModeEnabled) {
            return SkillExecutionResult.ignored("Task mode disabled")
        }

        val step =
            when {
                args.has("step") -> args.optInt("step", -1)
                args.has("step_number") -> args.optInt("step_number", -1)
                else -> -1
            }

        if (step <= 0) {
            return SkillExecutionResult.ignored("step must be > 0")
        }

        val phoneKey = senderPhone.ifBlank { senderName.ifBlank { "unknown_user" } }
        val result = taskEngine.completeStep(phoneKey, step)
        val action =
            if (result.isWorkflowCompleted) {
                "TASK_STEP_COMPLETE:$step:WORKFLOW_COMPLETED"
            } else {
                "TASK_STEP_COMPLETE:$step:NEXT_${result.movedToStep ?: "NA"}"
            }

        return SkillExecutionResult.success(
            message = "Task step updated",
            payload = JSONObject()
                .put("step", step)
                .put("workflow_completed", result.isWorkflowCompleted)
                .put("moved_to_step", result.movedToStep ?: JSONObject.NULL),
            toolActions = listOf(action)
        )
    }

    private suspend fun executeNeedProbe(
        args: JSONObject,
        senderPhone: String,
        userMessage: String
    ): SkillExecutionResult {
        val latestMessage = args.optString("latest_message").ifBlank { userMessage }
        val probe = needDiscoveryManager.probe(senderPhone, latestMessage)
        return SkillExecutionResult.success(
            message = "Need probe completed",
            payload = JSONObject()
                .put("closureReady", probe.closureReady)
                .put("knownValues", JSONObject(probe.knownValues))
                .put("missingRequiredFieldIds", org.json.JSONArray(probe.missingRequiredFieldIds))
                .put("suggestedQuestion", probe.suggestedQuestion)
        )
    }

    private suspend fun executeCalendarAction(args: JSONObject): SkillExecutionResult {
        val action = normalizeAction(
            raw = args.optString("action").ifBlank { args.optString("command") },
            prefix = "CALENDAR_"
        )
        if (action.isBlank()) {
            return SkillExecutionResult.ignored("calendar action is required")
        }

        val params = mergeActionParams(args)
        val result =
            when (action) {
                "CALENDAR_CREATE_EVENT" -> GoogleCalendarAgentTool.createEvent(params)
                "CALENDAR_LIST_EVENTS" -> GoogleCalendarAgentTool.listEvents(params)
                "CALENDAR_UPDATE_EVENT" -> {
                    val id = valueOf(params, "id", "eventId")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("eventId is required")
                    GoogleCalendarAgentTool.updateEvent(id, params)
                }
                "CALENDAR_CREATE_MEET_LINK" -> {
                    val id = valueOf(params, "id", "eventId")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("eventId is required")
                    GoogleCalendarAgentTool.createMeetLink(id, params)
                }
                "CALENDAR_DELETE_EVENT" -> {
                    val id = valueOf(params, "id", "eventId")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("eventId is required")
                    GoogleCalendarAgentTool.deleteEvent(id, params)
                }
                "CALENDAR_CREATE_TASK" -> GoogleCalendarAgentTool.createTask(params)
                "CALENDAR_LIST_TASKS" -> GoogleCalendarAgentTool.listTasks(params)
                "CALENDAR_UPDATE_TASK" -> {
                    val id = valueOf(params, "id", "taskId")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("taskId is required")
                    GoogleCalendarAgentTool.updateTask(id, params)
                }
                "CALENDAR_MOVE_TASK" -> {
                    val id = valueOf(params, "id", "taskId")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("taskId is required")
                    GoogleCalendarAgentTool.moveTask(id, params)
                }
                "CALENDAR_CLEAR_COMPLETED_TASKS" -> GoogleCalendarAgentTool.clearCompletedTasks(params)
                "CALENDAR_DELETE_TASK" -> {
                    val id = valueOf(params, "id", "taskId")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("taskId is required")
                    GoogleCalendarAgentTool.deleteTask(id, params)
                }
                "CALENDAR_LIST_TASKLISTS" -> GoogleCalendarAgentTool.listTaskLists(params)
                "CALENDAR_GET_TASKLIST" -> {
                    val id = valueOf(params, "id", "tasklistId", "taskListId")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("taskListId is required")
                    GoogleCalendarAgentTool.getTaskList(id)
                }
                "CALENDAR_CREATE_TASKLIST" -> GoogleCalendarAgentTool.createTaskList(params)
                "CALENDAR_UPDATE_TASKLIST" -> {
                    val id = valueOf(params, "id", "tasklistId", "taskListId")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("taskListId is required")
                    GoogleCalendarAgentTool.updateTaskList(id, params)
                }
                "CALENDAR_DELETE_TASKLIST" -> {
                    val id = valueOf(params, "id", "tasklistId", "taskListId")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("taskListId is required")
                    GoogleCalendarAgentTool.deleteTaskList(id)
                }
                "CALENDAR_LIST_CALENDARS" -> GoogleCalendarAgentTool.listCalendars(params)
                "CALENDAR_GET_CALENDAR" -> {
                    val id = valueOf(params, "calendarId", "id", "calendar")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("calendarId is required")
                    GoogleCalendarAgentTool.getCalendar(id)
                }
                "CALENDAR_UPDATE_CALENDAR" -> {
                    val id = valueOf(params, "calendarId", "id", "calendar")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("calendarId is required")
                    GoogleCalendarAgentTool.updateCalendar(id, params)
                }
                else -> return SkillExecutionResult.ignored("Unsupported calendar action: $action")
            }

        return fromJsonResult(action, result)
    }

    private suspend fun executeGmailAction(
        args: JSONObject,
        senderPhone: String,
        senderName: String
    ): SkillExecutionResult {
        val action = normalizeAction(
            raw = args.optString("action").ifBlank { args.optString("command") },
            prefix = "GMAIL_"
        )
        if (action.isBlank()) {
            return SkillExecutionResult.ignored("gmail action is required")
        }

        val baseParams = mergeActionParams(args)
        val convoParams = withConversationDefaults(baseParams, senderPhone, senderName)
        val result =
            when (action) {
                "GMAIL_LIST_EMAILS" -> GoogleGmailAgentTool.listEmails(baseParams)
                "GMAIL_READ_EMAIL" -> {
                    val id = valueOf(baseParams, "messageId", "id")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("messageId is required")
                    GoogleGmailAgentTool.readEmail(id, valueOf(baseParams, "format"))
                }
                "GMAIL_SEND_EMAIL" -> GoogleGmailAgentTool.sendEmail(convoParams)
                "GMAIL_REPLY_EMAIL" -> GoogleGmailAgentTool.replyEmail(convoParams)
                "GMAIL_TRASH_EMAIL" -> {
                    val id = valueOf(baseParams, "messageId", "id")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("messageId is required")
                    GoogleGmailAgentTool.trashEmail(id)
                }
                "GMAIL_UNTRASH_EMAIL" -> {
                    val id = valueOf(baseParams, "messageId", "id")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("messageId is required")
                    GoogleGmailAgentTool.untrashEmail(id)
                }
                "GMAIL_DELETE_EMAIL" -> {
                    val id = valueOf(baseParams, "messageId", "id")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("messageId is required")
                    GoogleGmailAgentTool.deleteEmail(id)
                }
                "GMAIL_MODIFY_EMAIL" -> GoogleGmailAgentTool.modifyEmail(baseParams)
                "GMAIL_LIST_THREADS" -> GoogleGmailAgentTool.listThreads(baseParams)
                "GMAIL_READ_THREAD" -> {
                    val id = valueOf(baseParams, "threadId", "id")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("threadId is required")
                    GoogleGmailAgentTool.readThread(id, valueOf(baseParams, "format"))
                }
                "GMAIL_MODIFY_THREAD" -> GoogleGmailAgentTool.modifyThread(baseParams)
                "GMAIL_TRASH_THREAD" -> {
                    val id = valueOf(baseParams, "threadId", "id")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("threadId is required")
                    GoogleGmailAgentTool.trashThread(id)
                }
                "GMAIL_UNTRASH_THREAD" -> {
                    val id = valueOf(baseParams, "threadId", "id")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("threadId is required")
                    GoogleGmailAgentTool.untrashThread(id)
                }
                "GMAIL_DELETE_THREAD" -> {
                    val id = valueOf(baseParams, "threadId", "id")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("threadId is required")
                    GoogleGmailAgentTool.deleteThread(id)
                }
                "GMAIL_LIST_DRAFTS" -> GoogleGmailAgentTool.listDrafts(baseParams)
                "GMAIL_READ_DRAFT" -> {
                    val id = valueOf(baseParams, "draftId", "id")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("draftId is required")
                    GoogleGmailAgentTool.readDraft(id, valueOf(baseParams, "format"))
                }
                "GMAIL_CREATE_DRAFT" -> GoogleGmailAgentTool.createDraft(convoParams)
                "GMAIL_UPDATE_DRAFT" -> GoogleGmailAgentTool.updateDraft(convoParams)
                "GMAIL_SEND_DRAFT" -> {
                    val id = valueOf(baseParams, "draftId", "id")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("draftId is required")
                    GoogleGmailAgentTool.sendDraft(id)
                }
                "GMAIL_DELETE_DRAFT" -> {
                    val id = valueOf(baseParams, "draftId", "id")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("draftId is required")
                    GoogleGmailAgentTool.deleteDraft(id)
                }
                "GMAIL_LIST_LABELS" -> GoogleGmailAgentTool.listLabels()
                "GMAIL_CREATE_LABEL" -> GoogleGmailAgentTool.createLabel(baseParams)
                "GMAIL_UPDATE_LABEL" -> GoogleGmailAgentTool.updateLabel(baseParams)
                "GMAIL_DELETE_LABEL" -> {
                    val id = valueOf(baseParams, "labelId", "id")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("labelId is required")
                    GoogleGmailAgentTool.deleteLabel(id)
                }
                "GMAIL_READ_ATTACHMENT" -> {
                    val messageId = valueOf(baseParams, "messageId")
                    val attachmentId = valueOf(baseParams, "attachmentId", "id")
                    if (messageId.isNullOrBlank() || attachmentId.isNullOrBlank()) {
                        return SkillExecutionResult.ignored("messageId and attachmentId are required")
                    }
                    GoogleGmailAgentTool.readAttachment(messageId, attachmentId)
                }
                "GMAIL_LIST_HISTORY" -> GoogleGmailAgentTool.listEmailHistory(withHistoryDefaults(baseParams, senderPhone, senderName))
                "GMAIL_GET_HISTORY" -> {
                    val id = valueOf(baseParams, "trackingId", "messageId", "id")
                    if (id.isNullOrBlank()) return SkillExecutionResult.ignored("trackingId is required")
                    GoogleGmailAgentTool.getEmailHistory(id)
                }
                else -> return SkillExecutionResult.ignored("Unsupported gmail action: $action")
            }

        syncGmailHistory(result)
        return fromJsonResult(action, result)
    }

    private suspend fun syncGmailHistory(result: JSONObject) {
        runCatching {
            if (result.optString("status") == "success") {
                gmailTrackingSheetManager.initializeSheetSystem()
                gmailTrackingSheetManager.syncHistoryPayload(result)
            }
        }
    }

    private fun fromJsonResult(action: String, result: JSONObject): SkillExecutionResult {
        val success = result.optString("status").equals("success", ignoreCase = true)
        val message =
            result.optString("message").ifBlank {
                if (success) "$action executed" else "$action failed"
            }

        val toolAction = "$action:${if (success) "SUCCESS" else "FAILED"}"
        return if (success) {
            SkillExecutionResult.success(
                message = message,
                payload = result,
                toolActions = listOf(toolAction)
            )
        } else {
            SkillExecutionResult.error(
                message = message,
                retryable = true,
                payload = result
            ).copy(toolActions = listOf(toolAction))
        }
    }

    private fun normalizeAction(raw: String, prefix: String): String {
        val normalized =
            raw.trim()
                .uppercase(Locale.ROOT)
                .replace(Regex("[^A-Z0-9_]+"), "_")
                .trim('_')
        if (normalized.isBlank()) return ""
        return if (normalized.startsWith(prefix)) normalized else "$prefix$normalized"
    }

    private fun parseDoubleArg(args: JSONObject, key: String): Double? {
        if (!args.has(key)) return null
        val raw = args.opt(key) ?: return null
        return when (raw) {
            is Number -> raw.toDouble()
            else -> raw.toString().trim().toDoubleOrNull()
        }
    }

    private fun parseLongArg(args: JSONObject, key: String): Long? {
        if (!args.has(key)) return null
        val raw = args.opt(key) ?: return null
        return when (raw) {
            is Number -> raw.toLong()
            else -> raw.toString().trim().toLongOrNull()
        }
    }

    private fun normalizePaymentStatus(
        apiStatus: String,
        firestoreStatus: String,
        verificationStatus: String
    ): String {
        val normalizedApi = apiStatus.trim().lowercase(Locale.ROOT)
        val normalizedFs = firestoreStatus.trim().lowercase(Locale.ROOT)
        val normalizedVerification = verificationStatus.trim().lowercase(Locale.ROOT)

        val candidates = listOf(normalizedApi, normalizedFs, normalizedVerification)
        return when {
            candidates.any { it == "paid" || it == "approved" || it == "processed" || it == "success" } -> "paid"
            candidates.any { it == "failed" || it == "rejected" } -> "failed"
            candidates.any { it == "expired" || it == "cancelled" || it == "canceled" } -> "expired"
            candidates.any { it == "pending" || it == "created" || it == "issued" || it == "under_review" } -> "pending"
            else -> "unknown"
        }
    }
    private fun mergeActionParams(args: JSONObject): Map<String, String> {
        val out = linkedMapOf<String, String>()

        args.optJSONObject("params")?.let { paramsObj ->
            val keys = paramsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next().trim()
                if (key.isBlank()) continue
                val value = paramsObj.opt(key)
                if (value == null || value == JSONObject.NULL) continue
                val text = value.toString().trim()
                if (text.isNotBlank()) {
                    out[key] = text
                }
            }
        }

        val keys = args.keys()
        while (keys.hasNext()) {
            val key = keys.next().trim()
            if (key.isBlank() || key.equals("action", ignoreCase = true) || key.equals("command", ignoreCase = true) || key.equals("params", ignoreCase = true)) {
                continue
            }
            val value = args.opt(key)
            if (value == null || value == JSONObject.NULL) continue
            val text = value.toString().trim()
            if (text.isNotBlank()) {
                out.putIfAbsent(key, text)
            }
        }

        return out
    }

    private fun valueOf(params: Map<String, String>, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            params.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.trim()
        }?.takeIf { it.isNotBlank() }
    }

    private fun withConversationDefaults(
        params: Map<String, String>,
        senderPhone: String,
        senderName: String
    ): Map<String, String> {
        val enriched = params.toMutableMap()
        if (valueOf(params, "conversationPhone", "senderPhone").isNullOrBlank() && senderPhone.isNotBlank()) {
            enriched["conversationPhone"] = senderPhone
        }
        if (valueOf(params, "conversationName", "senderName").isNullOrBlank() && senderName.isNotBlank()) {
            enriched["conversationName"] = senderName
        }
        if (valueOf(params, "recipientPhone", "phone", "contactPhone").isNullOrBlank() && senderPhone.isNotBlank()) {
            enriched["recipientPhone"] = senderPhone
        }
        if (valueOf(params, "recipientName", "name", "contactName").isNullOrBlank() && senderName.isNotBlank()) {
            enriched["recipientName"] = senderName
        }
        return enriched
    }

    private fun withHistoryDefaults(
        params: Map<String, String>,
        senderPhone: String,
        senderName: String
    ): Map<String, String> {
        val hasExplicitFilter =
            valueOf(
                params,
                "phone",
                "recipientPhone",
                "conversationPhone",
                "name",
                "recipientName",
                "conversationName",
                "email",
                "recipientEmail",
                "trackingId",
                "messageId",
                "id"
            ) != null
        if (hasExplicitFilter) return params

        val enriched = params.toMutableMap()
        if (senderPhone.isNotBlank()) enriched["phone"] = senderPhone
        if (senderName.isNotBlank()) enriched["name"] = senderName
        return enriched
    }
}





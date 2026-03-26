package com.message.bulksend.autorespond.aireply

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import com.message.bulksend.reminders.GlobalReminderManager
import com.message.bulksend.reminders.ReminderScheduleGuard
import com.message.bulksend.autorespond.ai.intent.IntentDetector
import com.message.bulksend.autorespond.ai.profile.SmartProfileExtractor
import com.message.bulksend.autorespond.aireply.handlers.MessageHandler
import com.message.bulksend.autorespond.ai.autonomous.AutonomousGoalRuntime
import com.message.bulksend.autorespond.ai.autonomous.PaymentStatusEventWatcher
import com.message.bulksend.autorespond.ai.needdiscovery.NeedDiscoveryManager
import com.message.bulksend.autorespond.ai.context.ToolOutcomeLearningManager
import com.message.bulksend.autorespond.aireply.tooling.NativeToolSkillRegistry
import com.message.bulksend.autorespond.aireply.tooling.NativeSkillExecutor
import com.message.bulksend.autorespond.aireply.tooling.SkillExecutionResult

class AIService(private val context: Context) {
    private val configManager = AIConfigManager(context)
    private val businessDataManager = AIBusinessDataManager(context)
    private val aiAgentSettings = com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager(context)
    private val advancedSettings = com.message.bulksend.autorespond.ai.settings.AIAgentAdvancedSettings(context)
    private val tableSheetDb = com.message.bulksend.tablesheet.data.TableSheetDatabase.getDatabase(context)
    private val aiAgentRepo = com.message.bulksend.autorespond.ai.data.repo.AIAgentRepository(
        context, 
        com.message.bulksend.autorespond.database.MessageDatabase.getDatabase(context).productDao(),
        com.message.bulksend.tablesheet.data.repository.TableSheetRepository(
            tableSheetDb.tableDao(),
            tableSheetDb.columnDao(),
            tableSheetDb.rowDao(),
            tableSheetDb.cellDao(),
            tableSheetDb.folderDao(),
            tableSheetDb.formulaDependencyDao(),
            tableSheetDb.cellSearchIndexDao(),
            tableSheetDb.rowVersionDao(),
            tableSheetDb.sheetTransactionDao(),
            tableSheetDb.filterViewDao(),
            tableSheetDb.conditionalFormatRuleDao(),
            tableSheetDb
        )
    )
    private val aiAgentContextBuilder = com.message.bulksend.autorespond.ai.core.AIAgentContextBuilder(context, aiAgentRepo, aiAgentSettings)
    private val leadScorer = com.message.bulksend.autorespond.ai.scoring.AIAgentLeadScorer()
    private val documentManager = com.message.bulksend.autorespond.ai.document.AIAgentDocumentManager(context)
    private val productManager = com.message.bulksend.autorespond.ai.product.AIAgentProductManager(context)
    private val taskManager = com.message.bulksend.autorespond.ai.customtask.manager.AgentTaskManager(context)
    private val gmailTrackingTableSheetManager = com.message.bulksend.aiagent.tools.gmail.GmailTrackingTableSheetManager(context)
    
    // NEW: Processor Registry for template-specific logic
    private val processorRegistry = com.message.bulksend.autorespond.aireply.processors.ProcessorRegistry(context)
    private val agentFormIntegration = com.message.bulksend.aiagent.tools.agentform.AgentFormAIIntegration(context)
    private val reminderScheduleGuard = ReminderScheduleGuard(context)
    private val autonomousGoalRuntime = AutonomousGoalRuntime(context)
    private val needDiscoveryManager = NeedDiscoveryManager(context)
    private val nativeToolRegistry = NativeToolSkillRegistry(aiAgentSettings)
    private val nativeSkillExecutor = NativeSkillExecutor(context, aiAgentSettings, needDiscoveryManager)
    private val paymentStatusWatcher = PaymentStatusEventWatcher.getInstance(context)
    private val toolOutcomeLearningManager = ToolOutcomeLearningManager(context)
    
    // NEW: Message Handlers for cross-cutting concerns
    private val allMessageHandlers = listOf(
        com.message.bulksend.autorespond.aireply.handlers.PaymentVerificationStatusHandler(context), // Highest priority for screenshot verification states
        com.message.bulksend.autorespond.aireply.handlers.RazorpayStatusCheckHandler(context), // HIGHEST PRIORITY - Check payment status first
        com.message.bulksend.autorespond.aireply.handlers.createTaskToolAllowlistGuardHandler(context), // Enforce per-step allowed tools
        com.message.bulksend.autorespond.aireply.handlers.DocumentDetectionHandler(com.message.bulksend.aiagent.tools.agentdocument.AgentDocumentAIIntegration(context)),
        com.message.bulksend.autorespond.aireply.handlers.PaymentDetectionHandler(com.message.bulksend.aiagent.tools.ecommerce.PaymentMethodAIIntegration(context)),
        com.message.bulksend.autorespond.aireply.handlers.StructuredSheetCommandHandler(context),
        com.message.bulksend.autorespond.aireply.handlers.CustomSheetWriteHandler(context),
        com.message.bulksend.autorespond.aireply.handlers.createTaskStepCompletionHandler(context),
        com.message.bulksend.autorespond.aireply.handlers.AgentFormDetectionHandler(agentFormIntegration),
        com.message.bulksend.autorespond.aireply.handlers.AgentFormStatusAutomationHandler(agentFormIntegration),
        com.message.bulksend.autorespond.aireply.handlers.RazorpayLinkHandler(context),
        com.message.bulksend.autorespond.aireply.handlers.CatalogueDetectionHandler(aiAgentRepo, productManager),
        com.message.bulksend.autorespond.aireply.handlers.ConversationLoggingHandler(
            com.message.bulksend.autorespond.ai.history.AIAgentHistoryManager(context),
            advancedSettings
        ),
        com.message.bulksend.autorespond.aireply.handlers.LeadScoringHandler(aiAgentRepo, leadScorer),
        com.message.bulksend.autorespond.aireply.handlers.CalendarEventHandler(context),
        com.message.bulksend.autorespond.aireply.handlers.GmailEventHandler(context)
    ).sortedBy { it.getPriority() }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            gmailTrackingTableSheetManager.initializeSheetSystem()
            gmailTrackingTableSheetManager.startRealtimeSync()
            if (aiAgentSettings.customTemplateContinuousAutonomousEnabled) {
                autonomousGoalRuntime.scheduleHeartbeat()
            }
            paymentStatusWatcher.startIfEligible()
        }
    }
    
    // REMOVED: Moved to CatalogueDetectionHandler
    
    // REMOVED: Moved to ClinicProcessor
    
    /**
     * Get document manager for AI Agent
     * Provides full access to document sending capabilities
     */
    fun getDocumentManager(): com.message.bulksend.autorespond.ai.document.AIAgentDocumentManager {
        return documentManager
    }
    
    /**
     * Get product manager for AI Agent
     * Provides full access to product catalogue sending
     */
    fun getProductManager(): com.message.bulksend.autorespond.ai.product.AIAgentProductManager {
        return productManager
    }

    private fun getMessageHandlersForTemplate(
        senderPhone: String,
        senderName: String
    ): List<MessageHandler> {
        if (!aiAgentSettings.activeTemplate.equals("CUSTOM", ignoreCase = true)) {
            return allMessageHandlers
        }

        val baseHandlers = allMessageHandlers.filter { handler ->
            when (handler) {
                is com.message.bulksend.autorespond.aireply.handlers.DocumentDetectionHandler ->
                    aiAgentSettings.customTemplateEnableDocumentTool

                is com.message.bulksend.autorespond.aireply.handlers.PaymentDetectionHandler,
                is com.message.bulksend.autorespond.aireply.handlers.RazorpayLinkHandler,
                is com.message.bulksend.autorespond.aireply.handlers.RazorpayStatusCheckHandler ->
                    aiAgentSettings.customTemplateEnablePaymentTool

                is com.message.bulksend.autorespond.aireply.handlers.PaymentVerificationStatusHandler ->
                    aiAgentSettings.customTemplateEnablePaymentTool &&
                        aiAgentSettings.customTemplateEnablePaymentVerificationTool

                is com.message.bulksend.autorespond.aireply.handlers.AgentFormDetectionHandler,
                is com.message.bulksend.autorespond.aireply.handlers.AgentFormStatusAutomationHandler ->
                    aiAgentSettings.customTemplateEnableAgentFormTool

                is com.message.bulksend.autorespond.aireply.handlers.CustomSheetWriteHandler ->
                    aiAgentSettings.customTemplateEnableSheetWriteTool

                is com.message.bulksend.autorespond.aireply.handlers.StructuredSheetCommandHandler ->
                    aiAgentSettings.customTemplateEnableSheetReadTool || aiAgentSettings.customTemplateEnableSheetWriteTool
                    
                is com.message.bulksend.autorespond.aireply.handlers.CalendarEventHandler ->
                    aiAgentSettings.customTemplateEnableGoogleCalendarTool

                is com.message.bulksend.autorespond.aireply.handlers.GmailEventHandler ->
                    aiAgentSettings.customTemplateEnableGoogleGmailTool

                else -> true
            }
        }

        val stepAllowlist = resolveStepToolAllowlist(senderPhone, senderName) ?: return baseHandlers
        return baseHandlers.filter { handler ->
            when (handler) {
                is com.message.bulksend.autorespond.aireply.handlers.DocumentDetectionHandler ->
                    com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry.SEND_DOCUMENT in stepAllowlist

                is com.message.bulksend.autorespond.aireply.handlers.PaymentDetectionHandler ->
                    com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry.SEND_PAYMENT in stepAllowlist

                is com.message.bulksend.autorespond.aireply.handlers.RazorpayLinkHandler ->
                    com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry.GENERATE_PAYMENT_LINK in stepAllowlist

                is com.message.bulksend.autorespond.aireply.handlers.PaymentVerificationStatusHandler,
                is com.message.bulksend.autorespond.aireply.handlers.RazorpayStatusCheckHandler ->
                    com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry.PAYMENT_VERIFICATION_STATUS in stepAllowlist

                is com.message.bulksend.autorespond.aireply.handlers.CustomSheetWriteHandler ->
                    true

                is com.message.bulksend.autorespond.aireply.handlers.StructuredSheetCommandHandler ->
                    true

                is com.message.bulksend.autorespond.aireply.handlers.CatalogueDetectionHandler ->
                    com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry.CATALOGUE_SEND in stepAllowlist

                is com.message.bulksend.autorespond.aireply.handlers.AgentFormDetectionHandler,
                is com.message.bulksend.autorespond.aireply.handlers.AgentFormStatusAutomationHandler ->
                    com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry.SEND_AGENT_FORM in stepAllowlist ||
                        com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry.CHECK_AGENT_FORM_RESPONSE in stepAllowlist

                is com.message.bulksend.autorespond.aireply.handlers.CalendarEventHandler ->
                    com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry.GOOGLE_CALENDAR in stepAllowlist

                is com.message.bulksend.autorespond.aireply.handlers.GmailEventHandler ->
                    com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry.GOOGLE_GMAIL in stepAllowlist

                else -> true
            }
        }
    }

    private fun getFollowUpHandlersForTemplate(
        senderPhone: String,
        senderName: String
    ): List<MessageHandler> {
        return getMessageHandlersForTemplate(senderPhone, senderName).filter { handler ->
            handler is com.message.bulksend.autorespond.aireply.handlers.TaskToolAllowlistGuardHandler ||
            handler is com.message.bulksend.autorespond.aireply.handlers.DocumentDetectionHandler ||
                handler is com.message.bulksend.autorespond.aireply.handlers.PaymentDetectionHandler ||
                handler is com.message.bulksend.autorespond.aireply.handlers.StructuredSheetCommandHandler ||
                handler is com.message.bulksend.autorespond.aireply.handlers.CustomSheetWriteHandler ||
                handler is com.message.bulksend.autorespond.aireply.handlers.TaskStepCompletionHandler ||
                handler is com.message.bulksend.autorespond.aireply.handlers.AgentFormDetectionHandler ||
                handler is com.message.bulksend.autorespond.aireply.handlers.RazorpayLinkHandler ||
                handler is com.message.bulksend.autorespond.aireply.handlers.CatalogueDetectionHandler ||
                handler is com.message.bulksend.autorespond.aireply.handlers.CalendarEventHandler ||
                handler is com.message.bulksend.autorespond.aireply.handlers.GmailEventHandler
        }
    }

    private fun resolveStepToolAllowlist(
        senderPhone: String,
        senderName: String
    ): Set<String>? {
        if (!aiAgentSettings.activeTemplate.equals("CUSTOM", ignoreCase = true)) return null
        if (aiAgentSettings.customTemplatePromptMode != com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager.PROMPT_MODE_STEP_FLOW) {
            return null
        }
        if (!aiAgentSettings.customTemplateTaskModeEnabled) return null

        val phoneKey = senderPhone.ifBlank { senderName.ifBlank { "unknown_user" } }
        val task = taskManager.getCurrentTask(phoneKey)
        val selectedStepTools = com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry
            .normalizeToolIds(task?.allowedTools.orEmpty())
            .filter {
                com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry
                    .isEnabledForTemplate(it, aiAgentSettings)
            }
            .toMutableSet()

        // Sheet commands remain default capability when sheet read/write tools are enabled.
        if (aiAgentSettings.customTemplateEnableSheetWriteTool || aiAgentSettings.customTemplateEnableSheetReadTool) {
            selectedStepTools.add(
                com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry.WRITE_SHEET
            )
        }

        return selectedStepTools
    }

    private data class HandlerExecutionResult(
        val response: String,
        val shouldStopChain: Boolean,
        val toolActions: List<String>
    )

    private suspend fun executeProcessorAndHandlers(
        message: String,
        response: String,
        senderPhone: String,
        senderName: String,
        handlers: List<MessageHandler>
    ): HandlerExecutionResult {
        var currentResponse = response
        val toolActions = mutableListOf<String>()
        var shouldStopChain = false

        val processor = processorRegistry.getProcessor(aiAgentSettings.activeTemplate)
        android.util.Log.d("AIService", "Using processor: ${processor.getTemplateType()}")

        currentResponse =
            processor.processResponse(
                response = currentResponse,
                message = message,
                senderPhone = senderPhone,
                senderName = senderName
            )

        for (handler in handlers) {
            try {
                val result = handler.handle(context, message, currentResponse, senderPhone, senderName)

                if (result.modifiedResponse != null) {
                    currentResponse = result.modifiedResponse
                }

                toolActions += extractToolActions(result.metadata)

                if (result.shouldStopChain) {
                    android.util.Log.d("AIService", "Handler chain stopped by ${handler.javaClass.simpleName}")
                    shouldStopChain = true
                    break
                }
            } catch (e: Exception) {
                android.util.Log.e("AIService", "Handler ${handler.javaClass.simpleName} failed: ${e.message}")
            }
        }

        return HandlerExecutionResult(
            response = currentResponse,
            shouldStopChain = shouldStopChain,
            toolActions = toolActions.distinct()
        )
    }

    private fun extractToolActions(metadata: Map<String, Any>): List<String> {
        val rawActions = metadata["tool_actions"] as? Iterable<*> ?: return emptyList()
        return rawActions.mapNotNull { action ->
            action?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun summarizeInternalAction(action: String): String {
        val trimmed = action.trim()
        if (trimmed.isBlank()) return trimmed

        val stepCompletionMatch =
            Regex(
                "TASK_STEP_COMPLETE:(\\d+):(WORKFLOW_COMPLETED|NEXT_.+)",
                RegexOption.IGNORE_CASE
            ).find(trimmed)
        if (stepCompletionMatch != null) {
            val step = stepCompletionMatch.groupValues.getOrNull(1).orEmpty()
            val state = stepCompletionMatch.groupValues.getOrNull(2).orEmpty()
            return if (state.equals("WORKFLOW_COMPLETED", ignoreCase = true)) {
                "Internal update: step $step complete and flow reached final goal check."
            } else {
                "Internal update: step $step complete and flow advanced."
            }
        }

        return trimmed
    }

    private fun buildToolFollowUpPrompt(
        basePrompt: String,
        userMessage: String,
        currentResponse: String,
        latestActions: List<String>,
        allActions: List<String>,
        round: Int
    ): String {
        val userHasStepOrder =
            Regex(
                "(\\bstep\\b|\\d+\\.|\\bthen\\b|\\bnext\\b|\\bfirst\\b|\\bafter that\\b|\\bphir\\b|\\buske baad\\b)",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(userMessage)
        val primaryGoal = aiAgentSettings.customTemplateGoal.trim()

        return buildString {
            append(basePrompt)
            append("\n\n[AUTONOMOUS EXECUTION LOOP]\n")
            append("Round: $round\n")
            append("User Goal: $userMessage\n")
            if (primaryGoal.isNotBlank()) {
                append("Template Primary Goal: $primaryGoal\n")
            }
            if (userHasStepOrder) {
                append("User ne step order diya hai. Steps ko same order me follow karo.\n")
            }
            append("Latest internal actions:\n")
            latestActions.forEach { append("- ${summarizeInternalAction(it)}\n") }
            if (allActions.isNotEmpty()) {
                append("All internal actions so far (repeat mat karo unless user explicitly asks):\n")
                allActions.forEach { append("- ${summarizeInternalAction(it)}\n") }
            }
            append("Current drafted reply after execution:\n")
            append(currentResponse)
            append("\n\nContinue the conversation naturally.\n")
            append("Rules:\n")
            append("1. Internal step/tool state user ko mat dikhao (no 'step complete', no workflow labels, no raw tags).\n")
            append("2. Agar next tool action chahiye, proper tool command output karo.\n")
            append("3. Agar goal abhi complete nahi hua, ek focused natural follow-up question ya next action do.\n")
            append("4. Goal complete ho to concise, human-like closing reply do.\n")
            append("5. Already completed action ko bina reason repeat mat karo.\n")
        }
    }

    private fun shouldUseNativeToolCalling(
        provider: AIProvider,
        senderPhone: String
    ): Boolean {
        if (!aiAgentSettings.isAgentEnabled) return false
        if (!aiAgentSettings.activeTemplate.equals("CUSTOM", ignoreCase = true)) return false
        if (!aiAgentSettings.customTemplateNativeToolCallingEnabled) return false
        if (senderPhone.isBlank()) return false
        return provider == AIProvider.CHATGPT || provider == AIProvider.GEMINI
    }

    private suspend fun callProviderWithNativeTools(
        provider: AIProvider,
        config: AIConfig,
        prompt: String,
        senderPhone: String,
        senderName: String,
        userMessage: String
    ): String? {
        if (!shouldUseNativeToolCalling(provider, senderPhone)) return null

        val stepAllowlist = resolveStepToolAllowlist(senderPhone, senderName)
        return runCatching {
            when (provider) {
                AIProvider.CHATGPT ->
                    callChatGPTWithNativeTools(
                        config = config,
                        prompt = prompt,
                        senderPhone = senderPhone,
                        senderName = senderName,
                        userMessage = userMessage,
                        stepAllowlist = stepAllowlist
                    )

                AIProvider.GEMINI ->
                    callGeminiWithNativeTools(
                        config = config,
                        prompt = prompt,
                        senderPhone = senderPhone,
                        senderName = senderName,
                        userMessage = userMessage,
                        stepAllowlist = stepAllowlist
                    )

                AIProvider.CHATSPROMO -> null
            }
        }.getOrElse {
            android.util.Log.e("AIService", "Native tool-call failed, falling back: ${it.message}")
            null
        }
    }

    private suspend fun callChatGPTWithNativeTools(
        config: AIConfig,
        prompt: String,
        senderPhone: String,
        senderName: String,
        userMessage: String,
        stepAllowlist: Set<String>?
    ): String? {
        val messages = JSONArray().apply {
            put(
                JSONObject()
                    .put("role", "system")
                    .put("content", "You are a helpful assistant that uses tools when needed.")
            )
            put(
                JSONObject()
                    .put("role", "user")
                    .put("content", prompt)
            )
        }

        val tools = nativeToolRegistry.buildOpenAITools(stepAllowlist)
        if (tools.length() == 0) return null

        val maxTurns = aiAgentSettings.customTemplateAutonomousMaxRounds.coerceAtLeast(1)
        var lastText = ""

        repeat(maxTurns) { turn ->
            val requestBody = JSONObject().apply {
                put("model", config.model)
                put("messages", messages)
                put("tools", tools)
                put("tool_choice", "auto")
                put("max_tokens", if (config.maxTokens < 500) 1000 else config.maxTokens)
                put("temperature", config.temperature)
            }

            val response =
                postJson(
                    url = "https://api.openai.com/v1/chat/completions",
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Authorization" to "Bearer ${config.apiKey}"
                    ),
                    requestBody = requestBody
                )
            if (response.code != 200) {
                android.util.Log.e("AIService", "Native ChatGPT error ${response.code}: ${response.body}")
                return null
            }

            val json = JSONObject(response.body)
            val messageObj =
                json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?: return null

            val rawContent = messageObj.optString("content")

            val content = if (rawContent.equals("null", ignoreCase = true)) "" else rawContent.trim()
            if (content.isNotBlank()) lastText = content

            val toolCalls = messageObj.optJSONArray("tool_calls")
            val assistantMessage = JSONObject().put("role", "assistant")
            assistantMessage.put("content", if (content.isNotBlank()) content else JSONObject.NULL)
            if (toolCalls != null && toolCalls.length() > 0) {
                assistantMessage.put("tool_calls", toolCalls)
            }
            messages.put(assistantMessage)

            if (toolCalls == null || toolCalls.length() == 0) {
                return lastText.ifBlank { content }
            }

            for (index in 0 until toolCalls.length()) {
                val call = toolCalls.optJSONObject(index) ?: continue
                val callId = call.optString("id").ifBlank { "call_${turn}_$index" }
                val fn = call.optJSONObject("function") ?: continue
                val fnName = fn.optString("name").trim()
                if (fnName.isBlank()) continue
                val args = parseToolArguments(fn.optString("arguments", "{}"))
                val resultContent =
                    executeNativeToolCall(
                        functionName = fnName,
                        args = args,
                        senderPhone = senderPhone,
                        senderName = senderName,
                        userMessage = userMessage,
                        stepAllowlist = stepAllowlist
                    )

                messages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", callId)
                        .put("content", resultContent)
                )
            }
        }

        return lastText.takeIf { it.isNotBlank() }
    }

    private suspend fun callGeminiWithNativeTools(
        config: AIConfig,
        prompt: String,
        senderPhone: String,
        senderName: String,
        userMessage: String,
        stepAllowlist: Set<String>?
    ): String? {
        val declarations = nativeToolRegistry.buildGeminiFunctionDeclarations(stepAllowlist)
        if (declarations.length() == 0) return null

        val contents = JSONArray().apply {
            put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
            )
        }

        val tools = JSONArray().put(JSONObject().put("functionDeclarations", declarations))
        val maxTurns = aiAgentSettings.customTemplateAutonomousMaxRounds.coerceAtLeast(1)
        var lastText = ""

        repeat(maxTurns) {
            val requestBody = JSONObject().apply {
                put("contents", contents)
                put("tools", tools)
                put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", config.temperature)
                        .put("maxOutputTokens", if (config.maxTokens < 1000) 1000 else config.maxTokens)
                )
            }

            val response =
                postJson(
                    url = "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=${config.apiKey}",
                    headers = mapOf("Content-Type" to "application/json"),
                    requestBody = requestBody
                )
            if (response.code != 200) {
                android.util.Log.e("AIService", "Native Gemini error ${response.code}: ${response.body}")
                return null
            }

            val json = JSONObject(response.body)
            val candidateContent =
                json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?: return null

            val parts = candidateContent.optJSONArray("parts") ?: JSONArray()
            contents.put(
                JSONObject()
                    .put("role", "model")
                    .put("parts", parts)
            )

            val functionCalls = mutableListOf<JSONObject>()
            val textChunks = mutableListOf<String>()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val text = part.optString("text").trim()
                if (text.isNotBlank()) textChunks += text
                part.optJSONObject("functionCall")?.let { functionCalls += it }
            }
            if (textChunks.isNotEmpty()) {
                lastText = textChunks.joinToString("\n").trim()
            }

            if (functionCalls.isEmpty()) {
                return lastText.takeIf { it.isNotBlank() }
            }

            val responseParts = JSONArray()
            functionCalls.forEach { fnCall ->
                val fnName = fnCall.optString("name").trim()
                if (fnName.isBlank()) return@forEach
                val args = fnCall.optJSONObject("args") ?: JSONObject()
                val resultText =
                    executeNativeToolCall(
                        functionName = fnName,
                        args = args,
                        senderPhone = senderPhone,
                        senderName = senderName,
                        userMessage = userMessage,
                        stepAllowlist = stepAllowlist
                    )

                val resultObj =
                    runCatching { JSONObject(resultText) }
                        .getOrElse { JSONObject().put("result", resultText) }

                responseParts.put(
                    JSONObject().put(
                        "functionResponse",
                        JSONObject()
                            .put("name", fnName)
                            .put("response", resultObj)
                    )
                )
            }

            contents.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", responseParts)
            )
        }

        return lastText.takeIf { it.isNotBlank() }
    }

    private suspend fun executeNativeToolCall(
        functionName: String,
        args: JSONObject,
        senderPhone: String,
        senderName: String,
        userMessage: String,
        stepAllowlist: Set<String>?
    ): String {
        val skill = nativeToolRegistry.findEnabledSkill(functionName, stepAllowlist)
            ?: return trackNativeToolResult(functionName, SkillExecutionResult.ignored("Skill disabled or unknown: $functionName").toJsonString())

        val policy = skill.executionPolicy
        val maxAttempts = (policy.maxRetries.coerceAtLeast(0) + 1).coerceAtMost(4)
        var lastErrorMessage = ""
        var lastResult: SkillExecutionResult? = null

        for (attempt in 1..maxAttempts) {
            val startedAt = System.currentTimeMillis()
            val result =
                runCatching {
                    withTimeout(policy.timeoutMs.coerceAtLeast(1_000L)) {
                        nativeSkillExecutor.execute(
                            skill = skill,
                            args = args,
                            senderPhone = senderPhone,
                            senderName = senderName,
                            userMessage = userMessage
                        )
                    }
                }.getOrElse { error ->
                    val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                    lastErrorMessage = error.message ?: "tool execution failed"
                    SkillExecutionResult.error(
                        message = lastErrorMessage,
                        retryable = attempt < maxAttempts,
                        attempts = attempt,
                        executionMillis = elapsed
                    )
                }

            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            val enriched = result.copy(attempts = attempt, executionMillis = elapsed)
            lastResult = enriched

            if (enriched.status.equals("success", ignoreCase = true)) {
                return trackNativeToolResult(functionName, enriched.toJsonString())
            }

            val shouldRetry = enriched.status == "error" && enriched.retryable && attempt < maxAttempts
            if (shouldRetry) {
                continue
            }
            break
        }

        val fallbackCommand = nativeToolRegistry.buildCommandForCall(functionName, args, stepAllowlist)
        if (!fallbackCommand.isNullOrBlank()) {
            val fallbackResult = runCatching {
                val handlers = getFollowUpHandlersForTemplate(senderPhone, senderName)
                val execution =
                    executeProcessorAndHandlers(
                        message = userMessage,
                        response = fallbackCommand,
                        senderPhone = senderPhone,
                        senderName = senderName,
                        handlers = handlers
                    )

                SkillExecutionResult.success(
                    message = "Executed via legacy fallback",
                    payload = JSONObject()
                        .put("command", fallbackCommand)
                        .put("response", execution.response),
                    toolActions = execution.toolActions,
                    shouldStopChain = execution.shouldStopChain,
                    usedFallback = true,
                    attempts = maxAttempts
                ).toJsonString()
            }.getOrElse { error ->
                SkillExecutionResult.error(
                    message = error.message ?: "Legacy fallback execution failed",
                    retryable = false,
                    payload = JSONObject().put("command", fallbackCommand),
                    attempts = maxAttempts,
                    usedFallback = true
                ).toJsonString()
            }
            return trackNativeToolResult(functionName, fallbackResult)
        }

        val finalResult = (lastResult
            ?: SkillExecutionResult.error(
                message = lastErrorMessage.ifBlank { "Tool execution failed" },
                retryable = false,
                attempts = maxAttempts
            )
            ).toJsonString()
        return trackNativeToolResult(functionName, finalResult)
    }

    private fun parseToolArguments(raw: String): JSONObject {
        return runCatching {
            val normalized = raw.trim().ifBlank { "{}" }
            JSONObject(normalized)
        }.getOrElse {
            JSONObject()
        }
    }


    private data class NativeToolAuditEntry(
        val functionName: String,
        val rawResult: String
    )

    private data class ParsedToolExecutionResult(
        val signal: ToolExecutionSignal,
        val toolActions: List<String> = emptyList(),
        val shouldStopChain: Boolean = false
    )

    private data class NativeToolProviderResult(
        val text: String,
        val toolSignals: List<ToolExecutionSignal>,
        val toolActions: List<String> = emptyList(),
        val shouldStopChain: Boolean = false
    )

    private val nativeToolExecutionAudit = ThreadLocal<MutableList<NativeToolAuditEntry>?>()

    private fun trackNativeToolResult(functionName: String, rawResult: String): String {
        nativeToolExecutionAudit.get()?.add(
            NativeToolAuditEntry(functionName = functionName, rawResult = rawResult)
        )
        return rawResult
    }

    private fun parseToolExecutionActions(resultJson: JSONObject): List<String> {
        val array = resultJson.optJSONArray("tool_actions") ?: return emptyList()
        val actions = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotBlank()) actions += value
        }
        return actions.distinct()
    }

    private fun parseToolExecutionSignal(
        functionName: String,
        rawResult: String,
        source: String
    ): ParsedToolExecutionResult {
        val resultJson = runCatching { JSONObject(rawResult) }.getOrNull()
        if (resultJson == null) {
            return ParsedToolExecutionResult(
                signal = ToolExecutionSignal(
                    actionName = functionName,
                    status = "success",
                    message = rawResult.replace(Regex("\\s+"), " ").trim().take(140),
                    source = source
                ),
                toolActions = listOf(functionName)
            )
        }

        val status = resultJson.optString("status").ifBlank { "success" }
        val message = resultJson.optString("message").replace(Regex("\\s+"), " ").trim().take(160)
        val toolActions = parseToolExecutionActions(resultJson)
        return ParsedToolExecutionResult(
            signal = ToolExecutionSignal(
                actionName = functionName,
                status = status,
                message = message,
                source = source,
                usedFallback = resultJson.optBoolean("used_fallback", false),
                retryable = resultJson.optBoolean("retryable", false),
                attempts = resultJson.optInt("attempts", 1).coerceAtLeast(1)
            ),
            toolActions = if (toolActions.isNotEmpty()) toolActions else if (status.equals("success", true)) listOf(functionName) else emptyList(),
            shouldStopChain = resultJson.optBoolean("should_stop_chain", false)
        )
    }

    private suspend fun callProviderWithNativeToolsDetailed(
        provider: AIProvider,
        config: AIConfig,
        prompt: String,
        senderPhone: String,
        senderName: String,
        userMessage: String
    ): NativeToolProviderResult? {
        if (!shouldUseNativeToolCalling(provider, senderPhone)) return null
        val auditTrail = mutableListOf<NativeToolAuditEntry>()
        nativeToolExecutionAudit.set(auditTrail)
        return try {
            val text = callProviderWithNativeTools(provider, config, prompt, senderPhone, senderName, userMessage)
            if (text.isNullOrBlank() && auditTrail.isEmpty()) {
                null
            } else {
                val source = if (provider == AIProvider.CHATGPT) "native_openai" else "native_gemini"
                val parsed = auditTrail.map { parseToolExecutionSignal(it.functionName, it.rawResult, source) }
                NativeToolProviderResult(
                    text = text.orEmpty(),
                    toolSignals = parsed.map { it.signal },
                    toolActions = parsed.flatMap { it.toolActions }.distinct(),
                    shouldStopChain = parsed.any { it.shouldStopChain }
                )
            }
        } finally {
            nativeToolExecutionAudit.remove()
        }
    }
    private data class JsonHttpResponse(val code: Int, val body: String)

    private fun postJson(
        url: String,
        headers: Map<String, String>,
        requestBody: JSONObject
    ): JsonHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            connection.doOutput = true
            connection.connectTimeout = 60000
            connection.readTimeout = 90000
            connection.outputStream.use { stream ->
                stream.write(requestBody.toString().toByteArray())
            }

            val code = connection.responseCode
            val body =
                if (code in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText().orEmpty()
                }
            JsonHttpResponse(code = code, body = body)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun generateReplyResult(
        provider: AIProvider,
        message: String,
        senderName: String = "User",
        senderPhone: String = "",
        fromAutonomousRuntime: Boolean = false
    ): AIReplyResult = withContext(Dispatchers.IO) {
        android.util.Log.d("AIService", "ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â°ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¬ generateReply STARTED")
        android.util.Log.d("AIService", "Provider: ${provider.displayName}, Sender: $senderName, Phone: $senderPhone")
        android.util.Log.d("AIService", "Message: $message")
        val config = configManager.getConfig(provider)
        android.util.Log.d("AIService", "Config loaded, API Key present: ${config.apiKey.isNotEmpty()}")

        // Variable to store detected intent (accessible throughout function)
        var detectedIntent = "UNKNOWN"
        val collectedToolSignals = mutableListOf<ToolExecutionSignal>()
        val collectedToolActions = linkedSetOf<String>()
        var usedNativeToolCalling = false
        var nativeFallbackUsed = false
        var finalShouldStopChain = false

        if (config.apiKey.isEmpty()) {
            android.util.Log.e("AIService", "API Key is empty!")
            return@withContext AIReplyResult(text = "AI not configured. Please add API key.", detectedIntent = detectedIntent)
        }



        if (aiAgentSettings.isAgentEnabled && senderPhone.isNotBlank() && !fromAutonomousRuntime) {
            runCatching { toolOutcomeLearningManager.recordIncomingEngagement(senderPhone) }

            runCatching {

                needDiscoveryManager.updateFromIncomingMessage(senderPhone, message)

            }.onFailure {

                android.util.Log.e("AIService", "Need-discovery update failed: ${it.message}")

            }

        }
        
        android.util.Log.d("AIService", "ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â°ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â Starting profile enrichment and intent detection...")
        
        // Auto-enrich profile from sheet if first contact
        if (aiAgentSettings.isAgentEnabled && senderPhone.isNotBlank()) {
            var profile = aiAgentRepo.getUserProfile(senderPhone)
            
            // Clear invalid auto-extracted name (e.g., payment/order/greeting).
            if (profile != null && profile.name != null) {
                if (!SmartProfileExtractor.isLikelyPersonName(profile.name)) {
                    android.util.Log.d("AIService", "Auto-clearing invalid name: ${profile.name}")
                    val clearedProfile = profile.copy(name = null, updatedAt = System.currentTimeMillis())
                    aiAgentRepo.saveUserProfile(clearedProfile)
                    profile = clearedProfile
                }
            }
            
            if (profile == null) {
                // First contact - auto-enrich from sheet
                try {
                    aiAgentRepo.enrichProfileFromSheet(senderPhone)
                } catch (e: Exception) {
                    android.util.Log.e("AIService", "Auto-enrichment failed: ${e.message}")
                }
            }
            
            // NEW: Intent Detection
            if (advancedSettings.enableIntentDetection) {
                try {
                    val intentDetector = com.message.bulksend.autorespond.ai.intent.IntentDetector()
                    val intentResult = intentDetector.detectIntent(message)
                    val priority = intentDetector.getIntentPriority(intentResult.intent)
                    detectedIntent = intentResult.intent
                    
                    android.util.Log.d("AIService", "Intent detected: ${intentResult.intent} (${intentResult.confidence})")
                    
                    // Log to sheet in background
                    if (advancedSettings.autoSaveIntentHistory) {
                        // Launch in IO context (already in withContext(Dispatchers.IO))
                        try {
                            val historyManager = com.message.bulksend.autorespond.ai.history.AIAgentHistoryManager(context)
                            historyManager.logIntent(
                                phoneNumber = senderPhone,
                                userName = senderName,
                                message = message,
                                intentResult = intentResult,
                                priority = priority
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("AIService", "Failed to log intent: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AIService", "Intent detection failed: ${e.message}")
                }

        // NEW: Reminder Detection Hook
        if (detectedIntent == IntentDetector.INTENT_REMINDER) {
            if (reminderScheduleGuard.canScheduleFromIncomingChat(senderPhone)) {
                processReminderRequest(message, senderPhone, senderName)?.let {
                    return@withContext AIReplyResult(text = it, detectedIntent = detectedIntent)
                }
            } else {
                android.util.Log.d(
                    "AIService",
                    "Reminder intent detected but scheduling blocked for non-owner sender: $senderPhone"
                )
            }
        }
            }
        }
        
        // Use new AI Agent flow if enabled, otherwise fallback to legacy
        val prompt = if (aiAgentSettings.isAgentEnabled) {
            try {
                // NEW: Get processor for active template
                val processor = processorRegistry.getProcessor(aiAgentSettings.activeTemplate)
                val templateContext = processor.generateContext(senderPhone)
                
                // Build base context
                val baseContext = aiAgentContextBuilder.buildContextPrompt(senderName, senderPhone, message)
                
                // Combine base context with template-specific context
                if (templateContext.isNotBlank()) {
                    "$baseContext\n$templateContext"
                } else {
                    baseContext
                }
            } catch (e: Exception) {
                android.util.Log.e("AIService", "ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ AI Agent context building failed: ${e.message}", e)
                // Fallback to legacy prompt
                val base = businessDataManager.buildAIPrompt(provider, message, senderName)
                "$base\n\n${config.responseLength.instruction}"
            }
        } else {
            val base = businessDataManager.buildAIPrompt(provider, message, senderName)
            "$base\n\n${config.responseLength.instruction}"
        }
        
        // Analyze user info (Name/Intent) in background if enabled
        if (aiAgentSettings.isAgentEnabled && senderPhone.isNotBlank()) {
            // We launch this without waiting for the response to keep UI snappy
            // But since we are already in IO context, we can just call it. 
            // Better to use a separate scope or just do it after.
            // For now, let's do it *after* reply generation to avoid latency on reply.
        }
        val initialNativeResult =
            when (provider) {
                AIProvider.CHATGPT,
                AIProvider.GEMINI ->
                    callProviderWithNativeToolsDetailed(
                        provider = provider,
                        config = config,
                        prompt = prompt,
                        senderPhone = senderPhone,
                        senderName = senderName,
                        userMessage = message
                    )
                AIProvider.CHATSPROMO -> null
            }
        if (initialNativeResult != null) {
            usedNativeToolCalling = true
            collectedToolSignals += initialNativeResult.toolSignals
            collectedToolActions.addAll(initialNativeResult.toolActions)
            finalShouldStopChain = finalShouldStopChain || initialNativeResult.shouldStopChain
            nativeFallbackUsed = nativeFallbackUsed || initialNativeResult.toolSignals.any { it.usedFallback }
        }

        val rawResponse = when (provider) {
            AIProvider.CHATSPROMO -> {
                val subscriptionManager = ChatsPromoAISubscriptionManager(context)
                if (!subscriptionManager.canUseAI()) {
                    return@withContext AIReplyResult(
                        text = "SUBSCRIPTION_REQUIRED: Your trial has expired. Please subscribe to continue using ChatsPromo AI.",
                        detectedIntent = detectedIntent
                    )
                }
                val chatsPromoService = ChatsPromoAIService(context)
                chatsPromoService.generateReply(message, senderName)
            }
            AIProvider.CHATGPT ->
                initialNativeResult?.text?.takeIf { it.isNotBlank() } ?: callChatGPT(config, prompt)
            AIProvider.GEMINI ->
                initialNativeResult?.text?.takeIf { it.isNotBlank() } ?: callGemini(config, prompt)
        }

        var cleanedResponse = cleanMarkdownFormatting(rawResponse)
        
        // NEW ARCHITECTURE: Use processors and handlers
        if (aiAgentSettings.isAgentEnabled && senderPhone.isNotBlank()) {
            val maxExecutionRounds =
                if (aiAgentSettings.activeTemplate.equals("CUSTOM", ignoreCase = true)) {
                    aiAgentSettings.customTemplateAutonomousMaxRounds.coerceAtLeast(1)
                } else {
                    2
                }
            val executedActions = linkedSetOf<String>().apply { addAll(collectedToolActions) }
            var roundIndex = 0
            var nextRoundResponse = cleanedResponse

            while (roundIndex < maxExecutionRounds && !finalShouldStopChain) {
                val handlers =
                    if (roundIndex == 0) getMessageHandlersForTemplate(senderPhone, senderName)
                    else getFollowUpHandlersForTemplate(senderPhone, senderName)

                val executionResult =
                    executeProcessorAndHandlers(
                        message = message,
                        response = nextRoundResponse,
                        senderPhone = senderPhone,
                        senderName = senderName,
                        handlers = handlers
                    )
                cleanedResponse = executionResult.response

                val latestNewActions = executionResult.toolActions.filter { executedActions.add(it) }
                collectedToolActions.addAll(latestNewActions)
                collectedToolSignals += latestNewActions.map { action ->
                    ToolExecutionSignal(
                        actionName = action,
                        status = "success",
                        message = "Executed through handler chain",
                        source = "legacy_handler"
                    )
                }
                finalShouldStopChain = finalShouldStopChain || executionResult.shouldStopChain
                val canContinueIteratively =
                    provider != AIProvider.CHATSPROMO &&
                        roundIndex < maxExecutionRounds - 1 &&
                        !executionResult.shouldStopChain &&
                        !finalShouldStopChain &&
                        latestNewActions.isNotEmpty()

                if (!canContinueIteratively) {
                    break
                }

                val followUpPrompt =
                    buildToolFollowUpPrompt(
                        basePrompt = prompt,
                        userMessage = message,
                        currentResponse = cleanedResponse,
                        latestActions = latestNewActions,
                        allActions = executedActions.toList(),
                        round = roundIndex + 2
                    )
                val followUpNativeResult =
                    when (provider) {
                        AIProvider.CHATGPT,
                        AIProvider.GEMINI ->
                            callProviderWithNativeToolsDetailed(
                                provider = provider,
                                config = config,
                                prompt = followUpPrompt,
                                senderPhone = senderPhone,
                                senderName = senderName,
                                userMessage = message
                            )
                        AIProvider.CHATSPROMO -> null
                    }
                if (followUpNativeResult != null) {
                    usedNativeToolCalling = true
                    collectedToolSignals += followUpNativeResult.toolSignals
                    collectedToolActions.addAll(followUpNativeResult.toolActions)
                    executedActions.addAll(followUpNativeResult.toolActions)
                    finalShouldStopChain = finalShouldStopChain || followUpNativeResult.shouldStopChain
                    nativeFallbackUsed = nativeFallbackUsed || followUpNativeResult.toolSignals.any { it.usedFallback }
                }

                val followUpRawResponse =
                    when (provider) {
                        AIProvider.CHATGPT ->
                            followUpNativeResult?.text?.takeIf { it.isNotBlank() }
                                ?: callChatGPT(config, followUpPrompt)
                        AIProvider.GEMINI ->
                            followUpNativeResult?.text?.takeIf { it.isNotBlank() }
                                ?: callGemini(config, followUpPrompt)
                        AIProvider.CHATSPROMO -> cleanedResponse
                    }
                nextRoundResponse = cleanMarkdownFormatting(followUpRawResponse)
                cleanedResponse = nextRoundResponse
                roundIndex++
            }
        }

        // Post-processing: Log conversation and update lead score
        if (aiAgentSettings.isAgentEnabled && senderPhone.isNotBlank()) {
            try {
                if (aiAgentSettings.activeTemplate.equals("CUSTOM", ignoreCase = true)) {
                    try {
                        val customTemplateName =
                            aiAgentSettings.customTemplateName.trim().ifBlank { "Custom AI Template" }
                        val writeColumns =
                            aiAgentSettings.customTemplateWriteSheetColumns
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                        val linkedFolderName = aiAgentSettings.customTemplateSheetFolderName.trim()
                        val sheetManager = com.message.bulksend.autorespond.ai.customsheet.CustomTemplateSheetManager(context)
                        sheetManager.ensureTemplateSheetSystem(
                            templateName = customTemplateName,
                            folderNameOverride = linkedFolderName,
                            readSheetNameOverride = aiAgentSettings.customTemplateReadSheetName,
                            writeSheetNameOverride = aiAgentSettings.customTemplateWriteSheetName,
                            salesSheetNameOverride = aiAgentSettings.customTemplateSalesSheetName,
                            writeCustomColumns = writeColumns
                        )

                        if (aiAgentSettings.customTemplateEnableSheetWriteTool) {
                            sheetManager.logInteraction(
                                templateName = customTemplateName,
                                phoneNumber = senderPhone,
                                userName = senderName,
                                userMessage = message,
                                aiReply = cleanedResponse,
                                intent = detectedIntent,
                                folderNameOverride = linkedFolderName
                            )
                        }

                        sheetManager.logProductLead(
                            templateName = customTemplateName,
                            phoneNumber = senderPhone,
                            userName = senderName,
                            userMessage = message,
                            intent = detectedIntent,
                            folderNameOverride = linkedFolderName
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("AIService", "Custom sheet logging failed: ${e.message}")
                    }
                }

                // Log conversation to sheet
                if (advancedSettings.autoSaveIntentHistory) {
                    try {
                        val historyManager = com.message.bulksend.autorespond.ai.history.AIAgentHistoryManager(context)
                        historyManager.logConversation(
                            phoneNumber = senderPhone,
                            userName = senderName,
                            userMessage = message,
                            aiReply = cleanedResponse,
                            intent = detectedIntent
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("AIService", "Failed to log conversation: ${e.message}")
                    }
                }
                
                // E-commerce: Check for pending orders and ask for address
                if (advancedSettings.enableEcommerceMode && advancedSettings.autoAskAddress) {
                    try {
                        val orderManager = com.message.bulksend.autorespond.ai.ecommerce.OrderManager(context)
                        if (orderManager.hasPendingOrder(senderPhone)) {
                            // User has pending order - check if they provided address
                            if (message.length > 20 && !message.contains("buy", ignoreCase = true)) {
                                // Likely an address - complete the order
                                val orderDetails = orderManager.getPendingOrderDetails(senderPhone)
                                if (orderDetails != null) {
                                    orderManager.completeOrder(
                                        phoneNumber = senderPhone,
                                        address = message,
                                        notes = "Order completed via AI Agent"
                                    )
                                    android.util.Log.d("AIService", "Order completed for $senderPhone")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AIService", "E-commerce processing failed: ${e.message}")
                    }
                }
                
                // Extract user info
                analyzeAndStoreUserInfo(provider, config, message, cleanedResponse, senderPhone)
                
                // Update lead score
                updateLeadScore(senderPhone)
                
                // Sync profile to AI Agent History profile sheet
                if (advancedSettings.autoCreateProfileSheet) {
                    try {
                        val profile = aiAgentRepo.getUserProfile(senderPhone)
                        if (profile != null) {
                            val messageCount = aiAgentRepo.getConversationHistory(senderPhone, limit = 100).size
                            val historyMgr = com.message.bulksend.autorespond.ai.history.AIAgentHistoryManager(context)
                            historyMgr.updateProfileSheet(
                                phoneNumber = senderPhone,
                                name = profile.name,
                                email = profile.email,
                                city = profile.address,
                                leadTier = profile.leadTier,
                                leadScore = profile.leadScore,
                                totalMessages = messageCount,
                                lastIntent = profile.currentIntent
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AIService", "Failed to sync profile to sheet: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AIService", "Error in post-processing: ${e.message}")
            }
        }
        
        if (
            !fromAutonomousRuntime &&
                aiAgentSettings.isAgentEnabled &&
                senderPhone.isNotBlank() &&
                aiAgentSettings.activeTemplate.equals("CUSTOM", ignoreCase = true) &&
                aiAgentSettings.customTemplateContinuousAutonomousEnabled
        ) {
            runCatching {
                autonomousGoalRuntime.enqueueFromIncomingMessage(
                    senderPhone = senderPhone,
                    senderName = senderName,
                    lastUserMessage = message
                )
                paymentStatusWatcher.startIfEligible()
            }.onFailure {
                android.util.Log.e("AIService", "Autonomous enqueue failed: ${it.message}")
            }
        }
        if (aiAgentSettings.isAgentEnabled && senderPhone.isNotBlank()) {
            runCatching {
                toolOutcomeLearningManager.recordReplyResult(
                    senderPhone = senderPhone,
                    replyText = cleanedResponse,
                    toolActions = collectedToolActions.toList(),
                    toolSignals = collectedToolSignals.distinctBy { signal ->
                        "${signal.source}|${signal.actionName}|${signal.status}|${signal.message}"
                    }
                )
            }.onFailure {
                android.util.Log.e("AIService", "Tool outcome learning update failed: ${it.message}")
            }
        }

        return@withContext AIReplyResult(
            text = cleanedResponse,
            detectedIntent = detectedIntent,
            toolActions = collectedToolActions.toList(),
            toolSignals = collectedToolSignals.distinctBy { signal ->
                "${signal.source}|${signal.actionName}|${signal.status}|${signal.message}"
            },
            usedNativeToolCalling = usedNativeToolCalling,
            nativeFallbackUsed = nativeFallbackUsed,
            shouldStopChain = finalShouldStopChain
        )
    }
    
    suspend fun generateReply(
        provider: AIProvider,
        message: String,
        senderName: String = "User",
        senderPhone: String = "",
        fromAutonomousRuntime: Boolean = false
    ): String {
        return generateReplyResult(
            provider = provider,
            message = message,
            senderName = senderName,
            senderPhone = senderPhone,
            fromAutonomousRuntime = fromAutonomousRuntime
        ).text
    }
    private suspend fun updateLeadScore(senderPhone: String) {
        try {
            val profile = aiAgentRepo.getUserProfile(senderPhone) ?: return
            val interactions = aiAgentRepo.getConversationHistory(senderPhone, limit = 20)
            
            // Calculate new score
            val oldScore = profile.leadScore
            val newScore = leadScorer.calculateLeadScore(profile, interactions)
            val newTier = leadScorer.getLeadTier(newScore)
            
            // Update profile with new score and tier
            val updatedProfile = profile.copy(
                leadScore = newScore,
                leadTier = newTier,
                updatedAt = System.currentTimeMillis()
            )
            
            aiAgentRepo.saveUserProfile(updatedProfile)
            
            // Log score improvement
            val improvement = leadScorer.calculateScoreImprovement(oldScore, newScore)
            if (improvement != 0) {
                android.util.Log.d("AIService", "Lead score updated: $oldScore ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾Ãƒâ€šÃ‚Â¢ $newScore (${if (improvement > 0) "+" else ""}$improvement)")
            }
        } catch (e: Exception) {
            android.util.Log.e("AIService", "Lead score update failed: ${e.message}")
        }
    }
    
    private suspend fun analyzeAndStoreUserInfo(
        provider: AIProvider,
        config: AIConfig,
        userMessage: String,
        aiReply: String,
        senderPhone: String
    ) {
        // Lightweight extraction prompt
        val extractionPrompt = """
            Analyze the following conversation:
            User: $userMessage
            AI: $aiReply
            
            Extract the following in JSON format:
            {
                "user_name": "extracted name or null",
                "user_intent": "short summary of intent or null"
            }
            CRITICAL INSTRUCTION: 
            - Do NOT extract greetings (e.g., Hi, Hello, Hey, Hii, Hiii, Bhai, Sir) as names.
            - Do NOT extract questions (e.g., Who are you, What is your name) as names.
            - Only extract a name if the user explicitly states it or is referred to by name.
            - If no valid name is found, return null.
            Only return the JSON.
        """.trimIndent()
        
        try {
            // Use Gemini for extraction as it's fast (or same provider)
            // Using same provider to respect user choice/key
            val response = when (provider) {
                AIProvider.CHATGPT -> callChatGPT(config, extractionPrompt)
                AIProvider.GEMINI -> callGemini(config, extractionPrompt)
                else -> ""
            }
            
            // Parse JSON (Simple regex or JSON parsing)
            val jsonString = response.substringAfter("{").substringBeforeLast("}")
            val finalJson = "{$jsonString}"
            val obj = JSONObject(finalJson)
            
            val rawName = obj.optString("user_name").takeIf { it != "null" && it.isNotBlank() }
            
            val name = rawName?.trim()?.takeIf { SmartProfileExtractor.isLikelyPersonName(it) }
            val intent = obj.optString("user_intent").takeIf { it != "null" && it.isNotBlank() }
            
            if (name != null) {
                aiAgentRepo.updateUserName(senderPhone, name)
            }
            if (intent != null) {
                // aiAgentRepo.updateUserIntent(senderPhone, intent) // Need to add this method to repo
                // Let's add it seamlessly by getting profile and updating
                val profile = aiAgentRepo.getUserProfile(senderPhone) ?: com.message.bulksend.autorespond.ai.data.model.UserProfile(phoneNumber = senderPhone)
                aiAgentRepo.saveUserProfile(profile.copy(currentIntent = intent, updatedAt = System.currentTimeMillis()))
            }
            
        } catch (e: Exception) {
            android.util.Log.e("AIService", "Extraction failed: ${e.message}")
        }
    }
    
    /**
     * Remove markdown formatting and unnecessary meta text from AI response
     */
    private fun cleanMarkdownFormatting(text: String): String {
        var cleaned = text

        val toolCommandPattern = Regex("\\[(SEND_PAYMENT|SEND_DOCUMENT|SEND_DOCUMENT_BY_TAG|GENERATE-PAYMENT-LINK|WRITE_SHEET|SHEET_(SELECT|AGG|UPSERT|BULK_UPSERT|PIVOT)|TASK_STEP_COMPLETE|SEND_AGENT_FORM|CHECK_AGENT_FORM_RESPONSE|CALENDAR_[A-Z_]+|GMAIL_[A-Z_]+)\\b", RegexOption.IGNORE_CASE)
        val firstLine = cleaned.lineSequence().firstOrNull()?.trim().orEmpty()
        val firstLineHasToolCommand = toolCommandPattern.containsMatchIn(firstLine)
        
        // Remove common meta phrases at the start
        val metaPhrases = listOf(
            "Of course! Here is",
            "Here is a",
            "Here's a",
            "Sure! Here is",
            "Certainly! Here is",
            "Here is the",
            "Here's the"
        )
        
        metaPhrases.forEach { phrase ->
            if (cleaned.startsWith(phrase, ignoreCase = true) && !firstLineHasToolCommand) {
                // Find the first newline after the meta phrase and remove everything before it
                val firstNewline = cleaned.indexOf('\n')
                if (firstNewline > 0) {
                    cleaned = cleaned.substring(firstNewline + 1).trimStart()
                }
            }
        }
        
        // Remove subject lines
        cleaned = cleaned.replace(Regex("^Subject:.*?\\n", RegexOption.MULTILINE), "")
        
        // Remove separators
        cleaned = cleaned.replace(Regex("^[*\\-=]{3,}\\s*$", RegexOption.MULTILINE), "")
        
        // Remove bold markdown (**text** or __text__)
        cleaned = cleaned.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        cleaned = cleaned.replace(Regex("__(.+?)__"), "$1")
        
        // Remove italic markdown (*text* or _text_)
        cleaned = cleaned.replace(Regex("\\*(.+?)\\*"), "$1")
        cleaned = cleaned.replace(Regex("_(.+?)_"), "$1")
        
        // Remove headers (# ## ### etc.)
        cleaned = cleaned.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        
        // Remove remaining single asterisks
        cleaned = cleaned.replace("*", "")
        
        // Remove code blocks (```text```)
        cleaned = cleaned.replace(Regex("```[\\s\\S]*?```"), "")
        
        // Remove inline code (`text`)
        cleaned = cleaned.replace(Regex("`(.+?)`"), "$1")
        
        // Remove bullet points but keep the text
        cleaned = cleaned.replace(Regex("^[ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â·ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¹ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¾ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂªÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â«]\\s*", RegexOption.MULTILINE), "")
        
        // Clean up extra whitespace
        cleaned = cleaned.replace(Regex("\\n{3,}"), "\n\n")
        
        return cleaned.trim()
    }
    
    private fun callChatGPT(config: AIConfig, prompt: String): String {
        val apiKey = config.apiKey
        val model = config.model
        return try {
            val url = URL("https://api.openai.com/v1/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            connection.connectTimeout = 60000  // Increased timeout
            connection.readTimeout = 90000     // Increased timeout for long responses
            
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are a helpful assistant that provides concise responses.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                // Increase max_tokens for complete responses
                val maxTokens = if (config.maxTokens < 500) 1000 else config.maxTokens
                put("max_tokens", maxTokens)
                put("temperature", config.temperature)
            }
            
            connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }
            
            if (connection.responseCode != 200) {
                val errorStream = connection.errorStream?.bufferedReader()?.readText()
                return "Error ${connection.responseCode}: $errorStream"
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = JSONObject(response)
            
            jsonResponse
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } catch (e: Exception) {
            "Error calling ChatGPT: ${e.message}"
        }
    }
    
    private fun callGemini(config: AIConfig, prompt: String): String {
        val apiKey = config.apiKey
        val model = config.model
        return try {
            android.util.Log.d("AIService", "ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â°ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¬ Calling Gemini API with model: $model")
            android.util.Log.d("AIService", "ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â°ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â Prompt length: ${prompt.length} chars")
            
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            
            android.util.Log.d("AIService", "ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â°ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â Connection created, setting properties...")
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000  // 30 seconds connect timeout
            connection.readTimeout = 45000     // 45 seconds read timeout
            
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", config.temperature)
                    // CRITICAL FIX: Increase tokens significantly to prevent cut responses
                    // Gemini 2.0 Flash supports up to 8192 output tokens
                    // Default to 1000 if not set or too low
                    val baseTokens = if (config.maxTokens < 1000) 1000 else config.maxTokens
                    // If thinking is enabled, we need extra tokens for the thinking process itself
                    val totalTokens = if (config.enableThinking) baseTokens + 2000 else baseTokens
                    put("maxOutputTokens", totalTokens)
                    
                    // Thinking config for 2.5 and 3.x models
                    if (config.enableThinking && (model.contains("2.5") || model.contains("3-") || model.contains("gemini-3"))) {
                        put("thinkingConfig", JSONObject().apply {
                            put("includeThoughts", true) 
                        })
                    }
                })
                put("safetySettings", JSONArray().apply {
                    val categories = listOf(
                        "HARM_CATEGORY_HARASSMENT",
                        "HARM_CATEGORY_HATE_SPEECH",
                        "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                        "HARM_CATEGORY_DANGEROUS_CONTENT"
                    )
                    categories.forEach { category ->
                        put(JSONObject().apply {
                            put("category", category)
                            put("threshold", "BLOCK_NONE")
                        })
                    }
                })
            }
            
            android.util.Log.d("AIService", "ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â°ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ Sending request to Gemini...")
            android.util.Log.d("AIService", "Request body: ${requestBody.toString()}")
            connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }
            
            android.util.Log.d("AIService", "ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³ Waiting for response...")
            android.util.Log.d("AIService", "Response code: ${connection.responseCode}")
            
            if (connection.responseCode != 200) {
                val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                android.util.Log.e("AIService", "ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ Gemini API Error ${connection.responseCode}: $errorStream")
                android.util.Log.e("AIService", "Gemini API Error: $errorStream")
                return "Error ${connection.responseCode}: $errorStream"
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            android.util.Log.d("AIService", "Gemini Response: $response")
            val jsonResponse = JSONObject(response)
            
            // Check if candidates array exists
            if (!jsonResponse.has("candidates")) {
                // Check for prompt feedback (blocked by safety)
                if (jsonResponse.has("promptFeedback")) {
                    val feedback = jsonResponse.getJSONObject("promptFeedback")
                    val blockReason = feedback.optString("blockReason", "Unknown")
                    return "Content blocked by Gemini safety filters: $blockReason"
                }
                return "Error: No candidates in response"
            }
            
            val candidates = jsonResponse.getJSONArray("candidates")
            if (candidates.length() == 0) {
                return "Error: Empty candidates array"
            }
            
            val candidate = candidates.getJSONObject(0)
            
            // Check finish reason
            val finishReason = candidate.optString("finishReason", "")
            if (finishReason == "SAFETY") {
                return "Response blocked by safety filters. Try rephrasing your message."
            }
            
            // Check if content exists
            if (!candidate.has("content")) {
                // Check safety ratings
                if (candidate.has("safetyRatings")) {
                    return "Content blocked due to safety concerns"
                }
                return "Error: No content in response"
            }
            
            val content = candidate.getJSONObject("content")
            
            // Log full content for debugging
            android.util.Log.d("AIService", "Content object: $content")
            
            // Check if parts exists
            if (!content.has("parts")) {
                android.util.Log.e("AIService", "No parts in content. Full candidate: $candidate")
                return "Error: No parts in content. Full response logged. Please check if model name is correct."
            }
            
            val parts = content.getJSONArray("parts")
            if (parts.length() == 0) {
                android.util.Log.e("AIService", "Empty parts array. Content: $content")
                return "Error: Empty parts array"
            }
            
            val part = parts.getJSONObject(0)
            android.util.Log.d("AIService", "Part object: $part")
            
            // Check if text exists
            if (!part.has("text")) {
                android.util.Log.e("AIService", "No text in part. Part: $part")
                // Sometimes Gemini returns empty response
                return "Gemini returned empty response. Try again."
            }
            
            val text = part.getString("text").trim()
            if (text.isEmpty()) {
                android.util.Log.e("AIService", "Empty text in response")
                return "Gemini returned empty text. Try again."
            }
            
            android.util.Log.d("AIService", "Successfully extracted text: ${text.take(100)}...")
            text
        } catch (e: Exception) {
            android.util.Log.e("AIService", "Gemini Exception: ${e.message}", e)
            "Error calling Gemini: ${e.message}"
        }
    }
    
    private fun callDeepSeek(apiKey: String, model: String, prompt: String): String {
        return try {
            val url = URL("https://api.deepseek.com/v1/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are a helpful assistant.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("max_tokens", 150)
                put("temperature", 0.7)
            }
            
            connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }
            
            if (connection.responseCode != 200) {
                val errorStream = connection.errorStream?.bufferedReader()?.readText()
                return "Error ${connection.responseCode}: $errorStream"
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = JSONObject(response)
            
            jsonResponse
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } catch (e: Exception) {
            "Error calling DeepSeek: ${e.message}"
        }
    }

    /**
     * Parse reminder details from message and schedule it
     */
    private suspend fun processReminderRequest(
        message: String,
        senderPhone: String,
        senderName: String
    ): String? {
        try {
            val replyManager = AIReplyManager(context)
            val provider = replyManager.getSelectedProvider()
            val config = configManager.getConfig(provider)

            if (config.apiKey.isEmpty()) return null

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val currentDateTime = dateFormat.format(Date())

            val prompt = """
                Task: Extract reminder details from message.
                Message: "$message"
                Current Time: $currentDateTime
                Output ONLY a JSON object: {"date": "YYYY-MM-DD", "time": "HH:mm", "context": "Reminder context"}
                Rules:
                - If relative time (e.g. 'in 10 mins'), calculate exact future time based on Current Time.
                - If date missing, assume today.
                - If specific time not mentioned, return null for time key.
                - Output STRICT JSON. No markdown.
            """.trimIndent()

            val rawResponse = when (provider) {
                AIProvider.GEMINI -> callGemini(config, prompt)
                AIProvider.CHATGPT -> callChatGPT(config, prompt)
                else -> return null
            }

            val jsonStr = rawResponse.replace("```json", "").replace("```", "").trim()
            val json = JSONObject(jsonStr)

            if (json.has("date") && json.has("time")) {
                val date = json.getString("date")
                val time = json.getString("time")
                val contextText = json.optString("context", "Reminder")

                GlobalReminderManager(context).addReminder(
                    phone = senderPhone,
                    name = senderName,
                    date = date,
                    time = time,
                    prompt = contextText,
                    templateType = "GENERAL"
                )

                return "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â¦ Reminder set for $date at $time:\n\"$contextText\""
            }
        } catch (e: Exception) {
            android.util.Log.e("AIService", "Reminder extraction failed: ${e.message}")
        }
        return null
    }

    suspend fun generateReminderMessage(
        phone: String,
        name: String,
        reminderContext: String,
        dateTime: String,
        template: String
    ): String = withContext(Dispatchers.IO) {
        val replyManager = AIReplyManager(context)
        val provider = replyManager.getSelectedProvider()
        val config = configManager.getConfig(provider)

        if (config.apiKey.isEmpty()) return@withContext "Reminder: $reminderContext at $dateTime (AI not configured)"

        val stringBuilder = StringBuilder()
        stringBuilder.append("System: You are an AI assistant for ${aiAgentSettings.agentName}.\n")

        if (template.equals("CLINIC", ignoreCase = true) || aiAgentSettings.activeTemplate == "CLINIC") {
            val clinicGenerator = com.message.bulksend.autorespond.ai.core.ClinicContextGenerator(context)
            stringBuilder.append(clinicGenerator.generatePrompt(phone))
        }

        stringBuilder.append("\n\n")
        stringBuilder.append("ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â´ URGENT TASK: Generate a reminder message to send to a customer.\n")
        stringBuilder.append("Customer Name: $name\n")
        stringBuilder.append("Customer Phone: $phone\n")
        stringBuilder.append("Event Date/Time: $dateTime\n")
        stringBuilder.append("Reminder Details: $reminderContext\n\n")
        stringBuilder.append("INSTRUCTION: Write a professional, friendly, and short reminder message.\n")
        stringBuilder.append("Do NOT include 'Here is the message' or quotes. Just the message body.\n")
        stringBuilder.append("Use emojis if appropriate.\n")

        val prompt = stringBuilder.toString()

        try {
            when (provider) {
                AIProvider.GEMINI -> callGemini(config, prompt)
                AIProvider.CHATGPT -> callChatGPT(config, prompt)
                else -> "Reminder: $reminderContext at $dateTime (Provider: $provider)"
            }
        } catch (e: Exception) {
            android.util.Log.e("AIService", "Error generating reminder: ${e.message}")
            "Reminder: $reminderContext at $dateTime"
        }
    }
}










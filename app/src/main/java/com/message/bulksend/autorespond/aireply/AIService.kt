package com.message.bulksend.autorespond.aireply

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        return buildString {
            append(basePrompt)
            append("\n\n[AUTONOMOUS EXECUTION LOOP]\n")
            append("Round: $round\n")
            append("User Goal: $userMessage\n")
            if (userHasStepOrder) {
                append("User ne step order diya hai. Steps ko same order me follow karo.\n")
            }
            append("Latest executed actions:\n")
            latestActions.forEach { append("- $it\n") }
            if (allActions.isNotEmpty()) {
                append("All completed actions so far (repeat mat karo unless user explicitly asks):\n")
                allActions.forEach { append("- $it\n") }
            }
            append("Current drafted reply after execution:\n")
            append(currentResponse)
            append("\n\nNow continue task completion.\n")
            append("Rules:\n")
            append("1. Agar next tool action chahiye, proper tool command output karo.\n")
            append("2. Agar task complete ho gaya hai, final user-facing reply do (no extra analysis).\n")
            append("3. Already completed action ko bina reason repeat mat karo.\n")
        }
    }
    
    suspend fun generateReply(
        provider: AIProvider,
        message: String,
        senderName: String = "User",
        senderPhone: String = "" // Added phone number parameter
    ): String = withContext(Dispatchers.IO) {
        android.util.Log.d("AIService", "🚀 generateReply STARTED")
        android.util.Log.d("AIService", "Provider: ${provider.displayName}, Sender: $senderName, Phone: $senderPhone")
        android.util.Log.d("AIService", "Message: $message")
        
        val config = configManager.getConfig(provider)
        android.util.Log.d("AIService", "Config loaded, API Key present: ${config.apiKey.isNotEmpty()}")
        
        if (config.apiKey.isEmpty()) {
            android.util.Log.e("AIService", "❌ API Key is empty!")
            return@withContext "AI not configured. Please add API key."
        }
        
        // Variable to store detected intent (accessible throughout function)
        var detectedIntent = "UNKNOWN"
        
        android.util.Log.d("AIService", "🔍 Starting profile enrichment and intent detection...")
        
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
                    return@withContext it
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
                android.util.Log.e("AIService", "❌ AI Agent context building failed: ${e.message}", e)
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

        val rawResponse = when (provider) {
            AIProvider.CHATSPROMO -> {
                // ChatsPromo logic...
                val subscriptionManager = ChatsPromoAISubscriptionManager(context)
                if (!subscriptionManager.canUseAI()) {
                    return@withContext "SUBSCRIPTION_REQUIRED: Your trial has expired. Please subscribe to continue using ChatsPromo AI."
                }
                val chatsPromoService = ChatsPromoAIService(context)
                chatsPromoService.generateReply(message, senderName)
            }
            AIProvider.CHATGPT -> callChatGPT(config, prompt)
            AIProvider.GEMINI -> callGemini(config, prompt)
        }
        
        var cleanedResponse = cleanMarkdownFormatting(rawResponse)
        
        // NEW ARCHITECTURE: Use processors and handlers
        if (aiAgentSettings.isAgentEnabled && senderPhone.isNotBlank()) {
            val maxExecutionRounds =
                if (aiAgentSettings.activeTemplate.equals("CUSTOM", ignoreCase = true)) 4 else 2
            val executedActions = linkedSetOf<String>()
            var roundIndex = 0
            var nextRoundResponse = cleanedResponse

            while (true) {
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
                val canContinueIteratively =
                    provider != AIProvider.CHATSPROMO &&
                        roundIndex < maxExecutionRounds - 1 &&
                        !executionResult.shouldStopChain &&
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

                val followUpRawResponse =
                    when (provider) {
                        AIProvider.CHATGPT -> callChatGPT(config, followUpPrompt)
                        AIProvider.GEMINI -> callGemini(config, followUpPrompt)
                        AIProvider.CHATSPROMO -> cleanedResponse
                    }
                nextRoundResponse = cleanMarkdownFormatting(followUpRawResponse)
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
                        val sheetManager = com.message.bulksend.autorespond.ai.customsheet.CustomTemplateSheetManager(context)
                        sheetManager.ensureTemplateSheetSystem(
                            templateName = customTemplateName,
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
                                intent = detectedIntent
                            )
                        }

                        sheetManager.logProductLead(
                            templateName = customTemplateName,
                            phoneNumber = senderPhone,
                            userName = senderName,
                            userMessage = message,
                            intent = detectedIntent
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
        
        return@withContext cleanedResponse
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
                android.util.Log.d("AIService", "Lead score updated: $oldScore → $newScore (${if (improvement > 0) "+" else ""}$improvement)")
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

        val toolCommandPattern = Regex("\\[(SEND_PAYMENT|SEND_DOCUMENT|GENERATE-PAYMENT-LINK)\\s*:", RegexOption.IGNORE_CASE)
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
        cleaned = cleaned.replace(Regex("^[•·∙◦▪▫]\\s*", RegexOption.MULTILINE), "")
        
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
            android.util.Log.d("AIService", "🚀 Calling Gemini API with model: $model")
            android.util.Log.d("AIService", "📝 Prompt length: ${prompt.length} chars")
            
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            
            android.util.Log.d("AIService", "🔗 Connection created, setting properties...")
            
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
            
            android.util.Log.d("AIService", "📤 Sending request to Gemini...")
            android.util.Log.d("AIService", "Request body: ${requestBody.toString()}")
            connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }
            
            android.util.Log.d("AIService", "⏳ Waiting for response...")
            android.util.Log.d("AIService", "Response code: ${connection.responseCode}")
            
            if (connection.responseCode != 200) {
                val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                android.util.Log.e("AIService", "❌ Gemini API Error ${connection.responseCode}: $errorStream")
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
                
                return "✅ Reminder set for $date at $time:\n\"$contextText\""
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
        
        // 1. Build Context
        // We temporarily override the prompt generation to focus on "Drafting a message"
        val stringBuilder = StringBuilder()
        stringBuilder.append("System: You are an AI assistant for ${aiAgentSettings.agentName}.\n")
        
        // Add Clinic context if template match
        if (template.equals("CLINIC", ignoreCase = true) || aiAgentSettings.activeTemplate == "CLINIC") {
            val clinicGenerator = com.message.bulksend.autorespond.ai.core.ClinicContextGenerator(context)
            stringBuilder.append(clinicGenerator.generatePrompt(phone))
        }
        
        stringBuilder.append("\n\n")
        stringBuilder.append("🔴 URGENT TASK: Generate a reminder message to send to a customer.\n")
        stringBuilder.append("Customer Name: $name\n")
        stringBuilder.append("Customer Phone: $phone\n")
        stringBuilder.append("Event Date/Time: $dateTime\n")
        stringBuilder.append("Reminder Details: $reminderContext\n\n")
        stringBuilder.append("INSTRUCTION: Write a professional, friendly, and short reminder message. \n")
        stringBuilder.append("Do NOT include 'Here is the message' or quotes. Just the message body.\n")
        stringBuilder.append("Use emojis if appropriate.\n")
        
        val prompt = stringBuilder.toString()
        
        // 2. Call AI Provider
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


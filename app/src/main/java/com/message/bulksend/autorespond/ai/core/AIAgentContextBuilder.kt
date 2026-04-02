package com.message.bulksend.autorespond.ai.core

import android.content.Context
import com.message.bulksend.autorespond.ai.customsheet.CustomTemplateSheetManager
import com.message.bulksend.autorespond.ai.context.ContextSwitchingManager
import com.message.bulksend.autorespond.ai.context.CustomerIntelligenceBriefBuilder
import com.message.bulksend.autorespond.ai.context.ConversationRollingSummaryManager
import com.message.bulksend.autorespond.ai.context.ToolOutcomeLearningManager
import com.message.bulksend.autorespond.ai.customtask.engine.AgentTaskEngine
import com.message.bulksend.autorespond.ai.customtask.manager.AgentTaskOwnerAlertManager
import com.message.bulksend.autorespond.ai.customtask.manager.AgentTaskManager
import com.message.bulksend.autorespond.ai.customtask.models.AgentTaskSessionStatus
import com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry
import com.message.bulksend.autorespond.ai.data.repo.AIAgentRepository
import com.message.bulksend.autorespond.ai.data.model.UserProfile
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.autorespond.ai.needdiscovery.NeedDiscoveryManager
import com.message.bulksend.autorespond.database.AttributeGroup
import com.message.bulksend.autorespond.database.AttributeOption
import com.message.bulksend.autorespond.database.Product
import com.message.bulksend.autorespond.database.MessageEntity
import com.message.bulksend.product.CatalogueRepository
import org.json.JSONArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class AIAgentContextBuilder(
        private val context: Context,
        private val repository: AIAgentRepository,
        private val settingsManager: AIAgentSettingsManager
) {

        private val contextSwitchingManager = ContextSwitchingManager()
        private val rollingSummaryManager =
                ConversationRollingSummaryManager(context, contextSwitchingManager)
        private val toolOutcomeLearningManager = ToolOutcomeLearningManager(context)
        private val customerIntelligenceBriefBuilder =
                CustomerIntelligenceBriefBuilder(contextSwitchingManager)
        private val taskManager by lazy { AgentTaskManager(context) }
        private val taskEngine by lazy { AgentTaskEngine(taskManager) }
        private val taskOwnerAlertManager by lazy { AgentTaskOwnerAlertManager(context) }
        private val needDiscoveryManager by lazy { NeedDiscoveryManager(context) }

        suspend fun buildContextPrompt(
                senderName: String,
                senderPhone: String,
                incomingMessage: String
        ): String =
                withContext(Dispatchers.IO) {
                        android.util.Log.d(
                                "AIContextBuilder",
                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§ Building context for: $senderName ($senderPhone)"
                        )
                        val stringBuilder = StringBuilder()

                        try {
                                // 0. Global Date/Time Context (FIRST - Most Important)
                                android.util.Log.d(
                                        "AIContextBuilder",
                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â¦ Adding date/time context"
                                )
                                stringBuilder.append(
                                        com.message.bulksend.utils.DateTimeHelper
                                                .getCurrentDateTimeContext()
                                )

                                // 1. System Instruction & Identity
                                android.util.Log.d(
                                        "AIContextBuilder",
                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œ Adding system instructions"
                                )
                                stringBuilder.append(
                                        "\nsystem: You are ${settingsManager.agentName}, a helpful AI assistant.\n"
                                )
                                if (settingsManager.customSystemPrompt.isNotBlank()) {
                                        stringBuilder.append(
                                                "${settingsManager.customSystemPrompt}\n"
                                        )
                                }

                                // Advanced / Super-Power Instructions
                                if (settingsManager.advancedInstruction.isNotBlank()) {
                                        stringBuilder.append(
                                                "\n[ADVANCED INTELLIGENCE / CORE RULES]\n"
                                        )
                                        stringBuilder.append(
                                                "${settingsManager.advancedInstruction}\n"
                                        )
                                }

                                appendCustomTemplateProfile(stringBuilder, senderPhone)
                                appendCustomTemplateSheetContext(
                                        stringBuilder = stringBuilder,
                                        senderPhone = senderPhone,
                                        incomingMessage = incomingMessage
                                )

                                // Clinic Template Context
                                android.util.Log.d(
                                        "AIContextBuilder",
                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¥ Checking clinic context"
                                )
                                val clinicGenerator = ClinicContextGenerator(context)
                                val clinicPrompt = clinicGenerator.generatePrompt(senderPhone)
                                if (clinicPrompt.isNotBlank()) {
                                        stringBuilder.append(clinicPrompt)
                                }

                                stringBuilder.append("\n")

                                // 2. User Profile Context
                                android.util.Log.d("AIContextBuilder", "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¹Ã…â€œÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ Fetching user profile")
                                val userProfile = repository.getUserProfile(senderPhone)
                                val userName = userProfile?.name
                                val isNameUnknown =
                                        userName.isNullOrBlank() || userName == "Unknown"

                                stringBuilder.append("[USER PROFILE]\n")
                                stringBuilder.append("Name: ${userName ?: "Unknown"}\n")
                                stringBuilder.append("Phone: $senderPhone\n")
                                if (userProfile?.currentIntent != null) {
                                        stringBuilder.append(
                                                "Last Intent: ${userProfile.currentIntent}\n"
                                        )
                                }
                                stringBuilder.append("\n")
                                appendCustomTaskContext(
                                        stringBuilder = stringBuilder,
                                        senderPhone = senderPhone,
                                        incomingMessage = incomingMessage,
                                        userProfile = userProfile
                                )

                                // 3. Product Catalogue Context (Search-based, not all)
                                val includeProductContext = shouldIncludeProductContext()
                                android.util.Log.d(
                                        "AIContextBuilder",
                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ Checking product lookup (base=${settingsManager.enableProductLookup}, include=$includeProductContext)"
                                )
                                if (includeProductContext) {
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ Fetching products for context"
                                        )

                                        val products = resolveProductsForContext(incomingMessage)
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ Context products selected: ${products.size}"
                                        )
                                        if (products.isNotEmpty()) {
                                                val catalogueRepo = CatalogueRepository(context)

                                                stringBuilder.append("[PRODUCT CATALOGUE]\n")
                                                stringBuilder.append(
                                                        "Available Products (Name | Price | Description | Variant Hints):\n"
                                                )
                                                for (product in products) {
                                                        stringBuilder.append("- ${product.name}")
                                                        if (product.price > 0) {
                                                                stringBuilder.append(
                                                                        " | Price: ${product.currency} ${product.price}"
                                                                )
                                                        }
                                                        if (product.description.isNotBlank()) {
                                                                stringBuilder.append(
                                                                        " | Desc: ${product.description.take(50)}..."
                                                                )
                                                        }
                                                        val variantHint =
                                                                buildVariantHint(
                                                                        catalogueRepo =
                                                                                catalogueRepo,
                                                                        product = product,
                                                                        incomingMessage =
                                                                                incomingMessage
                                                                )
                                                        if (variantHint.isNotBlank()) {
                                                                stringBuilder.append(
                                                                        " | $variantHint"
                                                                )
                                                        }
                                                        stringBuilder.append("\n")
                                                }

                                                stringBuilder.append(
                                                        "\n[PRODUCT SENDING CAPABILITY]\n"
                                                )
                                                stringBuilder.append(
                                                        "You can send complete product catalogues with all media files.\n"
                                                )
                                                stringBuilder.append("IMPORTANT RULES:\n")
                                                stringBuilder.append(
                                                        "1. IF USER ASKS FOR A LIST (e.g., 'Show products', 'Kya hai product list me'):\n"
                                                )
                                                stringBuilder.append(
                                                        "   - Provide a clean NUMBERED LIST of Name and Price only.\n"
                                                )
                                                stringBuilder.append(
                                                        "   - Do NOT show description or media counts unless asked.\n"
                                                )
                                                stringBuilder.append(
                                                        "   - Do NOT say 'Sending ... catalogue' for the whole list.\n"
                                                )
                                                stringBuilder.append("   - Example Output:\n")
                                                stringBuilder.append(
                                                        "     'Here are our products:\n"
                                                )
                                                stringBuilder.append(
                                                        "     1. iPhone 15 - ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¹70,000\n"
                                                )
                                                stringBuilder.append(
                                                        "     2. MacBook Pro - ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¹1,50,000\n"
                                                )
                                                stringBuilder.append(
                                                        "     Reply with a product name to see details.'\n"
                                                )
                                                stringBuilder.append("\n")
                                                stringBuilder.append(
                                                        "2. IF USER ASKS FOR A SPECIFIC PRODUCT (e.g., 'Send iPhone catalogue', 'iPhone details'):\n"
                                                )
                                                stringBuilder.append(
                                                        "   - Provide full details (Description, Price, etc.).\n"
                                                )
                                                stringBuilder.append(
                                                        "   - THEN say 'Sending [product name] catalogue' to trigger media sending.\n"
                                                )
                                                stringBuilder.append(
                                                        "   - Do not show fields that are empty or null.\n"
                                                )
                                                stringBuilder.append(
                                                        "   - If user mentions variant attributes (size/color/etc), use exact matching variant price and stock.\n"
                                                )
                                                stringBuilder.append("   - Example:\n")
                                                stringBuilder.append("     '[Product Details...]\n")
                                                stringBuilder.append(
                                                        "     Sending iPhone 15 catalogue with all media files.'\n"
                                                )

                                                stringBuilder.append(
                                                        "\n[VARIANT INTELLIGENCE RULES]\n"
                                                )
                                                stringBuilder.append(
                                                        "- Detect size/color/other variant attributes from user message.\n"
                                                )
                                                stringBuilder.append(
                                                        "- If exact variant matches, reply with variant-specific price and stock.\n"
                                                )
                                                stringBuilder.append(
                                                        "- If any attribute is missing, ask a short clarification question before final price/availability confirmation.\n"
                                                )
                                                stringBuilder.append(
                                                        "- Never guess unavailable variant details.\n"
                                                )

                                                if (settingsManager.productInstruction.isNotBlank()
                                                ) {
                                                        stringBuilder.append(
                                                                "[CUSTOM PRODUCT RULES]\n"
                                                        )
                                                        stringBuilder.append(
                                                                "${settingsManager.productInstruction}\n"
                                                        )
                                                }
                                                stringBuilder.append("\n")
                                                android.util.Log.d(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ Product context added"
                                                )
                                        }
                                } else {
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ Product context disabled"
                                        )
                                }

                                // 3.5 Payment Methods Context
                                if (shouldIncludePaymentContext()) {
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³ Fetching payment methods"
                                        )
                                        val paymentIntegration =
                                                com.message.bulksend.aiagent.tools.ecommerce
                                                        .PaymentMethodAIIntegration(context)
                                        val paymentMethodsText =
                                                paymentIntegration.getPaymentMethodsListForAI()
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³ Payment methods fetched"
                                        )

                                        if (paymentMethodsText.contains("Available Payment Methods:")) {
                                        stringBuilder.append("\n[PAYMENT CAPABILITY]\n")
                                        stringBuilder.append(paymentMethodsText)
                                        stringBuilder.append("\n[PAYMENT SENDING INSTRUCTIONS]\n")
                                        stringBuilder.append(
                                                "IMPORTANT: You can send payment methods to users.\n"
                                        )
                                        stringBuilder.append("WHEN TO SEND:\n")
                                        stringBuilder.append(
                                                "- User asks: 'How to pay?', 'Send payment details', 'QR code bhejo', 'Payment method?'\n"
                                        )
                                        stringBuilder.append("- User wants to make payment\n")
                                        stringBuilder.append("\n")
                                        stringBuilder.append("HOW TO SEND:\n")
                                        stringBuilder.append(
                                                "1. Tell user you're sending the payment method\n"
                                        )
                                        stringBuilder.append(
                                                "2. Use command: [SEND_PAYMENT: method_id]\n"
                                        )
                                        stringBuilder.append(
                                                "3. Replace 'method_id' with the actual ID from the payment methods list above\n"
                                        )
                                        stringBuilder.append("\n")
                                        stringBuilder.append("EXAMPLES:\n")
                                        stringBuilder.append("User: 'QR code bhejo'\n")
                                        stringBuilder.append(
                                                "You: 'Sure! Here is the QR code for payment: [SEND_PAYMENT: abc-123-xyz]'\n"
                                        )
                                        stringBuilder.append("\n")
                                        stringBuilder.append("User: 'How to pay?'\n")
                                        stringBuilder.append(
                                                "You: 'You can pay via UPI. Here are the details: [SEND_PAYMENT: upi-456]'\n"
                                        )
                                        stringBuilder.append("\n")
                                        stringBuilder.append("CRITICAL RULES:\n")
                                        stringBuilder.append(
                                                "- ALWAYS use [SEND_PAYMENT: method_id] command for payment methods\n"
                                        )
                                        stringBuilder.append(
                                                "- NEVER use [SEND_DOCUMENT: ...] for payment QR codes or payment details\n"
                                        )
                                        stringBuilder.append(
                                                "- Payment methods are SEPARATE from Agent Document Library\n"
                                        )
                                        stringBuilder.append(
                                                "- Use the exact ID from the 'Available Payment Methods' list above\n"
                                        )
                                        stringBuilder.append(
                                                "- For QR codes: Image will be sent automatically\n"
                                        )
                                        stringBuilder.append(
                                                "- For UPI/Bank: Details will be shown in text\n"
                                        )
                                        stringBuilder.append(
                                                "- Do NOT describe QR code content, just send it\n"
                                        )
                                        stringBuilder.append("\n")
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³ Payment context added"
                                        )

                                        // 3.6 Payment Verification Link Context
                                        if (shouldIncludePaymentVerificationContext()) {
                                                android.util.Log.d(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Checking payment verification link"
                                                )
                                                try {
                                                var recentNonRazorpayPaymentFlow = false
                                                val paymentVerifyIntegration =
                                                        com.message.bulksend.aiagent.tools
                                                                .paymentverification
                                                                .PaymentVerificationAIIntegration
                                                                .getInstance(context)

                                                if (paymentVerifyIntegration.isEnabled()) {
                                                        android.util.Log.d(
                                                                "AIContextBuilder",
                                                                "ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Payment verification enabled"
                                                        )

                                                        // Check if payment details/QR was sent in
                                                        // recent conversation
                                                        val qrWasSent =
                                                                if (settingsManager.enableMemory) {
                                                                        val recentHistory =
                                                                                repository
                                                                                        .getConversationHistory(
                                                                                                senderPhone,
                                                                                                limit =
                                                                                                        5
                                                                                        )
                                                                        recentHistory.any { msg ->
                                                                                val text =
                                                                                        if (msg.outgoingMessage
                                                                                                        .isNotEmpty()
                                                                                        )
                                                                                                msg.outgoingMessage
                                                                                        else
                                                                                                msg.incomingMessage
                                                                                text.contains(
                                                                                        "[SEND_PAYMENT:",
                                                                                        ignoreCase =
                                                                                                true
                                                                                ) ||
                                                                                        (text.contains(
                                                                                                "QR",
                                                                                                ignoreCase =
                                                                                                        true
                                                                                        ) &&
                                                                                                !text.contains(
                                                                                                        "Razorpay",
                                                                                                        ignoreCase =
                                                                                                                true
                                                                                                )) ||
                                                                                        (text.contains(
                                                                                                "payment",
                                                                                                ignoreCase =
                                                                                                        true
                                                                                        ) &&
                                                                                                !text.contains(
                                                                                                        "Razorpay",
                                                                                                        ignoreCase =
                                                                                                                true
                                                                                                ) &&
                                                                                                !text.contains(
                                                                                                        "link",
                                                                                                        ignoreCase =
                                                                                                                true
                                                                                                ))
                                                                        }
                                                                } else {
                                                                        false
                                                                }

                                                        if (qrWasSent) {
                                                                recentNonRazorpayPaymentFlow = true
                                                                stringBuilder.append(
                                                                        "\n[PAYMENT VERIFICATION MODE]\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "User recently received payment details or QR.\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "If user says payment done, verify internally before replying.\n\n"
                                                                )
                                                        }
                                                }

                                                // GLOBAL SAFEGUARD: If Razorpay is configured,
                                                // explicitly forbid manual
                                                // verification links
                                                // This protects against the AI learning bad habits
                                                // from conversation
                                                // history
                                                try {
                                                        val razorpayManager =
                                                                com.message.bulksend.aiagent.tools
                                                                        .ecommerce
                                                                        .RazorPaymentManager(
                                                                                context
                                                                        )
                                                        if (!razorpayManager
                                                                        .getRazorpayKeyId()
                                                                        .isNullOrBlank()
                                                        ) {
                                                                if (recentNonRazorpayPaymentFlow) {
                                                                        stringBuilder.append(
                                                                                "\n[PAYMENT FLOW SAFEGUARD - MIXED METHODS]\n"
                                                                        )
                                                                        stringBuilder.append(
                                                                                "Recent payment flow is QR/UPI/Bank verification.\n"
                                                                        )
                                                                        stringBuilder.append(
                                                                                "Do NOT auto-confirm Razorpay payment unless user clearly asks for Razorpay status.\n"
                                                                        )
                                                                        stringBuilder.append(
                                                                                "If user says payment done after QR/UPI/Bank, ask/check screenshot verification status first.\n"
                                                                        )
                                                                        stringBuilder.append("\n")
                                                                } else {
                                                                stringBuilder.append(
                                                                        "\n[ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â¨ CRITICAL PAYMENT INSTRUCTION - RAZORPAY ACTIVE ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â¨]\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "You are using RAZORPAY for payments.\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "1. Do NOT ask for payment screenshots.\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "2. Do NOT send any 'payment-verify.html' links.\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "3. Do NOT say 'I cannot check payment status directly'.\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "4. You CAN check payment status - it's automatically fetched from Razorpay API.\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "5. When user asks to verify payment, check the [USER PAYMENT STATUS] section above.\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "6. Tell user the actual status (PAID/PENDING/EXPIRED).\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "7. Ignore any previous verification links in the chat history.\n"
                                                                )
                                                                stringBuilder.append("\n")
                                                                }
                                                        }
                                                } catch (e: Exception) {
                                                        android.util.Log.e(
                                                                "AIContextBuilder",
                                                                "ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Error adding global safeguard: ${e.message}"
                                                        )
                                                }
                                                } catch (e: Exception) {
                                                        android.util.Log.e(
                                                                "AIContextBuilder",
                                                                "ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Error adding payment verification: ${e.message}",
                                                                e
                                                        )
                                                }
                                        } else {
                                                android.util.Log.d(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Payment verification context disabled for custom template"
                                                )
                                        }

                                        // 3.7 Dynamic Razorpay Link Generation Context
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³ Checking Razorpay configuration for dynamic links"
                                        )
                                        try {
                                                val razorpayManager =
                                                        com.message.bulksend.aiagent.tools.ecommerce
                                                                .RazorPaymentManager(context)

                                                // Check for active/recent payment links for this
                                                // user (Always check history if possible)
                                                /* DISABLED PER USER REQUEST - ALWAYS GENERATE NEW LINKS
                                                val latestLink =
                                                        razorpayManager.getLatestPaymentLinkForUser(
                                                                senderPhone
                                                        )

                                                if (latestLink != null) {
                                                        var status = latestLink.status
                                                        val amount = latestLink.amount
                                                        val description = latestLink.description

                                                        // If status is 'created' (pending), verify
                                                        // with API directly to
                                                        // get real-time status if configured
                                                        if ((status == "created" ||
                                                                        status == "issued") &&
                                                                        razorpayManager
                                                                                .isConfigured()
                                                        ) {
                                                                android.util.Log.d(
                                                                        "AIContextBuilder",
                                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³ Link ${latestLink.id} is pending, verifying with API..."
                                                                )
                                                                val apiStatus =
                                                                        razorpayManager
                                                                                .verifyPaymentStatusFromApi(
                                                                                        latestLink
                                                                                                .id
                                                                                )
                                                                if (apiStatus !=
                                                                                "unknown_no_creds" &&
                                                                                apiStatus !=
                                                                                        "api_error" &&
                                                                                apiStatus !=
                                                                                        "exception"
                                                                ) {
                                                                        status = apiStatus
                                                                        android.util.Log.d(
                                                                                "AIContextBuilder",
                                                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³ Status updated to: $status"
                                                                        )
                                                                }
                                                        }

                                                        stringBuilder.append(
                                                                "\n[USER PAYMENT STATUS - PRIORITY]\n"
                                                        )
                                                        if (status == "paid") {
                                                                stringBuilder.append(
                                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â¦ PAYMENT RECEIVED for Link ID: ${latestLink.id}\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "User PAID ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¹$amount for '$description'.\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "Action: Confirm receipt and proceed. Do NOT ask to pay again.\n"
                                                                )
                                                        } else if (status == "created" ||
                                                                        status == "issued"
                                                        ) {
                                                                stringBuilder.append(
                                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³ PAYMENT PENDING for Link ID: ${latestLink.id}\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "- Amount: ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¹$amount for $description\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "- Status: PENDING (Not paid yet)\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "If user says they paid, check this status again or ask them to wait.\n"
                                                                )
                                                        } else {
                                                                stringBuilder.append(
                                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ ÃƒÆ’Ã¢â‚¬Â¦ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ Previous Link Status: $status (ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¹$amount)\n"
                                                                )
                                                        }
                                                        stringBuilder.append("\n")
                                                }
                                                */

                                                if (razorpayManager.isConfigured()) {
                                                        stringBuilder.append(
                                                                "\n[SPECIAL TOOL: PAYMENT LINK GENERATION]\n"
                                                        )
                                                        stringBuilder.append(
                                                                "You have permission to generate Razorpay payment links.\n"
                                                        )
                                                        stringBuilder.append(
                                                                "COMMAND: [GENERATE-PAYMENT-LINK: amount, description]\n"
                                                        )
                                                        stringBuilder.append(
                                                                "Example: [GENERATE-PAYMENT-LINK: 500, Consultation Fee]\n"
                                                        )
                                                        stringBuilder.append("RULES:\n")
                                                        stringBuilder.append(
                                                                "1. If user asks for a payment link, YOU MUST USE THIS COMMAND.\n"
                                                        )
                                                        stringBuilder.append(
                                                                "2. Do NOT say 'I cannot generate links'. You CAN.\n"
                                                        )
                                                        stringBuilder.append(
                                                                "3. Extract amount and description from user request.\n"
                                                        )
                                                        stringBuilder.append(
                                                                "4. If amount is missing, ask for it first.\n"
                                                        )
                                                        stringBuilder.append("\n")
                                                        android.util.Log.d(
                                                                "AIContextBuilder",
                                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³ Razorpay dynamic link instructions added (Configured)"
                                                        )
                                                } else {
                                                        stringBuilder.append(
                                                                "\n[PAYMENT SYSTEM STATUS]\n"
                                                        )
                                                        stringBuilder.append(
                                                                "Razorpay is NOT configured in settings.\n"
                                                        )
                                                        stringBuilder.append(
                                                                "If user asks for payment link, tell them: 'I cannot generate a link because payment settings are not configured. Please contact support or check app settings.'\n"
                                                        )
                                                        stringBuilder.append("\n")
                                                        android.util.Log.d(
                                                                "AIContextBuilder",
                                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³ Razorpay not configured message added"
                                                        )
                                                }
                                        } catch (e: Exception) {
                                                android.util.Log.e(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ ÃƒÆ’Ã¢â‚¬Â¦ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ Error adding razorpay context: ${e.message}",
                                                        e
                                                )
                                        }
                                }

                                // 3.8 Razorpay Link Context Fallback (independent of payment
                                // methods)
                                // Fix: Dynamic Razorpay link generation must not depend on
                                // "Available Payment Methods"
                                if (!stringBuilder.toString().contains(
                                                "[SPECIAL TOOL: PAYMENT LINK GENERATION]"
                                        ) &&
                                                !stringBuilder.toString().contains(
                                                        "[PAYMENT SYSTEM STATUS]"
                                                )
                                ) {
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢Ãƒâ€šÃ‚Â³ Adding standalone Razorpay link context fallback"
                                        )
                                        try {
                                                val razorpayManager =
                                                        com.message.bulksend.aiagent.tools.ecommerce
                                                                .RazorPaymentManager(context)

                                                if (razorpayManager.isConfigured()) {
                                                        stringBuilder.append(
                                                                "\n[SPECIAL TOOL: PAYMENT LINK GENERATION]\n"
                                                        )
                                                        stringBuilder.append(
                                                                "You have permission to generate Razorpay payment links.\n"
                                                        )
                                                        stringBuilder.append(
                                                                "COMMAND: [GENERATE-PAYMENT-LINK: amount, description]\n"
                                                        )
                                                        stringBuilder.append(
                                                                "Example: [GENERATE-PAYMENT-LINK: 500, Consultation Fee]\n"
                                                        )
                                                        stringBuilder.append("RULES:\n")
                                                        stringBuilder.append(
                                                                "1. If user asks for a payment link, YOU MUST USE THIS COMMAND.\n"
                                                        )
                                                        stringBuilder.append(
                                                                "2. Do NOT say 'I cannot generate links'. You CAN.\n"
                                                        )
                                                        stringBuilder.append(
                                                                "3. Extract amount and description from user request.\n"
                                                        )
                                                        stringBuilder.append(
                                                                "4. If amount is missing, ask for it first.\n"
                                                        )
                                                        stringBuilder.append("\n")
                                                } else {
                                                        stringBuilder.append(
                                                                "\n[PAYMENT SYSTEM STATUS]\n"
                                                        )
                                                        stringBuilder.append(
                                                                "Razorpay is NOT configured in settings.\n"
                                                        )
                                                        stringBuilder.append(
                                                                "If user asks for payment link, tell them: 'I cannot generate a link because payment settings are not configured. Please contact support or check app settings.'\n"
                                                        )
                                                        stringBuilder.append("\n")
                                                }
                                        } catch (e: Exception) {
                                                android.util.Log.e(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Error adding standalone razorpay context: ${e.message}",
                                                        e
                                                )
                                        }
                                }
                        } else {
                                android.util.Log.d(
                                        "AIContextBuilder",
                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³ Payment context disabled for custom template"
                                )
                        }

                                // 4. Table Sheet Data Context (Business Data) - HIGHEST PRIORITY
                                android.util.Log.d(
                                        "AIContextBuilder",
                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â  Checking data sheet lookup: ${settingsManager.enableDataSheetLookup}"
                                )
                                if (settingsManager.enableDataSheetLookup) {
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â  Searching table sheets"
                                        )
                                        val sheetData =
                                                repository.searchTableSheets(
                                                        senderPhone,
                                                        incomingMessage
                                                )
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â  Found ${sheetData.size} sheet matches"
                                        )
                                        if (sheetData.isNotEmpty()) {
                                                stringBuilder.append(
                                                        "[BUSINESS DATA MATCHES - PRIORITY INFORMATION]\n"
                                                )
                                                stringBuilder.append(
                                                        "IMPORTANT: User's data found in sheets. Use this to answer their questions.\n"
                                                )
                                                stringBuilder.append(
                                                        "If user asks about their details, due, status, order, etc., answer from this data:\n\n"
                                                )
                                                sheetData.forEach { data ->
                                                        stringBuilder.append("- $data\n")
                                                }
                                                stringBuilder.append("\n[INSTRUCTION]\n")
                                                stringBuilder.append(
                                                        "Answer user's question using the above data. Don't ask for phone number if data is already found.\n"
                                                )
                                                stringBuilder.append(
                                                        "Example: If user asks 'What's my due?', check the data above and tell them directly.\n"
                                                )

                                                if (settingsManager.sheetInstruction.isNotBlank()) {
                                                        stringBuilder.append(
                                                                "\n[SHEET PROCESSING RULES]\n"
                                                        )
                                                        stringBuilder.append(
                                                                "${settingsManager.sheetInstruction}\n"
                                                        )
                                                }
                                                stringBuilder.append("\n")
                                                android.util.Log.d(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â  Sheet data context added"
                                                )
                                        }
                                }

                                // 4.5 Document Library Context (Available Documents from Agent
                                // Document System)
                                if (shouldIncludeDocumentContext()) {
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¾ Fetching agent document library"
                                        )
                                        try {
                                        // Add timeout to prevent hanging on database operations
                                        val documentsList =
                                                kotlinx.coroutines.withTimeoutOrNull(3000) {
                                                        val agentDocumentIntegration =
                                                                com.message.bulksend.aiagent.tools
                                                                        .agentdocument
                                                                        .AgentDocumentAIIntegration(
                                                                                context
                                                                        )
                                                        agentDocumentIntegration
                                                                .getDocumentsListForAI()
                                                }

                                        if (documentsList != null) {
                                                android.util.Log.d(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¾ Agent documents loaded"
                                                )

                                                if (documentsList.contains("Available Documents")) {
                                                        stringBuilder.append(
                                                                "\n[AGENT DOCUMENT LIBRARY]\n"
                                                        )
                                                        stringBuilder.append(documentsList)
                                                        stringBuilder.append(
                                                                "\n[HOW TO SEND DOCUMENTS]\n"
                                                        )
                                                        stringBuilder.append(
                                                                "IMPORTANT: This is for general documents (brochures, PDFs, media files).\n"
                                                        )
                                                        stringBuilder.append(
                                                                "For PAYMENT QR codes, use [SEND_PAYMENT: ...] command instead.\n"
                                                        )
                                                        stringBuilder.append("\n")
                                                        stringBuilder.append(
                                                                "When user asks for a document/file/brochure/PDF/media:\n"
                                                        )
                                                        stringBuilder.append(
                                                                "1. Check Name, Description, and Tags to find best match\n"
                                                        )
                                                        stringBuilder.append(
                                                                "2. If exact document is clear, use [SEND_DOCUMENT: document_id]\n"
                                                        )
                                                        stringBuilder.append(
                                                                "3. If only intent/tag is clear, use [SEND_DOCUMENT_BY_TAG: user_intent]\n"
                                                        )
                                                        stringBuilder.append(
                                                                "4. Replace document_id with actual ID from document list\n"
                                                        )
                                                        stringBuilder.append("\n")
                                                        stringBuilder.append("EXAMPLES:\n")
                                                        stringBuilder.append(
                                                                "User: 'send me the brochure' -> [SEND_DOCUMENT: abc-123-xyz]\n"
                                                        )
                                                        stringBuilder.append(
                                                                "User: 'pricing pdf bhejo' -> [SEND_DOCUMENT_BY_TAG: pricing pdf]\n"
                                                        )
                                                        stringBuilder.append(
                                                                "User: 'product PDF bhejo' -> [SEND_DOCUMENT: pdf-456-def]\n"
                                                        )
                                                        stringBuilder.append(
                                                                "User: 'video dikha do' -> [SEND_DOCUMENT: video-789-ghi]\n"
                                                        )
                                                        stringBuilder.append("\n")
                                                        stringBuilder.append(
                                                                "CRITICAL: Do NOT use this for payment QR codes!\n"
                                                        )
                                                        stringBuilder.append("\n")
                                                        android.util.Log.d(
                                                                "AIContextBuilder",
                                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¾ Agent document context added"
                                                        )
                                                } else {
                                                        android.util.Log.d(
                                                                "AIContextBuilder",
                                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¾ No agent documents available"
                                                        )
                                                }
                                        } else {
                                                android.util.Log.w(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â Document library fetch timed out (3s), skipping"
                                                )
                                        }
                                        } catch (e: Exception) {
                                                android.util.Log.e(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã¢â‚¬Â¦ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ Error loading agent documents: ${e.message}",
                                                        e
                                                )
                                        }
                                } else {
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¾ Document tool disabled for custom template"
                                        )
                                }

                                // 4.6 Agent Form Context (Prebuilt templates only)
                                if (shouldIncludeAgentFormContext()) {
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¾ Fetching agent form context"
                                        )
                                        try {
                                        val agentFormContext =
                                                kotlinx.coroutines.withTimeoutOrNull(4000) {
                                                        com.message.bulksend.aiagent.tools.agentform
                                                                .AgentFormAIIntegration(context)
                                                                .getAgentFormContextForAI(senderPhone)
                                                }

                                        if (!agentFormContext.isNullOrBlank()) {
                                                stringBuilder.append("\n[AGENT FORM CAPABILITY]\n")
                                                stringBuilder.append(agentFormContext)
                                                stringBuilder.append("\n")
                                                android.util.Log.d(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¾ Agent form context added"
                                                )
                                        }
                                        } catch (e: Exception) {
                                                android.util.Log.e(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã¢â‚¬Â¦ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ Error loading agent form context: ${e.message}",
                                                        e
                                                )
                                        }
                                } else {
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¾ Agent form tool disabled for custom template"
                                        )
                                }

                                // 4.7 Agent Speech Context (Voice Reply System)
                                if (shouldIncludeSpeechContext()) {
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â½ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ Fetching agent speech context"
                                        )
                                        try {
                                        val speechIntegration =
                                                com.message.bulksend.aiagent.tools.agentspeech
                                                        .AgentSpeechAIIntegration(context)
                                        val speechContext =
                                                speechIntegration.getSpeechContextForAI()

                                        if (speechContext.isNotBlank()) {
                                                stringBuilder.append("\n")
                                                stringBuilder.append(speechContext)
                                                android.util.Log.d(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â½ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ Agent speech context added"
                                                )
                                        } else {
                                                android.util.Log.d(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â½ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ Speech not enabled or no context"
                                                )
                                        }
                                        } catch (e: Exception) {
                                                android.util.Log.e(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã¢â‚¬Â¦ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ Error loading speech context: ${e.message}",
                                                        e
                                                )
                                        }
                                } else {
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â½ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ Agent speech tool disabled for custom template"
                                        )
                                }

                                // 4.8 Google Calendar Context
                                if (shouldIncludeGoogleCalendarContext()) {
                                        android.util.Log.d("AIContextBuilder", "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Fetching Google Calendar context")
                                        stringBuilder.append("\n[GOOGLE CALENDAR CAPABILITY]\n")
                                        stringBuilder.append("You can manage Google Calendar events, Google Meet links, Google Tasks, task lists, and user-level calendar reminder settings.\n")
                                        stringBuilder.append("SUPPORTED EVENT FIELDS: title/summary, description, location, startTime, endTime, startDate, endDate, timeZone, allDay, attendees, recurrence, reminders, visibility, status, transparency, colorId, guestsCanInviteOthers, guestsCanModify, guestsCanSeeOtherGuests, createMeetLink, conferenceType, conferenceRequestId, calendarId, sendUpdates.\n")
                                        stringBuilder.append("SUPPORTED TASK FIELDS: title, notes, due, completed, status, tasklistId, parent, previous, destinationTasklist, dueMin, dueMax, completedMin, completedMax, updatedMin, showCompleted, showDeleted, showHidden, showAssigned.\n")
                                        stringBuilder.append("SUPPORTED CALENDAR SETTINGS FIELDS: calendarId, summaryOverride, selected, hidden, colorId, backgroundColor, foregroundColor, defaultReminders, notifications.\n")
                                        stringBuilder.append("COMMANDS:\n")
                                        stringBuilder.append("- List Events: [CALENDAR_LIST_EVENTS: minTime=2024-03-10T00:00:00Z; maxTime=2024-03-11T00:00:00Z; calendarId=primary; q=meeting; maxResults=10]\n")
                                        stringBuilder.append("- Create Event: [CALENDAR_CREATE_EVENT: title=Meeting; startTime=2024-03-10T10:00:00Z; endTime=2024-03-10T11:00:00Z; timeZone=Asia/Kolkata; attendees=a@example.com,b@example.com; reminders=popup:30,email:1440; createMeetLink=true]\n")
                                        stringBuilder.append("- Update Event: [CALENDAR_UPDATE_EVENT: id=12345; location=Office; reminders=default; sendUpdates=all]\n")
                                        stringBuilder.append("- Create Meet Link on Existing Event: [CALENDAR_CREATE_MEET_LINK: id=12345; sendUpdates=all]\n")
                                        stringBuilder.append("- Delete Event: [CALENDAR_DELETE_EVENT: id=12345; sendUpdates=all]\n")
                                        stringBuilder.append("- List Tasks: [CALENDAR_LIST_TASKS: tasklistId=@default; dueMin=2024-03-10T00:00:00Z; dueMax=2024-03-11T00:00:00Z; maxResults=20; showCompleted=false; showAssigned=true]\n")
                                        stringBuilder.append("- Create Task: [CALENDAR_CREATE_TASK: title=Follow up client; notes=Call tomorrow; due=2024-03-10T12:00:00Z]\n")
                                        stringBuilder.append("- Update Task: [CALENDAR_UPDATE_TASK: id=abc123; status=completed; completed=2024-03-10T13:00:00Z]\n")
                                        stringBuilder.append("- Move Task: [CALENDAR_MOVE_TASK: id=abc123; tasklistId=@default; previous=def456]\n")
                                        stringBuilder.append("- Clear Completed Tasks: [CALENDAR_CLEAR_COMPLETED_TASKS: tasklistId=@default]\n")
                                        stringBuilder.append("- Delete Task: [CALENDAR_DELETE_TASK: id=abc123]\n")
                                        stringBuilder.append("- List Task Lists: [CALENDAR_LIST_TASKLISTS]\n")
                                        stringBuilder.append("- Create Task List: [CALENDAR_CREATE_TASKLIST: title=Client Followups]\n")
                                        stringBuilder.append("- Update Task List: [CALENDAR_UPDATE_TASKLIST: id=tasklist123; title=VIP Followups]\n")
                                        stringBuilder.append("- Delete Task List: [CALENDAR_DELETE_TASKLIST: id=tasklist123]\n")
                                        stringBuilder.append("- List Calendars: [CALENDAR_LIST_CALENDARS]\n")
                                        stringBuilder.append("- Get Calendar Settings: [CALENDAR_GET_CALENDAR: calendarId=primary]\n")
                                        stringBuilder.append("- Update Calendar Settings: [CALENDAR_UPDATE_CALENDAR: calendarId=primary; defaultReminders=popup:30,email:1440; notifications=agenda:email,eventCreation:email; selected=true]\n")
                                        stringBuilder.append("\nRULES:\n")
                                        stringBuilder.append("1. ALWAYS use the user's timezone when formatting dates/times. If not specified, ask the user or infer carefully from context.\n")
                                        stringBuilder.append("2. Output the exact command format. Do NOT enclose the command in backticks or markdown code blocks.\n")
                                        stringBuilder.append("3. Provide time in ISO-8601 format (for example 2024-03-10T10:00:00Z).\n")
                                        stringBuilder.append("4. For event reminders, use reminders=default or reminders=popup:30,email:1440.\n")
                                        stringBuilder.append("5. For calendar default reminders, use defaultReminders=popup:30,email:1440.\n")
                                        stringBuilder.append("6. For notification settings, use notifications=agenda:email,eventCreation:email,eventChange:email,eventCancellation:email,responseNeeded:email.\n")
                                        stringBuilder.append("7. Reminders are supported on calendar events and calendar-level default reminder settings. Tasks are managed through Google Tasks.\n")
                                        stringBuilder.append("8. For recurrence rules containing semicolons, use & inside the rule value instead of ;, for example recurrence=FREQ=DAILY&COUNT=5.\n")
                                        stringBuilder.append("9. Before deleting an event, task, or task list, ask for confirmation unless the user is absolutely clear.\n")
                                        stringBuilder.append("10. If task commands fail for an older connection, ask the user to reconnect Google Calendar because Google Tasks requires an added OAuth scope.\n\n")
                                }

                                // 4.9 Google Gmail Context
                                if (shouldIncludeGoogleGmailContext()) {
                                        android.util.Log.d("AIContextBuilder", "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒâ€šÃ‚Â§ Fetching Google Gmail context")
                                        stringBuilder.append("\n[GOOGLE GMAIL CAPABILITY]\n")
                                        stringBuilder.append("You can fully manage the user's Gmail: emails, threads, drafts, labels, attachments, and tracked history.\n")
                                        stringBuilder.append("All outgoing AI emails automatically get a unique tracking pixel and a Gmail history record. You do NOT need to add tracking manually.\n")
                                        stringBuilder.append("COMMANDS:\n")
                                        stringBuilder.append("- List Emails: [GMAIL_LIST_EMAILS: maxResults=10; q=is:unread]\n")
                                        stringBuilder.append("- Read Email: [GMAIL_READ_EMAIL: messageId=123xyz; format=full]\n")
                                        stringBuilder.append("- Send Email: [GMAIL_SEND_EMAIL: to=user@example.com; subject=Hello; body=Message text]\n")
                                        stringBuilder.append("- Reply Email: [GMAIL_REPLY_EMAIL: to=user@example.com; subject=Re: Hello; threadId=abc; messageIdRef=123xyz; body=Message text]\n")
                                        stringBuilder.append("- Trash Email: [GMAIL_TRASH_EMAIL: messageId=123xyz]\n")
                                        stringBuilder.append("- Restore Email: [GMAIL_UNTRASH_EMAIL: messageId=123xyz]\n")
                                        stringBuilder.append("- Delete Email: [GMAIL_DELETE_EMAIL: messageId=123xyz]\n")
                                        stringBuilder.append("- Modify Labels: [GMAIL_MODIFY_EMAIL: messageId=123xyz; addLabelIds=STARRED; removeLabelIds=UNREAD]\n")
                                        stringBuilder.append("- List Threads: [GMAIL_LIST_THREADS: q=from:client@example.com]\n")
                                        stringBuilder.append("- Read Thread: [GMAIL_READ_THREAD: threadId=thread123]\n")
                                        stringBuilder.append("- Modify Thread: [GMAIL_MODIFY_THREAD: threadId=thread123; addLabelIds=IMPORTANT]\n")
                                        stringBuilder.append("- List Drafts: [GMAIL_LIST_DRAFTS]\n")
                                        stringBuilder.append("- Create Draft: [GMAIL_CREATE_DRAFT: to=user@example.com; subject=Proposal; body=Draft body]\n")
                                        stringBuilder.append("- Update Draft: [GMAIL_UPDATE_DRAFT: draftId=draft123; to=user@example.com; subject=Updated Proposal; body=Updated body]\n")
                                        stringBuilder.append("- Send Draft: [GMAIL_SEND_DRAFT: draftId=draft123]\n")
                                        stringBuilder.append("- List Labels: [GMAIL_LIST_LABELS]\n")
                                        stringBuilder.append("- Create Label: [GMAIL_CREATE_LABEL: name=VIP Leads; labelListVisibility=labelShow]\n")
                                        stringBuilder.append("- Read Attachment: [GMAIL_READ_ATTACHMENT: messageId=123xyz; attachmentId=att456]\n")
                                        stringBuilder.append("- List Tracking History: [GMAIL_LIST_HISTORY]\n")
                                        stringBuilder.append("- Get Tracking Record: [GMAIL_GET_HISTORY: trackingId=trk_123]\n")
                                        stringBuilder.append("\nRULES:\n")
                                        stringBuilder.append("1. Output the exact command format. Do NOT enclose the command in backticks or markdown code blocks.\n")
                                        stringBuilder.append("2. Outgoing emails are auto-tracked by default. Do not ask the user to enable tracking unless they want it disabled.\n")
                                        stringBuilder.append("3. When sending or replying inside the current chat, assume the current contact's phone and name should be attached to tracking history unless the user explicitly overrides them.\n")
                                        stringBuilder.append("4. Before permanently deleting emails, threads, drafts, or labels, ask for confirmation unless the user is absolutely clear.\n")
                                        stringBuilder.append("5. Use [GMAIL_LIST_HISTORY] or [GMAIL_GET_HISTORY] when the user asks whether a specific email was opened, sent, or tracked.\n")

                                        runCatching {
                                                val historyResult =
                                                        com.message.bulksend.aiagent.tools.gmail
                                                                .GoogleGmailAgentTool
                                                                .listEmailHistory(
                                                                        linkedMapOf(
                                                                                "phone" to senderPhone,
                                                                                "name" to senderName,
                                                                                "maxResults" to "5"
                                                                        )
                                                                )
                                                if (historyResult.optString("status") == "success") {
                                                        com.message.bulksend.aiagent.tools.gmail
                                                                .GmailTrackingTableSheetManager(context)
                                                                .syncHistoryPayload(historyResult)
                                                        val historyItems =
                                                                historyResult.optJSONArray("history")
                                                                        ?: JSONArray()
                                                        val summary =
                                                                buildGmailHistorySummary(historyItems)
                                                        if (summary.isNotBlank()) {
                                                                stringBuilder.append("\n[CURRENT CONTACT GMAIL HISTORY]\n")
                                                                stringBuilder.append(summary)
                                                                stringBuilder.append("\n")
                                                        }
                                                }
                                        }.onFailure { error ->
                                                android.util.Log.w(
                                                        "AIContextBuilder",
                                                        "Failed to fetch Gmail tracking history: ${error.message}"
                                                )
                                        }

                                        stringBuilder.append("\n")
                                }

                                // 5. Conversation Memory & Context Switching
                                android.util.Log.d(
                                        "AIContextBuilder",
                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­ Checking memory: ${settingsManager.enableMemory}"
                                )
                                var hasAskedForName = false
                                var userIgnoredNameRequest = false
                                var previousTopic: String? = null
                                var conversationHistory = emptyList<MessageEntity>()
                                val longChatSummaryEnabled =
                                        isCustomTemplateActive() && settingsManager.customTemplateLongChatSummaryEnabled

                                if (settingsManager.enableMemory) {
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­ Fetching conversation history"
                                        )
                                        val historyLimit =
                                                if (settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true)) {
                                                        settingsManager.customTemplateConversationHistoryLimit
                                                } else {
                                                        10
                                                }
                                        val historyFetchLimit =
                                                if (longChatSummaryEnabled) {
                                                        (historyLimit * 3).coerceIn(historyLimit, 120)
                                                } else {
                                                        historyLimit
                                                }
                                        conversationHistory =
                                                repository.getConversationHistory(
                                                        senderPhone,
                                                        limit = historyFetchLimit
                                                )
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­ Found ${conversationHistory.size} messages in history"
                                        )
                                        val promptHistory = conversationHistory.take(historyLimit)
                                        if (promptHistory.isNotEmpty()) {
                                                stringBuilder.append("[CONVERSATION HISTORY]\n")
                                                // History comes newest first usually, so reverse it
                                                val reversedHistory = promptHistory.reversed()
                                                val perMessageLimit =
                                                        if (settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true)) {
                                                                320
                                                        } else {
                                                                220
                                                        }
                                                val historyCharBudget =
                                                        if (settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true)) {
                                                                5000
                                                        } else {
                                                                2200
                                                        }
                                                var consumedHistoryChars = 0
                                                var historyTruncated = false

                                                reversedHistory.forEachIndexed { index, msg ->
                                                        if (historyTruncated) return@forEachIndexed

                                                        val sender =
                                                                if (msg.status == "RECEIVED" ||
                                                                                msg.outgoingMessage
                                                                                        .isEmpty()
                                                                )
                                                                        "User"
                                                                else "Assistant"
                                                        val rawText =
                                                                if (msg.outgoingMessage.isNotEmpty()
                                                                )
                                                                        msg.outgoingMessage
                                                                else msg.incomingMessage
                                                        val normalizedText =
                                                                rawText.replace(Regex("\\s+"), " ").trim()
                                                        val text =
                                                                if (normalizedText.length > perMessageLimit) {
                                                                        normalizedText.take(perMessageLimit) + "..."
                                                                } else {
                                                                        normalizedText
                                                                }
                                                        val line = "$sender: $text\n"
                                                        if (consumedHistoryChars + line.length > historyCharBudget) {
                                                                stringBuilder.append("[...history truncated for prompt budget...]\n")
                                                                historyTruncated = true
                                                                return@forEachIndexed
                                                        }

                                                        stringBuilder.append(line)
                                                        consumedHistoryChars += line.length

                                                        // Detect topic from user messages
                                                        if (sender == "User") {
                                                                val topicResult =
                                                                        contextSwitchingManager
                                                                                .detectTopic(text)
                                                                previousTopic = topicResult.topic
                                                        }

                                                        // Check if we already asked for name
                                                        if (sender == "Assistant" &&
                                                                        text.contains(
                                                                                "name",
                                                                                ignoreCase = true
                                                                        ) &&
                                                                        (text.contains(
                                                                                "may i know",
                                                                                ignoreCase = true
                                                                        ) ||
                                                                                text.contains(
                                                                                        "what is your",
                                                                                        ignoreCase =
                                                                                                true
                                                                                ) ||
                                                                                text.contains(
                                                                                        "can i have",
                                                                                        ignoreCase =
                                                                                                true
                                                                                ) ||
                                                                                text.contains(
                                                                                        "could you tell",
                                                                                        ignoreCase =
                                                                                                true
                                                                                ) ||
                                                                                text.contains(
                                                                                        "please tell",
                                                                                        ignoreCase =
                                                                                                true
                                                                                ))
                                                        ) {
                                                                hasAskedForName = true

                                                                // Check if user responded but
                                                                // didn't give name
                                                                if (index + 1 < reversedHistory.size
                                                                ) {
                                                                        val nextMsg =
                                                                                reversedHistory[
                                                                                        index + 1]
                                                                        val userResponse =
                                                                                if (nextMsg.outgoingMessage
                                                                                                .isEmpty()
                                                                                )
                                                                                        nextMsg.incomingMessage
                                                                                else ""

                                                                        // Check if user's response
                                                                        // doesn't contain name-like
                                                                        // patterns
                                                                        if (userResponse
                                                                                        .isNotBlank() &&
                                                                                        !userResponse
                                                                                                .contains(
                                                                                                        "my name",
                                                                                                        ignoreCase =
                                                                                                                true
                                                                                                ) &&
                                                                                        !userResponse
                                                                                                .contains(
                                                                                                        "i am",
                                                                                                        ignoreCase =
                                                                                                                true
                                                                                                ) &&
                                                                                        !userResponse
                                                                                                .contains(
                                                                                                        "i'm",
                                                                                                        ignoreCase =
                                                                                                                true
                                                                                                ) &&
                                                                                        !userResponse
                                                                                                .contains(
                                                                                                        "call me",
                                                                                                        ignoreCase =
                                                                                                                true
                                                                                                ) &&
                                                                                        userResponse
                                                                                                .split(
                                                                                                        " "
                                                                                                )
                                                                                                .size <
                                                                                                4
                                                                        ) { // Short responses
                                                                                // likely not giving
                                                                                // name
                                                                                userIgnoredNameRequest =
                                                                                        true
                                                                        }
                                                                }
                                                        }
                                                }

                                                if (settingsManager.memoryInstruction.isNotBlank()
                                                ) {
                                                        stringBuilder.append("[MEMORY RULES]\n")
                                                        stringBuilder.append(
                                                                "${settingsManager.memoryInstruction}\n"
                                                        )
                                                }
                                                stringBuilder.append("\n")
                                                android.util.Log.d(
                                                        "AIContextBuilder",
                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­ Memory context added"
                                                )
                                        }
                                }

                                if (longChatSummaryEnabled) {
                                        val longChatSummary =
                                                rollingSummaryManager.buildContextSnippet(
                                                        senderPhone = senderPhone,
                                                        fullHistory = conversationHistory,
                                                        recentWindow =
                                                                if (settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true)) {
                                                                        settingsManager.customTemplateConversationHistoryLimit
                                                                } else {
                                                                        10
                                                                },
                                                        userProfile = userProfile,
                                                        needState = needDiscoveryManager.getState(senderPhone),
                                                        templateGoal = settingsManager.customTemplateGoal.trim()
                                                )
                                        if (longChatSummary.isNotBlank()) {
                                                stringBuilder.append(longChatSummary)
                                                stringBuilder.append("\n\n")
                                        }
                                }

                                val toolOutcomeSnippet = toolOutcomeLearningManager.buildContextSnippet(senderPhone)
                                if (toolOutcomeSnippet.isNotBlank()) {
                                        stringBuilder.append(toolOutcomeSnippet)
                                        stringBuilder.append("\n\n")
                                }

                                val intelligenceBrief =
                                        customerIntelligenceBriefBuilder.buildPromptBlock(
                                                senderPhone = senderPhone,
                                                incomingMessage = incomingMessage,
                                                templateGoal = settingsManager.customTemplateGoal.trim(),
                                                userProfile = userProfile,
                                                history = conversationHistory,
                                                needSchema = needDiscoveryManager.getSchema(),
                                                needState = needDiscoveryManager.getState(senderPhone),
                                                hasAskedForName = hasAskedForName,
                                                userIgnoredNameRequest = userIgnoredNameRequest
                                        )
                                if (intelligenceBrief.isNotBlank()) {
                                        stringBuilder.append(intelligenceBrief)
                                        stringBuilder.append("\n\n")
                                }
                                // Detect current topic
                                android.util.Log.d("AIContextBuilder", "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â½ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¯ Detecting topic")
                                val currentTopicResult =
                                        contextSwitchingManager.detectTopic(incomingMessage)
                                val currentTopic = currentTopicResult.topic
                                android.util.Log.d(
                                        "AIContextBuilder",
                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â½ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¯ Current topic: $currentTopic"
                                )

                                // Check for topic change
                                val topicChanged =
                                        contextSwitchingManager.detectTopicChange(
                                                currentTopic,
                                                previousTopic
                                        )

                                // Add context switching info
                                if (topicChanged) {
                                        stringBuilder.append("[TOPIC CHANGE DETECTED]\n")
                                        stringBuilder.append(
                                                contextSwitchingManager.buildContextString(
                                                        currentTopic = currentTopic,
                                                        previousTopic = previousTopic
                                                )
                                        )
                                        stringBuilder.append("\n")
                                }

                                // Check for comparison request
                                val comparisonItems =
                                        contextSwitchingManager.detectComparisonRequest(
                                                incomingMessage
                                        )
                                if (comparisonItems != null && comparisonItems.size >= 2) {
                                        stringBuilder.append("[COMPARISON REQUEST DETECTED]\n")
                                        stringBuilder.append(
                                                "User wants to compare: ${comparisonItems.joinToString(" and ")}\n"
                                        )
                                        stringBuilder.append(
                                                "AI should provide a comparison between these items.\n"
                                        )
                                        stringBuilder.append("\n")
                                }

                                // 6. Smart Profile Extraction (Name Only - No Auto Phone Request)
                                android.util.Log.d(
                                        "AIContextBuilder",
                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¹Ã…â€œÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ Smart profile extraction"
                                )
                                val profileExtractor =
                                        com.message.bulksend.autorespond.ai.profile
                                                .SmartProfileExtractor(context, repository)

                                // Check if we're awaiting name confirmation
                                android.util.Log.d(
                                        "AIContextBuilder",
                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¹Ã…â€œÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¤ Checking name confirmation status"
                                )
                                if (profileExtractor.isAwaitingNameConfirmation(senderPhone)) {
                                        val pendingName =
                                                profileExtractor.getPendingName(senderPhone)

                                        if (profileExtractor.isConfirmation(incomingMessage)) {
                                                // User confirmed name
                                                profileExtractor.confirmAndSaveName(senderPhone)
                                                stringBuilder.append("[NAME CONFIRMED]\n")
                                                stringBuilder.append(
                                                        "User confirmed their name as: $pendingName\n"
                                                )
                                                stringBuilder.append(
                                                        "Acknowledge the confirmation and continue conversation naturally.\n"
                                                )
                                                stringBuilder.append(
                                                        "Example: 'Great! Nice to meet you, $pendingName. How can I help you today?'\n"
                                                )
                                                stringBuilder.append("\n")
                                        } else if (profileExtractor.isRejection(incomingMessage)) {
                                                // User rejected name
                                                profileExtractor.cancelNameConfirmation(senderPhone)
                                                stringBuilder.append("[NAME REJECTED]\n")
                                                stringBuilder.append(
                                                        "User said the name is incorrect. Ask for their correct name.\n"
                                                )
                                                stringBuilder.append(
                                                        "Example: 'Oh, I apologize! What is your correct name?'\n"
                                                )
                                                stringBuilder.append("\n")
                                        } else {
                                                // User didn't respond to confirmation - maybe
                                                // asking something else
                                                stringBuilder.append(
                                                        "[AWAITING NAME CONFIRMATION]\n"
                                                )
                                                stringBuilder.append(
                                                        "You asked if their name is '$pendingName'.\n"
                                                )
                                                stringBuilder.append(
                                                        "If user is asking a different question, answer it first, then gently ask again.\n"
                                                )
                                                stringBuilder.append(
                                                        "Don't force confirmation if user has moved to another topic.\n"
                                                )
                                                stringBuilder.append("\n")
                                        }
                                } else if (isNameUnknown && settingsManager.askCurrentUserName) {
                                        // Try to extract name from current message
                                        val extractedName =
                                                profileExtractor.extractNameFromMessage(
                                                        incomingMessage
                                                )

                                        if (extractedName != null) {
                                                // Name found! Save as pending and ask for
                                                // confirmation
                                                profileExtractor.savePendingName(
                                                        senderPhone,
                                                        extractedName
                                                )
                                                stringBuilder.append("[NAME EXTRACTED]\n")
                                                stringBuilder.append(
                                                        "Extracted name from message: $extractedName\n"
                                                )
                                                stringBuilder.append(
                                                        "IMPORTANT: Verify this naturally. Do not be robotic.\n"
                                                )
                                                stringBuilder.append(
                                                        "Example: 'Nice to meet you, $extractedName!' or 'Is that your name?'\n"
                                                )
                                                stringBuilder.append(
                                                        "Wait for confirmation (yes/no) before proceeding.\n"
                                                )
                                                stringBuilder.append("\n")
                                        } else if (!hasAskedForName) {
                                                // No name found, ask for it (but don't block
                                                // conversation)
                                                stringBuilder.append("[ASK FOR NAME - OPTIONAL]\n")
                                                stringBuilder.append(
                                                        "User's name is unknown. You can politely ask for their name.\n"
                                                )
                                                stringBuilder.append(
                                                        "CRITICAL: Do NOT ask for the name if the user just said 'Hi', 'Hello', or a greeting.\n"
                                                )
                                                stringBuilder.append(
                                                        "Wait until the user asks a question or engages further.\n"
                                                )
                                                stringBuilder.append(
                                                        "Prioritize answering their questions first.\n"
                                                )
                                                stringBuilder.append("\n")
                                        } else if (settingsManager.reAskNameIfNotGiven &&
                                                        userIgnoredNameRequest
                                        ) {
                                                // User ignored previous request - don't force it
                                                stringBuilder.append("[RE-ASK FOR NAME - GENTLE]\n")
                                                stringBuilder.append(
                                                        "User didn't provide name earlier. Do NOT ask again immediately.\n"
                                                )
                                                stringBuilder.append(
                                                        "Only ask if it's absolutely necessary for the request.\n"
                                                )
                                                stringBuilder.append("\n")
                                        }
                                }

                                // Check if we're awaiting phone number (ONLY if explicitly asked)
                                if (profileExtractor.isAwaitingPhoneNumber(senderPhone)) {
                                        val extractedPhone =
                                                profileExtractor.extractPhoneFromMessage(
                                                        incomingMessage
                                                )

                                        if (extractedPhone != null) {
                                                // Phone found! Save it
                                                profileExtractor.savePhoneNumber(
                                                        senderPhone,
                                                        extractedPhone
                                                )
                                                stringBuilder.append("[PHONE NUMBER SAVED]\n")
                                                stringBuilder.append(
                                                        "Phone number saved: $extractedPhone\n"
                                                )
                                                stringBuilder.append(
                                                        "Acknowledge and continue: 'Thank you! I've saved your number.'\n"
                                                )
                                                stringBuilder.append("\n")
                                        } else {
                                                // User didn't provide phone - maybe they're asking
                                                // something else
                                                // Don't force phone number, let conversation
                                                // continue
                                                stringBuilder.append("[NOTE]\n")
                                                stringBuilder.append(
                                                        "User was asked for phone number but didn't provide it.\n"
                                                )
                                                stringBuilder.append(
                                                        "If user is asking a question, answer it first.\n"
                                                )
                                                stringBuilder.append(
                                                        "Don't force phone number collection if user has moved to another topic.\n"
                                                )
                                                stringBuilder.append("\n")
                                        }
                                }

                                // 6.5 E-commerce: Check for pending orders and ask for address
                                android.util.Log.d(
                                        "AIContextBuilder",
                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂºÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ Checking e-commerce mode"
                                )
                                val advancedSettings =
                                        com.message.bulksend.autorespond.ai.settings
                                                .AIAgentAdvancedSettings(context)
                                if (advancedSettings.enableEcommerceMode &&
                                                advancedSettings.autoAskAddress
                                ) {
                                        android.util.Log.d(
                                                "AIContextBuilder",
                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂºÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ Checking pending orders"
                                        )
                                        try {
                                                val orderManager =
                                                        com.message.bulksend.autorespond.ai
                                                                .ecommerce.OrderManager(context)
                                                if (orderManager.hasPendingOrder(senderPhone)) {
                                                        android.util.Log.d(
                                                                "AIContextBuilder",
                                                                "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂºÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ Pending order found"
                                                        )
                                                        val orderDetails =
                                                                orderManager.getPendingOrderDetails(
                                                                        senderPhone
                                                                )
                                                        if (orderDetails != null) {
                                                                stringBuilder.append(
                                                                        "[PENDING ORDER - ASK FOR ADDRESS]\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "The user has a pending order:\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "Product: ${orderDetails["Product"]}\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "Quantity: ${orderDetails["Quantity"]}\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "Total Amount: ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¹${orderDetails["Total Amount"]}\n"
                                                                )
                                                                stringBuilder.append("\n")
                                                                stringBuilder.append(
                                                                        "[IMPORTANT INSTRUCTION]\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "The user has confirmed payment. Now you MUST ask for their delivery address.\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "Be polite and clear. Ask for complete address including:\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "- House/Flat number\n"
                                                                )
                                                                stringBuilder.append(
                                                                        "- Street/Area\n"
                                                                )
                                                                stringBuilder.append("- City\n")
                                                                stringBuilder.append("- Pincode\n")
                                                                stringBuilder.append(
                                                                        "Example: 'Great! Your order is confirmed. Please provide your complete delivery address including house number, street, city, and pincode.'\n"
                                                                )
                                                                stringBuilder.append("\n")
                                                                android.util.Log.d(
                                                                        "AIContextBuilder",
                                                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂºÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ Pending order context added"
                                                                )
                                                        }
                                                }
                                        } catch (e: Exception) {
                                                android.util.Log.e(
                                                        "AIContextBuilder",
                                                        "Failed to check pending orders: ${e.message}"
                                                )
                                        }
                                }

                                // 7. REQUIRE NAME (Only if setting enabled and no sheet data found)
                                android.util.Log.d(
                                        "AIContextBuilder",
                                        "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â°ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚ÂÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â Checking name requirement"
                                )
                                if (isNameUnknown &&
                                                settingsManager.requireNameToContinue &&
                                                !profileExtractor.isAwaitingNameConfirmation(
                                                        senderPhone
                                                )
                                ) {
                                        // Check if we have sheet data - if yes, don't block
                                        // conversation
                                        val hasSheetData =
                                                if (settingsManager.enableDataSheetLookup) {
                                                        repository
                                                                .searchTableSheets(
                                                                        senderPhone,
                                                                        incomingMessage
                                                                )
                                                                .isNotEmpty()
                                                } else {
                                                        false
                                                }

                                        if (!hasSheetData) {
                                                stringBuilder.append(
                                                        "[NAME REQUIRED TO CONTINUE]\n"
                                                )
                                                stringBuilder.append(
                                                        "The user's name is required to continue this conversation.\n"
                                                )
                                                stringBuilder.append(
                                                        "Politely ask for their name.\n"
                                                )
                                                stringBuilder.append(
                                                        "Example: 'I'd love to help you, but I need to know your name first. May I have your name please?'\n"
                                                )
                                                stringBuilder.append("\n")
                                        }
                                }

                                // 8. Current User Query
                                android.util.Log.d(
                                        "AIContextBuilder",
                                        "ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Context built successfully, length: ${stringBuilder.length}"
                                )
                                stringBuilder.append("User: $incomingMessage\n")
                                stringBuilder.append("Assistant: ")

                                stringBuilder.toString()
                        } catch (e: Exception) {
                                android.util.Log.e(
                                        "AIContextBuilder",
                                        "ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Context building failed: ${e.message}",
                                        e
                                )
                                // Return minimal context on error
                                """
            system: You are ${settingsManager.agentName}, a helpful AI assistant.
            ${settingsManager.customSystemPrompt}
            
            User: $incomingMessage
            Assistant: 
        """.trimIndent()
                        }
                }

        private suspend fun resolveProductsForContext(incomingMessage: String): List<Product> {
                val directMatches =
                        repository
                                .searchProducts(incomingMessage)
                                .filter { it.isVisible }
                if (directMatches.isNotEmpty()) {
                        return directMatches.take(8)
                }

                val tokens = extractProductSearchTokens(incomingMessage)
                if (tokens.isNotEmpty()) {
                        val scoreByProductId = mutableMapOf<Long, Int>()
                        val productById = mutableMapOf<Long, Product>()

                        tokens.forEach { token ->
                                val tokenMatches =
                                        repository.searchProducts(token).filter { it.isVisible }
                                tokenMatches.forEach { product ->
                                        productById[product.id] = product
                                        val boost = scoreProductTokenMatch(product, token)
                                        scoreByProductId[product.id] =
                                                (scoreByProductId[product.id] ?: 0) + boost
                                }
                        }

                        if (scoreByProductId.isNotEmpty()) {
                                return scoreByProductId
                                        .entries
                                        .sortedByDescending { it.value }
                                        .mapNotNull { entry -> productById[entry.key] }
                                        .take(8)
                        }
                }

                return repository.getAllVisibleProducts().take(5)
        }

        private fun extractProductSearchTokens(query: String): List<String> {
                if (query.isBlank()) return emptyList()

                val stopWords =
                        setOf(
                                "show",
                                "send",
                                "share",
                                "catalog",
                                "catalogue",
                                "product",
                                "products",
                                "list",
                                "details",
                                "detail",
                                "price",
                                "kya",
                                "hai",
                                "ka",
                                "ki",
                                "ke",
                                "me",
                                "mein",
                                "mujhe",
                                "please",
                                "plz",
                                "and",
                                "or",
                                "for",
                                "the"
                        )

                return Regex("[^a-zA-Z0-9]+")
                        .split(query.lowercase(Locale.ROOT))
                        .map { it.trim() }
                        .filter { it.length >= 2 }
                        .filter { token -> token !in stopWords }
                        .distinct()
                        .take(8)
        }

        private fun scoreProductTokenMatch(product: Product, token: String): Int {
                val normalizedToken = token.lowercase(Locale.ROOT)
                val name = product.name.lowercase(Locale.ROOT)
                val category = product.category.lowercase(Locale.ROOT)
                val description = product.description.lowercase(Locale.ROOT)
                val customFields = product.customFields.lowercase(Locale.ROOT)

                var score = 0
                if (name == normalizedToken) score += 12
                else if (name.contains(normalizedToken)) score += 7
                if (category.contains(normalizedToken)) score += 4
                if (description.contains(normalizedToken)) score += 3
                if (customFields.contains(normalizedToken)) score += 2

                if (isLikelyVariantToken(normalizedToken)) {
                        // Variant words are weak product identifiers, keep weight lower.
                        score = (score - 1).coerceAtLeast(1)
                }
                return score.coerceAtLeast(1)
        }

        private fun isLikelyVariantToken(token: String): Boolean {
                return token in
                        setOf(
                                "red",
                                "blue",
                                "green",
                                "black",
                                "white",
                                "yellow",
                                "pink",
                                "orange",
                                "brown",
                                "grey",
                                "gray",
                                "xs",
                                "s",
                                "m",
                                "l",
                                "xl",
                                "xxl"
                        )
        }

        private suspend fun buildVariantHint(
                catalogueRepo: CatalogueRepository,
                product: Product,
                incomingMessage: String
        ): String {
                val allGroups =
                        catalogueRepo
                                .getAttributeGroups(product.id)
                                .sortedBy { it.displayOrder }
                if (allGroups.isEmpty()) return ""

                val optionsByGroup =
                        allGroups.associate { group ->
                                group.id to
                                        catalogueRepo
                                                .getOptions(group.id)
                                                .sortedBy { it.displayOrder }
                        }
                val groups = allGroups.filter { optionsByGroup[it.id].orEmpty().isNotEmpty() }
                if (groups.isEmpty()) return ""
                val skippedGroups = allGroups.filter { it.id !in groups.map { g -> g.id }.toSet() }
                if (skippedGroups.isNotEmpty()) {
                        android.util.Log.d(
                                "AIContextBuilder",
                                "Variant hint: skipping empty groups for product='${product.name}' -> ${skippedGroups.joinToString { it.name }}"
                        )
                }

                val labelByGroupId =
                        groups.associate { group ->
                                val options = optionsByGroup[group.id].orEmpty()
                                group.id to inferAttributeLabel(group, options)
                        }

                val matchedByGroup = linkedMapOf<Long, AttributeOption>()
                val ambiguousGroups = mutableSetOf<String>()

                groups.forEach { group ->
                        val options = optionsByGroup[group.id].orEmpty()
                        val groupLabel = labelByGroupId[group.id] ?: group.name
                        val scored =
                                options.map { option ->
                                        option to scoreAttributeTokenMatch(
                                                message = incomingMessage,
                                                optionValue = option.value,
                                                groupLabel = groupLabel
                                        )
                                }

                        val bestScore = scored.maxOfOrNull { it.second } ?: 0
                        if (bestScore <= 0) return@forEach

                        val topMatches = scored.filter { it.second == bestScore }
                        if (topMatches.size == 1) {
                                matchedByGroup[group.id] = topMatches.first().first
                        } else {
                                ambiguousGroups.add(labelByGroupId[group.id] ?: group.name)
                        }
                }

                if (matchedByGroup.isEmpty()) {
                        val normalizedMessage = normalizeAttributeText(incomingMessage)
                        val requestedLabels = detectRequestedParameterLabels(normalizedMessage)
                        if (requestedLabels.isNotEmpty()) {
                                val requestedLines =
                                        requestedLabels.mapNotNull { label ->
                                                val values =
                                                        groups
                                                                .filter {
                                                                        (labelByGroupId[it.id]
                                                                                        ?: "")
                                                                                .equals(
                                                                                        label,
                                                                                        ignoreCase = true
                                                                                )
                                                                }
                                                                .flatMap { group ->
                                                                        optionsByGroup[group.id]
                                                                                .orEmpty()
                                                                                .map { option ->
                                                                                        option.value
                                                                                }
                                                                }
                                                                .distinct()
                                                if (values.isEmpty()) {
                                                        null
                                                } else {
                                                        "Available $label: ${values.joinToString(", ")}"
                                                }
                                        }
                                if (requestedLines.isNotEmpty()) {
                                        return requestedLines.joinToString(" | ")
                                }
                        }

                        val groupPreview =
                                groups.joinToString("; ") { group ->
                                        val groupLabel = labelByGroupId[group.id] ?: group.name
                                        val values =
                                                optionsByGroup[group.id]
                                                        .orEmpty()
                                                        .take(6)
                                                        .joinToString(", ") {
                                                                it.value
                                                        }
                                        "$groupLabel=[$values]"
                                }
                        android.util.Log.d(
                                "AIContextBuilder",
                                "Variant hint: no attribute match for product='${product.name}', message='$incomingMessage'"
                        )
                        return "Variant options: $groupPreview"
                }

                val matchedValues = matchedByGroup.values.map { it.value }
                val matchedText =
                        matchedByGroup.values.joinToString(", ") { option ->
                                val groupName =
                                        groups
                                                .firstOrNull { it.id == option.groupId }
                                                ?.let { labelByGroupId[it.id] ?: it.name }
                                                ?: "Attribute"
                                "$groupName=${option.value}"
                        }

                if (ambiguousGroups.isNotEmpty()) {
                        val ambiguousText = ambiguousGroups.joinToString(", ")
                        android.util.Log.d(
                                "AIContextBuilder",
                                "Variant hint: ambiguous options for product='${product.name}', groups=$ambiguousText, message='$incomingMessage'"
                        )
                        return "Detected: $matchedText | Ambiguous: $ambiguousText (ask user to confirm exact option)"
                }

                val missingGroups =
                        groups.filter { group -> group.id !in matchedByGroup.keys }
                val exactVariant = catalogueRepo.findVariantByOptions(product.id, matchedValues)

                if (exactVariant != null) {
                        val variantPrice =
                                if (exactVariant.price > 0) {
                                        exactVariant.price
                                } else {
                                        product.price
                                }
                        val stockText =
                                if (exactVariant.stock >= 0) {
                                        exactVariant.stock.toString()
                                } else {
                                        "Unlimited"
                                }
                        val availability = if (exactVariant.isAvailable) "Available" else "Unavailable"
                        android.util.Log.d(
                                "AIContextBuilder",
                                "Variant hint: exact variant matched for product='${product.name}', matched='$matchedText', variantId=${exactVariant.id}"
                        )
                        return "Matched: $matchedText | Variant Price: ${product.currency} $variantPrice | Stock: $stockText | $availability"
                }

                if (missingGroups.isNotEmpty()) {
                        val missing =
                                missingGroups
                                        .map { group -> labelByGroupId[group.id] ?: group.name }
                                        .distinct()
                                        .joinToString(", ")
                        android.util.Log.d(
                                "AIContextBuilder",
                                "Variant hint: partial match for product='${product.name}', matched='$matchedText', missing='$missing'"
                        )
                        return "Detected: $matchedText | Missing: $missing (ask user before exact price/stock)"
                }

                android.util.Log.d(
                        "AIContextBuilder",
                        "Variant hint: no exact variant for product='${product.name}', matched='$matchedText'"
                )
                return "Detected: $matchedText | Exact variant unavailable/ambiguous (ask user to confirm option combination)"
        }

        private fun containsAttributeToken(message: String, optionValue: String): Boolean {
                return scoreAttributeTokenMatch(message, optionValue) > 0
        }

        private fun scoreAttributeTokenMatch(
                message: String,
                optionValue: String,
                groupLabel: String? = null
        ): Int {
                val normalizedMessage = normalizeAttributeText(message)
                val normalizedOption = normalizeAttributeText(optionValue)

                if (normalizedMessage.isBlank() || normalizedOption.isBlank()) return 0

                if (containsPhrase(normalizedMessage, normalizedOption)) {
                        return 100 + normalizedOption.length.coerceAtMost(20)
                }

                val messageWords =
                        normalizedMessage
                                .split(" ")
                                .filter { it.isNotBlank() }
                                .toSet()
                val canonicalMessageWords = messageWords.map { canonicalizeNumberToken(it) }.toSet()
                val optionWords =
                        normalizedOption
                                .split(" ")
                                .filter { it.isNotBlank() }
                                .map { canonicalizeNumberToken(it) }
                if (optionWords.isEmpty()) return 0

                if (optionWords.size == 1) {
                        val word = optionWords.first()
                        return if (word in canonicalMessageWords) 60 + word.length.coerceAtMost(20) else 0
                }

                val numericTokens = optionWords.filter { isNumericToken(it) }
                if (numericTokens.isNotEmpty()) {
                        if (!numericTokens.all { it in canonicalMessageWords }) return 0
                        val alphaTokens =
                                optionWords.filterNot {
                                        isNumericToken(it) || isIgnorableUnitToken(it)
                                }
                        val numericOnlyShortMessage =
                                canonicalMessageWords.count { isNumericToken(it) } >= 1 &&
                                        canonicalMessageWords.size <= 3
                        val measurementIntent =
                                hasMeasurementIntent(
                                        words = canonicalMessageWords,
                                        groupLabel = groupLabel
                                )
                        return when {
                                alphaTokens.isEmpty() -> 50
                                alphaTokens.any { it in canonicalMessageWords } -> 55 + alphaTokens.size
                                measurementIntent || numericOnlyShortMessage -> 46
                                else -> 0
                        }
                }

                val significantTokens = optionWords.filter { it.length >= 2 }
                if (significantTokens.isEmpty()) return 0
                val allPresent = significantTokens.all { it in canonicalMessageWords }
                return if (allPresent) 40 + significantTokens.size else 0
        }

        private fun containsPhrase(message: String, phrase: String): Boolean {
                val pattern = Regex("(^|\\s)${Regex.escape(phrase)}(\\s|$)")
                return pattern.containsMatchIn(message)
        }

        private fun isIgnorableUnitToken(token: String): Boolean {
                return token in setOf("eu", "uk", "us", "cm", "mm", "ml", "gb", "tb")
        }

        private fun normalizeAttributeText(raw: String): String {
                var normalized = raw.lowercase(Locale.ROOT)

                normalized =
                        normalized
                                .replace(Regex("(\\d),(\\d)"), "$1.$2")
                                .replace("\"", " in ")
                                .replace("'", " ft ")
                                .replace("x", " x ")
                                .replace(Regex("(?<=\\d)(?=[a-z])"), " ")
                                .replace(Regex("(?<=[a-z])(?=\\d)"), " ")

                val phraseSynonyms =
                        mapOf(
                                "extra small" to "xs",
                                "x small" to "xs",
                                "double extra large" to "xxl",
                                "double xl" to "xxl",
                                "2 xl" to "xxl",
                                "2xl" to "xxl",
                                "extra large" to "xl",
                                "x large" to "xl",
                                "medium" to "m",
                                "small" to "s",
                                "large" to "l",
                                "free size" to "free size",
                                "one size" to "free size",
                                "onesize" to "free size",
                                "standard size" to "free size",
                                "universal size" to "free size"
                        )
                phraseSynonyms.forEach { (source, target) ->
                        normalized =
                                normalized.replace(
                                        Regex("\\b${Regex.escape(source)}\\b"),
                                        target
                                )
                }

                val unitSynonyms =
                        mapOf(
                                "kilogram" to "kg",
                                "kilograms" to "kg",
                                "kilo" to "kg",
                                "kilos" to "kg",
                                "kgs" to "kg",
                                "gram" to "g",
                                "grams" to "g",
                                "gm" to "g",
                                "gms" to "g",
                                "liter" to "l",
                                "liters" to "l",
                                "litre" to "l",
                                "litres" to "l",
                                "ltr" to "l",
                                "ltrs" to "l",
                                "milliliter" to "ml",
                                "milliliters" to "ml",
                                "millilitre" to "ml",
                                "millilitres" to "ml",
                                "meter" to "m",
                                "meters" to "m",
                                "metre" to "m",
                                "metres" to "m",
                                "mtr" to "m",
                                "mtrs" to "m",
                                "centimeter" to "cm",
                                "centimeters" to "cm",
                                "centimetre" to "cm",
                                "centimetres" to "cm",
                                "millimeter" to "mm",
                                "millimeters" to "mm",
                                "millimetre" to "mm",
                                "millimetres" to "mm",
                                "foot" to "ft",
                                "feet" to "ft",
                                "inch" to "in",
                                "inches" to "in"
                        )
                unitSynonyms.forEach { (source, target) ->
                        normalized =
                                normalized.replace(
                                        Regex("\\b${Regex.escape(source)}\\b"),
                                        target
                                )
                }

                val wordSynonyms =
                        mapOf(
                                "laal" to "red",
                                "lal" to "red",
                                "neela" to "blue",
                                "nila" to "blue",
                                "neeli" to "blue",
                                "hara" to "green",
                                "hari" to "green",
                                "kala" to "black",
                                "kaala" to "black",
                                "safed" to "white",
                                "gulabi" to "pink",
                                "peela" to "yellow",
                                "narangi" to "orange",
                                "bhura" to "brown"
                        )
                wordSynonyms.forEach { (source, target) ->
                        normalized =
                                normalized.replace(
                                        Regex("\\b${Regex.escape(source)}\\b"),
                                        target
                                )
                }

                return normalized
                        .replace(Regex("(?<!\\d)\\.(?!\\d)"), " ")
                        .replace(Regex("[^a-z0-9.]+"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
        }

        private fun inferAttributeLabel(
                group: AttributeGroup,
                options: List<AttributeOption>
        ): String {
                val type = group.type.uppercase(Locale.ROOT)
                val normalizedName = normalizeAttributeText(group.name)
                val normalizedValues = options.map { normalizeAttributeText(it.value) }.filter { it.isNotBlank() }
                val valueWords =
                        normalizedValues
                                .flatMap { value -> value.split(" ").filter { it.isNotBlank() } }
                                .map { canonicalizeNumberToken(it) }
                val allNumeric = valueWords.isNotEmpty() && valueWords.all { isNumericToken(it) }
                val colorTokens =
                        setOf(
                                "red",
                                "blue",
                                "green",
                                "black",
                                "white",
                                "yellow",
                                "pink",
                                "orange",
                                "brown",
                                "grey",
                                "gray"
                        )
                val mostlyColorValues =
                        valueWords.isNotEmpty() && valueWords.count { it in colorTokens } >=
                                (valueWords.size / 2 + 1)
                val hasWeightUnits = valueWords.any { it in setOf("kg", "g") }
                val hasVolumeUnits = valueWords.any { it in setOf("l", "ml") }
                val hasDimensionUnits = valueWords.any { it in setOf("m", "cm", "mm", "ft", "in") }
                val nameLooksColor =
                        normalizedName.contains("color") || normalizedName.contains("colour") ||
                                normalizedName.contains("shade")
                val nameLooksSize =
                        normalizedName.contains("size") || normalizedName.contains("number") ||
                                normalizedName == "no"

                return when {
                        normalizedName.contains("weight") || normalizedName.contains("kilo") ||
                                normalizedName.contains("kg") || normalizedName.contains("gram") -> "Weight"
                        normalizedName.contains("volume") || normalizedName.contains("capacity") ||
                                normalizedName.contains("liter") || normalizedName.contains("litre") ||
                                normalizedName.contains("ml") -> "Volume"
                        normalizedName.contains("length") || normalizedName.contains("height") ||
                                normalizedName.contains("width") || normalizedName.contains("depth") ||
                                normalizedName.contains("dimension") || normalizedName.contains("inch") ||
                                normalizedName.contains("feet") || normalizedName.contains("foot") ||
                                normalizedName.contains("meter") || normalizedName.contains("metre") ||
                                normalizedName.contains("cm") || normalizedName.contains("mm") -> "Dimension"
                        hasWeightUnits -> "Weight"
                        hasVolumeUnits -> "Volume"
                        hasDimensionUnits -> "Dimension"
                        nameLooksColor && !allNumeric -> "Color"
                        nameLooksSize -> "Size"
                        allNumeric -> "Size"
                        mostlyColorValues -> "Color"
                        type.contains("NUMBER") -> "Size"
                        type.contains("COLOR") -> "Color"
                        else -> group.name.ifBlank { "Attribute" }
                }
        }

        private fun detectRequestedParameterLabels(normalizedMessage: String): List<String> {
                val tokens = normalizedMessage.split(" ").filter { it.isNotBlank() }
                val canonicalTokens = tokens.map { canonicalizeNumberToken(it) }
                val requested = mutableSetOf<String>()

                val sizeSignals =
                        setOf(
                                "size",
                                "sizes",
                                "number",
                                "num",
                                "no",
                                "uk",
                                "us",
                                "eu",
                                "fit",
                                "free",
                                "freesize",
                                "one",
                                "standard",
                                "universal"
                        )
                val colorSignals = setOf("color", "colour", "colors", "colours", "shade")
                val weightSignals =
                        setOf(
                                "weight",
                                "kg",
                                "kilo",
                                "kilos",
                                "gram",
                                "grams",
                                "gm",
                                "g"
                        )
                val volumeSignals =
                        setOf(
                                "volume",
                                "capacity",
                                "liter",
                                "litre",
                                "liters",
                                "litres",
                                "ltr",
                                "l",
                                "ml"
                        )
                val dimensionSignals =
                        setOf(
                                "dimension",
                                "dimensions",
                                "length",
                                "width",
                                "height",
                                "depth",
                                "inch",
                                "inches",
                                "in",
                                "ft",
                                "foot",
                                "feet",
                                "meter",
                                "metre",
                                "m",
                                "cm",
                                "mm"
                        )

                if (canonicalTokens.any { it in sizeSignals } ||
                                normalizedMessage.contains("free size")
                ) {
                        requested.add("Size")
                }
                if (canonicalTokens.any { it in colorSignals }) requested.add("Color")
                if (canonicalTokens.any { it in weightSignals }) requested.add("Weight")
                if (canonicalTokens.any { it in volumeSignals }) requested.add("Volume")
                if (canonicalTokens.any { it in dimensionSignals }) requested.add("Dimension")

                return requested.toList()
        }

        private fun hasMeasurementIntent(words: Set<String>, groupLabel: String?): Boolean {
                if (words.isEmpty()) return false
                val label = groupLabel?.lowercase(Locale.ROOT)?.trim().orEmpty()

                val sizeSignals =
                        setOf(
                                "size",
                                "sizes",
                                "number",
                                "num",
                                "no",
                                "uk",
                                "us",
                                "eu",
                                "fit",
                                "free",
                                "freesize"
                        )
                val weightSignals = setOf("weight", "kg", "kilo", "kilos", "gram", "grams", "gm", "g")
                val volumeSignals =
                        setOf(
                                "volume",
                                "capacity",
                                "liter",
                                "litre",
                                "liters",
                                "litres",
                                "ltr",
                                "l",
                                "ml"
                        )
                val dimensionSignals =
                        setOf(
                                "dimension",
                                "dimensions",
                                "length",
                                "width",
                                "height",
                                "depth",
                                "inch",
                                "inches",
                                "in",
                                "ft",
                                "foot",
                                "feet",
                                "meter",
                                "metre",
                                "m",
                                "cm",
                                "mm"
                        )

                return when {
                        label.equals("size", ignoreCase = true) -> words.any { it in sizeSignals }
                        label.equals("weight", ignoreCase = true) -> words.any { it in weightSignals }
                        label.equals("volume", ignoreCase = true) -> words.any { it in volumeSignals }
                        label.equals("dimension", ignoreCase = true) ->
                                words.any { it in dimensionSignals }
                        else -> false
                }
        }

        private fun isNumericToken(token: String): Boolean {
                return token.matches(Regex("\\d+(?:\\.\\d+)?"))
        }

        private fun canonicalizeNumberToken(token: String): String {
                if (!isNumericToken(token)) return token
                var value = token
                if (value.contains(".")) {
                        value = value.trimEnd('0').trimEnd('.')
                }
                if (value.startsWith("0") && value.length > 1 && value[1] != '.') {
                        value = value.trimStart('0').ifBlank { "0" }
                }
                return value
        }

        private fun isCustomTemplateActive(): Boolean {
                return settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true)
        }

        private fun shouldIncludeProductContext(): Boolean {
                if (!isCustomTemplateActive()) {
                        return settingsManager.enableProductLookup
                }
                // In custom template mode, catalogue/variant context follows catalogue switch.
                return settingsManager.customTemplateEnableAutonomousCatalogueSend
        }

        private fun shouldIncludePaymentContext(): Boolean {
                if (!isCustomTemplateActive()) return true
                return settingsManager.customTemplateEnablePaymentTool
        }

        private fun shouldIncludePaymentVerificationContext(): Boolean {
                if (!isCustomTemplateActive()) return true
                return settingsManager.customTemplateEnablePaymentTool &&
                        settingsManager.customTemplateEnablePaymentVerificationTool
        }

        private fun shouldIncludeDocumentContext(): Boolean {
                if (!isCustomTemplateActive()) return true
                return settingsManager.customTemplateEnableDocumentTool
        }

        private fun shouldIncludeAgentFormContext(): Boolean {
                if (!isCustomTemplateActive()) return true
                return settingsManager.customTemplateEnableAgentFormTool
        }

        private fun shouldIncludeSpeechContext(): Boolean {
                if (!isCustomTemplateActive()) return true
                return settingsManager.customTemplateEnableSpeechTool
        }

        private suspend fun appendCustomTemplateSheetContext(
                stringBuilder: StringBuilder,
                senderPhone: String,
                incomingMessage: String
        ) {
                if (!isCustomTemplateActive()) return

                val templateName =
                        settingsManager.customTemplateName.trim().ifBlank { "Custom AI Template" }
                val folderName =
                        settingsManager.customTemplateSheetFolderName.trim().ifBlank { "AI Agent Data Sheet" }
                val writeMode = settingsManager.customTemplateWriteStorageMode
                val writeFieldSchema = resolveCustomWriteFieldSchema()
                val writeFieldNames =
                        writeFieldSchema
                                .map { it.name }
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .distinctBy { it.lowercase(Locale.ROOT) }
                val referenceSheetName =
                        settingsManager.customTemplateReferenceSheetName.trim()
                val allReferenceSheets = referenceSheetName.equals("All Sheets", ignoreCase = true)
                val resolvedReferenceSheet =
                        if (referenceSheetName.isBlank() || allReferenceSheets) {
                                null
                        } else {
                                referenceSheetName
                        }
                val defaultReadSheet =
                        settingsManager.customTemplateReadSheetName.trim().ifBlank {
                                CustomTemplateSheetManager.DEFAULT_READ_SHEET_NAME
                        }
                val defaultWriteSheet =
                        settingsManager.customTemplateWriteSheetName.trim().ifBlank {
                                CustomTemplateSheetManager.DEFAULT_WRITE_SHEET_NAME
                        }
                val linkedWriteSheet =
                        settingsManager.customTemplateLinkedWriteSheetName.trim()
                val readSheetForExamples = resolvedReferenceSheet ?: defaultReadSheet
                val writeSheetForExamples = linkedWriteSheet.ifBlank { defaultWriteSheet }
                val configuredMatchFields =
                        settingsManager.customTemplateSheetMatchFields
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .distinctBy { it.lowercase(Locale.ROOT) }
                val effectiveMatchFields = configuredMatchFields.ifEmpty { listOf("Phone Number") }
                val primaryMatchField = effectiveMatchFields.first()
                val emailCandidate =
                        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
                                .find(incomingMessage)
                                ?.value
                                .orEmpty()
                val primaryMatchValue =
                        when {
                                primaryMatchField.contains("phone", ignoreCase = true) && senderPhone.isNotBlank() -> senderPhone
                                primaryMatchField.contains("email", ignoreCase = true) -> emailCandidate.ifBlank { "customer@example.com" }
                                senderPhone.isNotBlank() -> senderPhone
                                else -> "<value>"
                        }
                val matchFieldsLabel = effectiveMatchFields.joinToString(", ")
                val readSourceMode =
                        if (resolvedReferenceSheet == null) {
                                "all sheets"
                        } else {
                                "sheet '$referenceSheetName'"
                        }

                stringBuilder.append("[CUSTOM SHEET CONFIG]\n")
                stringBuilder.append("Read Source Folder: $folderName ($readSourceMode)\n")
                stringBuilder.append(
                        "Read Tool: ${if (settingsManager.customTemplateEnableSheetReadTool) "ON" else "OFF"}\n"
                )
                stringBuilder.append(
                        "Write Tool: ${if (settingsManager.customTemplateEnableSheetWriteTool) "ON" else "OFF"}\n"
                )
                stringBuilder.append("Match Fields: $matchFieldsLabel\n")
                stringBuilder.append("\n")

                val sheetManager = CustomTemplateSheetManager(context)
                val writeColumns =
                        settingsManager.customTemplateWriteSheetColumns
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                val schemaColumns = writeFieldNames.ifEmpty { writeColumns }
                sheetManager.ensureTemplateSheetSystem(
                        templateName = templateName,
                        folderNameOverride = folderName,
                        readSheetNameOverride = settingsManager.customTemplateReadSheetName,
                        writeSheetNameOverride = settingsManager.customTemplateWriteSheetName,
                        salesSheetNameOverride = settingsManager.customTemplateSalesSheetName,
                        writeCustomColumns = schemaColumns
                )

                if (settingsManager.customTemplateEnableSheetReadTool &&
                                senderPhone.isNotBlank()
                ) {
                        val allMatches =
                                repository.searchTableSheets(
                                        senderPhone,
                                        incomingMessage,
                                        tableNameFilter = resolvedReferenceSheet,
                                        allowedFolders = listOf(folderName),
                                        matchFields = effectiveMatchFields
                                )

                        if (allMatches.isNotEmpty()) {
                                stringBuilder.append("[CUSTOM FOLDER SHEET MATCHES]\n")
                                stringBuilder.append(
                                        if (resolvedReferenceSheet == null) {
                                                "Matched by query + configured match fields ($matchFieldsLabel) across all sheets in selected folder '$folderName'.\n"
                                        } else {
                                                "Matched by query + configured match fields ($matchFieldsLabel) in selected sheet '$referenceSheetName'.\n"
                                        }
                                )
                                allMatches.take(20).forEach { row ->
                                        stringBuilder.append("- $row\n")
                                }
                                stringBuilder.append(
                                        "Use this data as high-priority context for replies.\n"
                                )
                                stringBuilder.append("\n")
                        }
                }

                if (settingsManager.customTemplateEnableSheetReadTool) {
                        stringBuilder.append("[CUSTOM SHEET READ RULES]\n")
                        stringBuilder.append(
                                "For structured read operations you can use:\n"
                        )
                        stringBuilder.append(
                                "[SHEET_SELECT: {\"table\":\"$readSheetForExamples\",\"where\":{\"$primaryMatchField\":\"$primaryMatchValue\"},\"columns\":[\"Name\",\"$primaryMatchField\"],\"limit\":1}]\n"
                        )
                        stringBuilder.append(
                                "[SHEET_AGG: {\"table\":\"$readSheetForExamples\",\"operation\":\"COUNTIF\",\"column\":\"Status\",\"criteria\":\"=PAID\"}]\n"
                        )
                        stringBuilder.append(
                                "[SHEET_PIVOT: {\"table\":\"$readSheetForExamples\",\"groupBy\":\"Status\",\"operation\":\"COUNT\"}]\n"
                        )
                        stringBuilder.append("\n")
                }

                if (settingsManager.customTemplateEnableSheetWriteTool) {
                        stringBuilder.append("[CUSTOM SHEET WRITE RULES]\n")
                        stringBuilder.append("Write Mode: $writeMode\n")
                        if (writeFieldNames.isNotEmpty()) {
                                stringBuilder.append(
                                        "Allowed write fields: ${writeFieldNames.joinToString(", ")}\n"
                                )
                        }
                        if (linkedWriteSheet.isNotBlank() &&
                                        writeMode != AIAgentSettingsManager.SHEET_WRITE_MODE_GOOGLE
                        ) {
                                stringBuilder.append(
                                        "Linked user write sheet: $linkedWriteSheet\n"
                                )
                        }
                        stringBuilder.append(
                                "When user asks to save/update information, append command:\n"
                        )
                        stringBuilder.append(
                                "[WRITE_SHEET: field1=value1; field2=value2]\n"
                        )
                        stringBuilder.append(
                                "For structured table operations you can also use:\n"
                        )
                        stringBuilder.append(
                                "[SHEET_SELECT: {\"table\":\"$readSheetForExamples\",\"where\":{\"$primaryMatchField\":\"$primaryMatchValue\"},\"columns\":[\"Name\",\"$primaryMatchField\"],\"limit\":1}]\n"
                        )
                        stringBuilder.append(
                                "[SHEET_AGG: {\"table\":\"$writeSheetForExamples\",\"operation\":\"COUNTIF\",\"column\":\"Status\",\"criteria\":\"=PAID\"}]\n"
                        )
                        stringBuilder.append(
                                "[SHEET_PIVOT: {\"table\":\"$writeSheetForExamples\",\"groupBy\":\"Status\",\"operation\":\"COUNT\"}]\n"
                        )
                        stringBuilder.append(
                                "[SHEET_UPSERT: {\"table\":\"$writeSheetForExamples\",\"key\":{\"$primaryMatchField\":\"$primaryMatchValue\"},\"values\":{\"Last Intent\":\"order_status\"}}]\n"
                        )
                        stringBuilder.append(
                                "[SHEET_BULK_UPSERT: {\"table\":\"$writeSheetForExamples\",\"keyColumns\":[\"$primaryMatchField\"],\"rows\":[{\"$primaryMatchField\":\"$primaryMatchValue\",\"Last Intent\":\"order_status\",\"Status\":\"ACTIVE\"}]}]\n"
                        )
                        if (writeMode == AIAgentSettingsManager.SHEET_WRITE_MODE_GOOGLE) {
                                val targetGoogleSheet =
                                    settingsManager.customTemplateGoogleWriteSheetName.ifBlank { "default" }
                                stringBuilder.append("Write target sheet: $targetGoogleSheet\n")
                                stringBuilder.append(
                                        "Use configured Google Sheet headers. Do not pass sheet/table name in google mode.\n"
                                )
                                stringBuilder.append(
                                        "Only use keys that are present in the configured Google Sheet or template-defined fields.\n"
                                )
                        } else if (linkedWriteSheet.isNotBlank()) {
                                stringBuilder.append(
                                        "Default linked local write sheet is '$linkedWriteSheet'. If user wants normal save/write, do not mention sheet/table name; the system will write there automatically.\n"
                                )
                                stringBuilder.append(
                                        "Only use configured allowed write fields for this linked sheet. Do not invent or add new columns there.\n"
                                )
                                stringBuilder.append(
                                        "If user explicitly wants a different table, then use:\n[WRITE_SHEET: sheet=Insurance Leads; policy_id=ABC123; premium=5000]\n"
                                )
                        } else {
                                stringBuilder.append(
                                        "For custom target table use:\n[WRITE_SHEET: sheet=Insurance Leads; policy_id=ABC123; premium=5000]\n"
                                )
                        }
                        if (writeMode != AIAgentSettingsManager.SHEET_WRITE_MODE_GOOGLE &&
                                        linkedWriteSheet.isBlank()
                        ) {
                                stringBuilder.append(
                                        "New field names become new sheet columns automatically.\n"
                                )
                        } else {
                                stringBuilder.append(
                                        "Do not invent new columns in Google sheet mode; use existing headers only.\n"
                                )
                        }
                        stringBuilder.append(
                                "Only write explicit user-provided facts. Never invent values.\n"
                        )
                        stringBuilder.append(
                                "After command, continue normal human-readable response.\n"
                        )
                        stringBuilder.append("\n")
                }
        }

        private fun resolveCustomWriteFieldSchema(): List<CustomWriteFieldSchema> {
                val parsed = parseCustomWriteFieldSchema(settingsManager.customTemplateWriteFieldSchema)
                return if (parsed.isNotEmpty()) {
                        parsed
                } else {
                        settingsManager.customTemplateWriteSheetColumns
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .map { rawName -> CustomWriteFieldSchema(name = rawName) }
                }
        }

        private fun parseCustomWriteFieldSchema(raw: String): List<CustomWriteFieldSchema> {
                return try {
                        val arr = JSONArray(raw)
                        val out = mutableListOf<CustomWriteFieldSchema>()
                        for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                val name = obj.optString("name").trim()
                                if (name.isNotBlank()) {
                                        out.add(
                                                CustomWriteFieldSchema(
                                                        name = name,
                                                        type = normalizeWriteFieldType(obj.optString("type").trim())
                                                )
                                        )
                                }
                        }
                        out
                } catch (_: Exception) {
                        emptyList()
                }
        }

        private fun normalizeWriteFieldType(raw: String): String {
                return if (raw.isBlank()) "Text" else raw
        }

        private data class CustomWriteFieldSchema(
                val name: String,
                val type: String = "Text"
        )

                private fun appendCustomTemplateProfile(stringBuilder: StringBuilder, senderPhone: String) {
                if (!isCustomTemplateActive()) return

                val templateName =
                        settingsManager.customTemplateName.trim().ifBlank { "Custom AI Template" }
                val goal = settingsManager.customTemplateGoal.trim()
                val tone = settingsManager.customTemplateTone.trim()
                val customInstructions = settingsManager.customTemplateInstructions.trim()

                stringBuilder.append("\n[CUSTOM TEMPLATE PROFILE]\n")
                stringBuilder.append("Template Name: $templateName\n")
                if (goal.isNotBlank()) {
                        stringBuilder.append("Primary Goal: $goal\n")
                }
                if (tone.isNotBlank()) {
                        stringBuilder.append("Tone: $tone\n")
                }
                if (customInstructions.isNotBlank()) {
                        stringBuilder.append("Custom Instructions:\n")
                        stringBuilder.append("$customInstructions\n")
                }

                val businessKnowledge =
                        com.message.bulksend.autorespond.ai.ui.customai.CustomBusinessKnowledgeCodec
                                .fromJson(settingsManager.customTemplateBusinessKnowledgeJson)
                if (businessKnowledge.hasContent()) {
                        stringBuilder.append("Business Knowledge:\n")
                        if (businessKnowledge.businessName.isNotBlank()) {
                                stringBuilder.append("- Business Name: ${businessKnowledge.businessName}\n")
                        }
                        if (businessKnowledge.userName.isNotBlank()) {
                                stringBuilder.append("- Owner/User Name: ${businessKnowledge.userName}\n")
                        }
                        if (businessKnowledge.businessDetails.isNotBlank()) {
                                stringBuilder.append("- Business Details:\n")
                                stringBuilder.append("${businessKnowledge.businessDetails}\n")
                        }
                        val faqItems =
                                businessKnowledge.faqs.filter {
                                        it.question.isNotBlank() && it.answer.isNotBlank()
                                }
                        if (faqItems.isNotEmpty()) {
                                stringBuilder.append("- FAQs:\n")
                                faqItems.forEachIndexed { index, faq ->
                                        stringBuilder.append("  ${index + 1}. Q: ${faq.question}\n")
                                        stringBuilder.append("     A: ${faq.answer}\n")
                                }
                        }
                        stringBuilder.append(
                                "Use this business knowledge and FAQ data to answer side questions accurately before asking for more clarification.\n"
                        )
                }

                stringBuilder.append("Runtime Flags:\n")
                stringBuilder.append(
                        "- Native Tool Calling: ${if (settingsManager.customTemplateNativeToolCallingEnabled) "ON" else "OFF"}\n"
                )
                stringBuilder.append(
                        "- Continuous Autonomous Mode: ${if (settingsManager.customTemplateContinuousAutonomousEnabled) "ON" else "OFF"}\n"
                )
                if (settingsManager.customTemplateContinuousAutonomousEnabled) {
                        stringBuilder.append(
                                "- Autonomous Silence Gap: ${settingsManager.customTemplateAutonomousSilenceGapMinutes}m\n"
                        )
                        stringBuilder.append(
                                "- Autonomous Max Nudges/Day: ${settingsManager.customTemplateAutonomousMaxNudgesPerDay}\n"
                        )
                }

                val needSnippet = needDiscoveryManager.buildContextSnippet(senderPhone)
                if (needSnippet.isNotBlank()) {
                        stringBuilder.append("\n")
                        stringBuilder.append(needSnippet)
                        stringBuilder.append("\n")
                }

                stringBuilder.append("Goal-First Conversation Rules:\n")
                stringBuilder.append("- Primary objective is to move user toward goal naturally.\n")
                stringBuilder.append(
                        "- Steps are internal guidance for the agent, not scripted user-facing flow.\n"
                )
                stringBuilder.append(
                        "- Reply human-like and ask at most one relevant clarification when needed.\n"
                )

                stringBuilder.append("Enabled Tools:\n")
                stringBuilder.append(
                        "- Payment: ${if (settingsManager.customTemplateEnablePaymentTool) "ON" else "OFF"}\n"
                )
                stringBuilder.append(
                        "- Payment Verification: ${if (settingsManager.customTemplateEnablePaymentVerificationTool) "ON" else "OFF"}\n"
                )
                stringBuilder.append(
                        "- Documents: ${if (settingsManager.customTemplateEnableDocumentTool) "ON" else "OFF"}\n"
                )
                stringBuilder.append(
                        "- Agent Forms: ${if (settingsManager.customTemplateEnableAgentFormTool) "ON" else "OFF"}\n"
                )
                stringBuilder.append(
                        "- Agent Speech: ${if (settingsManager.customTemplateEnableSpeechTool) "ON" else "OFF"}\n"
                )
                stringBuilder.append(
                        "- Sheet Read: ${if (settingsManager.customTemplateEnableSheetReadTool) "ON" else "OFF"}\n"
                )
                stringBuilder.append(
                        "- Sheet Write: ${if (settingsManager.customTemplateEnableSheetWriteTool) "ON" else "OFF"}\n"
                )
                stringBuilder.append(
                        "- Autonomous Catalogue Send: ${if (settingsManager.customTemplateEnableAutonomousCatalogueSend) "ON" else "OFF"}\n"
                )
                if (settingsManager.customTemplateSheetFolderName.isNotBlank()) {
                        stringBuilder.append(
                                "Sheet Folder: ${settingsManager.customTemplateSheetFolderName}\n"
                        )
                }
                stringBuilder.append(
                        "Use only enabled tools. Never output tool commands for disabled tools.\n\n"
                )
        }

        private fun appendCustomTaskContext(
                stringBuilder: StringBuilder,
                senderPhone: String,
                incomingMessage: String,
                userProfile: UserProfile?
        ) {
                if (!isCustomTemplateActive()) return
                if (settingsManager.customTemplatePromptMode != AIAgentSettingsManager.PROMPT_MODE_STEP_FLOW) {
                        return
                }
                if (!settingsManager.customTemplateTaskModeEnabled) {
                        return
                }

                val sessionKey = senderPhone.ifBlank { "unknown_user" }
                val existingSession = taskManager.getSession(sessionKey)
                if (
                        existingSession?.status == AgentTaskSessionStatus.COMPLETED &&
                                shouldRestartCompletedSession(incomingMessage)
                ) {
                        // Returning user with a new requirement should start fresh flow.
                        taskManager.restartWorkflowForPhone(sessionKey)
                }

                val knownDetails = buildKnownDetailMap(senderPhone, userProfile)
                autoAdvancePastPrefilledSteps(
                        senderPhone = sessionKey,
                        knownDetails = knownDetails
                )

                val context = taskEngine.buildStepRuntimeContext(sessionKey)
                if (!context.isTaskModeAvailable || context.currentTask == null) {
                        stringBuilder.append("[STEP FLOW TASK MODE]\n")
                        stringBuilder.append("Task Mode is ON but no active steps are configured.\n")
                        stringBuilder.append("Fallback to normal custom prompt behavior.\n\n")
                        return
                }

                val task = context.currentTask
                val templateGoal = settingsManager.customTemplateGoal.trim()
                var repeatGuardLimitReached = false
                stringBuilder.append("[STEP FLOW TASK MODE]\n")
                stringBuilder.append("Mode: STEP_FLOW\n")
                stringBuilder.append("Session Status: ${context.sessionStatus.name}\n")
                stringBuilder.append("Current Step: ${context.currentStep}/${context.totalSteps}\n")
                stringBuilder.append("Step Title: ${task.title}\n")
                if (task.goal.isNotBlank()) {
                        stringBuilder.append("Step Goal: ${task.goal}\n")
                }
                if (templateGoal.isNotBlank()) {
                        stringBuilder.append("Primary Goal: $templateGoal\n")
                }
                stringBuilder.append("Step Instruction: ${task.instruction}\n")
                if (task.followUpQuestion.isNotBlank()) {
                        stringBuilder.append("Suggested Clarifying Question (internal): ${task.followUpQuestion}\n")
                }

                if (settingsManager.customTemplateRepeatCounterEnabled) {
                        val repeatLimit = settingsManager.customTemplateRepeatCounterLimit
                        if (repeatLimit > 0) {
                                val repeatGuardResult =
                                        taskManager.trackStepRepeatAndCheckThreshold(
                                                phoneNumber = sessionKey,
                                                stepOrder = task.stepOrder,
                                                threshold = repeatLimit
                                        )
                                repeatGuardLimitReached = repeatGuardResult.limitReached
                                stringBuilder.append("[STEP REPEAT COUNTER]\n")
                                stringBuilder.append(
                                        "Repeat Count: ${repeatGuardResult.repeatCount}/$repeatLimit\n"
                                )
                                if (repeatGuardResult.limitReached) {
                                        stringBuilder.append(
                                                "Status: LIMIT_REACHED - prioritize resolving current step quickly.\n"
                                        )
                                } else {
                                        stringBuilder.append("Status: WITHIN_LIMIT\n")
                                }

                                if (
                                                repeatGuardResult.shouldAlertOwner &&
                                                        settingsManager.customTemplateRepeatCounterOwnerNotifyEnabled
                                ) {
                                        val ownerPhoneOverride =
                                                settingsManager.customTemplateRepeatCounterOwnerPhone
                                                        .trim()
                                        CoroutineScope(Dispatchers.IO).launch {
                                                taskOwnerAlertManager.alertOwnerIfPossible(
                                                        customerPhone = senderPhone.trim(),
                                                        task = task,
                                                        repeatCount = repeatGuardResult.repeatCount,
                                                        limit = repeatLimit,
                                                        ownerPhoneOverride = ownerPhoneOverride
                                                )
                                        }
                                }
                        } else {
                                stringBuilder.append("[STEP REPEAT COUNTER]\n")
                                stringBuilder.append(
                                        "Enabled but limit is not set. Set Repeat Limit in Custom Template settings.\n"
                                )
                        }
                }

                val selectedStepTools = AgentTaskToolRegistry.normalizeToolIds(task.allowedTools.orEmpty())
                val selectedAgentFormTemplateKey = task.agentFormTemplateKey.trim()
                val isAgentFormStepConfigured = selectedAgentFormTemplateKey.isNotBlank()
                val stepSelectedAllowedTools =
                        if (selectedStepTools.isEmpty()) {
                                emptyList()
                        } else {
                                selectedStepTools.filter { toolId ->
                                        val enabled = AgentTaskToolRegistry.isEnabledForTemplate(toolId, settingsManager)
                                        if (!enabled) {
                                                false
                                        } else if (
                                                        toolId == AgentTaskToolRegistry.SEND_AGENT_FORM &&
                                                                !isAgentFormStepConfigured
                                        ) {
                                                // Do not allow SEND_AGENT_FORM without explicit form key selection.
                                                false
                                        } else {
                                                true
                                        }
                                }
                        }
                val effectiveAllowedTools = buildList {
                        addAll(stepSelectedAllowedTools)
                        if (settingsManager.customTemplateEnableSheetWriteTool) {
                                add(AgentTaskToolRegistry.WRITE_SHEET)
                        }
                }.distinct()
                val disabledSelectedTools =
                        selectedStepTools.filterNot {
                                val enabled = AgentTaskToolRegistry.isEnabledForTemplate(it, settingsManager)
                                if (!enabled) {
                                        true
                                } else {
                                        it == AgentTaskToolRegistry.SEND_AGENT_FORM &&
                                                !isAgentFormStepConfigured
                                }
                        }

                stringBuilder.append("[STEP TOOL ALLOWLIST]\n")
                if (selectedStepTools.isEmpty()) {
                        stringBuilder.append(
                                "Selection Mode: none selected\n"
                        )
                } else {
                        stringBuilder.append("Selection Mode: custom selection from step editor\n")
                }

                if (effectiveAllowedTools.isEmpty()) {
                        stringBuilder.append("Allowed Tools: NONE\n")
                        stringBuilder.append("Do not output any tool command for this step.\n")
                } else {
                        stringBuilder.append("Allowed Tools:\n")
                        effectiveAllowedTools.forEach { toolId ->
                                val definition = AgentTaskToolRegistry.getDefinition(toolId)
                                if (definition != null) {
                                        val commandHint =
                                                if (
                                                                toolId == AgentTaskToolRegistry.SEND_AGENT_FORM &&
                                                                        isAgentFormStepConfigured
                                                ) {
                                                        "[SEND_AGENT_FORM: $selectedAgentFormTemplateKey]"
                                                } else {
                                                        definition.commandHint
                                                }
                                        stringBuilder.append(
                                                "- ${definition.label} (${definition.id}) -> $commandHint\n"
                                        )
                                } else if (toolId == AgentTaskToolRegistry.WRITE_SHEET) {
                                        stringBuilder.append(
                                                "- Write Sheet (${AgentTaskToolRegistry.WRITE_SHEET}) -> [WRITE_SHEET: key=value; key2=value2] OR [SHEET_SELECT: {...}] / [SHEET_AGG: {...}] / [SHEET_PIVOT: {...}] / [SHEET_UPSERT: {...}] / [SHEET_BULK_UPSERT: {...}]\n"
                                        )
                                }
                        }
                }

                if (disabledSelectedTools.isNotEmpty()) {
                        stringBuilder.append(
                                "Selected but currently disabled by template switches:\n"
                        )
                        disabledSelectedTools.forEach { toolId ->
                                stringBuilder.append("- ${AgentTaskToolRegistry.labelFor(toolId)} ($toolId)\n")
                        }
                }

                if (selectedStepTools.contains(AgentTaskToolRegistry.SEND_AGENT_FORM)) {
                        stringBuilder.append("[STEP AGENT FORM]\n")
                        if (isAgentFormStepConfigured) {
                                stringBuilder.append("Selected Template Key: $selectedAgentFormTemplateKey\n")
                                stringBuilder.append(
                                        "If form send is needed, use exactly: [SEND_AGENT_FORM: $selectedAgentFormTemplateKey]\n"
                                )
                                stringBuilder.append("Do not use any other template key in this step.\n")
                        } else {
                                stringBuilder.append(
                                        "Form key is not selected in step editor, so SEND_AGENT_FORM is blocked for this step.\n"
                                )
                        }
                }

                stringBuilder.append("[KNOWN CUSTOMER DETAILS]\n")
                knownDetails.forEach { (field, value) ->
                        if (value.isNotBlank()) {
                                stringBuilder.append("- $field: AVAILABLE ($value)\n")
                        } else {
                                stringBuilder.append("- $field: MISSING\n")
                        }
                }

                val requiredFields =
                        detectLikelyRequiredFields(
                                text = listOf(
                                                task.title,
                                                task.goal,
                                                task.instruction,
                                                task.followUpQuestion,
                                                incomingMessage
                                        )
                                        .joinToString(" ")
                        )
                if (requiredFields.isNotEmpty()) {
                        stringBuilder.append("[STEP REQUIRED FIELD STATUS]\n")
                        requiredFields.forEach { required ->
                                val mappedKey = normalizeRequiredFieldKey(required)
                                val value = knownDetails[mappedKey].orEmpty()
                                val status = if (value.isNotBlank()) "AVAILABLE" else "MISSING"
                                stringBuilder.append("- $mappedKey: $status\n")
                        }
                }

                stringBuilder.append("Rules:\n")
                stringBuilder.append("- Steps are internal planning state only. Never tell user step numbers, completion markers, or workflow status.\n")
                stringBuilder.append("- Reply naturally and human-like while keeping responses concise.\n")
                stringBuilder.append("- If user asks side question, answer first and then smoothly continue toward the goal.\n")
                stringBuilder.append("- Step tool allowlist overrides general tool usage for this step.\n")
                stringBuilder.append("- Use only tools listed under [STEP TOOL ALLOWLIST].\n")
                stringBuilder.append("- Never output commands for tools not allowed in current step.\n")
                stringBuilder.append(
                        "- Decide step completion from conversation state, collected details, and explicit confirmations.\n"
                )
                stringBuilder.append(
                        "- Use [TASK_STEP_COMPLETE:${task.stepOrder}] only as internal state marker and never expose it in user-facing text.\n"
                )
                stringBuilder.append("- Do not jump to next step until current step is truly complete.\n\n")
                stringBuilder.append("- If a required field is AVAILABLE, treat it as pre-filled and do not ask again.\n")
                stringBuilder.append("- Ask only for MISSING fields.\n\n")

                if (context.currentStep >= context.totalSteps) {
                        stringBuilder.append("[FINAL GOAL CHECK]\n")
                        if (templateGoal.isNotBlank()) {
                                stringBuilder.append("Primary Goal to verify: $templateGoal\n")
                        }
                        if (task.goal.isNotBlank()) {
                                stringBuilder.append("Final Step Goal to verify: ${task.goal}\n")
                        }
                        stringBuilder.append(
                                "Before marking final step complete, verify goal achievement from explicit user confirmation or clear evidence.\n"
                        )
                        stringBuilder.append(
                                "If goal is not achieved yet, continue naturally with one focused question or tool action instead of closing.\n\n"
                        )
                }

                if (repeatGuardLimitReached) {
                        stringBuilder.append(
                                "- Repeat limit reached for this step. Avoid repeating same question; close or resolve the step decisively.\n\n"
                        )
                }
        }

        private fun autoAdvancePastPrefilledSteps(
                senderPhone: String,
                knownDetails: Map<String, String>
        ) {
                val activeTasks = taskManager.getActiveTasks()
                if (activeTasks.isEmpty()) return

                var guard = 0
                while (guard < activeTasks.size) {
                        val currentTask = taskManager.getCurrentTask(senderPhone) ?: break
                        val requiredFields =
                                detectLikelyRequiredFields(
                                        text =
                                                listOf(
                                                                currentTask.title,
                                                                currentTask.goal,
                                                                currentTask.instruction,
                                                                currentTask.followUpQuestion
                                                        )
                                                        .joinToString(" ")
                                )

                        // Only auto-skip data-collection style steps where all required fields are already known.
                        if (requiredFields.isEmpty()) break
                        val allAvailable =
                                requiredFields
                                        .map(::normalizeRequiredFieldKey)
                                        .all { key -> knownDetails[key].orEmpty().isNotBlank() }
                        if (!allAvailable) break

                        val result =
                                taskManager.completeCurrentStepAndAdvance(
                                        phoneNumber = senderPhone,
                                        completedStepOrder = currentTask.stepOrder
                                )
                        if (result.isWorkflowCompleted) break
                        guard++
                }
        }

        private fun shouldRestartCompletedSession(incomingMessage: String): Boolean {
                val normalized = incomingMessage.trim().lowercase(Locale.ROOT)
                if (normalized.isBlank()) return false

                val passiveMessages =
                        setOf(
                                "ok",
                                "okay",
                                "thanks",
                                "thank you",
                                "thx",
                                "done",
                                "haan",
                                "han",
                                "yes",
                                "no"
                        )
                if (normalized in passiveMessages) return false
                return true
        }

        private fun normalizeRequiredFieldKey(required: String): String {
                return when (required.lowercase(Locale.ROOT)) {
                        "pin", "postal", "postal code" -> "Pincode"
                        else -> required
                }
        }

        private fun buildKnownDetailMap(
                senderPhone: String,
                userProfile: UserProfile?
        ): Map<String, String> {
                val customMap = parseCustomDataMap(userProfile?.customData)
                fun pick(vararg keys: String): String {
                        keys.forEach { key ->
                                val v = customMap[key.lowercase(Locale.ROOT)].orEmpty().trim()
                                if (v.isNotBlank()) return v
                        }
                        return ""
                }

                val name = userProfile?.name.orEmpty().trim()
                val email = userProfile?.email.orEmpty().trim()
                val address = userProfile?.address.orEmpty().trim()
                val city = pick("city", "town")
                val state = pick("state", "province")
                val pincode = pick("pincode", "pin", "postal_code", "zipcode")

                return linkedMapOf(
                        "Name" to name,
                        "Phone" to senderPhone.trim(),
                        "Email" to email,
                        "Address" to address,
                        "City" to city,
                        "State" to state,
                        "Pincode" to pincode
                )
        }

        private fun parseCustomDataMap(customDataJson: String?): Map<String, String> {
                if (customDataJson.isNullOrBlank()) return emptyMap()
                return try {
                        val json = org.json.JSONObject(customDataJson)
                        val out = mutableMapOf<String, String>()
                        val keys = json.keys()
                        while (keys.hasNext()) {
                                val key = keys.next()
                                val value = json.optString(key).trim()
                                if (value.isNotBlank()) {
                                        out[key.lowercase(Locale.ROOT)] = value
                                }
                        }
                        out
                } catch (_: Exception) {
                        emptyMap()
                }
        }

        private fun shouldIncludeGoogleCalendarContext(): Boolean {
                if (!settingsManager.isAgentEnabled) return false
                if (!settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true)) return false
                return settingsManager.customTemplateEnableGoogleCalendarTool
        }

        private fun shouldIncludeGoogleGmailContext(): Boolean {
                if (!settingsManager.isAgentEnabled) return false
                if (!settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true)) return false
                return settingsManager.customTemplateEnableGoogleGmailTool
        }

        private fun buildGmailHistorySummary(historyItems: JSONArray): String {
                if (historyItems.length() == 0) return ""

                return buildString {
                        for (index in 0 until historyItems.length()) {
                                val item = historyItems.optJSONObject(index) ?: continue
                                val trackingId = item.optString("trackingId")
                                val recipient =
                                        item.optString("recipientEmail").ifBlank {
                                                item.optString("recipientName")
                                        }.ifBlank { "unknown recipient" }
                                val subject =
                                        item.optString("subject").ifBlank { "(no subject)" }
                                val status = item.optString("status").ifBlank { "UNKNOWN" }
                                val openCount = item.optInt("openCount")
                                append("- ")
                                append(trackingId.ifBlank { "unknown-id" })
                                append(" | ")
                                append(recipient)
                                append(" | ")
                                append(subject)
                                append(" | ")
                                append(status)
                                append(" | opens=")
                                append(openCount)
                                append('\n')
                        }
                }.trim()
        }

        private fun detectLikelyRequiredFields(text: String): List<String> {
                val lower = text.lowercase(Locale.ROOT)
                val fields = linkedSetOf<String>()

                if (lower.contains("name")) fields.add("Name")
                if (lower.contains("phone") || lower.contains("mobile") || lower.contains("number")) {
                        fields.add("Phone")
                }
                if (lower.contains("email")) fields.add("Email")
                if (lower.contains("address")) fields.add("Address")
                if (lower.contains("city")) fields.add("City")
                if (lower.contains("state")) fields.add("State")
                if (
                        lower.contains("pincode") ||
                                lower.contains("pin code") ||
                                lower.contains("postal") ||
                                lower.contains("zip")
                ) {
                        fields.add("Pincode")
                }

                return fields.toList()
        }
}







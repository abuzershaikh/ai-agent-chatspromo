package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import android.util.Log
import com.message.bulksend.aiagent.tools.ecommerce.PaymentFlowMode
import com.message.bulksend.aiagent.tools.ecommerce.PaymentFlowModeManager
import com.message.bulksend.aiagent.tools.ecommerce.RazorPaymentManager
import com.message.bulksend.product.ProductRepository
import java.util.Locale

/**
 * Handler for Razorpay payment link generation.
 *
 * Primary path:
 * - Detects [GENERATE-PAYMENT-LINK: amount, description] in AI response
 * - Generates and injects payment link
 *
 * Recovery path:
 * - If AI gives a refusal for an obvious payment-link request, recover locally
 *   by generating the link directly (or asking only for amount when missing).
 */
class RazorpayLinkHandler(private val context: Context) : MessageHandler {
    private val flowModeManager = PaymentFlowModeManager(context)

    override fun getPriority(): Int =
            45 // Execute after PaymentDetection (40) and before Catalogue (50)

    override suspend fun handle(
            context: Context,
            message: String,
            response: String,
            senderPhone: String,
            senderName: String
    ): HandlerResult {
        return try {
            val commandPattern =
                    Regex(
                            "\\[GENERATE-PAYMENT-LINK:\\s*([0-9.]+)\\s*,\\s*(.+?)\\]",
                            RegexOption.IGNORE_CASE
                    )
            val manager = RazorPaymentManager(context)

            val match = commandPattern.find(response)
            if (match == null) {
                Log.d("RazorpayLinkHandler", "No command found, checking fallback recovery")
                return handleRecoveryIfNeeded(
                        manager = manager,
                        userMessage = message,
                        aiResponse = response,
                        senderPhone = senderPhone,
                        senderName = senderName
                )
            }

            if (!flowModeManager.canUseRazorpay()) {
                return HandlerResult(
                    success = true,
                    modifiedResponse =
                        "Abhi payment mode QR/UPI/Bank par hai. Razorpay link enable karne ke liye Payment Verification me mode ko Razorpay karein.",
                    shouldStopChain = true
                )
            }

            val amountStr = match.groupValues[1].trim()
            val description = match.groupValues[2].trim()
            val amount = amountStr.toDoubleOrNull()

            if (amount == null) {
                return HandlerResult(
                        success = false,
                        modifiedResponse =
                                response.replace(match.value, "(Error: Invalid amount provided)"),
                        metadata =
                                mapOf(
                                        "tool_actions" to
                                                listOf(
                                                        "GENERATE-PAYMENT-LINK:$amountStr:${description}:FAILED_INVALID_AMOUNT"
                                                ),
                                        "tool_action_count" to 1
                                )
                )
            }

            val paymentLink =
                    manager.createPaymentLink(
                            amount = amount,
                            description = description,
                            customerName = senderName,
                            customerPhone = senderPhone
                    )

            val modifiedResponse =
                    if (paymentLink != null) {
                        flowModeManager.setMode(PaymentFlowMode.RAZORPAY)
                        response.replace(match.value, paymentLink)
                    } else {
                        response.replace(
                                match.value,
                                "(Could not generate payment link at this time. Please try again later.)"
                        )
                    }

            HandlerResult(
                    success = true,
                    modifiedResponse = modifiedResponse.replace(Regex("\\n{3,}"), "\n\n").trim(),
                    metadata =
                            mapOf(
                                    "tool_actions" to
                                            listOf(
                                                    "GENERATE-PAYMENT-LINK:$amount:$description:${if (paymentLink != null) "SUCCESS" else "FAILED"}"
                                            ),
                                    "tool_action_count" to 1
                            )
            )
        } catch (e: Exception) {
            Log.e("RazorpayLinkHandler", "Error: ${e.message}", e)
            HandlerResult(success = false)
        }
    }

    private suspend fun handleRecoveryIfNeeded(
            manager: RazorPaymentManager,
            userMessage: String,
            aiResponse: String,
            senderPhone: String,
            senderName: String
    ): HandlerResult {
        if (isPaymentLinkIntent(userMessage) && !flowModeManager.canUseRazorpay()) {
            return HandlerResult(
                    success = true,
                    modifiedResponse =
                            "Abhi payment mode QR/UPI/Bank par hai. Razorpay link enable karne ke liye mode ko Razorpay karein.",
                    shouldStopChain = true
            )
        }

        if (!shouldRecoverFromRefusal(userMessage, aiResponse, manager)) {
            return HandlerResult(success = true)
        }

        var amount = extractAmountFromMessage(userMessage)
        var description = extractDescriptionFromMessage(userMessage)

        if (amount == null) {
            val productRepository = ProductRepository(context)
            val productQuery = if (description != DEFAULT_DESCRIPTION) description else userMessage
            val matchedProduct = productRepository.searchProducts(productQuery).firstOrNull { it.price > 0 }
            if (matchedProduct != null) {
                amount = matchedProduct.price
                description = matchedProduct.name
            }
        }

        if (amount == null) {
            val recentLinks = manager.getRecentPaymentLinksForUser(senderPhone, limit = 5)
            val shouldReuseLatest = shouldReuseLastPaymentAmount(userMessage)

            if (recentLinks.size == 1 || (recentLinks.isNotEmpty() && shouldReuseLatest)) {
                val latestLink = recentLinks.first()
                if (latestLink.amount > 0) {
                    amount = latestLink.amount
                    if (description == DEFAULT_DESCRIPTION && latestLink.description.isNotBlank()) {
                        description = latestLink.description
                    }
                }
            }
        }

        if (amount == null) {
            return HandlerResult(
                    success = true,
                    modifiedResponse =
                            "Payment link bhejne ke liye amount bata dijiye (example: Rs 500).",
                    shouldStopChain = true
            )
        }

        val paymentLink =
                manager.createPaymentLink(
                        amount = amount,
                        description = description,
                        customerName = senderName,
                        customerPhone = senderPhone
                )

        return if (paymentLink != null) {
            flowModeManager.setMode(PaymentFlowMode.RAZORPAY)
            HandlerResult(
                    success = true,
                    modifiedResponse = paymentLink,
                    shouldStopChain = true,
                    metadata =
                            mapOf(
                                    "tool_actions" to
                                            listOf(
                                                    "GENERATE-PAYMENT-LINK:${formatAmount(amount)}:$description:SUCCESS_RECOVERY"
                                            ),
                                    "tool_action_count" to 1
                            )
            )
        } else {
            HandlerResult(
                    success = true,
                    modifiedResponse =
                            "Payment link abhi generate nahi ho paaya. Please thodi der baad try karein.",
                    shouldStopChain = true,
                    metadata =
                            mapOf(
                                    "tool_actions" to
                                            listOf(
                                                    "GENERATE-PAYMENT-LINK:${formatAmount(amount)}:$description:FAILED_RECOVERY"
                                            ),
                                    "tool_action_count" to 1
                            )
            )
        }
    }

    private fun shouldRecoverFromRefusal(
            userMessage: String,
            aiResponse: String,
            manager: RazorPaymentManager
    ): Boolean {
        if (!manager.isConfigured()) return false
        if (!isPaymentLinkIntent(userMessage)) return false
        return looksLikeRefusal(aiResponse)
    }

    private fun isPaymentLinkIntent(message: String): Boolean {
        val lower = message.lowercase()
        val hasLinkRequest =
                lower.contains("payment link") ||
                        lower.contains("pay link") ||
                        lower.contains("link send") ||
                        lower.contains("send link") ||
                        lower.contains("link bhej")
        val hasPaymentContext =
                lower.contains("payment") ||
                        lower.contains("pay") ||
                        lower.contains("razorpay")
        return hasLinkRequest || (hasPaymentContext && (lower.contains("link") || lower.contains("bhej") || lower.contains("send")))
    }

    private fun looksLikeRefusal(response: String): Boolean {
        val lower = response.lowercase()
        val refusalSignals =
                listOf(
                        "cannot generate",
                        "can't generate",
                        "unable to",
                        "limitations",
                        "i am an ai assistant",
                        "main ek ai assistant",
                        "nahi kar sakta",
                        "nahi bhej sakta",
                        "content blocked by gemini safety filters",
                        "blocked by safety",
                        "safety filter",
                        "safety filters",
                        "prohibited_content",
                        "candidate was blocked due to safety",
                        "response blocked"
                )
        return refusalSignals.any { lower.contains(it) }
    }

    private fun shouldReuseLastPaymentAmount(message: String): Boolean {
        val lower = message.lowercase(Locale.ROOT)
        return lower.contains("same amount") ||
                lower.contains("same payment") ||
                lower.contains("same link") ||
                lower.contains("dobara") ||
                lower.contains("phir se") ||
                lower.contains("again")
    }

    private fun extractAmountFromMessage(message: String): Double? {
        val contextualAmount =
                Regex(
                                "(?i)(?:₹|rs\\.?|inr|amount|pay|payment)\\s*[:\\-]?\\s*(\\d{1,6}(?:\\.\\d{1,2})?)"
                        )
                        .find(message)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toDoubleOrNull()
        if (contextualAmount != null && contextualAmount > 0) return contextualAmount

        return Regex("\\b(\\d{1,5}(?:\\.\\d{1,2})?)\\b")
                .findAll(message)
                .mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }
                .firstOrNull { it > 0 }
    }

    private fun extractDescriptionFromMessage(message: String): String {
        val cleaned =
                message
                        .replace(
                                Regex(
                                        "(?i)(payment|pay|link|send|bhej|karna|kar|hai|please|pls|ka|ki|ke|ko)"
                                ),
                                " "
                        )
                        .replace(Regex("(₹|rs\\.?|inr)?\\s*\\d+(?:\\.\\d{1,2})?"), " ")
                        .replace(Regex("\\s{2,}"), " ")
                        .trim()
        return if (cleaned.length >= 3) cleaned.take(60) else DEFAULT_DESCRIPTION
    }

    private fun formatAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            amount.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", amount)
        }
    }

    companion object {
        private const val DEFAULT_DESCRIPTION = "Payment"
    }
}

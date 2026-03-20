package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import com.message.bulksend.aiagent.tools.ecommerce.PaymentMethodAIIntegration

/**
 * Handler for detecting payment send requests in AI responses
 * Detects [SEND_PAYMENT: ID] command
 */
class PaymentDetectionHandler(
    private val paymentIntegration: PaymentMethodAIIntegration
) : MessageHandler {

    override fun getPriority(): Int = 40 // Execute before Catalogue (50)

    override suspend fun handle(
        context: Context,
        message: String,
        response: String,
        senderPhone: String,
        senderName: String
    ): HandlerResult {
        return try {
            android.util.Log.d("PaymentHandler", "Checking response for payment command")
            android.util.Log.d("PaymentHandler", "Response: $response")

            val commandPattern = Regex("\\[SEND_PAYMENT:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
            val matches =
                commandPattern.findAll(response)
                    .toList()
                    .distinctBy { it.groupValues[1].trim().lowercase() }

            if (matches.isEmpty()) {
                android.util.Log.d("PaymentHandler", "No payment command found in response")
                return HandlerResult(success = true)
            }

            var modifiedResponse = response
            val toolActions = mutableListOf<String>()

            matches.forEach { match ->
                val methodId = match.groupValues[1].trim()
                android.util.Log.d("PaymentHandler", "Payment command detected for ID: $methodId")

                val result = paymentIntegration.sendPaymentMethod(senderPhone, senderName, methodId)
                android.util.Log.d(
                    "PaymentHandler",
                    "Send result: success=${result.success}, isMedia=${result.isMedia}, message=${result.message}"
                )

                toolActions +=
                    "SEND_PAYMENT:$methodId:${if (result.success) "SUCCESS" else "FAILED"}"

                modifiedResponse = modifiedResponse.replace(match.value, "").trim()

                if (result.success) {
                    if (result.isMedia) {
                        android.util.Log.d("PaymentHandler", "Media (QR) sent successfully")
                    } else {
                        android.util.Log.d("PaymentHandler", "Text payment details generated")
                    }

                    // Common append path:
                    // - For UPI/Bank details: result.details contains payment text
                    // - For QR: result.details may contain verification instruction/link
                    if (!result.details.isNullOrBlank()) {
                        modifiedResponse =
                            if (modifiedResponse.isBlank()) {
                                result.details
                            } else {
                                "$modifiedResponse\n\n${result.details}"
                            }
                    }
                } else {
                    android.util.Log.e("PaymentHandler", "Failed to send payment: ${result.message}")
                    if (modifiedResponse.isBlank()) {
                        modifiedResponse = result.message
                    }
                }
            }

            if (toolActions.isEmpty()) {
                return HandlerResult(success = true)
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
            android.util.Log.e("PaymentHandler", "Error: ${e.message}", e)
            HandlerResult(success = false)
        }
    }
}

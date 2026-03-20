package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import com.message.bulksend.aiagent.tools.ecommerce.PaymentFlowModeManager
import com.message.bulksend.aiagent.tools.ecommerce.RazorPaymentManager
import java.util.Locale

/**
 * Razorpay Payment Status Check Handler
 *
 * Detects verification/status requests from user message and returns real payment
 * state from Razorpay. This avoids screenshot-based/manual verification flow.
 */
class RazorpayStatusCheckHandler(
    private val appContext: Context
) : MessageHandler {

    private val razorpayManager = RazorPaymentManager(appContext)
    private val flowModeManager = PaymentFlowModeManager(appContext)

    override suspend fun handle(
        context: Context,
        message: String,
        response: String,
        senderPhone: String,
        senderName: String
    ): HandlerResult {
        return try {
            if (!isVerificationRequest(message)) {
                android.util.Log.d("RazorpayStatusCheck", "Not a payment verification request")
                return HandlerResult(success = false)
            }

            if (!flowModeManager.canUseRazorpay()) {
                android.util.Log.d("RazorpayStatusCheck", "Razorpay mode disabled by payment mode setting")
                return HandlerResult(success = false)
            }

            android.util.Log.d("RazorpayStatusCheck", "Payment verification request detected")

            // If configuration is missing, handle this request explicitly instead of passing through.
            if (!razorpayManager.isConfigured()) {
                return HandlerResult(
                    success = true,
                    modifiedResponse =
                        "Payment verification abhi available nahi hai kyunki Razorpay settings configured nahi hain.",
                    shouldStopChain = true
                )
            }

            val resolution = PaymentLinkResolver.resolveForMessage(
                manager = razorpayManager,
                senderPhone = senderPhone,
                message = message,
                limit = 10
            )

            if (resolution.isAmbiguous) {
                return HandlerResult(
                    success = true,
                    modifiedResponse = PaymentLinkResolver.buildAmbiguityPrompt(resolution.ambiguousCandidates),
                    shouldStopChain = true
                )
            }

            val selectedLink = resolution.selectedLink
            if (selectedLink == null) {
                return HandlerResult(
                    success = true,
                    modifiedResponse =
                        """
                        Mujhe aapka koi recent payment link nahi mila.

                        Kya main aapke liye naya payment link generate kar du?
                        """.trimIndent(),
                    shouldStopChain = true
                )
            }

            var finalStatus = selectedLink.status.lowercase(Locale.ROOT)
            val apiStatus = razorpayManager.verifyPaymentStatusFromApi(selectedLink.id)
            if (apiStatus !in setOf("unknown_no_creds", "api_error", "exception")) {
                finalStatus = apiStatus.lowercase(Locale.ROOT)
            }

            val statusResponse = buildStatusResponse(
                status = finalStatus,
                amount = selectedLink.amount,
                description = selectedLink.description,
                shortUrl = selectedLink.shortUrl,
                linkId = selectedLink.id
            )

            HandlerResult(
                success = true,
                modifiedResponse = statusResponse,
                shouldStopChain = true
            )
        } catch (e: Exception) {
            android.util.Log.e("RazorpayStatusCheck", "Error: ${e.message}", e)
            HandlerResult(success = false)
        }
    }

    private fun isVerificationRequest(message: String): Boolean {
        val lower = message.lowercase(Locale.ROOT).trim()

        val directPhrases = listOf(
            "payment verify",
            "verify payment",
            "payment verification",
            "check payment",
            "payment check",
            "payment status",
            "status of payment",
            "mera payment",
            "payment hua",
            "payment ho gaya",
            "payment done",
            "razorpay verify",
            "razorpay check",
            "transaction status",
            "paid or not",
            "payment received"
        )
        if (directPhrases.any { lower.contains(it) }) return true

        val paymentWords = listOf(
            "payment",
            "pay",
            "razorpay",
            "transaction",
            "txn",
            "amount"
        )
        val verifyWords = listOf(
            "verify",
            "verification",
            "check",
            "status",
            "confirm",
            "confirmed",
            "done",
            "received",
            "receive",
            "hua",
            "ho gaya",
            "hogaya"
        )

        return paymentWords.any { lower.contains(it) } && verifyWords.any { lower.contains(it) }
    }

    private fun buildStatusResponse(
        status: String,
        amount: Double,
        description: String,
        shortUrl: String,
        linkId: String
    ): String {
        val amountText = formatAmount(amount)
        return when (status) {
            "paid" -> {
                """
                Payment verify ho gaya.

                Status: PAID
                Amount: Rs $amountText
                For: $description
                Link ID: $linkId
                """.trimIndent()
            }
            "created", "issued" -> {
                """
                Payment status abhi PENDING hai.

                Amount: Rs $amountText
                For: $description
                Link ID: $linkId
                Link: $shortUrl

                Agar payment already kiya hai to 2-3 minute baad dobara check karein.
                """.trimIndent()
            }
            "expired" -> {
                """
                Payment link EXPIRED ho chuka hai.

                Amount: Rs $amountText
                For: $description
                Link ID: $linkId

                Chahein to main naya payment link generate kar sakta hoon.
                """.trimIndent()
            }
            "cancelled" -> {
                """
                Payment status CANCELLED hai.

                Amount: Rs $amountText
                For: $description
                Link ID: $linkId

                Chahein to main naya payment link generate kar sakta hoon.
                """.trimIndent()
            }
            else -> {
                """
                Payment status: ${status.uppercase(Locale.ROOT)}

                Amount: Rs $amountText
                For: $description
                Link ID: $linkId
                """.trimIndent()
            }
        }
    }

    private fun formatAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            amount.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", amount)
        }
    }

    override fun getPriority(): Int = 1 // Highest priority - execute first
}

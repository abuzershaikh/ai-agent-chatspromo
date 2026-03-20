package com.message.bulksend.aiagent.tools.ecommerce

import android.content.Context
import android.util.Log
import com.message.bulksend.aiagent.tools.paymentverification.PaymentVerificationAIIntegration
import com.message.bulksend.autorespond.ai.document.AIAgentDocumentManager
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI Agent Payment Method Integration
 * Allows AI Agent to:
 * - List available payment methods
 * - Send payment methods to users (QR Codes as images, others as text)
 * - Get payment method details
 */
class PaymentMethodAIIntegration(private val context: Context) {

    companion object {
        const val TAG = "PaymentMethodAI"
    }

    private val paymentManager = PaymentMethodManager(context)
    private val aiDocumentManager = AIAgentDocumentManager(context)
    private val paymentVerifyIntegration = PaymentVerificationAIIntegration.getInstance(context)
    private val settingsManager = AIAgentSettingsManager(context)
    private val flowModeManager = PaymentFlowModeManager(context)

    /**
     * Get all payment methods formatted for AI context
     */
    suspend fun getPaymentMethodsListForAI(): String = withContext(Dispatchers.IO) {
        try {
            paymentManager.getPaymentMethodsForAI()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting payment methods list: ${e.message}")
            "Error loading payment methods."
        }
    }

    /**
     * Send payment method to user
     * - If QR Code: Sends the image
     * - If UPI/Custom/Text: Returns the text details so AI can send it as message
     */
    suspend fun sendPaymentMethod(
        phoneNumber: String,
        userName: String,
        methodId: String
    ): PaymentMethodResponse = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sending payment method: $methodId to $userName ($phoneNumber)")

            val method = paymentManager.getPaymentMethodById(methodId)
            if (method == null) {
                Log.e(TAG, "Payment method not found with ID: $methodId")
                return@withContext PaymentMethodResponse(
                    success = false,
                    message = "Payment method not found with ID: $methodId"
                )
            }

            Log.d(TAG, "Found payment method: ${method.name}, type: ${method.type}")

            if (!method.isEnabled) {
                Log.e(TAG, "Payment method is disabled: ${method.name}")
                return@withContext PaymentMethodResponse(
                    success = false,
                    message = "This payment method is currently disabled."
                )
            }

            if (method.type != PaymentMethodType.RAZORPAY && !flowModeManager.canUseManualMethods()) {
                return@withContext PaymentMethodResponse(
                    success = false,
                    message =
                        "Razorpay mode active hai. QR/UPI/Bank use karne ke liye Payment Verification screen me mode ko 'QR/UPI/Bank' karein."
                )
            }

            when (method.type) {
                PaymentMethodType.QR_CODE -> {
                    Log.d(TAG, "QR Code type detected")
                    if (method.qrCodeImagePath != null) {
                        val success = aiDocumentManager.sendImage(phoneNumber, userName, method.qrCodeImagePath)
                        if (success) {
                            val verificationNote =
                                buildVerificationNote(
                                    phoneNumber = phoneNumber,
                                    orderId = method.id,
                                    expectedName = method.name,
                                    expectedUpiId = method.upiId ?: "",
                                    expectedAmount = method.fixedPrice ?: 0.0
                                )
                            PaymentMethodResponse(
                                success = true,
                                message = "QR Code image sent successfully.",
                                isMedia = true,
                                details = verificationNote
                            ).also {
                                flowModeManager.setMode(PaymentFlowMode.MANUAL_QR_UPI_BANK)
                            }
                        } else {
                            Log.e(TAG, "Failed to send QR Code image")
                            PaymentMethodResponse(
                                success = false,
                                message = "Failed to send QR Code image."
                            )
                        }
                    } else {
                        Log.e(TAG, "QR Code image path is null")
                        PaymentMethodResponse(
                            success = false,
                            message = "QR Code image not found."
                        )
                    }
                }

                PaymentMethodType.UPI_ID -> {
                    Log.d(TAG, "UPI ID type detected")
                    val upiDetails = "UPI ID: ${method.upiId}"
                    val verificationNote =
                        buildVerificationNote(
                            phoneNumber = phoneNumber,
                            orderId = method.id,
                            expectedName = method.name,
                            expectedUpiId = method.upiId ?: "",
                            expectedAmount = method.fixedPrice ?: 0.0
                        )
                    val fullDetails = if (verificationNote != null) "$upiDetails\n\n$verificationNote" else upiDetails

                    PaymentMethodResponse(
                        success = true,
                        message = "Here is the UPI ID: ${method.upiId}",
                        isMedia = false,
                        details = fullDetails
                    ).also {
                        flowModeManager.setMode(PaymentFlowMode.MANUAL_QR_UPI_BANK)
                    }
                }

                PaymentMethodType.CUSTOM_GROUP -> {
                    Log.d(TAG, "Custom group type detected")
                    val detailsBuilder = StringBuilder()
                    detailsBuilder.append("${method.customGroupName}:\n")
                    method.customFields?.forEach { field ->
                        detailsBuilder.append("- ${field.fieldName}: ${field.fieldValue}\n")
                    }

                    val expectedCustomFields =
                        method.customFields
                            ?.associate { field -> field.fieldName to field.fieldValue }
                            ?: emptyMap()
                    val verificationNote =
                        buildVerificationNote(
                            phoneNumber = phoneNumber,
                            orderId = method.id,
                            expectedName = method.name,
                            expectedAmount = method.fixedPrice ?: 0.0,
                            customFieldValues = expectedCustomFields
                        )
                    if (verificationNote != null) {
                        detailsBuilder.append("\n")
                        detailsBuilder.append(verificationNote)
                    }

                    val detailsText = detailsBuilder.toString()
                    PaymentMethodResponse(
                        success = true,
                        message = detailsText,
                        isMedia = false,
                        details = detailsText
                    ).also {
                        flowModeManager.setMode(PaymentFlowMode.MANUAL_QR_UPI_BANK)
                    }
                }

                PaymentMethodType.RAZORPAY, PaymentMethodType.PAYPAL -> {
                    Log.d(TAG, "${method.type.displayName} not yet supported")
                    PaymentMethodResponse(
                        success = false,
                        message = "${method.type.displayName} is coming soon."
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending payment method: ${e.message}", e)
            PaymentMethodResponse(
                success = false,
                message = "Error: ${e.message}"
            )
        }
    }

    /**
     * Search payment methods by name
     */
    suspend fun searchPaymentMethods(query: String): String = withContext(Dispatchers.IO) {
        try {
            val allMethods = mutableListOf<PaymentMethod>()
            paymentManager.getEnabledPaymentMethods().collect { allMethods.addAll(it) }

            val matches = allMethods.filter {
                it.name.contains(query, ignoreCase = true) ||
                    (it.customGroupName?.contains(query, ignoreCase = true) == true)
            }

            if (matches.isEmpty()) {
                "No payment methods found matching '$query'."
            } else {
                val sb = StringBuilder("Found ${matches.size} payment methods:\n")
                matches.forEach { method ->
                    sb.append("- ${method.name} (ID: ${method.id}, Type: ${method.type.displayName})\n")
                }
                sb.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching payment methods: ${e.message}")
            "Error searching payment methods."
        }
    }

    /**
     * Get function call schema for AI Agent
     */
    fun getFunctionCallSchema(): String {
        return """
            Payment Method Functions:

            1. list_payment_methods()
               - Get all available enabled payment methods

            2. send_payment_method(phone_number, user_name, method_id)
               - Send a specific payment method to the user
               - If it's a QR code, it will be sent as an image
               - If it's text (UPI, Bank), the details will be returned for you to say

            3. search_payment_methods(query)
               - Find payment method by name
        """.trimIndent()
    }

    private suspend fun buildVerificationNote(
        phoneNumber: String,
        orderId: String = "",
        expectedName: String = "",
        expectedUpiId: String = "",
        expectedAmount: Double = 0.0,
        customFieldValues: Map<String, String> = emptyMap()
    ): String? {
        if (
            settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true) &&
                !settingsManager.customTemplateEnablePaymentVerificationTool
        ) {
            return null
        }

        return try {
            val link =
                paymentVerifyIntegration.generateVerificationLinkForPayment(
                    phoneNumber = phoneNumber,
                    orderId = orderId,
                    expectedName = expectedName,
                    expectedUpiId = expectedUpiId,
                    expectedAmount = expectedAmount,
                    customFieldValues = customFieldValues
                )
            if (link != null) {
                paymentVerifyIntegration.buildVerificationInstruction(link)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate payment verification link: ${e.message}")
            null
        }
    }
}

data class PaymentMethodResponse(
    val success: Boolean,
    val message: String,
    val isMedia: Boolean = false,
    val details: String? = null
)

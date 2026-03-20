package com.message.bulksend.aiagent.tools.paymentverification

import android.content.Context
import android.util.Log
import com.message.bulksend.aiagent.tools.ecommerce.PaymentMethod
import com.message.bulksend.aiagent.tools.ecommerce.PaymentMethodManager
import kotlinx.coroutines.flow.first

/**
 * AI Integration for Payment Verification
 * Automatically sends verification link after QR code is sent
 */
class PaymentVerificationAIIntegration(private val context: Context) {
    
    private val verificationManager = PaymentVerificationManager.getInstance(context)
    private val paymentMethodManager = PaymentMethodManager(context)
    
    companion object {
        private const val TAG = "PaymentVerifyAI"
        
        @Volatile
        private var INSTANCE: PaymentVerificationAIIntegration? = null
        
        fun getInstance(context: Context): PaymentVerificationAIIntegration {
            return INSTANCE ?: synchronized(this) {
                val instance = PaymentVerificationAIIntegration(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    /**
     * Check if payment verification is enabled
     */
    suspend fun isEnabled(): Boolean {
        val prefs = context.getSharedPreferences("payment_verification_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("send_verification_link_after_qr", false)
    }
    
    /**
     * Enable/Disable payment verification link sending
     */
    fun setEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences("payment_verification_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("send_verification_link_after_qr", enabled).apply()
        Log.d(TAG, "Payment verification link sending: $enabled")
    }
    
    /**
     * Generate verification link after QR is sent
     * Returns link if conditions are met, null otherwise
     */
    suspend fun generateVerificationLinkAfterQR(
        phoneNumber: String,
        orderId: String = "",
        qrWasSent: Boolean
    ): String? {
        // Check if feature is enabled
        if (!isEnabled()) {
            Log.d(TAG, "Payment verification link disabled")
            return null
        }
        
        // Check if QR was sent
        if (!qrWasSent) {
            Log.d(TAG, "QR was not sent, skipping verification link")
            return null
        }
        
        // Generate link
        val link = verificationManager.generateCustomerLink(phoneNumber, orderId)
        Log.d(TAG, "Generated verification link for $phoneNumber: $link")
        
        return link
    }
    
    /**
     * Get AI context for payment verification
     * This will be added to AI Agent's context
     */
    suspend fun getAIContext(phoneNumber: String, qrWasSent: Boolean): String {
        if (!isEnabled() || !qrWasSent) {
            return ""
        }
        
        val link = generateVerificationLinkAfterQR(phoneNumber, "", qrWasSent)
        
        return if (link != null) {
            """
            
            PAYMENT VERIFICATION:
            After sending payment QR code, you should send this verification link to the customer:
            "$link"
            
            Say something like:
            "कृपया अपना payment screenshot यहाँ upload करें: $link"
            or
            "Please upload your payment screenshot here: $link"
            
            This link allows the customer to upload their payment screenshot for automatic verification.
            """.trimIndent()
        } else {
            ""
        }
    }

    /**
     * Generate verification link for any payment method (QR/UPI/Bank/etc.)
     * Controlled by same feature toggle.
     */
    suspend fun generateVerificationLinkForPayment(
        phoneNumber: String,
        orderId: String = "",
        expectedName: String = "",
        expectedUpiId: String = "",
        expectedAmount: Double = 0.0,
        customFieldValues: Map<String, String> = emptyMap()
    ): String? {
        if (!isEnabled()) return null
        val link =
            verificationManager.generateCustomerLink(
                customerPhone = phoneNumber,
                orderId = orderId,
                expectedName = expectedName,
                expectedUpiId = expectedUpiId,
                expectedAmount = expectedAmount,
                customFieldValues = customFieldValues
            )
        return link.takeIf { it.isNotBlank() }
    }

    /**
     * Human-readable verification instruction to append in outgoing replies.
     */
    fun buildVerificationInstruction(link: String): String {
        return """
            Payment hone ke baad verification ke liye screenshot is link par upload karein:
            $link
            
            Screenshot clear hona chahiye (amount, UPI/Bank details, transaction ID visible).
        """.trimIndent()
    }
    
    /**
     * Save expected payment details for verification
     * This will be used to match against uploaded screenshot
     */
    suspend fun saveExpectedPaymentDetails(
        phoneNumber: String,
        orderId: String,
        expectedName: String,
        expectedUpiId: String,
        expectedAmount: Double
    ) {
        val prefs = context.getSharedPreferences("expected_payments", Context.MODE_PRIVATE)
        val key = "${phoneNumber}_$orderId"
        
        prefs.edit().apply {
            putString("${key}_name", expectedName)
            putString("${key}_upiId", expectedUpiId)
            putFloat("${key}_amount", expectedAmount.toFloat())
            putLong("${key}_timestamp", System.currentTimeMillis())
            apply()
        }
        
        Log.d(TAG, "Saved expected payment: $key - Name: $expectedName, UPI: $expectedUpiId, Amount: $expectedAmount")
    }
    
    /**
     * Get expected payment details
     */
    fun getExpectedPaymentDetails(phoneNumber: String, orderId: String): ExpectedPaymentDetails? {
        val prefs = context.getSharedPreferences("expected_payments", Context.MODE_PRIVATE)
        val key = "${phoneNumber}_$orderId"
        
        val name = prefs.getString("${key}_name", null)
        val upiId = prefs.getString("${key}_upiId", null)
        val amount = prefs.getFloat("${key}_amount", 0f).toDouble()
        val timestamp = prefs.getLong("${key}_timestamp", 0)
        
        return if (name != null && upiId != null) {
            ExpectedPaymentDetails(name, upiId, amount, timestamp)
        } else {
            null
        }
    }
    
    /**
     * Get payment method details (UPI ID, Name) from last sent QR
     */
    suspend fun getPaymentDetailsFromLastQR(phoneNumber: String): PaymentMethodDetails? {
        // Get enabled payment methods
        val paymentMethods = mutableListOf<PaymentMethod>()
        paymentMethodManager.getEnabledPaymentMethods().collect { methods ->
            paymentMethods.addAll(methods)
        }
        
        // For now, return first enabled payment method
        // In future, track which QR was sent to which customer
        val activeMethod = paymentMethods.firstOrNull()
        
        return if (activeMethod != null && activeMethod.upiId != null) {
            PaymentMethodDetails(
                name = activeMethod.name,
                upiId = activeMethod.upiId ?: "",
                amount = 0.0 // Amount will come from conversation context
            )
        } else {
            null
        }
    }
    
    /**
     * Match verification against expected details
     */
    fun matchVerification(
        verification: PaymentVerification,
        expected: ExpectedPaymentDetails
    ): MatchResult {
        val nameMatch = verification.payeeName.contains(expected.name, ignoreCase = true) ||
                        verification.payerName.contains(expected.name, ignoreCase = true)
        
        val upiMatch = verification.upiId.equals(expected.upiId, ignoreCase = true)
        
        val amountMatch = if (expected.amount > 0) {
            Math.abs(verification.amount - expected.amount) < 1.0 // Allow ₹1 difference
        } else {
            true // If amount not specified, consider it a match
        }
        
        val allMatch = nameMatch && upiMatch && amountMatch
        
        return MatchResult(
            nameMatch = nameMatch,
            upiMatch = upiMatch,
            amountMatch = amountMatch,
            overallMatch = allMatch,
            confidence = when {
                allMatch -> 100
                nameMatch && upiMatch -> 90
                upiMatch -> 70
                nameMatch -> 50
                else -> 0
            }
        )
    }
}

/**
 * Expected payment details for verification
 */
data class ExpectedPaymentDetails(
    val name: String,
    val upiId: String,
    val amount: Double,
    val timestamp: Long
)

/**
 * Payment method details from QR
 */
data class PaymentMethodDetails(
    val name: String,
    val upiId: String,
    val amount: Double
)

/**
 * Match result
 */
data class MatchResult(
    val nameMatch: Boolean,
    val upiMatch: Boolean,
    val amountMatch: Boolean,
    val overallMatch: Boolean,
    val confidence: Int
)

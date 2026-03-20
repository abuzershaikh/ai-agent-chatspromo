package com.message.bulksend.aiagent.tools.ecommerce

import android.net.Uri

/**
 * Payment Method Types
 */
enum class PaymentMethodType(val displayName: String) {
    QR_CODE("QR Code"),
    UPI_ID("UPI ID"),
    RAZORPAY("Razorpay"),
    PAYPAL("PayPal"),
    CUSTOM_GROUP("Custom Field Group")
}

/**
 * QR Code Type
 */
enum class QRCodeType(val displayName: String) {
    FIXED_PRICE("Fixed Price"),
    UNLIMITED("Unlimited Payment")
}

/**
 * Payment Method Data
 */
data class PaymentMethod(
    val id: String = "",
    val name: String = "",
    val type: PaymentMethodType = PaymentMethodType.QR_CODE,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    
    // QR Code specific
    val qrCodeType: QRCodeType? = null,
    val qrCodeImagePath: String? = null,
    val qrCodeImageUri: Uri? = null,
    val fixedPrice: Double? = null,
    val agentPriceField: String? = null, // Field name for AI Agent to use
    
    // UPI specific
    val upiId: String? = null,
    
    // Razorpay specific
    val razorpayKeyId: String? = null,
    val razorpayKeySecret: String? = null,
    val razorpayWebhookSecret: String? = null,
    
    // PayPal specific
    val paypalEmail: String? = null,
    val paypalClientId: String? = null,
    val paypalClientSecret: String? = null,
    
    // Custom field group specific
    val customGroupName: String? = null,
    val customFields: List<CustomField>? = null
)

/**
 * Custom Field for grouped payment details
 */
data class CustomField(
    val fieldName: String = "",
    val fieldValue: String = "",
    val fieldType: CustomFieldType = CustomFieldType.TEXT
)

/**
 * Custom Field Types
 */
enum class CustomFieldType(val displayName: String) {
    TEXT("Text"),
    NUMBER("Number"),
    EMAIL("Email"),
    PHONE("Phone"),
    ACCOUNT_NUMBER("Account Number"),
    IFSC_CODE("IFSC Code"),
    SWIFT_CODE("SWIFT Code"),
    IBAN("IBAN")
}

/**
 * Payment Verification Settings
 */
data class PaymentVerificationSettings(
    val verifyByAgent: Boolean = false,
    val autoVerifyScreenshot: Boolean = false,
    val requireTransactionId: Boolean = false,
    val verificationMessage: String = "Please send payment screenshot for verification"
)

/**
 * Payment Configuration
 */
data class PaymentConfiguration(
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val verificationSettings: PaymentVerificationSettings = PaymentVerificationSettings(),
    val defaultPaymentMethod: String? = null, // Payment method ID
    val allowMultiplePayments: Boolean = true
)

/**
 * Payment Transaction (for AI Agent verification)
 */
data class PaymentTransaction(
    val id: String = "",
    val orderId: String = "",
    val phoneNumber: String = "",
    val userName: String = "",
    val amount: Double = 0.0,
    val paymentMethodId: String = "",
    val paymentMethodName: String = "",
    val transactionId: String? = null,
    val screenshotPath: String? = null,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val verifiedByAgent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val verifiedAt: Long? = null,
    val notes: String? = null
)

/**
 * Payment Status
 */
enum class PaymentStatus(val displayName: String) {
    PENDING("Pending"),
    VERIFIED("Verified"),
    REJECTED("Rejected"),
    COMPLETED("Completed")
}

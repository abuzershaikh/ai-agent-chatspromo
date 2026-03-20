package com.message.bulksend.autorespond

/**
 * Data class to hold WhatsApp notification information
 */
data class NotificationData(
    val senderName: String,
    val messageText: String,
    val packageName: String,
    val timestamp: Long,
    val isWhatsAppBusiness: Boolean = packageName == "com.whatsapp.w4b"
)

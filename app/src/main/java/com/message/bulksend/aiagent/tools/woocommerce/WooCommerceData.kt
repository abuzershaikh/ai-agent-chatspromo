package com.message.bulksend.aiagent.tools.woocommerce

/**
 * Data models for WooCommerce integration.
 */

data class WooCommerceConfig(
    val uid: String = "",
    val email: String = "",
    val siteUrl: String = "",
    val webhookUrl: String = "",
    val webhookSecret: String = "",
    val ownerWhatsappNumber: String = "",
    val fcmToken: String = "",
    val pushNotificationsEnabled: Boolean = true,
    val whatsappAlertsEnabled: Boolean = true,
    val setupAt: String = "",
    val lastOrderAt: String = "",
    val lastOrderId: String = ""
)

data class WooCommerceOrder(
    val orderId: String = "",
    val ownerUid: String = "",
    val customerName: String = "",
    val customerEmail: String = "",
    val customerPhone: String = "",
    val total: String = "",
    val currency: String = "INR",
    val status: String = "",
    val items: String = "",
    val rawTopic: String = "",
    val receivedAt: String = "",
    val siteUrl: String = ""
)

data class WooCommerceAlert(
    val alertId: String = "",
    val uid: String = "",
    val type: String = "woocommerce_order_alert",
    val toPhone: String = "",
    val message: String = "",
    val status: String = "pending",
    val createdAt: String = ""
)

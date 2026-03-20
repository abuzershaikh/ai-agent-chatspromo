package com.message.bulksend.aiagent.tools.delhivery

import com.message.bulksend.aiagent.tools.courier.CourierWorkerConfig

// ── Delhivery Data Models ────────────────────────────────────────────────

data class DelhiveryConfig(
    val userId: String = "",
    val apiToken: String = "",
    val pickupLocation: String = "",
    val email: String = "",
    val emailUsername: String = "",
    val webhookUrl: String = "",
    val workerBaseUrl: String = CourierWorkerConfig.DELHIVERY_WORKER_BASE_URL,
    val setupAt: String = ""
)

data class DelhiveryOrderRequest(
    val userId: String,
    val chatPhone: String,
    val chatName: String = "",
    val customerName: String,
    val customerPhone: String,
    val customerEmail: String = "",
    val address: String,
    val city: String,
    val state: String,
    val pincode: String,
    val country: String = "India",
    val paymentMode: String = "Prepaid",   // "Prepaid" or "COD"
    val codAmount: Double = 0.0,
    val amount: Double,
    val productName: String,
    val sku: String = "ITEM-001",
    val quantity: Int = 1,
    val weight: Double = 0.5,             // kg
    val length: Double = 10.0,
    val breadth: Double = 10.0,
    val height: Double = 10.0,
    val waybill: String = ""
)

data class DelhiveryOrderResponse(
    val success: Boolean = false,
    val orderId: String = "",
    val awb: String = "",
    val status: String = "",
    val firestorePath: String = "",
    val error: String? = null
)

data class DelhiveryTrackingInfo(
    val awb: String = "",
    val status: String = "Unknown",
    val expectedDelivery: String = "",
    val lastLocation: String = "",
    val lastStatusAt: String = ""
)

data class DelhiveryServiceabilityResult(
    val pincode: String = "",
    val serviceable: Boolean = false,
    val prepaid: Boolean = false,
    val cod: Boolean = false,
    val pickup: Boolean = false
)

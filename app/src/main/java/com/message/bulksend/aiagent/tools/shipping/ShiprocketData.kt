package com.message.bulksend.aiagent.tools.shipping

import com.message.bulksend.aiagent.tools.courier.CourierWorkerConfig

// ---- Data models for Shiprocket Integration ----

data class ShiprocketConfig(
    val userId: String = "",
    val apiEmail: String = "",
    val workerBaseUrl: String = CourierWorkerConfig.SHIPROCKET_WORKER_BASE_URL
)

data class ShiprocketOrderRequest(
    val orderNumber: String,
    val orderDate: String,          // Format: yyyy-MM-dd HH:mm
    val channelId: String = "",
    val comment: String = "",
    val chatPhone: String = "",
    val chatName: String = "",
    val billingCustomerName: String,
    val billingLastName: String = "",
    val billingAddress: String,
    val billingCity: String,
    val billingPincode: String,
    val billingState: String,
    val billingCountry: String = "India",
    val billingEmail: String,
    val billingPhone: String,
    val shippingIsBilling: Boolean = true,
    val orderItems: List<ShiprocketOrderItem>,
    val paymentMethod: String = "Prepaid",  // "Prepaid" or "COD"
    val subTotal: Double,
    val length: Double = 10.0,
    val breadth: Double = 10.0,
    val height: Double = 10.0,
    val weight: Double = 0.5
)

data class ShiprocketOrderItem(
    val name: String,
    val sku: String,
    val units: Int,
    val sellingPrice: Double,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val hsn: Int = 0
)

data class ShiprocketOrderResponse(
    val orderId: Int? = null,
    val shipmentId: Int? = null,
    val awbCode: String? = null,
    val status: String? = null,
    val statusCode: Int? = null,
    val onboardingCompletedNow: Boolean = false,
    val error: String? = null
)

data class ShiprocketTrackingInfo(
    val awb: String = "",
    val status: String = "Unknown",
    val currentLocation: String = "",
    val expectedDelivery: String? = null,
    val lastUpdated: String = "",
    val activities: List<TrackingActivity> = emptyList()
)

data class TrackingActivity(
    val date: String,
    val activity: String,
    val location: String
)

// Firestore order log model
data class ShiprocketOrderLog(
    val userId: String = "",
    val ownerUserId: String = "",
    val orderId: String = "",
    val awb: String = "",
    val status: String = "CREATED",
    val shipmentId: String = "",
    val orderPlacedAt: String = "",
    val orderPlacedAtEpoch: Long = 0L,
    val lastStatusAt: String = "",
    val chatPhone: String = "",
    val chatPhoneKey: String = "",
    val chatName: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val address: String = "",
    val amount: String = "",
    val timestamp: String = "",
    val items: String = "",   // Human-readable item summary
    val paymentMethod: String = "Prepaid"
)

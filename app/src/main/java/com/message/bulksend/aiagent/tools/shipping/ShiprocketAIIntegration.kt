package com.message.bulksend.aiagent.tools.shipping

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ShiprocketAIIntegration - tool wrapper for AI Agent shipping flows.
 */
class ShiprocketAIIntegration(private val context: Context) {

    private val tag = "ShiprocketAI"
    private val manager = ShiprocketManager(context)

    suspend fun checkSetup(): String {
        return try {
            if (manager.isSetupDone()) {
                "Shiprocket account connected hai. Aap orders place kar sakte hain."
            } else {
                "Shiprocket account connect nahi hua. Settings -> AI Agent -> Shipping -> Setup Shiprocket"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun placeOrder(params: Map<String, String>): String {
        return try {
            val customerName = params["customer_name"] ?: return "Customer name required"
            val phone = params["phone"] ?: return "Phone number required"
            val chatPhone = params["chat_phone"] ?: params["incoming_phone"] ?: params["sender_phone"] ?: phone
            val chatName = params["chat_name"] ?: params["sender_name"] ?: customerName
            val address = params["address"] ?: return "Delivery address required"
            val city = params["city"] ?: return "City required"
            val state = params["state"] ?: return "State required"
            val pincode = params["pincode"] ?: return "Pincode required"
            val productName = params["product_name"] ?: return "Product name required"
            val quantity = params["quantity"]?.toIntOrNull() ?: 1
            val price = params["price"]?.toDoubleOrNull() ?: return "Price required"
            val weight = params["weight_kg"]?.toDoubleOrNull() ?: 0.5
            val paymentMethod = if (params["payment"]?.lowercase() == "cod") "COD" else "Prepaid"

            val orderNumber = "ORD-${System.currentTimeMillis()}"
            val orderDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

            val orderRequest = ShiprocketOrderRequest(
                orderNumber = orderNumber,
                orderDate = orderDate,
                chatPhone = chatPhone,
                chatName = chatName,
                billingCustomerName = customerName,
                billingPhone = phone,
                billingEmail = params["email"] ?: "noemail@placeholder.com",
                billingAddress = address,
                billingCity = city,
                billingState = state,
                billingPincode = pincode,
                orderItems = listOf(
                    ShiprocketOrderItem(
                        name = productName,
                        sku = "SKU-${System.currentTimeMillis()}",
                        units = quantity,
                        sellingPrice = price
                    )
                ),
                paymentMethod = paymentMethod,
                subTotal = price * quantity,
                weight = weight
            )

            val result = manager.placeOrder(orderRequest)
            result.fold(
                onSuccess = { response ->
                    buildString {
                        appendLine("Order placed successfully")
                        appendLine("Order ID: ${response.orderId}")
                        if (!response.awbCode.isNullOrEmpty()) {
                            appendLine("AWB: ${response.awbCode}")
                        }
                        appendLine("Status: ${response.status}")
                        appendLine("Chat Number: $chatPhone")
                        appendLine("Customer: $customerName")
                        appendLine("Delivery Phone: $phone")
                        appendLine("Address: $address, $city, $state - $pincode")
                        appendLine("Amount: Rs.${price * quantity} ($paymentMethod)")
                    }
                },
                onFailure = { error ->
                    "Order failed: ${error.message}"
                }
            )
        } catch (e: Exception) {
            Log.e(tag, "placeOrder error: ${e.message}", e)
            "Error placing order: ${e.message}"
        }
    }

    suspend fun trackOrder(awb: String): String {
        return try {
            if (awb.isBlank()) return "AWB / tracking number required"

            manager.trackOrder(awb).fold(
                onSuccess = { info ->
                    buildString {
                        appendLine("Tracking: $awb")
                        appendLine("Current Status: ${info.status}")
                        if (info.currentLocation.isNotEmpty()) {
                            appendLine("Location: ${info.currentLocation}")
                        }
                        if (!info.expectedDelivery.isNullOrEmpty()) {
                            appendLine("Expected Delivery: ${info.expectedDelivery}")
                        }
                        appendLine("Last Updated: ${info.lastUpdated}")
                    }
                },
                onFailure = {
                    "Tracking not found for AWB '$awb'"
                }
            )
        } catch (e: Exception) {
            "Tracking error: ${e.message}"
        }
    }

    suspend fun listMyOrders(): String {
        return try {
            val orders = manager.fetchMyOrders()
            if (orders.isEmpty()) {
                "Abhi koi orders nahi hain."
            } else {
                buildString {
                    appendLine("Recent Orders (${orders.size})")
                    appendLine()
                    orders.take(10).forEachIndexed { index, order ->
                        appendLine("${index + 1}. ${order.chatName.ifBlank { order.customerName }} - ${order.items}")
                        appendLine("   AWB: ${order.awb} | Status: ${order.status}")
                        appendLine("   Chat: ${order.chatPhone.ifBlank { order.customerPhone }} | Delivery: ${order.customerPhone} | Rs.${order.amount}")
                        appendLine("   Date: ${order.orderPlacedAt.ifBlank { order.timestamp }}")
                        appendLine()
                    }
                }
            }
        } catch (e: Exception) {
            "Orders fetch error: ${e.message}"
        }
    }

    suspend fun executeTool(toolName: String, params: Map<String, String>): String {
        Log.d(tag, "Executing tool: $toolName with params: $params")
        return when (toolName) {
            "check_shiprocket_setup" -> checkSetup()
            "place_shiprocket_order" -> placeOrder(params)
            "track_shiprocket_order" -> trackOrder(params["awb"] ?: params["tracking_number"] ?: "")
            "list_my_orders" -> listMyOrders()
            else -> "Unknown shipping tool: $toolName"
        }
    }
}

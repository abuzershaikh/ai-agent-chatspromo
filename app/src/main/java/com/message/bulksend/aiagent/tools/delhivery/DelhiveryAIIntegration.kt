package com.message.bulksend.aiagent.tools.delhivery

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * DelhiveryAIIntegration - tool wrapper for AI Agent shipping flows.
 */
class DelhiveryAIIntegration(private val context: Context) {

    private val tag = "DelhiveryAI"
    private val manager = DelhiveryManager(context)

    suspend fun checkSetup(): String {
        return try {
            if (manager.isSetupDone()) {
                "Delhivery account connected hai. Aap orders place kar sakte hain."
            } else {
                "Delhivery account connect nahi hua. Settings -> AI Agent -> Shipping -> Setup Delhivery"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun checkPincode(pincode: String): String {
        return try {
            if (pincode.isBlank()) return "Pincode required"

            manager.checkPincodeServiceability(pincode).fold(
                onSuccess = { result ->
                    if (result.serviceable) {
                        buildString {
                            appendLine("Pincode $pincode is serviceable.")
                            appendLine("Prepaid: ${if (result.prepaid) "Yes" else "No"}")
                            appendLine("COD: ${if (result.cod) "Yes" else "No"}")
                            appendLine("Pickup: ${if (result.pickup) "Yes" else "No"}")
                        }
                    } else {
                        "Pincode $pincode is NOT serviceable by Delhivery."
                    }
                },
                onFailure = { "Pincode check failed: ${it.message}" }
            )
        } catch (e: Exception) {
            "Error checking pincode: ${e.message}"
        }
    }

    suspend fun placeOrder(params: Map<String, String>): String {
        return try {
            val customerName = pick(
                params,
                "customer_name",
                "customerName",
                "name"
            ) ?: return "Customer name required"
            val phone = pick(
                params,
                "phone",
                "customer_phone",
                "delivery_phone",
                "mobile"
            ) ?: return "Phone number required"

            val chatPhone = pick(
                params,
                "chat_phone",
                "incoming_phone",
                "sender_phone"
            ) ?: phone
            val chatName = pick(
                params,
                "chat_name",
                "sender_name"
            ) ?: customerName

            val address = pick(params, "address", "delivery_address") ?: return "Delivery address required"
            val city = pick(params, "city") ?: return "City required"
            val state = pick(params, "state") ?: return "State required"
            val pincode = pick(params, "pincode", "pin", "postal_code") ?: return "Pincode required"
            val productName = pick(params, "product_name", "item_name", "product") ?: return "Product name required"

            val quantity = pick(params, "quantity", "qty", "units")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val unitPrice = pick(
                params,
                "price",
                "unit_price",
                "selling_price"
            )?.toDoubleOrNull()
            val totalAmount = pick(
                params,
                "amount",
                "total_amount",
                "order_amount",
                "sub_total"
            )?.toDoubleOrNull()
                ?: unitPrice?.times(quantity)
                ?: return "Amount/price required"

            val paymentMethod = normalizePaymentMode(
                pick(params, "payment", "payment_mode", "paymentMethod")
            )
            val codAmount = pick(params, "cod_amount", "codAmount")
                ?.toDoubleOrNull()
                ?: if (paymentMethod == "COD") totalAmount else 0.0

            val weight = pick(params, "weight_kg", "weight")?.toDoubleOrNull() ?: 0.5
            val length = pick(params, "length", "length_cm")?.toDoubleOrNull() ?: 10.0
            val breadth = pick(params, "breadth", "width", "width_cm")?.toDoubleOrNull() ?: 10.0
            val height = pick(params, "height", "height_cm")?.toDoubleOrNull() ?: 10.0
            val waybill = pick(params, "waybill", "awb") ?: ""
            val sku = pick(params, "sku", "item_sku") ?: "SKU-${System.currentTimeMillis()}"
            val country = pick(params, "country") ?: "India"
            val clientName = pick(params, "client_name", "clientName", "cl")

            if (waybill.isBlank()) {
                manager.fetchWaybills(1, clientName).onFailure {
                    Log.w(tag, "Prefetch waybill failed before order: ${it.message}")
                }
            }

            val orderRequest = DelhiveryOrderRequest(
                userId = manager.currentUserId,
                chatPhone = chatPhone,
                chatName = chatName,
                customerName = customerName,
                customerPhone = phone,
                customerEmail = pick(params, "email", "customer_email") ?: "",
                address = address,
                city = city,
                state = state,
                pincode = pincode,
                country = country,
                paymentMode = paymentMethod,
                codAmount = codAmount,
                amount = totalAmount,
                productName = productName,
                sku = sku,
                quantity = quantity,
                weight = weight,
                length = length,
                breadth = breadth,
                height = height,
                waybill = waybill
            )

            manager.createOrder(orderRequest).fold(
                onSuccess = { response ->
                    buildString {
                        appendLine("Delhivery order placed successfully")
                        appendLine("Order ID: ${response.orderId}")
                        if (response.awb.isNotBlank()) appendLine("AWB: ${response.awb}")
                        appendLine("Status: ${response.status}")
                        appendLine("Chat Number: $chatPhone")
                        appendLine("Customer: $customerName")
                        appendLine("Delivery Phone: $phone")
                        appendLine("Address: $address, $city, $state - $pincode")
                        appendLine("Amount: Rs.$totalAmount ($paymentMethod)")
                    }
                },
                onFailure = { "Delhivery order failed: ${it.message}" }
            )
        } catch (e: Exception) {
            Log.e(tag, "placeOrder error: ${e.message}", e)
            "Error placing Delhivery order: ${e.message}"
        }
    }

    suspend fun trackOrder(awb: String): String {
        return try {
            if (awb.isBlank()) return "AWB / tracking number required"

            manager.trackOrder(awb).fold(
                onSuccess = { info ->
                    buildString {
                        appendLine("Delhivery Tracking: $awb")
                        appendLine("Current Status: ${info.status}")
                        if (info.lastLocation.isNotBlank()) {
                            appendLine("Location / Details: ${info.lastLocation}")
                        }
                        if (info.expectedDelivery.isNotBlank()) {
                            appendLine("Expected Delivery: ${info.expectedDelivery}")
                        }
                        appendLine("Last Updated: ${info.lastStatusAt}")
                    }
                },
                onFailure = { "Tracking failed for Delhivery AWB '$awb': ${it.message}" }
            )
        } catch (e: Exception) {
            "Tracking error: ${e.message}"
        }
    }

    suspend fun cancelOrder(waybill: String): String {
        return try {
            if (waybill.isBlank()) return "Waybill / AWB required to cancel"
            manager.cancelOrder(waybill).fold(
                onSuccess = { "Success: $it" },
                onFailure = { "Cancel failed: ${it.message}" }
            )
        } catch (e: Exception) {
            "Cancel error: ${e.message}"
        }
    }

    suspend fun fetchWaybill(params: Map<String, String>): String {
        val count = pick(params, "count", "quantity")?.toIntOrNull()?.coerceIn(1, 10_000) ?: 1
        val clientName = pick(params, "client_name", "clientName", "cl")
        return manager.fetchWaybills(count, clientName).fold(
            onSuccess = { waybills ->
                if (waybills.isEmpty()) {
                    "Waybill fetch success but empty response. Client name (cl) check karein."
                } else {
                    buildString {
                        appendLine("Fetched ${waybills.size} waybill(s):")
                        waybills.forEachIndexed { index, wb -> appendLine("${index + 1}. $wb") }
                    }
                }
            },
            onFailure = { "Waybill fetch failed: ${it.message}" }
        )
    }

    suspend fun updateEwaybill(params: Map<String, String>): String {
        val waybill = pick(params, "waybill", "awb") ?: return "Waybill / AWB required"
        val ewaybillNumber = pick(
            params,
            "ewaybill_number",
            "ewaybill",
            "e_waybill",
            "ewb"
        ) ?: return "E-waybill number required"

        return manager.updateEwaybill(waybill, ewaybillNumber).fold(
            onSuccess = { it },
            onFailure = { "E-waybill update failed: ${it.message}" }
        )
    }

    suspend fun printLabel(params: Map<String, String>): String {
        val awb = pick(params, "awb", "waybill", "tracking_number")
            ?: return "AWB / waybill required"

        return manager.downloadLabelPdf(awb).fold(
            onSuccess = { bytes ->
                val sizeKb = bytes.size / 1024.0
                "Label generated for $awb. PDF size: ${"%.1f".format(Locale.US, sizeKb)} KB"
            },
            onFailure = { "Label generation failed for $awb: ${it.message}" }
        )
    }

    suspend fun calculateShippingCost(params: Map<String, String>): String {
        val originPin = pick(params, "origin_pin", "originPin", "pickup_pin")
            ?: return "Origin pincode required"
        val destinationPin = pick(params, "destination_pin", "destinationPin", "delivery_pin", "pincode")
            ?: return "Destination pincode required"
        val weightKg = pick(params, "weight_kg", "weight")?.toDoubleOrNull()
            ?: return "Weight (kg) required"
        val paymentMode = normalizePaymentMode(
            pick(params, "payment_mode", "payment")
        )
        val codAmount = pick(params, "cod_amount", "codAmount")?.toDoubleOrNull() ?: 0.0

        return manager.calculateShippingCost(
            originPin = originPin,
            destinationPin = destinationPin,
            weightKg = weightKg,
            paymentMode = paymentMode,
            codAmount = codAmount
        ).fold(
            onSuccess = { cost ->
                "Shipping cost response: ${cost.toString(2)}"
            },
            onFailure = { "Shipping cost check failed: ${it.message}" }
        )
    }

    suspend fun createPickup(params: Map<String, String>): String {
        val pickupDate = pick(params, "pickup_date", "date") ?: tomorrowDate()
        val pickupTime = pick(params, "pickup_time", "time") ?: "10:00:00"
        val packageCount = pick(params, "package_count", "expected_package_count", "count")
            ?.toIntOrNull()
            ?.coerceAtLeast(1)
            ?: 1
        val pickupLocation = pick(params, "pickup_location", "pickupLocation")

        return manager.createPickupRequest(
            pickupDate = pickupDate,
            pickupLocation = pickupLocation,
            packageCount = packageCount,
            pickupTime = pickupTime
        ).fold(
            onSuccess = { it },
            onFailure = { "Pickup request failed: ${it.message}" }
        )
    }

    suspend fun listMyOrders(): String {
        return try {
            val orders = manager.fetchMyOrders()
            if (orders.isEmpty()) {
                "Abhi koi Delhivery orders nahi hain."
            } else {
                buildString {
                    appendLine("Recent Delhivery Orders (${orders.size})")
                    appendLine()
                    orders.take(10).forEachIndexed { index, doc ->
                        val chatName = doc["chatName"] as? String ?: ""
                        val customerName = doc["customerName"] as? String ?: ""
                        val name = chatName.ifBlank { customerName }

                        val awb = doc["awb"] as? String ?: "Pending"
                        val status = doc["status"] as? String ?: "Unknown"
                        val chatPhone = doc["chatPhone"] as? String ?: ""
                        val deliveryPhone = doc["customerPhone"] as? String ?: ""
                        val amount = doc["amount"]?.toString() ?: "0.0"
                        val items = doc["productName"] as? String ?: "Items"
                        val date = doc["orderCreatedAtDisplay"] as? String ?: ""

                        appendLine("${index + 1}. $name - $items")
                        appendLine("   AWB: $awb | Status: $status")
                        appendLine("   Chat: $chatPhone | Delivery: $deliveryPhone | Rs.$amount")
                        appendLine("   Date: $date")
                        appendLine()
                    }
                }
            }
        } catch (e: Exception) {
            "Orders fetch error: ${e.message}"
        }
    }

    suspend fun executeTool(toolName: String, params: Map<String, String>): String {
        Log.d(tag, "Executing Delhivery tool: $toolName with params: $params")
        return when (toolName) {
            "check_delhivery_setup" -> checkSetup()
            "check_delhivery_pincode" -> checkPincode(
                pick(params, "pincode", "pin", "postal_code").orEmpty()
            )
            "place_delhivery_order" -> placeOrder(params)
            "track_delhivery_order" -> trackOrder(
                pick(params, "awb", "waybill", "tracking_number").orEmpty()
            )
            "cancel_delhivery_order" -> cancelOrder(
                pick(params, "awb", "waybill").orEmpty()
            )
            "list_delhivery_orders" -> listMyOrders()
            "fetch_delhivery_waybill", "get_delhivery_waybill" -> fetchWaybill(params)
            "update_delhivery_ewaybill", "assign_delhivery_ewaybill" -> updateEwaybill(params)
            "print_delhivery_label", "generate_delhivery_label" -> printLabel(params)
            "calculate_delhivery_shipping_cost", "delhivery_shipping_cost" -> calculateShippingCost(params)
            "create_delhivery_pickup", "schedule_delhivery_pickup" -> createPickup(params)
            else -> "Unknown Delhivery shipping tool: $toolName"
        }
    }

    fun getFunctionCallSchema(): String {
        return """
            Delhivery Functions:
            - check_delhivery_setup()
            - check_delhivery_pincode(pincode)
            - place_delhivery_order(customer_name, phone, address, city, state, pincode, product_name, amount/price, quantity, payment_mode, weight_kg, waybill)
            - fetch_delhivery_waybill(count, client_name)
            - track_delhivery_order(awb)
            - cancel_delhivery_order(awb/waybill)
            - update_delhivery_ewaybill(waybill, ewaybill_number)
            - print_delhivery_label(awb)
            - calculate_delhivery_shipping_cost(origin_pin, destination_pin, weight_kg, payment_mode, cod_amount)
            - create_delhivery_pickup(pickup_date, pickup_time, package_count, pickup_location)
            - list_delhivery_orders()
        """.trimIndent()
    }

    private fun pick(params: Map<String, String>, vararg keys: String): String? {
        keys.forEach { key ->
            val value = params[key]?.trim()
            if (!value.isNullOrEmpty()) {
                return value
            }
        }
        return null
    }

    private fun normalizePaymentMode(value: String?): String {
        return if (value?.trim()?.equals("COD", ignoreCase = true) == true) "COD" else "Prepaid"
    }

    private fun tomorrowDate(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }
}

package com.message.bulksend.aiagent.tools.shipping

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.message.bulksend.aiagent.tools.courier.CourierWorkerConfig
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Shiprocket manager for setup, order create, tracking and Firestore sync.
 */
class ShiprocketManager(private val context: Context) {

    private val tag = "ShiprocketManager"

    private val workerBaseUrl = CourierWorkerConfig.SHIPROCKET_WORKER_BASE_URL
    private val shiprocketApi = "https://apiv2.shiprocket.in/v1/external"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance("chatspromoweb")
    }

    private val auth = FirebaseAuth.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    val currentUserEmail: String
        get() = auth.currentUser?.email ?: ""

    suspend fun setupShiprocketCredentials(
        shiprocketEmail: String,
        shiprocketPassword: String
    ): Result<String> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return Result.failure(Exception("User not logged in"))

            val body = JSONObject().apply {
                put("userId", userId)
                put("email", shiprocketEmail)
                put("password", shiprocketPassword)
            }.toString().toRequestBody(jsonType)

            val request = Request.Builder()
                .url("$workerBaseUrl/user/setup")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            val json = if (responseBody.isNotBlank()) JSONObject(responseBody) else JSONObject()

            if (response.isSuccessful && json.optBoolean("success", false)) {
                val webhookUrlFromWorker = json.optString("webhookUrl").takeIf { it.isNotBlank() }
                saveWebhookToFirestore(userId, webhookUrlFromWorker)
                Result.success("Setup successful! Your Shiprocket API linked.")
            } else {
                Result.failure(Exception(json.optString("error", "Setup failed")))
            }
        } catch (e: Exception) {
            Log.e(tag, "Setup error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun saveWebhookToFirestore(userId: String, webhookUrlFromWorker: String?) {
        try {
            val webhookUrl = webhookUrlFromWorker ?: "$workerBaseUrl/webhook/$userId"
            val data = mapOf(
                "userId" to userId,
                "userEmail" to currentUserEmail,
                "webhookUrl" to webhookUrl,
                "lastSetupAt" to getCurrentIsoTimestamp()
            )
            firestore.collection("users_config").document(userId).set(data, SetOptions.merge()).await()
            Log.d(tag, "Webhook metadata updated for userId=$userId")
        } catch (e: Exception) {
            Log.e(tag, "Webhook metadata save error: ${e.message}", e)
        }
    }

    suspend fun getActiveToken(): Result<String> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return Result.failure(Exception("User not logged in"))

            val request = Request.Builder()
                .url("$workerBaseUrl/auth/refresh?userId=$userId")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            if (response.isSuccessful && json.has("token")) {
                Result.success(json.getString("token"))
            } else {
                Result.failure(Exception(json.optString("error", "Token fetch failed")))
            }
        } catch (e: Exception) {
            Log.e(tag, "Token fetch error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun placeOrder(orderRequest: ShiprocketOrderRequest): Result<ShiprocketOrderResponse> {
        return try {
            val tokenResult = getActiveToken()
            if (tokenResult.isFailure) return Result.failure(tokenResult.exceptionOrNull()!!)
            val token = tokenResult.getOrThrow()

            val itemsArray = JSONArray()
            orderRequest.orderItems.forEach { item ->
                itemsArray.put(JSONObject().apply {
                    put("name", item.name)
                    put("sku", item.sku)
                    put("units", item.units)
                    put("selling_price", item.sellingPrice)
                    put("discount", item.discount)
                    put("tax", item.tax)
                    put("hsn", item.hsn)
                })
            }

            val payload = JSONObject().apply {
                put("order_id", orderRequest.orderNumber)
                put("order_date", orderRequest.orderDate)
                put("pickup_location", "Primary")
                put("billing_customer_name", orderRequest.billingCustomerName)
                put("billing_last_name", orderRequest.billingLastName)
                put("billing_address", orderRequest.billingAddress)
                put("billing_city", orderRequest.billingCity)
                put("billing_pincode", orderRequest.billingPincode)
                put("billing_state", orderRequest.billingState)
                put("billing_country", orderRequest.billingCountry)
                put("billing_email", orderRequest.billingEmail)
                put("billing_phone", orderRequest.billingPhone)
                put("shipping_is_billing", orderRequest.shippingIsBilling)
                put("order_items", itemsArray)
                put("payment_method", orderRequest.paymentMethod)
                put("sub_total", orderRequest.subTotal)
                put("length", orderRequest.length)
                put("breadth", orderRequest.breadth)
                put("height", orderRequest.height)
                put("weight", orderRequest.weight)
            }

            val body = payload.toString().toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$shiprocketApi/orders/create/adhoc")
                .post(body)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseJson = JSONObject(response.body?.string() ?: "{}")

            if (response.isSuccessful) {
                val orderResponse = ShiprocketOrderResponse(
                    orderId = responseJson.optInt("order_id").takeIf { it > 0 },
                    shipmentId = responseJson.optInt("shipment_id").takeIf { it > 0 },
                    awbCode = responseJson.optString("awb_code").takeIf { it.isNotBlank() },
                    status = responseJson.optString("status").ifBlank { "CREATED" },
                    statusCode = responseJson.optInt("status_code").takeIf { it != 0 }
                )

                syncOrderToFirestore(orderRequest, orderResponse)
                Result.success(orderResponse)
            } else {
                Result.failure(Exception(responseJson.optString("message", "Order placement failed")))
            }
        } catch (e: Exception) {
            Log.e(tag, "Order placement error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun syncOrderToFirestore(
        request: ShiprocketOrderRequest,
        response: ShiprocketOrderResponse
    ) {
        try {
            val userId = currentUserId
            if (userId.isBlank()) return

            val orderId = response.orderId?.toString() ?: UUID.randomUUID().toString()
            val shipmentId = response.shipmentId?.toString().orEmpty()
            val awb = response.awbCode?.takeIf { it.isNotBlank() }.orEmpty()
            val nowIso = getCurrentIsoTimestamp()
            val nowDisplay = getCurrentTimestamp()
            val nowEpoch = System.currentTimeMillis()

            val chatPhone = request.chatPhone.ifBlank { request.billingPhone }
            val chatPhoneKey = sanitizePhoneKey(chatPhone)
            val chatName = request.chatName.ifBlank { request.billingCustomerName }
            val itemsSummary = request.orderItems.joinToString(", ") { "${it.name} x${it.units}" }

            val workerPayload = JSONObject().apply {
                put("userId", userId)
                put("shiprocketResponse", JSONObject().apply {
                    put("order_id", orderId)
                    put("shipment_id", shipmentId)
                    put("awb_code", awb)
                    put("status", response.status ?: "CREATED")
                })
                put("orderData", JSONObject().apply {
                    put("chatPhone", chatPhone)
                    put("chatName", chatName)
                    put("customerName", request.billingCustomerName)
                    put("customerPhone", request.billingPhone)
                    put("address", request.billingAddress)
                    put("city", request.billingCity)
                    put("state", request.billingState)
                    put("pincode", request.billingPincode)
                    put("amount", request.subTotal.toString())
                    put("items", itemsSummary)
                    put("paymentMethod", request.paymentMethod)
                    put("orderPlacedAt", nowIso)
                    put("orderPlacedAtEpoch", nowEpoch)
                })
            }

            val workerRequest = Request.Builder()
                .url("$workerBaseUrl/sync-order")
                .post(workerPayload.toString().toRequestBody(jsonType))
                .build()

            val workerResponse = httpClient.newCall(workerRequest).execute()
            if (!workerResponse.isSuccessful) {
                Log.e(tag, "Worker sync failed: ${workerResponse.code} ${workerResponse.body?.string().orEmpty()}")
            }

            val customerDoc = firestore
                .collection("users_config")
                .document(userId)
                .collection("customers")
                .document(chatPhoneKey)

            val existingCustomer = customerDoc.get().await()
            val firstSeenAt = existingCustomer.getString("firstSeenAt") ?: nowIso

            val customerPayload = mapOf(
                "ownerUserId" to userId,
                "phone" to chatPhone,
                "name" to chatName,
                "firstSeenAt" to firstSeenAt,
                "lastMessageAt" to nowIso,
                "defaultAddress" to request.billingAddress,
                "defaultCity" to request.billingCity,
                "defaultState" to request.billingState,
                "defaultPincode" to request.billingPincode,
                "updatedAt" to nowIso
            )
            customerDoc.set(customerPayload, SetOptions.merge()).await()

            val orderLog = mapOf(
                "userId" to userId,
                "ownerUserId" to userId,
                "orderId" to orderId,
                "shipmentId" to shipmentId,
                "awb" to awb,
                "status" to (response.status ?: "CREATED"),
                "orderPlacedAt" to nowIso,
                "orderPlacedAtEpoch" to nowEpoch,
                "lastStatusAt" to nowIso,
                "chatPhone" to chatPhone,
                "chatPhoneKey" to chatPhoneKey,
                "chatName" to chatName,
                "customerName" to request.billingCustomerName,
                "customerPhone" to request.billingPhone,
                "address" to request.billingAddress,
                "city" to request.billingCity,
                "state" to request.billingState,
                "pincode" to request.billingPincode,
                "amount" to request.subTotal.toString(),
                "items" to itemsSummary,
                "paymentMethod" to request.paymentMethod,
                "timestamp" to nowDisplay
            )

            customerDoc
                .collection("orders")
                .document(orderId)
                .set(orderLog, SetOptions.merge())
                .await()

            if (awb.isNotBlank()) {
                firestore
                    .collection("users_config")
                    .document(userId)
                    .collection("awb_index")
                    .document(awb)
                    .set(
                        mapOf(
                            "ownerUserId" to userId,
                            "chatPhone" to chatPhone,
                            "chatPhoneKey" to chatPhoneKey,
                            "orderId" to orderId,
                            "updatedAt" to nowIso
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }

            val orderLogKey = if (awb.isNotBlank()) awb else "PENDING_$orderId"
            firestore.collection("orders_log").document(orderLogKey).set(orderLog, SetOptions.merge()).await()

            Log.d(tag, "Order synced userId=$userId chatPhoneKey=$chatPhoneKey orderId=$orderId awb=$awb")
        } catch (e: Exception) {
            Log.e(tag, "Firestore sync error: ${e.message}", e)
        }
    }

    suspend fun fetchMyOrders(): List<ShiprocketOrderLog> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return emptyList()

            val snapshot = firestore
                .collectionGroup("orders")
                .whereEqualTo("ownerUserId", userId)
                .limit(200)
                .get()
                .await()

            snapshot.documents.map { doc ->
                val amountString = doc.getString("amount")
                    ?: doc.getDouble("amount")?.toString()
                    ?: doc.getLong("amount")?.toString()
                    ?: ""

                ShiprocketOrderLog(
                    userId = doc.getString("userId") ?: "",
                    ownerUserId = doc.getString("ownerUserId") ?: userId,
                    orderId = doc.getString("orderId") ?: doc.id,
                    awb = doc.getString("awb") ?: "",
                    status = doc.getString("status") ?: "",
                    shipmentId = doc.getString("shipmentId") ?: "",
                    orderPlacedAt = doc.getString("orderPlacedAt") ?: "",
                    orderPlacedAtEpoch = doc.getLong("orderPlacedAtEpoch") ?: 0L,
                    lastStatusAt = doc.getString("lastStatusAt") ?: "",
                    chatPhone = doc.getString("chatPhone") ?: "",
                    chatPhoneKey = doc.getString("chatPhoneKey") ?: "",
                    chatName = doc.getString("chatName") ?: "",
                    customerName = doc.getString("customerName") ?: "",
                    customerPhone = doc.getString("customerPhone") ?: "",
                    address = doc.getString("address") ?: "",
                    city = doc.getString("city") ?: "",
                    state = doc.getString("state") ?: "",
                    pincode = doc.getString("pincode") ?: "",
                    amount = amountString,
                    timestamp = doc.getString("timestamp") ?: "",
                    items = doc.getString("items") ?: "",
                    paymentMethod = doc.getString("paymentMethod") ?: "Prepaid"
                )
            }.sortedByDescending { it.orderPlacedAtEpoch }.take(50)
        } catch (e: Exception) {
            Log.e(tag, "Fetch orders error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun trackOrder(awb: String): Result<ShiprocketTrackingInfo> {
        return try {
            val tokenResult = getActiveToken()
            if (tokenResult.isFailure) return Result.failure(tokenResult.exceptionOrNull()!!)
            val token = tokenResult.getOrThrow()

            val request = Request.Builder()
                .url("$shiprocketApi/courier/track/awb/$awb")
                .get()
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = httpClient.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            if (response.isSuccessful) {
                val trackingData = json.optJSONObject("tracking_data") ?: JSONObject()
                val shipmentTrackArray = trackingData.optJSONArray("shipment_track")
                val shipmentTrack = if (shipmentTrackArray != null && shipmentTrackArray.length() > 0) {
                    shipmentTrackArray.optJSONObject(0) ?: JSONObject()
                } else {
                    JSONObject()
                }

                val lastUpdated = shipmentTrack.optString("updated_at", getCurrentIsoTimestamp())
                val info = ShiprocketTrackingInfo(
                    awb = awb,
                    status = shipmentTrack.optString("current_status", "Unknown"),
                    currentLocation = shipmentTrack.optString("location", ""),
                    expectedDelivery = shipmentTrack.optString("etd").takeIf { it.isNotBlank() },
                    lastUpdated = lastUpdated
                )

                updateOrderStatusInFirestore(awb, info.status, lastUpdated)
                Result.success(info)
            } else {
                Result.failure(Exception("Tracking failed for AWB: $awb"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Tracking error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun updateOrderStatusInFirestore(awb: String, status: String, lastUpdated: String) {
        try {
            val userId = currentUserId
            if (userId.isBlank()) return

            val nowIso = if (lastUpdated.isNotBlank()) lastUpdated else getCurrentIsoTimestamp()

            firestore.collection("orders_log").document(awb)
                .set(
                    mapOf(
                        "status" to status,
                        "lastStatusAt" to nowIso,
                        "lastTracked" to getCurrentTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()

            val indexDoc = firestore
                .collection("users_config")
                .document(userId)
                .collection("awb_index")
                .document(awb)
                .get()
                .await()

            if (indexDoc.exists()) {
                val chatPhoneKey = indexDoc.getString("chatPhoneKey").orEmpty()
                val orderId = indexDoc.getString("orderId").orEmpty()

                if (chatPhoneKey.isNotBlank() && orderId.isNotBlank()) {
                    firestore
                        .collection("users_config")
                        .document(userId)
                        .collection("customers")
                        .document(chatPhoneKey)
                        .collection("orders")
                        .document(orderId)
                        .set(
                            mapOf(
                                "status" to status,
                                "lastStatusAt" to nowIso,
                                "lastTracked" to getCurrentTimestamp()
                            ),
                            SetOptions.merge()
                        )
                        .await()
                    return
                }
            }

            val fallback = firestore
                .collectionGroup("orders")
                .whereEqualTo("ownerUserId", userId)
                .whereEqualTo("awb", awb)
                .limit(1)
                .get()
                .await()

            fallback.documents.firstOrNull()?.reference
                ?.set(
                    mapOf(
                        "status" to status,
                        "lastStatusAt" to nowIso,
                        "lastTracked" to getCurrentTimestamp()
                    ),
                    SetOptions.merge()
                )
                ?.await()
        } catch (e: Exception) {
            Log.e(tag, "Status update error: ${e.message}", e)
        }
    }

    suspend fun isSetupDone(): Boolean {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return false

            val doc = firestore.collection("users_config").document(userId).get().await()
            val hasWebhook = !doc.getString("webhookUrl").isNullOrBlank()
            val hasApiEmail = !doc.getString("api_email").isNullOrBlank()
            doc.exists() && (hasWebhook || hasApiEmail)
        } catch (_: Exception) {
            false
        }
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun getCurrentIsoTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }

    private fun sanitizePhoneKey(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.isBlank()) "unknown_phone" else digits
    }
}

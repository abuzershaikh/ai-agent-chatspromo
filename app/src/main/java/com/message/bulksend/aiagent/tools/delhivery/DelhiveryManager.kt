package com.message.bulksend.aiagent.tools.delhivery

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.message.bulksend.aiagent.tools.courier.CourierWorkerConfig
import kotlinx.coroutines.tasks.await
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
import java.util.concurrent.TimeUnit

/**
 * Manages all Delhivery courier operations via the deployed delhivery-worker.
 *
 * Firestore path: users_config/{userId}/customers/{phoneKey}/delhivery_orders/{orderId}
 */
class DelhiveryManager(private val context: Context) {

    private val tag = "DelhiveryManager"

    var workerBaseUrl: String = CourierWorkerConfig.DELHIVERY_WORKER_BASE_URL

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

    // ── Setup ─────────────────────────────────────────────────────────────

    /**
     * Saves the user's Delhivery API token, pickup location, and email to the worker.
     * Returns [Result] with the generated webhook URL on success.
     */
    suspend fun setup(
        apiToken: String,
        pickupLocation: String,
        email: String
    ): Result<DelhiveryConfig> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return Result.failure(Exception("User not signed in"))

            val body = JSONObject().apply {
                put("userId", userId)
                put("apiToken", apiToken)
                put("pickupLocation", pickupLocation)
                put("email", email)
            }.toString().toRequestBody(jsonType)

            val request = Request.Builder()
                .url("$workerBaseUrl/delhivery/setup")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseText = response.body?.string().orEmpty()
            val json = if (responseText.isNotBlank()) JSONObject(responseText) else JSONObject()

            if (response.isSuccessful && json.optBoolean("success", false)) {
                val webhookUrl = json.optString("webhookUrl", "")
                val emailUsername = json.optString("emailUsername", "")

                val config = DelhiveryConfig(
                    userId = userId,
                    apiToken = apiToken,
                    pickupLocation = pickupLocation,
                    email = email,
                    emailUsername = emailUsername,
                    webhookUrl = webhookUrl,
                    workerBaseUrl = workerBaseUrl,
                    setupAt = getCurrentIsoTimestamp()
                )

                // Also cache locally in Firestore for offline reads
                saveConfigToFirestore(config)

                Result.success(config)
            } else {
                Result.failure(Exception(json.optString("error", "Setup failed")))
            }
        } catch (e: Exception) {
            Log.e(tag, "Setup error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun saveConfigToFirestore(config: DelhiveryConfig) {
        try {
            firestore.collection("users_config")
                .document(config.userId)
                .collection("delhivery_config")
                .document("main")
                .set(
                    mapOf(
                        "userId" to config.userId,
                        "apiToken" to config.apiToken,
                        "pickupLocation" to config.pickupLocation,
                        "email" to config.email,
                        "emailUsername" to config.emailUsername,
                        "webhookUrl" to config.webhookUrl,
                        "workerBaseUrl" to config.workerBaseUrl,
                        "setupAt" to config.setupAt
                    ),
                    SetOptions.merge()
                )
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Save config error: ${e.message}", e)
        }
    }

    /**
     * Loads the user's Delhivery config from Firestore.
     * Returns null if not set up yet.
     */
    suspend fun getConfig(): DelhiveryConfig? {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return null

            val doc = firestore.collection("users_config")
                .document(userId)
                .collection("delhivery_config")
                .document("main")
                .get()
                .await()

            if (!doc.exists()) return null

            DelhiveryConfig(
                userId = doc.getString("userId") ?: userId,
                apiToken = doc.getString("apiToken") ?: "",
                pickupLocation = doc.getString("pickupLocation") ?: "",
                email = doc.getString("email") ?: "",
                emailUsername = doc.getString("emailUsername") ?: "",
                webhookUrl = doc.getString("webhookUrl") ?: "",
                workerBaseUrl = doc.getString("workerBaseUrl") ?: workerBaseUrl,
                setupAt = doc.getString("setupAt") ?: ""
            ).also { config ->
                if (config.workerBaseUrl.isNotBlank()) {
                    workerBaseUrl = config.workerBaseUrl
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Get config error: ${e.message}", e)
            null
        }
    }

    suspend fun isSetupDone(): Boolean {
        val config = getConfig()
        return config != null && config.apiToken.isNotBlank() && config.webhookUrl.isNotBlank()
    }

    // ── Pincode Serviceability ─────────────────────────────────────────────

    suspend fun checkPincodeServiceability(pincode: String): Result<DelhiveryServiceabilityResult> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return Result.failure(Exception("User not signed in"))

            val request = Request.Builder()
                .url(
                    buildWorkerUrl(
                        "/delhivery/pincode",
                        mapOf(
                            "userId" to userId,
                            "pincode" to pincode
                        )
                    )
                )
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            if (response.isSuccessful) {
                Result.success(
                    DelhiveryServiceabilityResult(
                        pincode = pincode,
                        serviceable = json.optBoolean("serviceable", false),
                        prepaid = json.optBoolean("prepaid", false),
                        cod = json.optBoolean("cod", false),
                        pickup = json.optBoolean("pickup", false)
                    )
                )
            } else {
                Result.failure(Exception(json.optString("error", "Serviceability check failed")))
            }
        } catch (e: Exception) {
            Log.e(tag, "Pincode check error: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Create Order ───────────────────────────────────────────────────────

    /**
     * Creates a B2C Delhivery shipment.
     * Call this when an order WhatsApp message is received.
     */
    suspend fun createOrder(orderRequest: DelhiveryOrderRequest): Result<DelhiveryOrderResponse> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return Result.failure(Exception("User not signed in"))

            val body = JSONObject().apply {
                put("userId", userId)
                put("chatPhone", orderRequest.chatPhone)
                put("chatName", orderRequest.chatName)
                put("customerName", orderRequest.customerName)
                put("customerPhone", orderRequest.customerPhone)
                put("customerEmail", orderRequest.customerEmail)
                put("address", orderRequest.address)
                put("city", orderRequest.city)
                put("state", orderRequest.state)
                put("pincode", orderRequest.pincode)
                put("country", orderRequest.country)
                put("paymentMode", orderRequest.paymentMode)
                put("codAmount", orderRequest.codAmount)
                put("amount", orderRequest.amount)
                put("productName", orderRequest.productName)
                put("sku", orderRequest.sku)
                put("quantity", orderRequest.quantity)
                put("weight", orderRequest.weight)
                put("length", orderRequest.length)
                put("breadth", orderRequest.breadth)
                put("height", orderRequest.height)
                put("waybill", orderRequest.waybill)
            }.toString().toRequestBody(jsonType)

            val request = Request.Builder()
                .url("$workerBaseUrl/delhivery/create-order")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            if (response.isSuccessful && json.optBoolean("success", false)) {
                Result.success(
                    DelhiveryOrderResponse(
                        success = true,
                        orderId = json.optString("orderId", ""),
                        awb = json.optString("awb", ""),
                        status = json.optString("status", "CREATED"),
                        firestorePath = json.optString("firestorePath", "")
                    )
                )
            } else {
                Result.failure(Exception(json.optString("error", "Order creation failed")))
            }
        } catch (e: Exception) {
            Log.e(tag, "Create order error: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Track Order ────────────────────────────────────────────────────────

    suspend fun trackOrder(awb: String): Result<DelhiveryTrackingInfo> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return Result.failure(Exception("User not signed in"))

            val request = Request.Builder()
                .url(
                    buildWorkerUrl(
                        "/delhivery/track",
                        mapOf(
                            "userId" to userId,
                            "awb" to awb
                        )
                    )
                )
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            if (response.isSuccessful) {
                // Parse inner Delhivery tracking data
                val tracking = json.optJSONObject("tracking")
                val shipmentData = tracking?.optJSONArray("ShipmentData")
                val shipment = shipmentData
                    ?.optJSONObject(0)
                    ?.optJSONObject("Shipment")
                val statusObj = shipment?.optJSONObject("Status")

                Result.success(
                    DelhiveryTrackingInfo(
                        awb = awb,
                        status = statusObj?.optString("Status", "Unknown") ?: "Unknown",
                        expectedDelivery = shipment?.optString("ExpectedDeliveryDate", "") ?: "",
                        lastLocation = statusObj?.optString("StatusLocation")
                            ?.takeIf { it.isNotBlank() }
                            ?: statusObj?.optString("Instructions", "").orEmpty(),
                        lastStatusAt = getCurrentIsoTimestamp()
                    )
                )
            } else {
                Result.failure(Exception(json.optString("error", "Tracking failed")))
            }
        } catch (e: Exception) {
            Log.e(tag, "Track error: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Cancel Order ───────────────────────────────────────────────────────

    suspend fun cancelOrder(waybill: String): Result<String> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return Result.failure(Exception("User not signed in"))

            val body = JSONObject().apply {
                put("userId", userId)
                put("waybill", waybill)
            }.toString().toRequestBody(jsonType)

            val request = Request.Builder()
                .url("$workerBaseUrl/delhivery/cancel-order")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            if (response.isSuccessful) {
                Result.success("Order $waybill cancelled successfully")
            } else {
                Result.failure(Exception(json.optString("error", "Cancel failed")))
            }
        } catch (e: Exception) {
            Log.e(tag, "Cancel error: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Fetch Orders from Firestore ────────────────────────────────────────

    suspend fun fetchMyOrders(): List<Map<String, Any>> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return emptyList()

            val snapshot = firestore
                .collectionGroup("delhivery_orders")
                .whereEqualTo("ownerUserId", userId)
                .limit(100)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.data?.toMutableMap()?.also { map ->
                    map["__docId"] = doc.id
                    val orderCreatedAt = map["orderCreatedAt"] as? String
                    if (!orderCreatedAt.isNullOrBlank()) {
                        map["orderCreatedAtDisplay"] = formatIsoForDisplay(orderCreatedAt)
                    }
                }
            }.sortedByDescending {
                (it["orderCreatedAt"] as? String) ?: ""
            }
        } catch (e: Exception) {
            Log.e(tag, "Fetch orders error: ${e.message}", e)
            emptyList()
        }
    }

    // ── Shipping Cost ──────────────────────────────────────────────────────

    suspend fun calculateShippingCost(
        originPin: String,
        destinationPin: String,
        weightKg: Double,
        paymentMode: String = "Prepaid",
        codAmount: Double = 0.0
    ): Result<JSONObject> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return Result.failure(Exception("User not signed in"))

            val request = Request.Builder()
                .url(
                    buildWorkerUrl(
                        "/delhivery/shipping-cost",
                        mapOf(
                            "userId" to userId,
                            "originPin" to originPin,
                            "destinationPin" to destinationPin,
                            "weight" to weightKg.toString(),
                            "paymentMode" to paymentMode,
                            "codAmount" to codAmount.toString()
                        )
                    )
                )
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            if (response.isSuccessful) {
                Result.success(json.optJSONObject("shippingCost") ?: json)
            } else {
                Result.failure(Exception(json.optString("error", "Cost calculation failed")))
            }
        } catch (e: Exception) {
            Log.e(tag, "Shipping cost error: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Pickup Request ─────────────────────────────────────────────────────

    suspend fun createPickupRequest(
        pickupDate: String,       // yyyy-MM-dd
        pickupLocation: String? = null,
        packageCount: Int = 1,
        pickupTime: String = "10:00:00"
    ): Result<String> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return Result.failure(Exception("User not signed in"))

            val body = JSONObject().apply {
                put("userId", userId)
                put("pickupDate", pickupDate)
                put("pickupTime", pickupTime)
                put("expectedPackageCount", packageCount)
                if (!pickupLocation.isNullOrBlank()) {
                    put("pickupLocation", pickupLocation)
                }
            }.toString().toRequestBody(jsonType)

            val request = Request.Builder()
                .url("$workerBaseUrl/delhivery/pickup")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            if (response.isSuccessful) {
                val pickupId = json.optString("pickupId", "")
                Result.success("Pickup scheduled. ID: $pickupId")
            } else {
                Result.failure(Exception(json.optString("error", "Pickup request failed")))
            }
        } catch (e: Exception) {
            Log.e(tag, "Pickup error: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    suspend fun fetchWaybills(count: Int = 1, clientName: String? = null): Result<List<String>> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return Result.failure(Exception("User not signed in"))

            val query = mutableMapOf(
                "userId" to userId,
                "count" to count.coerceIn(1, 10_000).toString()
            )
            if (!clientName.isNullOrBlank()) {
                query["clientName"] = clientName
            }

            val request = Request.Builder()
                .url(
                    buildWorkerUrl(
                        "/delhivery/waybill",
                        query
                    )
                )
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            if (!response.isSuccessful) {
                return Result.failure(Exception(json.optString("error", "Waybill fetch failed")))
            }

            val waybills = mutableListOf<String>()
            collectWaybills(json.opt("waybills"), waybills)
            collectWaybills(json.opt("data"), waybills)
            val singleWaybill = json.optString("waybill")
            if (singleWaybill.isNotBlank()) {
                waybills.add(singleWaybill)
            }

            Result.success(waybills.map { it.trim() }.filter { it.isNotBlank() }.distinct())
        } catch (e: Exception) {
            Log.e(tag, "Fetch waybills error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateEwaybill(waybill: String, ewaybillNumber: String): Result<String> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return Result.failure(Exception("User not signed in"))

            val body = JSONObject().apply {
                put("userId", userId)
                put("waybill", waybill)
                put("ewaybillNumber", ewaybillNumber)
            }.toString().toRequestBody(jsonType)

            val request = Request.Builder()
                .url("$workerBaseUrl/delhivery/ewaybill")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            if (response.isSuccessful) {
                Result.success("E-waybill updated for $waybill")
            } else {
                Result.failure(Exception(json.optString("error", "E-waybill update failed")))
            }
        } catch (e: Exception) {
            Log.e(tag, "E-waybill update error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun downloadLabelPdf(awb: String): Result<ByteArray> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return Result.failure(Exception("User not signed in"))

            val request = Request.Builder()
                .url(
                    buildWorkerUrl(
                        "/delhivery/label",
                        mapOf(
                            "userId" to userId,
                            "awb" to awb
                        )
                    )
                )
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body
            if (response.isSuccessful && body != null) {
                Result.success(body.bytes())
            } else {
                val errorText = body?.string().orEmpty()
                val errorMessage = if (errorText.startsWith("{")) {
                    JSONObject(errorText).optString("error", "Label generation failed")
                } else {
                    "Label generation failed"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(tag, "Label download error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun buildWorkerUrl(path: String, queryParams: Map<String, String>): String {
        val base = workerBaseUrl.toHttpUrlOrNull()
            ?: throw IllegalStateException("Invalid workerBaseUrl: $workerBaseUrl")

        val normalizedPath = "/" + path.trim().trim('/')
        val basePath = base.encodedPath.trimEnd('/')
        val resolvedPath = if (basePath.isBlank() || basePath == "/") {
            normalizedPath
        } else {
            "$basePath$normalizedPath"
        }

        val builder = base.newBuilder().encodedPath(resolvedPath)
        queryParams.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        return builder.build().toString()
    }

    private fun collectWaybills(node: Any?, output: MutableList<String>) {
        when (node) {
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectWaybills(node.opt(i), output)
                }
            }
            is JSONObject -> {
                val candidateKeys = listOf("waybill", "wbn", "awb", "value")
                candidateKeys.forEach { key ->
                    val value = node.optString(key)
                    if (value.isNotBlank()) {
                        output.add(value)
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    collectWaybills(node.opt(key), output)
                }
            }
            is String -> {
                val cleaned = node.trim()
                if (cleaned.matches(Regex("^(?=.*\\d)[A-Za-z0-9-]{6,}$"))) {
                    output.add(cleaned)
                }
            }
        }
    }

    private fun formatIsoForDisplay(value: String): String {
        val output = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val inputFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            },
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        )

        inputFormats.forEach { format ->
            try {
                val parsed = format.parse(value)
                if (parsed != null) return output.format(parsed)
            } catch (_: Exception) {
                // Try next format.
            }
        }
        return value
    }

    private fun getCurrentIsoTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }
}

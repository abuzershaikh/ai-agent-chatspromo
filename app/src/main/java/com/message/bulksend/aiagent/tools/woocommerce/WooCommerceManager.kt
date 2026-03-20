package com.message.bulksend.aiagent.tools.woocommerce

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.message.bulksend.aiagent.tools.reverseai.ReverseAIManager
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * WooCommerce Manager
 *
 * Handles communication with the woocommerce-worker Cloudflare Worker
 * and reads real-time order data from Firestore.
 *
 * Firestore path: users/{uid}/woocommerce/config
 *                 users/{uid}/woocommerce/{orderId}
 *                 users/{uid}/woocommerce_alerts/{alertId}
 */
class WooCommerceManager(private val context: Context) {

    private val tag = "WooCommerceManager"

    // Default deployed WooCommerce worker URL
    var workerBaseUrl = "https://woocommerce-worker.aawuazer.workers.dev"

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

    // ─── SETUP ───────────────────────────────────────────────────────────────

    /**
     * Connect user's WooCommerce store to this app.
     * Saves uid, email, siteUrl, webhookSecret, ownerWhatsappNumber to Firestore.
     * ownerWhatsappNumber is auto-resolved from Reverse AI Owner Assistant settings.
     * via the woocommerce-worker /setup endpoint.
     *
     * Returns the webhook URL to paste into WooCommerce settings.
     */
    suspend fun setupWooCommerce(
        siteUrl: String,
        webhookSecret: String,
        fcmToken: String = "",
        pushNotificationsEnabled: Boolean = true,
        whatsappAlertsEnabled: Boolean = true
    ): Result<WooCommerceConfig> {
        return try {
            val uid = currentUserId
            val email = currentUserEmail
            if (uid.isBlank()) return Result.failure(Exception("User not logged in"))
            val ownerWhatsappNumber = ReverseAIManager(context).ownerPhoneNumber.trim()
            if (whatsappAlertsEnabled && ownerWhatsappNumber.isBlank()) {
                return Result.failure(
                    Exception("Owner Assistant number is not set. Configure it in Reverse AI settings.")
                )
            }

            val body = JSONObject().apply {
                put("uid", uid)
                put("email", email)
                put("siteUrl", siteUrl)
                put("webhookSecret", webhookSecret)
                put("ownerWhatsappNumber", ownerWhatsappNumber)
                put("fcmToken", fcmToken)
                put("pushNotificationsEnabled", pushNotificationsEnabled)
                put("whatsappAlertsEnabled", whatsappAlertsEnabled)
            }.toString().toRequestBody(jsonType)

            val request = Request.Builder()
                .url("$workerBaseUrl/setup")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            val json = if (responseBody.isNotBlank()) JSONObject(responseBody) else JSONObject()

            if (response.isSuccessful && json.optBoolean("success", false)) {
                val webhookUrl = json.optString("webhookUrl")

                // Also update fcmToken locally in Firestore (worker may not have it yet)
                if (fcmToken.isNotBlank()) {
                    saveFieldsToFirestore(uid, mapOf("fcmToken" to fcmToken))
                }

                val config = WooCommerceConfig(
                    uid = uid,
                    email = email,
                    siteUrl = siteUrl,
                    webhookUrl = webhookUrl,
                    webhookSecret = webhookSecret,
                    ownerWhatsappNumber = ownerWhatsappNumber,
                    fcmToken = fcmToken,
                    pushNotificationsEnabled = pushNotificationsEnabled,
                    whatsappAlertsEnabled = whatsappAlertsEnabled,
                    setupAt = getCurrentIsoTimestamp()
                )
                Result.success(config)
            } else {
                Result.failure(Exception(json.optString("error", "Setup failed")))
            }
        } catch (e: Exception) {
            Log.e(tag, "Setup error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update only the FCM token in Firestore (call this after FCM token refresh)
     */
    suspend fun updateFcmToken(fcmToken: String) {
        val uid = currentUserId
        if (uid.isBlank() || fcmToken.isBlank()) return
        try {
            saveFieldsToFirestore(uid, mapOf("fcmToken" to fcmToken))
            Log.d(tag, "FCM token updated for uid=$uid")
        } catch (e: Exception) {
            Log.e(tag, "FCM token update error: ${e.message}", e)
        }
    }

    // ─── CONFIG ──────────────────────────────────────────────────────────────

    /**
     * Fetch WooCommerce config from Firestore.
     * Returns null if not set up yet.
     */
    suspend fun getConfig(): WooCommerceConfig? {
        return try {
            val uid = currentUserId
            if (uid.isBlank()) return null

            val doc = firestore
                .collection("users")
                .document(uid)
                .collection("woocommerce")
                .document("config")
                .get()
                .await()

            if (!doc.exists()) return null

            WooCommerceConfig(
                uid = doc.getString("uid") ?: uid,
                email = doc.getString("email") ?: currentUserEmail,
                siteUrl = doc.getString("siteUrl") ?: "",
                webhookUrl = doc.getString("webhookUrl") ?: "",
                webhookSecret = doc.getString("webhookSecret") ?: "",
                ownerWhatsappNumber = doc.getString("ownerWhatsappNumber") ?: "",
                fcmToken = doc.getString("fcmToken") ?: "",
                pushNotificationsEnabled = doc.getBoolean("pushNotificationsEnabled") ?: true,
                whatsappAlertsEnabled = doc.getBoolean("whatsappAlertsEnabled") ?: true,
                setupAt = doc.getString("setupAt") ?: "",
                lastOrderAt = doc.getString("lastOrderAt") ?: "",
                lastOrderId = doc.getString("lastOrderId") ?: ""
            )
        } catch (e: Exception) {
            Log.e(tag, "Get config error: ${e.message}", e)
            null
        }
    }

    /**
     * Check if WooCommerce has been set up for this user.
     */
    suspend fun isSetupDone(): Boolean {
        val config = getConfig()
        return config != null && config.webhookUrl.isNotBlank()
    }

    // ─── ORDERS ──────────────────────────────────────────────────────────────

    /**
     * Fetch orders from Firestore (most recent 50).
     */
    suspend fun fetchOrders(): List<WooCommerceOrder> = fetchOrdersFlat()

    /**
     * Fetch orders from users/{uid}/woocommerce/{orderId}.
     * This is the Firestore path written by the worker.
     */
    suspend fun fetchOrdersFlat(): List<WooCommerceOrder> {
        return try {
            val uid = currentUserId
            if (uid.isBlank()) return emptyList()

            // Worker writes order docs directly under users/{uid}/woocommerce/{orderId}.
            // "config" also lives in this collection, so filter by orderId field.

            val snapshot = firestore
                .collection("users")
                .document(uid)
                .collection("woocommerce")
                .get()
                .await()

            snapshot.documents
                .filter { doc ->
                    // Only docs that look like orders (have orderId field)
                    doc.getString("orderId") != null
                }
                .map { doc -> mapDocToOrder(doc) }
                .sortedByDescending { it.receivedAt }
                .take(50)
        } catch (e: Exception) {
            Log.e(tag, "Fetch orders flat error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Fetch pending WhatsApp alerts created by worker.
     */
    suspend fun fetchPendingAlerts(limit: Int = 20): List<WooCommerceAlert> {
        return try {
            val uid = currentUserId
            if (uid.isBlank()) return emptyList()

            val snapshot = firestore
                .collection("users")
                .document(uid)
                .collection("woocommerce_alerts")
                .whereEqualTo("status", "pending")
                .limit(limit.toLong())
                .get()
                .await()

            snapshot.documents.mapNotNull { doc -> mapDocToAlert(doc, uid) }
        } catch (e: Exception) {
            Log.e(tag, "Fetch pending alerts error: ${e.message}", e)
            emptyList()
        }
    }

    private fun mapDocToOrder(doc: com.google.firebase.firestore.DocumentSnapshot): WooCommerceOrder {
        return WooCommerceOrder(
            orderId = doc.getString("orderId") ?: doc.id,
            ownerUid = doc.getString("ownerUid") ?: "",
            customerName = doc.getString("customerName") ?: "",
            customerEmail = doc.getString("customerEmail") ?: "",
            customerPhone = doc.getString("customerPhone") ?: "",
            total = doc.getString("total") ?: "0",
            currency = doc.getString("currency") ?: "INR",
            status = doc.getString("status") ?: "",
            items = doc.getString("items") ?: "",
            rawTopic = doc.getString("rawTopic") ?: "",
            receivedAt = doc.getString("receivedAt") ?: "",
            siteUrl = doc.getString("siteUrl") ?: ""
        )
    }

    /**
     * Listen to new orders in real-time (Firestore snapshot listener).
     * Call removeListener() on the returned ListenerRegistration when done.
     */
    fun listenToOrders(onUpdate: (List<WooCommerceOrder>) -> Unit): ListenerRegistration {
        val uid = currentUserId
        return firestore
            .collection("users")
            .document(uid)
            .collection("woocommerce")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "Orders listener error: ${error.message}", error)
                    return@addSnapshotListener
                }
                val orders = snapshot?.documents
                    ?.filter { doc -> doc.getString("orderId") != null }
                    ?.map { doc -> mapDocToOrder(doc) }
                    ?.sortedByDescending { it.receivedAt }
                    ?.take(50)
                    ?: emptyList()

                onUpdate(orders)
            }
    }

    // ─── PENDING ALERTS ──────────────────────────────────────────────────────

    /**
     * Listen for pending WhatsApp alerts queued by the Worker.
     * The app should send the WhatsApp message and then mark the alert as "sent".
     */
    fun listenForPendingAlerts(onAlert: (WooCommerceAlert) -> Unit): ListenerRegistration {
        val uid = currentUserId
        return firestore
            .collection("users")
            .document(uid)
            .collection("woocommerce_alerts")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "Alert listener error: ${error.message}", error)
                    return@addSnapshotListener
                }
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type != DocumentChange.Type.ADDED) return@forEach
                    val alert = mapDocToAlert(change.document, uid) ?: return@forEach
                    onAlert(alert)
                }
            }
    }

    /**
     * Mark an alert as sent so it won't be processed again.
     */
    suspend fun markAlertSent(alertId: String) {
        val uid = currentUserId
        if (uid.isBlank() || alertId.isBlank()) return
        try {
            firestore
                .collection("users")
                .document(uid)
                .collection("woocommerce_alerts")
                .document(alertId)
                .set(mapOf("status" to "sent", "sentAt" to getCurrentIsoTimestamp()), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Mark alert sent error: ${e.message}", e)
        }
    }

    /**
     * Track a failed send attempt without removing the alert from queue.
     */
    suspend fun markAlertAttemptFailed(alertId: String, errorMessage: String) {
        val uid = currentUserId
        if (uid.isBlank() || alertId.isBlank()) return
        try {
            firestore
                .collection("users")
                .document(uid)
                .collection("woocommerce_alerts")
                .document(alertId)
                .set(
                    mapOf(
                        "lastAttemptAt" to getCurrentIsoTimestamp(),
                        "lastError" to errorMessage.take(500),
                        "attemptCount" to FieldValue.increment(1)
                    ),
                    SetOptions.merge()
                )
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Mark alert attempt failed error: ${e.message}", e)
        }
    }

    // ─── PRIVATE HELPERS ─────────────────────────────────────────────────────

    private suspend fun saveFieldsToFirestore(uid: String, fields: Map<String, Any>) {
        firestore
            .collection("users")
            .document(uid)
            .collection("woocommerce")
            .document("config")
            .set(fields, SetOptions.merge())
            .await()
    }

    private fun mapDocToAlert(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        defaultUid: String
    ): WooCommerceAlert? {
        val alert = WooCommerceAlert(
            alertId = doc.id,
            uid = doc.getString("uid") ?: defaultUid,
            type = doc.getString("type") ?: "woocommerce_order_alert",
            toPhone = doc.getString("toPhone") ?: "",
            message = doc.getString("message") ?: "",
            status = doc.getString("status") ?: "pending",
            createdAt = doc.getString("createdAt") ?: ""
        )
        return if (alert.toPhone.isNotBlank() && alert.message.isNotBlank()) alert else null
    }

    private fun getCurrentIsoTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }
}

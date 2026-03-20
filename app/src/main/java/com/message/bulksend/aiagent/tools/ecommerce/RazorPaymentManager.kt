package com.message.bulksend.aiagent.tools.ecommerce

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import kotlin.math.max
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class RazorPaymentManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("razorpay_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

    // Worker URL - Hardcoded for this deployment
    private val WORKER_URL = "https://razorpay-webhook-worker.aawuazer.workers.dev"

    fun saveRazorpayCredentials(keyId: String, secret: String) {
        prefs.edit()
            .putString("key_id", keyId)
            .putString("key_secret", secret)
            .apply()
    }

    fun saveUserEmail(email: String) {
        prefs.edit().putString("user_email", email.lowercase()).apply()
    }

    fun getUserEmail(): String? = prefs.getString("user_email", null)

    fun getRazorpayKeyId(): String? = prefs.getString("key_id", null)
    fun getRazorpaySecret(): String? = prefs.getString("key_secret", null)
    fun getWebhookUrl(): String? = prefs.getString("webhook_url", null)
    
    fun isConfigured(): Boolean {
        return !getRazorpayKeyId().isNullOrBlank() && !getRazorpaySecret().isNullOrBlank() && !getUserEmail().isNullOrBlank()
    }

    // Redirect Settings
    fun saveRedirectSettings(mode: String, customUrl: String, waNumber: String, waMessage: String) {
        prefs.edit()
            .putString("redirect_mode", mode)
            .putString("redirect_custom_url", customUrl)
            .putString("redirect_wa_number", waNumber)
            .putString("redirect_wa_message", waMessage)
            .apply()
    }

    fun getRedirectMode(): String = prefs.getString("redirect_mode", "THANK_YOU") ?: "THANK_YOU"
    fun getCustomRedirectUrl(): String = prefs.getString("redirect_custom_url", "") ?: ""
    fun getWhatsAppNumber(): String = prefs.getString("redirect_wa_number", "") ?: ""
    fun getWhatsAppMessage(): String = prefs.getString("redirect_wa_message", "Payment successful! 🎉") ?: "Payment successful! 🎉"

    suspend fun registerWithWorker(workerUrl: String, email: String, keyId: String, secret: String): Boolean {
        // Save email locally when registering
        val lowerEmail = email.lowercase()
        saveUserEmail(lowerEmail)
        
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("RazorPaymentManager", "Registering $lowerEmail with $workerUrl")
                val json = JSONObject().apply {
                    put("userId", lowerEmail)
                    put("razorpayKeyId", keyId)
                    put("razorpaySecret", secret)
                    // forwardUrl is optional, defaults to creating payment links in Firestore
                }

                val request = Request.Builder()
                    .url("$workerUrl/register")
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .build()

                android.util.Log.d("RazorPaymentManager", "Sending request: $json")
                val response = client.newCall(request).execute()
                android.util.Log.d("RazorPaymentManager", "Response Code: ${response.code}")
                
                if (response.isSuccessful) {
                    // Calculate and save the webhook URL for reference
                    val webhookUrl = "$workerUrl/webhook/$email"
                    prefs.edit().putString("webhook_url", webhookUrl).apply()
                    android.util.Log.d("RazorPaymentManager", "Registration successful. Webhook: $webhookUrl")
                    return@withContext true
                } else {
                    android.util.Log.e("RazorPaymentManager", "Registration failed: ${response.body?.string()}")
                    return@withContext false
                }
            } catch (e: Exception) {
                android.util.Log.e("RazorPaymentManager", "Exception during registration", e)
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    /**
     * Creates a payment link via the Cloudflare Worker
     */
    suspend fun createPaymentLink(
        amount: Double,
        description: String,
        customerName: String,
        customerPhone: String,
        email: String? = null // Optional, uses stored if null
    ): String? {
        val merchantEmail = email ?: getUserEmail()
        if (merchantEmail == null) return null

        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("email", merchantEmail.lowercase())
                    put("amount", amount)
                    put("description", description)
                    put("customerName", customerName)
                    put("customerContact", customerPhone)

                    // Determine Callback URL based on settings
                    var callbackUrl: String? = null
                    val mode = getRedirectMode()
                    if (mode == "URL") {
                        callbackUrl = getCustomRedirectUrl().takeIf { it.isNotBlank() }
                    } else if (mode == "WHATSAPP") {
                        val number = getWhatsAppNumber().takeIf { it.isNotBlank() }
                        val message = getWhatsAppMessage()
                        if (number != null) {
                            val encodedMessage =  java.net.URLEncoder.encode(message, "UTF-8")
                            callbackUrl = "https://wa.me/$number?text=$encodedMessage"
                        }
                    }
                    
                    if (callbackUrl != null) {
                        put("callback_url", callbackUrl)
                    }
                }

                val request = Request.Builder()
                    .url("$WORKER_URL/create-payment-link")
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext null

                if (response.isSuccessful) {
                    val responseJson = JSONObject(responseBody)
                    return@withContext responseJson.optString("short_url")
                } else {
                    return@withContext null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    data class PaymentLinkInfo(
        val id: String,
        val amount: Double,
        val description: String,
        val status: String,
        val shortUrl: String,
        val createdAt: String,
        val customerName: String?,
        val customerContact: String?
    )

    fun getPaymentLinksFlow(email: String): kotlinx.coroutines.flow.Flow<List<PaymentLinkInfo>> = kotlinx.coroutines.flow.callbackFlow {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val collection = db.collection("users").document(email.lowercase()).collection("payment_links")
        
        val registration = collection.orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    android.util.Log.e("RazorPaymentManager", "Listen failed.", e)
                    close(e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    android.util.Log.d("RazorPaymentManager", "Received ${snapshots.size()} payment links for $email")
                    val links = snapshots.documents.map { doc ->
                        val status = doc.getString("status") ?: "unknown"
                        android.util.Log.d("RazorPaymentManager", "Link: ${doc.id}, Status: $status")
                        PaymentLinkInfo(
                            id = doc.id,
                            amount = doc.getDouble("amount") ?: 0.0,
                            description = doc.getString("description") ?: "",
                            status = status,
                            shortUrl = doc.getString("short_url") ?: "",
                            createdAt = doc.getString("created_at") ?: "",
                            customerName = doc.getString("customer_name"),
                            customerContact = doc.getString("customer_contact")
                        )
                    }
                    trySend(links)
                }
            }
            
        awaitClose { registration.remove() }
    }
    /**
     * Fetch the latest payment link for a specific customer phone number from Firestore
     */
    suspend fun getLatestPaymentLinkForUser(customerPhone: String): PaymentLinkInfo? {
        val email = getUserEmail() ?: return null
        val normalizedTargetPhone = normalizePhone(customerPhone)
        
        return withContext(Dispatchers.IO) {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val collection = db.collection("users").document(email.lowercase()).collection("payment_links")

                // Index-free approach:
                // 1) Get latest documents ordered by created_at (single-field index)
                // 2) Match phone client-side using normalized comparison
                val snapshot = try {
                    collection
                        .orderBy("created_at", Query.Direction.DESCENDING)
                        .limit(100)
                        .get()
                        .await()
                } catch (e: Exception) {
                    android.util.Log.e(
                        "RazorPaymentManager",
                        "Ordered fetch failed, fallback to unordered fetch: ${e.message}"
                    )
                    collection.limit(100).get().await()
                }

                if (snapshot.isEmpty) return@withContext null

                val matchedDoc =
                    snapshot.documents.firstOrNull { doc ->
                        isSamePhone(
                            targetPhone = normalizedTargetPhone,
                            candidatePhone = doc.getString("customer_contact")
                        )
                    }

                if (matchedDoc != null) {
                    toPaymentLinkInfo(matchedDoc)
                } else {
                    android.util.Log.d(
                        "RazorPaymentManager",
                        "No payment link matched for phone=$customerPhone (normalized=$normalizedTargetPhone)"
                    )
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("RazorPaymentManager", "Error fetching payment link: ${e.message}")
                null
            }
        }
    }

    /**
     * Fetch recent payment links for a specific customer phone number.
     * Used for smarter payment-source decisions and real-time verification checks.
     */
    suspend fun getRecentPaymentLinksForUser(
        customerPhone: String,
        limit: Int = 20
    ): List<PaymentLinkInfo> {
        val email = getUserEmail() ?: return emptyList()
        val normalizedTargetPhone = normalizePhone(customerPhone)
        if (normalizedTargetPhone.isBlank()) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()
                val collection = db.collection("users").document(email.lowercase()).collection("payment_links")
                val fetchLimit = max(limit * 5, 50).coerceAtMost(300).toLong()

                val snapshot = try {
                    collection
                        .orderBy("created_at", Query.Direction.DESCENDING)
                        .limit(fetchLimit)
                        .get()
                        .await()
                } catch (e: Exception) {
                    android.util.Log.e(
                        "RazorPaymentManager",
                        "Ordered recent fetch failed, fallback to unordered fetch: ${e.message}"
                    )
                    collection.limit(fetchLimit).get().await()
                }

                snapshot.documents
                    .filter { doc ->
                        isSamePhone(
                            targetPhone = normalizedTargetPhone,
                            candidatePhone = doc.getString("customer_contact")
                        )
                    }
                    .map { doc -> toPaymentLinkInfo(doc) }
                    .take(limit)
            } catch (e: Exception) {
                android.util.Log.e("RazorPaymentManager", "Error fetching recent links: ${e.message}")
                emptyList()
            }
        }
    }

    private fun toPaymentLinkInfo(doc: DocumentSnapshot): PaymentLinkInfo {
        val status = doc.getString("status") ?: "unknown"
        return PaymentLinkInfo(
            id = doc.id,
            amount = doc.getDouble("amount") ?: 0.0,
            description = doc.getString("description") ?: "",
            status = status,
            shortUrl = doc.getString("short_url") ?: "",
            createdAt = doc.getString("created_at") ?: "",
            customerName = doc.getString("customer_name"),
            customerContact = doc.getString("customer_contact")
        )
    }

    private fun normalizePhone(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        return phone.replace(Regex("[^0-9]"), "")
    }

    private fun isSamePhone(targetPhone: String, candidatePhone: String?): Boolean {
        if (targetPhone.isBlank() || candidatePhone.isNullOrBlank()) return false

        val candidate = normalizePhone(candidatePhone)
        if (candidate.isBlank()) return false
        if (targetPhone == candidate) return true

        val targetLast10 = targetPhone.takeLast(10)
        val candidateLast10 = candidate.takeLast(10)
        return targetLast10.length == 10 && targetLast10 == candidateLast10
    }

    /**
     * Verify payment status directly from Razorpay API
     * Uses the stored Key ID and Secret
     */
    suspend fun verifyPaymentStatusFromApi(plinkId: String): String {
        val keyId = getRazorpayKeyId()
        val secret = getRazorpaySecret()
        
        if (keyId.isNullOrBlank() || secret.isNullOrBlank()) {
            return "unknown_no_creds"
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val credentials =okhttp3.Credentials.basic(keyId, secret)
                
                val request = Request.Builder()
                    .url("https://api.razorpay.com/v1/payment_links/$plinkId")
                    .header("Authorization", credentials)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    // Razorpay returns status: created, paid, cancelled, expired
                    val status = json.optString("status", "unknown")
                    
                    // Also check if amount_paid matches amount
                    val amountPaid = json.optLong("amount_paid", 0)
                    val amount = json.optLong("amount", 0)
                    
                    android.util.Log.d("RazorPaymentManager", "API Status for $plinkId: $status (Paid: $amountPaid/$amount)")
                    
                    status
                } else {
                    android.util.Log.e("RazorPaymentManager", "API Error: ${response.code} - $responseBody")
                    "api_error"
                }
            } catch (e: Exception) {
                android.util.Log.e("RazorPaymentManager", "Exception verifying payment: ${e.message}")
                "exception"
            }
        }
    }
    
    // Helper for Firestore await
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            addOnSuccessListener { result ->
                cont.resume(result)
            }
            addOnFailureListener { exception ->
                cont.resumeWith(Result.failure(exception))
            }
        }
    }
}

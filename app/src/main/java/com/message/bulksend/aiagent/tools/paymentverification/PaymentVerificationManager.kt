package com.message.bulksend.aiagent.tools.paymentverification

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Locale

/**
 * Manager for Payment Verification System
 * Each app user gets unique webhook URL based on their email
 */
class PaymentVerificationManager(private val context: Context) {
    
    private val database = PaymentVerificationDatabase.getDatabase(context)
    private val dao = database.verificationDao()
    private val firestore = FirebaseFirestore.getInstance()
    private var firestoreListener: ListenerRegistration? = null
    private val settingsPrefs =
        context.getSharedPreferences("payment_verification_settings", Context.MODE_PRIVATE)
    private val alertPrefs =
        context.getSharedPreferences("payment_verification_owner_alerts", Context.MODE_PRIVATE)
    private val ownerNotifier = OwnerPaymentReviewNotifier(context.applicationContext)
    
    companion object {
        private const val TAG = "PaymentVerificationMgr"
        private const val BASE_URL = "https://us-central1-mailtracker-demo.cloudfunctions.net/verifyPaymentScreenshot"
        private const val UPLOAD_PAGE_URL = "https://mailtracker-demo.web.app/payment-verify.html"
        private const val KEY_DECISION_MODE = "decision_mode"
        private const val ALERTED_PREFIX = "alerted_"
        
        @Volatile
        private var INSTANCE: PaymentVerificationManager? = null
        
        fun getInstance(context: Context): PaymentVerificationManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PaymentVerificationManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    /**
     * Generate unique webhook URL for current user
     * Format: https://us-central1-chatspromo.cloudfunctions.net/verifyPaymentScreenshot?userId={UID}
     */
    fun generateWebhookUrl(): String {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (userId.isEmpty()) {
            Log.e(TAG, "User not logged in")
            return BASE_URL
        }
        
        val webhookUrl = "$BASE_URL?userId=$userId"
        Log.d(TAG, "Generated webhook URL with UID: $webhookUrl")
        return webhookUrl
    }
    
    /**
     * Generate payment verification link for customer
     * Customer will upload screenshot here
     */
    fun generateCustomerLink(
        customerPhone: String, 
        orderId: String = "",
        expectedName: String = "",
        expectedUpiId: String = "",
        expectedAmount: Double = 0.0,
        customFieldValues: Map<String, String> = emptyMap()
    ): String {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        
        if (userId.isEmpty()) {
            Log.e(TAG, "User not logged in")
            return ""
        }
        
        // Save expected details to Firestore if provided
        if (expectedName.isNotEmpty() || expectedUpiId.isNotEmpty() || expectedAmount > 0 || customFieldValues.isNotEmpty()) {
            saveExpectedDetails(customerPhone, orderId, expectedName, expectedUpiId, expectedAmount, customFieldValues)
        }
        
        // Generate unique link with UID (not sanitized email)
        val params = buildString {
            append("?userId=$userId")  // Send actual Firebase UID
            append("&phone=$customerPhone")
            if (orderId.isNotEmpty()) {
                append("&orderId=$orderId")
            }
        }
        
        val customerLink = UPLOAD_PAGE_URL + params
        Log.d(TAG, "Generated customer link with UID: $customerLink")
        return customerLink
    }
    
    /**
     * Save expected payment details to Firestore
     */
    suspend fun saveExpectedDetailsToFirestore(
        customerPhone: String,
        orderId: String,
        expectedName: String,
        expectedUpiId: String,
        expectedAmount: Double,
        customFieldValues: Map<String, String> = emptyMap()
    ) {
        val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: return
        val sanitizedEmail = userEmail.replace("@", "_at_").replace(".", "_dot_")
        
        val expectedData = hashMapOf(
            "customerPhone" to customerPhone,
            "orderId" to orderId,
            "expectedName" to expectedName,
            "expectedUpiId" to expectedUpiId,
            "expectedAmount" to expectedAmount,
            "customFieldValues" to customFieldValues,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        
        try {
            firestore.collection("expectedPayments")
                .document(sanitizedEmail)
                .collection("expected")
                .document("${customerPhone}_${orderId}")
                .set(expectedData)
                .await()
            
            Log.d(TAG, "Saved expected details to Firestore: $customerPhone, $orderId, customFields: $customFieldValues")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save expected details to Firestore", e)
        }
    }
    
    /**
     * Get expected payment details from Firestore
     */
    suspend fun getExpectedDetailsFromFirestore(
        customerPhone: String,
        orderId: String
    ): Map<String, Any>? {
        val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: return null
        val sanitizedEmail = userEmail.replace("@", "_at_").replace(".", "_dot_")
        
        return try {
            val doc = firestore.collection("expectedPayments")
                .document(sanitizedEmail)
                .collection("expected")
                .document("${customerPhone}_${orderId}")
                .get()
                .await()
            
            if (doc.exists()) {
                doc.data
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get expected details from Firestore", e)
            null
        }
    }
    
    /**
     * Save expected payment details for verification
     */
    fun saveExpectedDetails(
        customerPhone: String,
        orderId: String,
        expectedName: String,
        expectedUpiId: String,
        expectedAmount: Double,
        customFieldValues: Map<String, String> = emptyMap()
    ) {
        // Save to Firestore in background
        CoroutineScope(Dispatchers.IO).launch {
            saveExpectedDetailsToFirestore(customerPhone, orderId, expectedName, expectedUpiId, expectedAmount, customFieldValues)
        }
        
        Log.d(TAG, "Saving expected details: name=$expectedName, upi=$expectedUpiId, amount=$expectedAmount, customFields=$customFieldValues")
    }
    
    /**
     * Get expected payment details
     */
    suspend fun getExpectedDetails(customerPhone: String, orderId: String): Map<String, Any> {
        // Try to get from Firestore first
        val firestoreData = getExpectedDetailsFromFirestore(customerPhone, orderId)
        if (firestoreData != null) {
            return mapOf(
                "expectedName" to (firestoreData["expectedName"] as? String ?: ""),
                "expectedUpiId" to (firestoreData["expectedUpiId"] as? String ?: ""),
                "expectedAmount" to ((firestoreData["expectedAmount"] as? Number)?.toDouble() ?: 0.0)
            )
        }
        
        return mapOf(
            "expectedName" to "",
            "expectedUpiId" to "",
            "expectedAmount" to 0.0
        )
    }
    
    /**
     * Start listening to Firestore for new payment verifications
     */
    fun startListening() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.e(TAG, "❌ Cannot start listening: User not logged in")
            return
        }
        val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: run {
            Log.e(TAG, "❌ Cannot start listening: User email not found")
            return
        }
        val sanitizedEmail = userEmail.replace("@", "_at_").replace(".", "_dot_")
        
        Log.d(TAG, "🎧 Starting Firestore listener")
        Log.d(TAG, "   User ID: $userId")
        Log.d(TAG, "   User Email: $userEmail")
        Log.d(TAG, "   Sanitized Email: $sanitizedEmail")
        Log.d(TAG, "   Listening on path 1: users/$userId/paymentVerifications")
        Log.d(TAG, "   Listening on path 2: users/$sanitizedEmail/paymentVerifications")
        
        // Listen to user's payment verifications collection (by UID)
        firestoreListener = firestore
            .collection("users")
            .document(userId)
            .collection("paymentVerifications")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Firestore listener error (UID path)", error)
                    return@addSnapshotListener
                }
                
                if (snapshots != null) {
                    Log.d(TAG, "📥 Received ${snapshots.documentChanges.size} changes (UID path)")
                    
                    if (snapshots.isEmpty) {
                        Log.d(TAG, "   Collection is empty (UID path)")
                    }
                    
                    for (change in snapshots.documentChanges) {
                        Log.d(TAG, "   Change type: ${change.type}, Document: ${change.document.id}")
                        
                        when (change.type) {
                            com.google.firebase.firestore.DocumentChange.Type.ADDED,
                            com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                val doc = change.document
                                Log.d(TAG, "🔍 Processing document: ${doc.id}")
                                Log.d(TAG, "   Document data keys: ${doc.data.keys}")
                                
                                try {
                                    // Parse Firestore document to PaymentVerification
                                    val verification = parseFirestoreDocument(doc.id, doc.data)
                                    Log.d(TAG, "✅ Parsed verification: ${verification.customerPhone}, ${verification.orderId}")
                                    
                                    // Save to local database
                                    CoroutineScope(Dispatchers.IO).launch {
                                        persistIncomingVerification(verification = verification, source = "firestore_listener")
                                        Log.d(TAG, "💾 Saved verification to local DB: ${verification.id}")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Error parsing document: ${doc.id}", e)
                                    Log.e(TAG, "   Document data: ${doc.data}")
                                }
                            }
                            else -> {
                                Log.d(TAG, "   Skipping change type: ${change.type}")
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "⚠️ Snapshots is null (UID path)")
                }
            }
        
        // ALSO listen with sanitized email as fallback (for when UID lookup fails)
        firestore
            .collection("users")
            .document(sanitizedEmail)
            .collection("paymentVerifications")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Firestore listener error (email path)", error)
                    return@addSnapshotListener
                }
                
                if (snapshots != null) {
                    Log.d(TAG, "📥 Received ${snapshots.documentChanges.size} changes (email path)")
                    
                    if (snapshots.isEmpty) {
                        Log.d(TAG, "   Collection is empty (email path)")
                    }
                    
                    if (snapshots.documentChanges.isNotEmpty()) {
                        for (change in snapshots.documentChanges) {
                            Log.d(TAG, "   Change type: ${change.type}, Document: ${change.document.id}")
                            
                            when (change.type) {
                                com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                    val doc = change.document
                                    Log.d(TAG, "🔍 Processing document (email): ${doc.id}")
                                    Log.d(TAG, "   Document data keys: ${doc.data.keys}")
                                    
                                    try {
                                        val verification = parseFirestoreDocument(doc.id, doc.data)
                                        Log.d(TAG, "✅ Parsed verification (email): ${verification.customerPhone}, ${verification.orderId}")
                                        
                                        CoroutineScope(Dispatchers.IO).launch {
                                            persistIncomingVerification(verification = verification, source = "firestore_listener")
                                            Log.d(TAG, "💾 Saved verification to local DB (email): ${verification.id}")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Error parsing document (email): ${doc.id}", e)
                                        Log.e(TAG, "   Document data: ${doc.data}")
                                    }
                                }
                                else -> {
                                    Log.d(TAG, "   Skipping change type: ${change.type}")
                                }
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "⚠️ Snapshots is null (email path)")
                }
            }
        
        Log.d(TAG, "✅ Firestore listeners started successfully")
    }
    
    /**
     * Manually fetch all verifications from Firestore
     */
    suspend fun fetchFromFirestore() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.e(TAG, "❌ Cannot fetch: User not logged in")
            return
        }
        
        val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
        
        Log.d(TAG, "🔄 Manually fetching from Firestore...")
        Log.d(TAG, "   User ID: $userId")
        Log.d(TAG, "   User Email: $userEmail")
        Log.d(TAG, "   Query path: users/$userId/paymentVerifications")
        
        try {
            val collectionRef = firestore
                .collection("users")
                .document(userId)
                .collection("paymentVerifications")
            
            Log.d(TAG, "   Collection reference created: ${collectionRef.path}")
            
            val snapshot = collectionRef.get().await()
            
            Log.d(TAG, "📥 Fetched ${snapshot.documents.size} documents from Firestore")
            Log.d(TAG, "   Is empty: ${snapshot.isEmpty}")
            Log.d(TAG, "   Document count: ${snapshot.size()}")
            
            if (snapshot.isEmpty) {
                Log.w(TAG, "⚠️ Collection is empty! No documents found at path: users/$userId/paymentVerifications")
                Log.w(TAG, "   This could mean:")
                Log.w(TAG, "   1. Cloud Function saved to different path")
                Log.w(TAG, "   2. Data not synced yet (eventual consistency)")
                Log.w(TAG, "   3. Firestore rules blocking read access")
                Log.w(TAG, "   4. User ID mismatch between app and cloud function")
            }
            
            snapshot.documents.forEach { doc ->
                Log.d(TAG, "📄 Document ID: ${doc.id}")
                Log.d(TAG, "   Document exists: ${doc.exists()}")
                Log.d(TAG, "   Document data keys: ${doc.data?.keys}")
                
                try {
                    val verification = parseFirestoreDocument(doc.id, doc.data ?: emptyMap())
                    persistIncomingVerification(verification = verification, source = "firestore_listener")
                    Log.d(TAG, "💾 Saved to local DB: ${verification.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error parsing document: ${doc.id}", e)
                    Log.e(TAG, "   Document data: ${doc.data}")
                }
            }
            
            Log.d(TAG, "✅ Fetch complete! Processed ${snapshot.documents.size} documents")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fetch failed with exception", e)
            Log.e(TAG, "   Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "   Exception message: ${e.message}")
            Log.e(TAG, "   User ID used: $userId")
        }
    }
    
    /**
     * Stop listening to Firestore
     */
    fun stopListening() {
        firestoreListener?.remove()
        firestoreListener = null
        Log.d(TAG, "Stopped Firestore listener")
    }
    
    /**
     * Parse Firestore document to PaymentVerification object
     */
        private fun parseFirestoreDocument(docId: String, data: Map<String, Any>): PaymentVerification {
        Log.d(TAG, "Parsing document: $docId")
        Log.d(TAG, "Data keys: ${data.keys}")

        val paymentDetails = data["paymentDetails"] as? Map<String, Any> ?: emptyMap()
        val metadata = data["metadata"] as? Map<String, Any> ?: emptyMap()
        val customFields = data["customFields"] as? Map<String, Any> ?: emptyMap()
        val customFieldsExpected = data["customFieldsExpected"] as? Map<String, Any> ?: emptyMap()

        val recommendation =
            normalizeRecommendation(
                recommendationRaw = data["recommendation"] as? String ?: "",
                geminiRawResponse = data["geminiRawResponse"] as? String ?: ""
            )
        val isFake = data["isFake"] as? Boolean ?: false
        val confidence = (data["confidence"] as? Number)?.toInt() ?: 0
        val expectedName = data["expectedName"] as? String ?: ""
        val expectedUpiId = data["expectedUpiId"] as? String ?: ""
        val expectedAmount = (data["expectedAmount"] as? Number)?.toDouble() ?: 0.0
        val nameMatched = data["nameMatched"] as? Boolean ?: false
        val upiMatched = data["upiMatched"] as? Boolean ?: false
        val amountMatched = data["amountMatched"] as? Boolean ?: false
        val customFieldsMatched = data["customFieldsMatched"] as? Boolean ?: false
        val paymentStatus = paymentDetails["status"] as? String ?: ""
        val geminiRawResponse = data["geminiRawResponse"] as? String ?: ""
        val strictResult =
            evaluateStrictFieldMatch(
                paymentStatus = paymentStatus,
                geminiRawResponse = geminiRawResponse,
                isFake = isFake,
                confidence = confidence,
                expectedName = expectedName,
                expectedUpiId = expectedUpiId,
                expectedAmount = expectedAmount,
                nameMatched = nameMatched,
                upiMatched = upiMatched,
                amountMatched = amountMatched,
                customFieldsExpected = customFieldsExpected,
                customFieldsExtracted = customFields
            )
        val status =
            deriveStatusByMode(
                firestoreStatus = data["status"] as? String ?: "",
                strictMatch = strictResult
            )
        val notes =
            buildAutoStatusNotes(
                recommendation = recommendation,
                status = status,
                nameMatched = nameMatched,
                upiMatched = upiMatched,
                amountMatched = amountMatched,
                customFieldsMatched = customFieldsMatched,
                isFake = isFake,
                confidence = confidence,
                mode = getDecisionMode(),
                strictResult = strictResult
            )

        return PaymentVerification(
            id = docId,
            customerPhone = data["customerPhone"] as? String ?: "",
            orderId = data["orderId"] as? String ?: "",
            timestamp = (data["timestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time
                ?: System.currentTimeMillis(),
            recommendation = recommendation,
            isFake = isFake,
            confidence = confidence,

            // Payment Details
            upiId = paymentDetails["upiId"] as? String ?: "",
            amount = (paymentDetails["amount"] as? Number)?.toDouble() ?: 0.0,
            paymentDate = paymentDetails["date"] as? String ?: "",
            paymentTime = paymentDetails["time"] as? String ?: "",
            transactionId = paymentDetails["transactionId"] as? String ?: "",
            payeeName = paymentDetails["payeeName"] as? String ?: "",
            payerName = paymentDetails["payerName"] as? String ?: "",
            paymentStatus = paymentStatus,

            // Expected Details
            expectedName = expectedName,
            expectedUpiId = expectedUpiId,
            expectedAmount = expectedAmount,

            // Match Results
            nameMatched = nameMatched,
            upiMatched = upiMatched,
            amountMatched = amountMatched,

            // Custom Fields
            customFieldsExtracted = org.json.JSONObject(customFields).toString(),
            customFieldsExpected = org.json.JSONObject(customFieldsExpected).toString(),
            customFieldsMatched = customFieldsMatched,

            // Metadata
            screenshotTimestamp = metadata["screenshotTimestamp"] as? String ?: "",
            uploadTimestamp = metadata["uploadTimestamp"] as? String ?: "",
            timeDifferenceMinutes = (metadata["timeDifferenceMinutes"] as? Number)?.toDouble() ?: 0.0,
            isWithinTimeLimit = metadata["isWithinTimeLimit"] as? Boolean ?: false,

            // AI Analysis
            reasoning = data["reasoning"] as? String ?: "",
            geminiRawResponse = geminiRawResponse,

            // Status
            status = status,
            processedAt = if (status == "PENDING") 0 else System.currentTimeMillis(),
            notes = notes
        )
    }

    private fun deriveStatusByMode(
        firestoreStatus: String,
        strictMatch: StrictMatchResult
    ): String {
        val normalizedFirestoreStatus = normalizeStatus(firestoreStatus)
        if (normalizedFirestoreStatus !in setOf("", "PENDING")) {
            return normalizedFirestoreStatus
        }

        return when (getDecisionMode()) {
            PaymentDecisionMode.OWNER_CONFIRM -> "PENDING"
            PaymentDecisionMode.AUTO_CONFIRM_STRICT -> {
                if (strictMatch.matched) "APPROVED" else "PENDING"
            }
        }
    }

    private data class StrictMatchResult(
        val matched: Boolean,
        val configuredFieldCount: Int,
        val mismatchLabels: List<String>,
        val reason: String
    )

    private fun evaluateStrictFieldMatch(
        paymentStatus: String,
        geminiRawResponse: String,
        isFake: Boolean,
        confidence: Int,
        expectedName: String,
        expectedUpiId: String,
        expectedAmount: Double,
        nameMatched: Boolean,
        upiMatched: Boolean,
        amountMatched: Boolean,
        customFieldsExpected: Map<String, Any>,
        customFieldsExtracted: Map<String, Any>
    ): StrictMatchResult {
        if (!isPaymentSuccess(paymentStatus, geminiRawResponse)) {
            return StrictMatchResult(
                matched = false,
                configuredFieldCount = 0,
                mismatchLabels = listOf("payment_status"),
                reason = "payment_not_success"
            )
        }

        if (isFake || confidence < 85) {
            return StrictMatchResult(
                matched = false,
                configuredFieldCount = 0,
                mismatchLabels = listOf("authenticity"),
                reason = "fake_or_low_confidence"
            )
        }

        val mismatches = mutableListOf<String>()
        var configured = 0

        if (expectedName.isNotBlank()) {
            configured += 1
            if (!nameMatched) mismatches += "Name"
        }

        if (expectedUpiId.isNotBlank()) {
            configured += 1
            if (!upiMatched) mismatches += "UPI ID"
        }

        if (expectedAmount > 0.0) {
            configured += 1
            if (!amountMatched) mismatches += "Amount"
        }

        val expectedCustom =
            customFieldsExpected.mapNotNull { (key, value) ->
                val label = key.trim()
                val expectedValue = normalizeMapValue(value)
                if (label.isBlank() || expectedValue.isBlank()) null else label to expectedValue
            }

        val extractedCustom =
            customFieldsExtracted.mapKeys { it.key.trim() }.mapValues { normalizeMapValue(it.value) }

        expectedCustom.forEach { (label, expectedValue) ->
            configured += 1
            val actualValue = extractedCustom[label].orEmpty()
            if (!actualValue.equals(expectedValue, ignoreCase = true)) {
                mismatches += label
            }
        }

        if (configured == 0) {
            return StrictMatchResult(
                matched = false,
                configuredFieldCount = 0,
                mismatchLabels = listOf("no_expected_fields"),
                reason = "no_expected_fields"
            )
        }

        return StrictMatchResult(
            matched = mismatches.isEmpty(),
            configuredFieldCount = configured,
            mismatchLabels = mismatches,
            reason = if (mismatches.isEmpty()) "all_fields_matched" else "field_value_mismatch"
        )
    }

    private fun normalizeMapValue(value: Any?): String {
        if (value == null) return ""
        return when (value) {
            is Number -> {
                val number = value.toDouble()
                if (number % 1.0 == 0.0) number.toInt().toString() else String.format(Locale.US, "%.2f", number)
            }
            else -> value.toString().trim()
        }
    }

    private fun buildAutoStatusNotes(
        recommendation: String,
        status: String,
        nameMatched: Boolean,
        upiMatched: Boolean,
        amountMatched: Boolean,
        customFieldsMatched: Boolean,
        isFake: Boolean,
        confidence: Int,
        mode: PaymentDecisionMode,
        strictResult: StrictMatchResult
    ): String {
        return listOf(
                "auto_status=$status",
                "decision_mode=${mode.name}",
                "recommendation=$recommendation",
                "fake=$isFake",
                "confidence=$confidence",
                "strictMatched=${strictResult.matched}",
                "configuredFields=${strictResult.configuredFieldCount}",
                "strictReason=${strictResult.reason}",
                "strictMismatches=${strictResult.mismatchLabels.joinToString(",")}",
                "nameMatched=$nameMatched",
                "upiMatched=$upiMatched",
                "amountMatched=$amountMatched",
                "customFieldsMatched=$customFieldsMatched"
            )
            .joinToString(" | ")
    }

    private fun normalizeRecommendation(
        recommendationRaw: String,
        geminiRawResponse: String
    ): String {
        val recommendation = recommendationRaw.trim().uppercase()
        if (recommendation in setOf("PAID", "MANUAL_REVIEW", "REJECTED")) {
            return recommendation
        }

        val raw = geminiRawResponse.lowercase()
        return when {
            raw.contains("manual_review") -> "MANUAL_REVIEW"
            raw.contains("\"recommendation\":\"paid\"") || raw.contains("\"recommendation\": \"paid\"") -> "PAID"
            raw.contains("\"recommendation\":\"rejected\"") || raw.contains("\"recommendation\": \"rejected\"") -> "REJECTED"
            raw.contains("\"recommendation\":\"manual_review\"") || raw.contains("\"recommendation\": \"manual_review\"") -> "MANUAL_REVIEW"
            else -> recommendation.ifBlank { "MANUAL_REVIEW" }
        }
    }

    private fun normalizeStatus(statusRaw: String): String {
        return when (statusRaw.trim().uppercase()) {
            "APPROVED", "APPROVE", "PAID", "VERIFIED" -> "APPROVED"
            "REJECTED", "REJECT" -> "REJECTED"
            "MANUAL_REVIEW", "MANUAL REVIEW", "REVIEW" -> "MANUAL_REVIEW"
            "PROCESSED" -> "PROCESSED"
            "PENDING" -> "PENDING"
            else -> ""
        }
    }

    private fun isPaymentSuccess(paymentStatus: String, geminiRawResponse: String): Boolean {
        val statusLower = paymentStatus.lowercase()
        if (
            statusLower.contains("success") ||
                statusLower.contains("successful") ||
                statusLower.contains("succeed") ||
                statusLower.contains("completed") ||
                statusLower.contains("paid")
        ) {
            return true
        }

        val raw = geminiRawResponse.lowercase()
        return raw.contains("\"status\":\"success\"") ||
            raw.contains("\"status\": \"success\"") ||
            raw.contains("\"status\":\"successful\"") ||
            raw.contains("\"status\": \"successful\"") ||
            raw.contains("\"status\":\"completed\"") ||
            raw.contains("\"status\": \"completed\"")
    }

    private suspend fun persistIncomingVerification(
        verification: PaymentVerification,
        source: String
    ) {
        val previous = dao.getById(verification.id)
        dao.insert(verification)

        if (verification.status == "PENDING") {
            val shouldNotify =
                previous == null ||
                    previous.status != "PENDING" ||
                    previous.notes != verification.notes
            if (shouldNotify) {
                triggerOwnerReviewIfPending(
                    verification = verification,
                    source = source
                )
            }
        } else {
            clearOwnerAlertFlag(verification.id)
        }
    }

    suspend fun triggerOwnerReviewIfPending(
        verification: PaymentVerification,
        source: String
    ) {
        if (verification.status != "PENDING") return
        if (isOwnerAlertSent(verification.id)) return

        ownerNotifier.notifyPendingReview(verification = verification, source = source)
        markOwnerAlertSent(verification.id)
    }

    private fun markOwnerAlertSent(id: String) {
        alertPrefs.edit().putBoolean("$ALERTED_PREFIX$id", true).apply()
    }

    private fun isOwnerAlertSent(id: String): Boolean {
        return alertPrefs.getBoolean("$ALERTED_PREFIX$id", false)
    }

    private fun clearOwnerAlertFlag(id: String) {
        alertPrefs.edit().remove("$ALERTED_PREFIX$id").apply()
    }
    /**
     * Get all verifications
     */
    fun getAllVerifications(): Flow<List<PaymentVerification>> = dao.getAllVerifications()
    
    /**
     * Get pending verifications
     */
    fun getPendingVerifications(): Flow<List<PaymentVerification>> = dao.getPendingVerifications()
    
    /**
     * Get verifications by recommendation
     */
    fun getByRecommendation(recommendation: String): Flow<List<PaymentVerification>> = 
        dao.getByRecommendation(recommendation)
    
    /**
     * Get verifications by customer phone
     */
    fun getByCustomerPhone(phone: String): Flow<List<PaymentVerification>> = 
        dao.getByCustomerPhone(phone)

    /**
     * Get latest verification by customer phone (handles +91/spacing differences)
     */
    suspend fun getLatestVerificationForCustomer(customerPhone: String): PaymentVerification? {
        if (customerPhone.isBlank()) return null

        val exact = dao.getByCustomerPhone(customerPhone).first().firstOrNull()
        if (exact != null) return exact

        val normalizedTargetPhone = normalizePhone(customerPhone)
        if (normalizedTargetPhone.isBlank()) return null

        val recent = dao.getRecentVerifications(250)
        return recent.firstOrNull { verification ->
            isSamePhone(
                targetPhone = normalizedTargetPhone,
                candidatePhone = verification.customerPhone
            )
        }
    }
    
    /**
     * Get pending count
     */
    fun getPendingCount(): Flow<Int> = dao.getPendingCount()

    fun getContext(): Context = context.applicationContext

    fun getDecisionMode(): PaymentDecisionMode {
        val raw = settingsPrefs.getString(KEY_DECISION_MODE, PaymentDecisionMode.OWNER_CONFIRM.name)
        return runCatching {
                PaymentDecisionMode.valueOf(raw ?: PaymentDecisionMode.OWNER_CONFIRM.name)
            }
            .getOrDefault(PaymentDecisionMode.OWNER_CONFIRM)
    }

    fun setDecisionMode(mode: PaymentDecisionMode) {
        settingsPrefs.edit().putString(KEY_DECISION_MODE, mode.name).apply()
        Log.d(TAG, "Payment decision mode updated: $mode")
    }

    suspend fun getVerificationById(id: String): PaymentVerification? {
        return dao.getById(id)
    }

    suspend fun getLatestPendingVerification(): PaymentVerification? {
        return dao.getLatestPending()
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
     * Update verification status
     */
    suspend fun updateStatus(id: String, status: String, notes: String = "") {
        dao.updateStatus(id, status, System.currentTimeMillis(), notes)
        Log.d(TAG, "Updated verification status: $id -> $status")
        if (status != "PENDING") {
            clearOwnerAlertFlag(id)
        }
        
        // Also update in Firestore
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            firestore
                .collection("users")
                .document(userId)
                .collection("paymentVerifications")
                .document(id)
                .update(
                    mapOf(
                        "status" to status,
                        "processedAt" to com.google.firebase.Timestamp.now(),
                        "notes" to notes
                    )
                )
                .await()
            Log.d(TAG, "Updated Firestore status: $id -> $status")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Firestore status", e)
        }
    }
    
    /**
     * Approve payment (mark as PAID)
     */
    suspend fun approvePayment(id: String, notes: String = "Approved by user") {
        updateStatus(id, "APPROVED", notes)
    }
    
    /**
     * Reject payment
     */
    suspend fun rejectPayment(id: String, notes: String = "Rejected by user") {
        updateStatus(id, "REJECTED", notes)
    }
    
    /**
     * Mark as processed
     */
    suspend fun markAsProcessed(id: String, notes: String = "Processed") {
        updateStatus(id, "PROCESSED", notes)
    }
    
    /**
     * Cleanup old verifications (older than 30 days)
     */
    suspend fun cleanup() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        dao.deleteOldVerifications(thirtyDaysAgo)
        Log.d(TAG, "Cleaned up old verifications")
    }
    
    /**
     * Debug method to check Firestore path and document existence
     */
    suspend fun debugFirestorePath() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.e(TAG, "❌ DEBUG: User not logged in")
            return
        }
        val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
        val sanitizedEmail = userEmail.replace("@", "_at_").replace(".", "_dot_")
        
        Log.d(TAG, "🔍 DEBUG: Firestore Path Check")
        Log.d(TAG, "   User ID (UID): $userId")
        Log.d(TAG, "   User Email: $userEmail")
        Log.d(TAG, "   Sanitized Email: $sanitizedEmail")
        Log.d(TAG, "   Expected path 1: users/$userId/paymentVerifications")
        Log.d(TAG, "   Expected path 2: users/$sanitizedEmail/paymentVerifications")
        
        // Check UID path
        try {
            val uidSnapshot = firestore
                .collection("users")
                .document(userId)
                .collection("paymentVerifications")
                .limit(10)
                .get()
                .await()
            
            Log.d(TAG, "✅ UID Path Check:")
            Log.d(TAG, "   Documents found: ${uidSnapshot.size()}")
            uidSnapshot.documents.forEach { doc ->
                Log.d(TAG, "   - Document ID: ${doc.id}")
                Log.d(TAG, "     Customer: ${doc.getString("customerPhone")}")
                Log.d(TAG, "     Order: ${doc.getString("orderId")}")
                Log.d(TAG, "     Recommendation: ${doc.getString("recommendation")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ UID Path Error", e)
        }
        
        // Check sanitized email path
        try {
            val emailSnapshot = firestore
                .collection("users")
                .document(sanitizedEmail)
                .collection("paymentVerifications")
                .limit(10)
                .get()
                .await()
            
            Log.d(TAG, "✅ Email Path Check:")
            Log.d(TAG, "   Documents found: ${emailSnapshot.size()}")
            emailSnapshot.documents.forEach { doc ->
                Log.d(TAG, "   - Document ID: ${doc.id}")
                Log.d(TAG, "     Customer: ${doc.getString("customerPhone")}")
                Log.d(TAG, "     Order: ${doc.getString("orderId")}")
                Log.d(TAG, "     Recommendation: ${doc.getString("recommendation")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Email Path Error", e)
        }
        
        Log.d(TAG, "🔍 DEBUG: Path check complete")
    }
    
    /**
     * Get custom fields for verification
     */
    /**
     * Delete verification (Clear from UI)
     */
    suspend fun deleteVerification(id: String) {
        try {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
            firestore.collection("users").document(userId).collection("paymentVerifications").document(id).delete().await()
            android.util.Log.d(TAG, "Deleted Firestore verification: $id")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed deleted Firestore", e)
        }
        try {
            updateStatus(id, "CLEARED", "Cleared by user")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to update local status", e)
        }
    }

    fun getCustomFields(): List<com.message.bulksend.aiagent.tools.paymentverification.CustomField> {
        val prefs = context.getSharedPreferences("payment_verification_custom_fields", Context.MODE_PRIVATE)
        val fieldsJson = prefs.getString("custom_fields", "[]") ?: "[]"
        
        return try {
            val fields = mutableListOf<com.message.bulksend.aiagent.tools.paymentverification.CustomField>()
            if (fieldsJson != "[]") {
                // Parse JSON array
                val jsonArray = fieldsJson.trim().removeSurrounding("[", "]")
                if (jsonArray.isNotEmpty()) {
                    jsonArray.split("},").forEach { fieldJson ->
                        val cleanJson = if (fieldJson.endsWith("}")) fieldJson else "$fieldJson}"
                        val nameMatch = "\"fieldName\":\"([^\"]+)\"".toRegex().find(cleanJson)
                        val descMatch = "\"fieldDescription\":\"([^\"]+)\"".toRegex().find(cleanJson)
                        
                        if (nameMatch != null && descMatch != null) {
                            fields.add(com.message.bulksend.aiagent.tools.paymentverification.CustomField(nameMatch.groupValues[1], descMatch.groupValues[1]))
                        }
                    }
                }
            }
            fields
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing custom fields", e)
            emptyList()
        }
    }
    
    /**
     * Save custom fields
     */
    fun saveCustomFields(fields: List<com.message.bulksend.aiagent.tools.paymentverification.CustomField>) {
        val prefs = context.getSharedPreferences("payment_verification_custom_fields", Context.MODE_PRIVATE)
        
        val fieldsJson = if (fields.isEmpty()) {
            "[]"
        } else {
            buildString {
                append("[")
                fields.forEachIndexed { index, field ->
                    if (index > 0) append(",")
                    append("{\"fieldName\":\"${field.fieldName}\",\"fieldDescription\":\"${field.fieldDescription}\"}")
                }
                append("]")
            }
        }
        
        prefs.edit().putString("custom_fields", fieldsJson).apply()
        Log.d(TAG, "Saved ${fields.size} custom fields")
    }
    
    /**
     * Get custom fields as prompt for Gemini
     */
    fun getCustomFieldsPrompt(): String {
        val fields = getCustomFields()
        if (fields.isEmpty()) return ""
        
        return buildString {
            appendLine("\nCUSTOM FIELDS TO EXTRACT:")
            fields.forEach { field ->
                appendLine("- ${field.fieldName}: ${field.fieldDescription}")
            }
        }
    }
}

enum class PaymentDecisionMode {
    OWNER_CONFIRM,
    AUTO_CONFIRM_STRICT
}


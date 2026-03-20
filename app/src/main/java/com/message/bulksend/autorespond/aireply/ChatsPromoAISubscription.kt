package com.message.bulksend.autorespond.aireply

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * SIMPLE ChatsPromo AI Subscription Manager
 * Firestore = Boss, Android = Follower
 * No complex logic, just sync and check
 */
class ChatsPromoAISubscriptionManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ChatsPromoAI"
        private const val COLLECTION_NAME = "chatspromo_ai_subscriptions"
        private const val PREFS_NAME = "chatspromo_ai_subscription"

        // Simple prefs keys
        private const val KEY_IS_ACTIVE = "is_active"
        private const val KEY_SUB_END = "subscription_end_time"
        private const val KEY_LAST_SYNC = "last_sync_time"
        private const val KEY_TRIAL_END = "trial_end_time"
    }

    /**
     * Get current user email
     */
    fun getUserEmail(): String? = auth.currentUser?.email

    /**
     * Get sanitized email for Firestore document ID
     */
    private fun getSanitizedEmail(): String? {
        return getUserEmail()?.replace(".", "_")
    }

    // ==================== SIMPLE SYNC (SINGLE METHOD) ====================

    /**
     * Simple sync from Firestore - just copy what's there
     */
    suspend fun syncFromFirestore(): Boolean = withContext(Dispatchers.IO) {
        val email = getSanitizedEmail() ?: return@withContext false

        try {
            // Debug auth status
            Log.d(TAG, "=== AUTH DEBUG ===")
            Log.d(TAG, "Current user: ${auth.currentUser?.email}")
            Log.d(TAG, "Sanitized email: $email")
            Log.d(TAG, "Document path: chatspromo_ai_subscriptions/$email")
            
            Log.d(TAG, "Syncing from Firestore for: $email")
            val doc = firestore.collection(COLLECTION_NAME).document(email).get().await()

            if (!doc.exists()) {
                clearPrefs()
                Log.d(TAG, "❌ No subscription document found at: chatspromo_ai_subscriptions/$email")
                Log.d(TAG, "Expected document for: sonathe333@gmail.com → sonathe333_gmail_com")
                return@withContext false
            }
            
            Log.d(TAG, "✅ Document found! Data: ${doc.data}")

            val isActive = doc.getBoolean("isActive") ?: false
            val endTimestamp = doc.getTimestamp("subscriptionEndDate")

            if (endTimestamp == null) {
                clearPrefs()
                Log.d(TAG, "No end date found")
                return@withContext false
            }

            val endMillis = endTimestamp.seconds * 1000

            // Simple save to prefs - just copy Firestore data
            prefs.edit().apply {
                putBoolean(KEY_IS_ACTIVE, isActive)
                putLong(KEY_SUB_END, endMillis)
                putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                apply()
            }

            Log.d(TAG, "✅ Subscription synced: active=$isActive, endTime=$endMillis")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing: ${e.message}")
            return@withContext false
        }
    }

    // ==================== FAST OFFLINE CHECK (MOST IMPORTANT) ====================

    /**
     * Fast offline check - this is what the whole app uses
     */
    fun canUseAI(): Boolean {
        val endTime = prefs.getLong(KEY_SUB_END, 0)
        val isActive = prefs.getBoolean(KEY_IS_ACTIVE, false)
        val currentTime = System.currentTimeMillis()

        val canUse = isActive && currentTime < endTime
        Log.d(TAG, "canUseAI: $canUse (active=$isActive, expired=${currentTime >= endTime})")
        return canUse
    }

    // ==================== TRIAL LOGIC (SEPARATE) ====================

    /**
     * Start 2 hour trial (local + Firestore)
     */
    suspend fun startTrial(): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            val trialEndTime = currentTime + (2 * 60 * 60 * 1000) // 2 hours

            // Save trial to prefs
            prefs.edit().apply {
                putLong(KEY_TRIAL_END, trialEndTime)
                putLong(KEY_LAST_SYNC, currentTime)
                apply()
            }

            Log.d(TAG, "✅ Trial started: ends at $trialEndTime")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Error starting trial: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Check if trial is active
     */
    fun isTrialActive(): Boolean {
        val trialEndTime = prefs.getLong(KEY_TRIAL_END, 0)
        val currentTime = System.currentTimeMillis()
        return trialEndTime > 0 && currentTime < trialEndTime
    }

    // ==================== SYNC CONTROL ====================

    /**
     * Ultra simple sync check - only sync when needed
     */
    fun needsSync(): Boolean {
        val endTime = prefs.getLong(KEY_SUB_END, 0)
        val currentTime = System.currentTimeMillis()

        // Only sync if: no data OR expired
        return endTime == 0L || currentTime >= endTime
    }

    /**
     * Force sync after purchase
     */
    suspend fun syncAfterPurchase(): Boolean {
        Log.d(TAG, "Syncing after purchase...")
        return syncFromFirestore()
    }

    // ==================== HELPER METHODS ====================

    /**
     * Clear all prefs
     */
    private fun clearPrefs() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Prefs cleared")
    }

    /**
     * Get subscription status for UI
     */
    fun getSubscriptionStatus(): SubscriptionStatus {
        return when {
            canUseAI() -> SubscriptionStatus.SUBSCRIBED
            isTrialActive() -> SubscriptionStatus.TRIAL_ACTIVE
            prefs.getLong(KEY_TRIAL_END, 0) > 0 -> SubscriptionStatus.TRIAL_EXPIRED
            else -> SubscriptionStatus.NO_TRIAL
        }
    }

    /**
     * Initialize - just sync once
     */
    suspend fun initialize(): Boolean {
        return syncFromFirestore()
    }

    /**
     * Get simple status for UI (no complex calculations)
     */
    fun getSimpleStatus(): String {
        return if (canUseAI()) "Active" else "Inactive"
    }
}

/**
 * Simple subscription status
 */
enum class SubscriptionStatus {
    NO_TRIAL,
    TRIAL_ACTIVE,
    TRIAL_EXPIRED,
    SUBSCRIBED
}
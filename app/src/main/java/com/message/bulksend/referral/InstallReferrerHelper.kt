package com.message.bulksend.referral

import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails

/**
 * Helper class to get install referrer from Play Store
 * This detects if user installed app via a referral link
 */
class InstallReferrerHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "InstallReferrerHelper"
        private const val REFERRER_PREFIX = "ref_"
        private const val PREFS_NAME = "referral_prefs"
        private const val KEY_REFERRER_CHECKED = "referrer_checked"
        private const val KEY_PENDING_REFERRAL_CODE = "pending_referral_code"
        private const val KEY_INSTALL_TRACKING_ID = "install_tracking_id"
        private const val KEY_ANONYMOUS_INSTALL_TRACKED_CODE = "anonymous_install_tracked_code"
    }
    
    private var referrerClient: InstallReferrerClient? = null
    
    /**
     * Check for install referrer from Play Store
     * Should be called once on first app launch
     * 
     * @param onResult Callback with referral code (null if not found)
     */
    fun checkInstallReferrer(onResult: (String?) -> Unit) {
        // Check if already checked
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REFERRER_CHECKED, false)) {
            Log.d(TAG, "Referrer already checked, returning cached code")
            val cachedCode = prefs.getString(KEY_PENDING_REFERRAL_CODE, null)
            onResult(cachedCode)
            return
        }
        
        referrerClient = InstallReferrerClient.newBuilder(context).build()
        
        referrerClient?.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        try {
                            val response: ReferrerDetails? = referrerClient?.installReferrer
                            val referrer = response?.installReferrer
                            
                            Log.d(TAG, "Install referrer received: $referrer")
                            Log.d(TAG, "Referrer click time: ${response?.referrerClickTimestampSeconds}")
                            Log.d(TAG, "Install begin time: ${response?.installBeginTimestampSeconds}")
                            
                            // Extract referral code from "ref_XXXX" format
                            val code = extractReferralCode(referrer)
                            
                            // Save to preferences
                            prefs.edit()
                                .putBoolean(KEY_REFERRER_CHECKED, true)
                                .putString(KEY_PENDING_REFERRAL_CODE, code)
                                .apply()
                            
                            Log.d(TAG, "Extracted referral code: $code")
                            onResult(code)
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting referrer details", e)
                            markAsChecked(prefs)
                            onResult(null)
                        }
                    }
                    
                    InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                        Log.d(TAG, "Install Referrer API not supported")
                        markAsChecked(prefs)
                        onResult(null)
                    }
                    
                    InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                        Log.d(TAG, "Install Referrer service unavailable")
                        // Don't mark as checked - might be temporary
                        onResult(null)
                    }
                    
                    else -> {
                        Log.d(TAG, "Unknown response code: $responseCode")
                        markAsChecked(prefs)
                        onResult(null)
                    }
                }
                
                // End connection
                endConnection()
            }
            
            override fun onInstallReferrerServiceDisconnected() {
                Log.d(TAG, "Install Referrer service disconnected")
                onResult(null)
            }
        })
    }
    
    /**
     * Extract referral code from referrer string
     * Expected format: "ref_XXXX" or "ref_XXXX&utm_source=..."
     */
    private fun extractReferralCode(referrer: String?): String? {
        if (referrer.isNullOrBlank()) return null
        
        // Decode URL encoded string
        val decoded = try {
            java.net.URLDecoder.decode(referrer, "UTF-8")
        } catch (e: Exception) {
            referrer
        }
        
        // Check if starts with our prefix
        return if (decoded.startsWith(REFERRER_PREFIX)) {
            // Extract code (handle case where there might be additional params)
            val codeWithParams = decoded.removePrefix(REFERRER_PREFIX)
            // Take only the code part (before any & or other params)
            codeWithParams.split("&", " ", "?").firstOrNull()?.trim()?.uppercase()
        } else {
            // Try to find ref_ anywhere in the string
            val refIndex = decoded.indexOf(REFERRER_PREFIX)
            if (refIndex >= 0) {
                val afterRef = decoded.substring(refIndex + REFERRER_PREFIX.length)
                afterRef.split("&", " ", "?").firstOrNull()?.trim()?.uppercase()
            } else {
                null
            }
        }
    }
    
    /**
     * Mark referrer as checked in preferences
     */
    private fun markAsChecked(prefs: android.content.SharedPreferences) {
        prefs.edit().putBoolean(KEY_REFERRER_CHECKED, true).apply()
    }
    
    /**
     * Set a test referral code for debugging (without Play Store)
     * Use: adb shell am start -n com.message.bulksend/.userdetails.UserDetailsActivity --es referral_code "TEST1234"
     */
    fun setTestReferralCode(code: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PENDING_REFERRAL_CODE, code.uppercase())
            .putBoolean(KEY_REFERRER_CHECKED, true)
            .remove(KEY_ANONYMOUS_INSTALL_TRACKED_CODE)
            .apply()
        Log.d(TAG, "Test referral code set: ${code.uppercase()}")
    }
    
    /**
     * Get pending referral code from preferences
     * Use this to get the code saved during install
     */
    fun getPendingReferralCode(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PENDING_REFERRAL_CODE, null)
    }
    
    /**
     * Clear pending referral code after it's been processed
     */
    fun clearPendingReferralCode() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_PENDING_REFERRAL_CODE).apply()
        Log.d(TAG, "Pending referral code cleared")
    }

    fun getOrCreateInstallTrackingId(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingId = prefs.getString(KEY_INSTALL_TRACKING_ID, null)
        if (!existingId.isNullOrBlank()) {
            return existingId
        }

        val newId = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_TRACKING_ID, newId).apply()
        Log.d(TAG, "Created install tracking ID: $newId")
        return newId
    }

    fun isAnonymousInstallTracked(referralCode: String? = null): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val trackedCode = prefs.getString(KEY_ANONYMOUS_INSTALL_TRACKED_CODE, null)
        return if (referralCode.isNullOrBlank()) {
            !trackedCode.isNullOrBlank()
        } else {
            trackedCode.equals(referralCode, ignoreCase = true)
        }
    }

    fun markAnonymousInstallTracked(referralCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ANONYMOUS_INSTALL_TRACKED_CODE, referralCode.uppercase()).apply()
        Log.d(TAG, "Anonymous install marked tracked for: ${referralCode.uppercase()}")
    }
    
    /**
     * Check if referrer has been checked
     */
    fun isReferrerChecked(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_REFERRER_CHECKED, false)
    }
    
    /**
     * End connection to referrer client
     */
    fun endConnection() {
        try {
            referrerClient?.endConnection()
            referrerClient = null
            Log.d(TAG, "Referrer client connection ended")
        } catch (e: Exception) {
            Log.e(TAG, "Error ending referrer connection", e)
        }
    }
}

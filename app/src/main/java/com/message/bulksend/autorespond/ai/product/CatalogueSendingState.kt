package com.message.bulksend.autorespond.ai.product

import android.content.Context
import android.util.Log

/**
 * Manages catalogue sending state
 * Ensures media files are sent after text reply
 */
class CatalogueSendingState private constructor() {
    
    companion object {
        const val TAG = "CatalogueSendingState"
        private const val PREFS_NAME = "catalogue_sending_state"
        private const val KEY_PENDING_CATALOGUE = "pending_catalogue_"
        private const val KEY_PRODUCT_ID = "product_id_"
        private const val KEY_TIMESTAMP = "timestamp_"
        
        @Volatile
        private var instance: CatalogueSendingState? = null
        
        fun getInstance(): CatalogueSendingState {
            return instance ?: synchronized(this) {
                instance ?: CatalogueSendingState().also { instance = it }
            }
        }
    }
    
    /**
     * Mark that catalogue needs to be sent for this phone number
     */
    fun setPendingCatalogue(context: Context, phoneNumber: String, productId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_PENDING_CATALOGUE + phoneNumber, true)
            .putLong(KEY_PRODUCT_ID + phoneNumber, productId)
            .putLong(KEY_TIMESTAMP + phoneNumber, System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "📌 Pending catalogue set for $phoneNumber, product ID: $productId")
    }
    
    /**
     * Check if catalogue is pending for this phone number
     */
    fun hasPendingCatalogue(context: Context, phoneNumber: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isPending = prefs.getBoolean(KEY_PENDING_CATALOGUE + phoneNumber, false)
        
        // Check if timestamp is not too old (max 5 minutes)
        if (isPending) {
            val timestamp = prefs.getLong(KEY_TIMESTAMP + phoneNumber, 0)
            val age = System.currentTimeMillis() - timestamp
            if (age > 5 * 60 * 1000) { // 5 minutes
                Log.w(TAG, "⚠️ Pending catalogue expired for $phoneNumber")
                clearPendingCatalogue(context, phoneNumber)
                return false
            }
        }
        
        return isPending
    }
    
    /**
     * Get pending product ID
     */
    fun getPendingProductId(context: Context, phoneNumber: String): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val productId = prefs.getLong(KEY_PRODUCT_ID + phoneNumber, -1L)
        return if (productId != -1L) productId else null
    }
    
    /**
     * Clear pending catalogue state
     */
    fun clearPendingCatalogue(context: Context, phoneNumber: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PENDING_CATALOGUE + phoneNumber)
            .remove(KEY_PRODUCT_ID + phoneNumber)
            .remove(KEY_TIMESTAMP + phoneNumber)
            .apply()
        
        Log.d(TAG, "✅ Pending catalogue cleared for $phoneNumber")
    }
}

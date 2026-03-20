package com.message.bulksend.notification

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * FCM Token Manager
 * Handles FCM token registration and updates
 */
object FCMTokenManager {
    
    private const val TAG = "FCMTokenManager"
    
    /**
     * Get current FCM token and save to Firestore
     * Call this after user login
     */
    suspend fun registerToken(context: Context): Boolean {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "FCM Token: $token")
            
            val email = FirebaseAuth.getInstance().currentUser?.email
            if (email != null) {
                FirebaseFirestore.getInstance()
                    .collection("email_data")
                    .document(email)
                    .update("pushToken", token)
                    .await()
                
                Log.d(TAG, "FCM token registered for: $email")
                
                // Clear any pending token
                val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                prefs.edit().remove("pending_token").apply()
                
                true
            } else {
                // Save token locally for later
                val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("pending_token", token).apply()
                Log.d(TAG, "FCM token saved locally (user not logged in)")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering FCM token", e)
            false
        }
    }
    
    /**
     * Check and register any pending token
     * Call this after user login
     */
    suspend fun registerPendingToken(context: Context): Boolean {
        val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        val pendingToken = prefs.getString("pending_token", null)
        
        if (pendingToken != null) {
            val email = FirebaseAuth.getInstance().currentUser?.email
            if (email != null) {
                return try {
                    FirebaseFirestore.getInstance()
                        .collection("email_data")
                        .document(email)
                        .update("pushToken", pendingToken)
                        .await()
                    
                    prefs.edit().remove("pending_token").apply()
                    Log.d(TAG, "Pending FCM token registered for: $email")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error registering pending token", e)
                    false
                }
            }
        }
        return false
    }
    
    /**
     * Get current FCM token
     */
    suspend fun getToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FCM token", e)
            null
        }
    }
    
    /**
     * Delete FCM token (call on logout)
     */
    suspend fun deleteToken(): Boolean {
        return try {
            FirebaseMessaging.getInstance().deleteToken().await()
            Log.d(TAG, "FCM token deleted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting FCM token", e)
            false
        }
    }
    
    /**
     * Subscribe to topic
     */
    suspend fun subscribeToTopic(topic: String): Boolean {
        return try {
            FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
            Log.d(TAG, "Subscribed to topic: $topic")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to topic", e)
            false
        }
    }
    
    /**
     * Unsubscribe from topic
     */
    suspend fun unsubscribeFromTopic(topic: String): Boolean {
        return try {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
            Log.d(TAG, "Unsubscribed from topic: $topic")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error unsubscribing from topic", e)
            false
        }
    }
}

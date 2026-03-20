package com.message.bulksend.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.message.bulksend.R
import com.message.bulksend.aiagent.tools.woocommerce.WooCommerceAlertProcessor
import com.message.bulksend.aiagent.tools.woocommerce.WooCommerceManager
import com.message.bulksend.support.CustomerChatSupportActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatNotificationService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "ChatNotificationService"
        const val CHANNEL_ID = "chat_support_channel"
        const val CHANNEL_NAME = "Chat Support"
        const val NOTIFICATION_ID = 1001
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        
        // Save token to Firestore
        saveFCMToken(token)

        // Keep WooCommerce config in sync so worker can push new-order events.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WooCommerceManager(applicationContext).updateFcmToken(token)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update WooCommerce FCM token: ${e.message}", e)
            }
        }
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "Message received from: ${remoteMessage.from}")
        Log.d(TAG, "Message data: ${remoteMessage.data}")
        
        val data = remoteMessage.data
        val notificationType = data["type"]
        
        when (notificationType) {
            "chat_support" -> {
                handleChatNotification(remoteMessage)
            }
            "woocommerce_order" -> {
                handleWooCommerceOrderNotification(remoteMessage)
            }
            else -> {
                // Handle other notification types or show default
                remoteMessage.notification?.let {
                    showNotification(
                        title = it.title ?: "New Message",
                        body = it.body ?: "",
                        data = data
                    )
                }
            }
        }
    }
    
    private fun handleChatNotification(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val notification = remoteMessage.notification
        
        val title = notification?.title ?: data["title"] ?: "💬 Support Reply"
        val body = notification?.body ?: data["body"] ?: "You have a new message"
        val oderId = data["oderId"] ?: ""
        
        showChatNotification(title, body, oderId)
    }

    private fun handleWooCommerceOrderNotification(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val title = notification?.title ?: data["title"] ?: "🛒 New WooCommerce Order!"
        val body = notification?.body ?: data["body"] ?: "A new WooCommerce order was received."

        showNotification(title, body, data)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = WooCommerceAlertProcessor(applicationContext)
                    .processPendingAlerts(limit = 5)
                Log.d(
                    TAG,
                    "Woo alerts processed. fetched=${result.totalFetched}, sent=${result.sentCount}, failed=${result.failedCount}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process Woo alerts: ${e.message}", e)
            }
        }
    }
    
    private fun showChatNotification(title: String, body: String, oderId: String) {
        // Check if user is logged in
        val currentUser = FirebaseAuth.getInstance().currentUser
        
        val intent = if (currentUser != null) {
            // User logged in - go directly to chat
            Intent(this, CustomerChatSupportActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("oderId", oderId)
                putExtra("fromNotification", true)
            }
        } else {
            // User not logged in - go to auth first, then redirect to chat
            Intent(this, com.message.bulksend.auth.AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("redirectToChat", true)
                putExtra("oderId", oderId)
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(), // Unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Load app logo as large icon
        val largeIcon = android.graphics.BitmapFactory.decodeResource(
            resources,
            R.drawable.logo
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(resources.getColor(R.color.purple_500, null))
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        Log.d(TAG, "Chat notification shown: $title")
    }
    
    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        // Load app logo as large icon
        val largeIcon = android.graphics.BitmapFactory.decodeResource(
            resources,
            R.drawable.logo
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setColor(resources.getColor(R.color.purple_500, null))
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for chat support messages"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "Notification channel created")
        }
    }
    
    private fun saveFCMToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val email = FirebaseAuth.getInstance().currentUser?.email
                if (email != null) {
                    FirebaseFirestore.getInstance()
                        .collection("email_data")
                        .document(email)
                        .update("pushToken", token)
                        .await()
                    
                    Log.d(TAG, "FCM token saved for: $email")
                } else {
                    // Save token locally for later
                    val prefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("pending_token", token).apply()
                    Log.d(TAG, "FCM token saved locally (user not logged in)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving FCM token", e)
            }
        }
    }
}

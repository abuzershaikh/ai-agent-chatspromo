package com.message.bulksend.debug

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.message.bulksend.notification.FCMTokenManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NotificationTestActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            NotificationTestScreen()
        }
    }
    
    @Composable
    fun NotificationTestScreen() {
        val context = LocalContext.current
        var testResults by remember { mutableStateOf("") }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Notification Debug Test",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Button(
                onClick = {
                    lifecycleScope.launch {
                        testResults = runNotificationTest(context)
                    }
                }
            ) {
                Text("Run FCM Test")
            }
            
            Button(
                onClick = {
                    lifecycleScope.launch {
                        testSendNotification(context)
                    }
                }
            ) {
                Text("Test Local Notification")
            }
            
            if (testResults.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = testResults,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
    
    private suspend fun runNotificationTest(context: Context): String {
        val results = StringBuilder()
        results.appendLine("=== FCM Debug Test Results ===\n")
        
        try {
            // 1. Check FCM token
            val token = FirebaseMessaging.getInstance().token.await()
            results.appendLine("✅ FCM Token: ${token.take(20)}...")
            
            // 2. Check user authentication
            val user = FirebaseAuth.getInstance().currentUser
            results.appendLine("✅ User logged in: ${user != null}")
            results.appendLine("   Email: ${user?.email}")
            
            // 3. Test token registration
            val registered = FCMTokenManager.registerToken(context)
            results.appendLine("✅ Token registration: $registered")
            
            // 4. Check notification permissions
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val areEnabled = notificationManager.areNotificationsEnabled()
            results.appendLine("✅ Notifications enabled: $areEnabled")
            
            // 5. Check notification channel
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = notificationManager.getNotificationChannel("chat_support_channel")
                results.appendLine("✅ Channel exists: ${channel != null}")
                if (channel != null) {
                    results.appendLine("   Importance: ${channel.importance}")
                }
            }
            
            // 6. Test topic subscription
            try {
                FCMTokenManager.subscribeToTopic("customer_support")
                results.appendLine("✅ Subscribed to customer_support topic")
            } catch (e: Exception) {
                results.appendLine("❌ Topic subscription failed: ${e.message}")
            }
            
        } catch (e: Exception) {
            results.appendLine("❌ Test failed: ${e.message}")
            Log.e("NotificationTest", "Test failed", e)
        }
        
        return results.toString()
    }
    
    private fun testSendNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val notification = androidx.core.app.NotificationCompat.Builder(context, "chat_support_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Test Notification")
                .setContentText("This is a test notification from customer support")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(9999, notification)
            Toast.makeText(context, "Test notification sent", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to send notification: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("NotificationTest", "Failed to send test notification", e)
        }
    }
}
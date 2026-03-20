package com.message.bulksend.support

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class WelcomeMessageManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("welcome_prefs", Context.MODE_PRIVATE)
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var mediaPlayer: MediaPlayer? = null
    
    companion object {
        private const val KEY_WELCOME_SENT = "welcome_messages_sent_"
        private const val KEY_UNREAD_COUNT = "unread_count"
        private const val KEY_LAST_CHECK = "last_check_time"
        private const val KEY_LAST_MESSAGE_COUNT = "last_message_count"
    }
    
    // Check if welcome messages already sent for this user
    private fun isWelcomeMessagesSent(userEmail: String): Boolean {
        return prefs.getBoolean(KEY_WELCOME_SENT + userEmail, false)
    }
    
    // Mark welcome messages as sent
    private fun markWelcomeMessagesSent(userEmail: String) {
        prefs.edit().putBoolean(KEY_WELCOME_SENT + userEmail, true).apply()
    }
    
    // Send welcome messages for first time users
    suspend fun sendWelcomeMessagesIfNeeded(): Boolean {
        val user = auth.currentUser ?: return false
        val userEmail = user.email ?: return false
        val oderId = userEmail.replace(".", "_")
        
        // Check if welcome messages already sent
        if (isWelcomeMessagesSent(userEmail)) {
            return false // Already sent
        }
        
        try {
            // Get user name from user details preference or Firebase
            val userName = getUserName(userEmail) ?: "User"
            
            // Check if user is from India
            val isIndianUser = isUserFromIndia()
            
            // Create welcome messages in English
            val welcomeMessages = mutableListOf(
                "Hello $userName! 👋\nWelcome to ChatsPromo!",
                "Our support team is here to help you. 😊\nFeel free to ask any questions or report any issues!",
                "You can get answers to all your questions here. 💬\nWe're happy to assist you!"
            )
            
            // Add WhatsApp support message for Indian users only
            if (isIndianUser) {
                welcomeMessages.add("Get instant support on WhatsApp! 📱\nClick below to chat with us directly.")
            }
            
            // Send each welcome message
            welcomeMessages.forEachIndexed { index, message ->
                val isWhatsAppMessage = isIndianUser && index == welcomeMessages.lastIndex
                
                val messageData = hashMapOf(
                    "senderId" to "admin",
                    "senderName" to "ChatsPromo Support",
                    "senderEmail" to "support@chatspromo.com",
                    "message" to message,
                    "messageType" to if (isWhatsAppMessage) "whatsapp_template" else "text",
                    "imageUrl" to "",
                    "timestamp" to Timestamp.now(),
                    "isFromAdmin" to true,
                    "isRead" to false
                )
                
                // Add WhatsApp specific data for template message
                if (isWhatsAppMessage) {
                    messageData["whatsappNumber"] = "919137167857"
                    messageData["whatsappMessage"] = "Hi ChatsPromo Support, I need help with the app."
                }
                
                db.collection("admin_chats").document(oderId)
                    .collection("messages").add(messageData).await()
                
                // Small delay between messages
                kotlinx.coroutines.delay(500)
            }
            
            // Update chat session
            val chatSession = hashMapOf<String, Any>(
                "lastMessage" to welcomeMessages.last(),
                "lastMessageTime" to Timestamp.now(),
                "lastMessageBy" to "admin",
                "unreadCount" to 0, // User messages unread count
                "userUnreadCount" to welcomeMessages.size, // Admin messages unread count for user
                "totalMessages" to welcomeMessages.size
            )
            
            db.collection("admin_chats").document(oderId)
                .update(chatSession).await()
            
            // Mark as sent
            markWelcomeMessagesSent(userEmail)
            
            // Update local unread count
            updateUnreadCount(welcomeMessages.size)
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    // Check if user is from India based on SIM or saved country
    private fun isUserFromIndia(): Boolean {
        return try {
            // Method 1: Check from UserDetailsPreferences
            val userDetailsPref = context.getSharedPreferences("user_details_prefs", Context.MODE_PRIVATE)
            val countryIso = userDetailsPref.getString("country_iso", null)
            val country = userDetailsPref.getString("country", null)
            
            if (countryIso == "IN" || country?.contains("India", ignoreCase = true) == true) {
                return true
            }
            
            // Method 2: Check from SIM card
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            val simCountryIso = telephonyManager?.simCountryIso?.uppercase()
            
            if (simCountryIso == "IN") {
                return true
            }
            
            // Method 3: Check from network country
            val networkCountryIso = telephonyManager?.networkCountryIso?.uppercase()
            
            networkCountryIso == "IN"
        } catch (e: Exception) {
            android.util.Log.e("WelcomeMessageManager", "Error detecting country: ${e.message}")
            false
        }
    }
    
    // Get user name from preferences or Firebase
    private suspend fun getUserName(userEmail: String): String? {
        return try {
            // First try to get from Firebase user data
            val userDoc = db.collection("email_data").document(userEmail).get().await()
            val displayName = userDoc.getString("displayName")
            
            if (!displayName.isNullOrEmpty()) {
                displayName
            } else {
                // Fallback to Firebase Auth display name
                auth.currentUser?.displayName
            }
        } catch (e: Exception) {
            auth.currentUser?.displayName
        }
    }
    
    // Update unread count in local storage
    fun updateUnreadCount(count: Int) {
        prefs.edit().putInt(KEY_UNREAD_COUNT, count).apply()
    }
    
    // Get current unread count
    fun getUnreadCount(): Int {
        return prefs.getInt(KEY_UNREAD_COUNT, 0)
    }
    
    // Get last message count (for detecting new messages)
    private fun getLastMessageCount(): Int {
        return prefs.getInt(KEY_LAST_MESSAGE_COUNT, 0)
    }
    
    // Update last message count
    private fun updateLastMessageCount(count: Int) {
        prefs.edit().putInt(KEY_LAST_MESSAGE_COUNT, count).apply()
    }
    
    // Play notification sound for new messages
    private fun playNotificationSound() {
        try {
            // Release any existing MediaPlayer
            mediaPlayer?.release()
            
            // Create new MediaPlayer
            mediaPlayer = MediaPlayer()
            
            // Load sound from assets
            val assetFileDescriptor: AssetFileDescriptor = context.assets.openFd("messagetone.mp3")
            mediaPlayer?.setDataSource(
                assetFileDescriptor.fileDescriptor,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.length
            )
            assetFileDescriptor.close()
            
            // Prepare and play
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            
            // Release when playback completes
            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
                mediaPlayer = null
            }
            
            android.util.Log.d("WelcomeMessageManager", "Playing notification sound")
        } catch (e: Exception) {
            android.util.Log.e("WelcomeMessageManager", "Error playing notification sound: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // Clear unread count (when user opens chat)
    suspend fun clearUnreadCount() {
        prefs.edit().putInt(KEY_UNREAD_COUNT, 0).apply()
        
        // Also clear in Firebase
        val user = auth.currentUser ?: return
        val userEmail = user.email ?: return
        val oderId = userEmail.replace(".", "_")
        
        try {
            // Update userUnreadCount in chat document
            db.collection("admin_chats").document(oderId)
                .update("userUnreadCount", 0)
            
            // Mark all admin messages as read
            val unreadMessages = db.collection("admin_chats").document(oderId)
                .collection("messages")
                .whereEqualTo("isFromAdmin", true)
                .whereEqualTo("isRead", false)
                .get()
                .await()
            
            // Update each unread admin message
            unreadMessages.documents.forEach { doc ->
                doc.reference.update(
                    mapOf(
                        "isRead" to true,
                        "readAt" to Timestamp.now()
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Listen for new admin messages and update badge
    suspend fun checkForNewMessages(): Int {
        val user = auth.currentUser ?: return 0
        val userEmail = user.email ?: return 0
        val oderId = userEmail.replace(".", "_")
        
        return try {
            // Debug: Get all messages to see what's in the database
            val allMessages = db.collection("admin_chats").document(oderId)
                .collection("messages")
                .get()
                .await()
            
            android.util.Log.d("WelcomeMessageManager", "Total messages in DB: ${allMessages.size()}")
            
            allMessages.documents.forEach { doc ->
                val data = doc.data
                val isFromAdmin = data?.get("isFromAdmin")
                val isRead = data?.get("isRead")
                val message = data?.get("message") as? String ?: ""
                android.util.Log.d("WelcomeMessageManager", "Message: '$message', isFromAdmin: $isFromAdmin, isRead: $isRead")
            }
            
            // Always count unread admin messages directly for accuracy
            val unreadMessages = db.collection("admin_chats").document(oderId)
                .collection("messages")
                .whereEqualTo("isFromAdmin", true)
                .whereEqualTo("isRead", false)
                .get()
                .await()
            
            val unreadCount = unreadMessages.size()
            val previousUnreadCount = getUnreadCount()
            
            // Check if there are new messages (count increased)
            if (unreadCount > previousUnreadCount && unreadCount > 0) {
                android.util.Log.d("WelcomeMessageManager", "New messages detected! Previous: $previousUnreadCount, Current: $unreadCount")
                // Play notification sound for new messages
                playNotificationSound()
            }
            
            // Update local count
            updateUnreadCount(unreadCount)
            
            // Also update the chat document with correct count
            val chatDoc = db.collection("admin_chats").document(oderId).get().await()
            if (chatDoc.exists() && unreadCount >= 0) {
                db.collection("admin_chats").document(oderId)
                    .update("userUnreadCount", unreadCount)
            }
            
            // Debug logging
            android.util.Log.d("WelcomeMessageManager", "Checked messages for $oderId: $unreadCount unread admin messages")
            
            unreadCount
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("WelcomeMessageManager", "Error checking messages: ${e.message}")
            // Return local count as fallback
            getUnreadCount()
        }
    }
    
    // Reset welcome messages (for testing)
    fun resetWelcomeMessages(userEmail: String) {
        prefs.edit().remove(KEY_WELCOME_SENT + userEmail).apply()
    }
    
    // Cleanup resources
    fun cleanup() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
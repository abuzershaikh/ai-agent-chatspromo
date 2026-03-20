package com.message.bulksend.autorespond.aireply

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * ChatsPromo AI Service
 * Uses Firebase Cloud Function - No API key needed for users
 * User email is the unique identifier
 */
class ChatsPromoAIService(private val context: Context) {
    
    companion object {
        private const val TAG = "ChatsPromoAI"
        private const val BASE_URL = "https://us-central1-chatspromo.cloudfunctions.net"
        private const val AI_ENDPOINT = "$BASE_URL/chatspromoAI"
        private const val HISTORY_ENDPOINT = "$BASE_URL/getUserHistory"
        private const val STATS_ENDPOINT = "$BASE_URL/getUserStats"
    }
    
    private val businessDataManager = AIBusinessDataManager(context)
    
    /**
     * Get current user email from Firebase Auth
     */
    private fun getUserEmail(): String? {
        return FirebaseAuth.getInstance().currentUser?.email
    }
    
    /**
     * Generate AI reply using ChatsPromo AI Cloud Function
     */
    suspend fun generateReply(
        message: String,
        senderName: String = "User"
    ): String = withContext(Dispatchers.IO) {
        val userEmail = getUserEmail()
        
        if (userEmail.isNullOrEmpty()) {
            return@withContext "Please login to use ChatsPromo AI"
        }
        
        try {
            Log.d(TAG, "Sending request for user: $userEmail")
            
            // Get business context if available
            val businessContext = businessDataManager.buildBusinessContext(AIProvider.CHATSPROMO)
            
            val url = URL(AI_ENDPOINT)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 60000  // Increased timeout
            connection.readTimeout = 120000    // Increased to 2 minutes for long AI responses

            val requestBody = JSONObject().apply {
                put("userEmail", userEmail)
                put("message", message)
                put("senderName", senderName)
                put("businessContext", businessContext)
            }
            
            connection.outputStream.use { 
                it.write(requestBody.toString().toByteArray()) 
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")
            
            if (responseCode != 200) {
                val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e(TAG, "Error: $errorStream")
                return@withContext "Error: Unable to get response. Please try again."
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = JSONObject(response)
            
            if (jsonResponse.optBoolean("success", false)) {
                val aiResponse = jsonResponse.getString("response")
                Log.d(TAG, "Got response: ${aiResponse.take(100)}...")
                return@withContext aiResponse
            } else {
                val error = jsonResponse.optString("error", "Unknown error")
                return@withContext "Error: $error"
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}", e)
            return@withContext "Error: ${e.message ?: "Connection failed"}"
        }
    }
    
    /**
     * Get conversation history for current user
     */
    suspend fun getConversationHistory(limit: Int = 50): List<ConversationItem> = withContext(Dispatchers.IO) {
        val userEmail = getUserEmail() ?: return@withContext emptyList()
        
        try {
            val url = URL("$HISTORY_ENDPOINT?email=$userEmail&limit=$limit")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            if (connection.responseCode != 200) {
                return@withContext emptyList()
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = JSONObject(response)
            
            if (jsonResponse.optBoolean("success", false)) {
                val conversations = jsonResponse.getJSONArray("conversations")
                val result = mutableListOf<ConversationItem>()
                
                for (i in 0 until conversations.length()) {
                    val conv = conversations.getJSONObject(i)
                    result.add(ConversationItem(
                        userMessage = conv.getString("userMessage"),
                        aiResponse = conv.getString("aiResponse"),
                        senderName = conv.optString("senderName", "User"),
                        timestamp = conv.optLong("timestampMs", 0)
                    ))
                }
                
                return@withContext result
            }
            
            return@withContext emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting history: ${e.message}")
            return@withContext emptyList()
        }
    }
    
    /**
     * Get usage stats for current user
     */
    suspend fun getUserStats(): UserStats? = withContext(Dispatchers.IO) {
        val userEmail = getUserEmail() ?: return@withContext null
        
        try {
            val url = URL("$STATS_ENDPOINT?email=$userEmail")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            if (connection.responseCode != 200) {
                return@withContext null
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = JSONObject(response)
            
            if (jsonResponse.optBoolean("success", false)) {
                val stats = jsonResponse.getJSONObject("stats")
                return@withContext UserStats(
                    totalMessages = stats.optInt("totalMessages", 0)
                )
            }
            
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stats: ${e.message}")
            return@withContext null
        }
    }
}

/**
 * Data class for conversation item
 */
data class ConversationItem(
    val userMessage: String,
    val aiResponse: String,
    val senderName: String,
    val timestamp: Long
)

/**
 * Data class for user stats (from Cloud Function)
 */
data class UserStats(
    val totalMessages: Int
)

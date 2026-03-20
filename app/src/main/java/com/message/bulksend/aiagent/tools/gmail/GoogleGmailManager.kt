package com.message.bulksend.aiagent.tools.gmail

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GoogleGmailManager(private val context: Context) {

    private val tag = "GoogleGmailManager"
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance("chatspromoweb")
    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    val workerBaseUrl: String
        get() = GoogleGmailAgentTool.WORKER_URL.trimEnd('/')

    fun isWorkerConfigured(): Boolean {
        return workerBaseUrl.isNotBlank() && !workerBaseUrl.contains("YOUR_ACCOUNT")
    }

    suspend fun initiateOAuthLogin(clientId: String, clientSecret: String): Result<String> {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) {
                return Result.failure(IllegalStateException("User not logged in"))
            }
            if (!isWorkerConfigured()) {
                return Result.failure(
                    IllegalStateException("Google Gmail worker URL is not configured")
                )
            }

            val requestBody =
                JSONObject()
                    .apply {
                        put("userId", userId)
                        put("clientId", clientId.trim())
                        put("clientSecret", clientSecret.trim())
                    }
                    .toString()
                    .toRequestBody(jsonType)

            val request =
                Request.Builder()
                    .url("$workerBaseUrl/auth/login")
                    .post(requestBody)
                    .build()

            val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
            response.use {
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    return Result.failure(IllegalStateException("HTTP ${it.code}: $raw"))
                }

                val json = JSONObject(raw)
                val authUrl = json.optString("url")
                if (authUrl.isBlank()) {
                    Result.failure(IllegalStateException("Auth URL missing in worker response"))
                } else {
                    Result.success(authUrl)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "initiateOAuthLogin failed", e)
            Result.failure(e)
        }
    }

    suspend fun getConfig(): GmailConfig? {
        return try {
            val userId = currentUserId
            if (userId.isBlank()) return null

            val snapshot =
                firestore.collection("users_config").document(userId).get().await()
            if (!snapshot.exists()) return null

            GmailConfig(
                uid = userId,
                gmailApiConnected = snapshot.getBoolean("gmailApiConnected") ?: false,
                gmailApiLastSetup = snapshot.getString("gmailApiLastSetup") ?: "",
                googleGmailConfig = snapshot.getString("googleGmailConfig") ?: ""
            )
        } catch (e: Exception) {
            Log.e(tag, "getConfig failed", e)
            null
        }
    }

    suspend fun disconnect() {
        try {
            val userId = currentUserId
            if (userId.isNotBlank()) {
                firestore.collection("users_config").document(userId)
                    .update(
                        mapOf(
                            "gmailApiConnected" to false,
                            "googleGmailConfig" to com.google.firebase.firestore.FieldValue.delete()
                        )
                    ).await()
            }
        } catch (e: Exception) {
            Log.e(tag, "disconnect failed", e)
        }
    }

    suspend fun isSetupDone(): Boolean = getConfig()?.gmailApiConnected == true
}

data class GmailConfig(
    val uid: String,
    val gmailApiConnected: Boolean,
    val gmailApiLastSetup: String,
    val googleGmailConfig: String
)

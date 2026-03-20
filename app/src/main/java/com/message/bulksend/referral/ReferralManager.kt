package com.message.bulksend.referral

import android.content.Context
import android.content.Intent
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

/**
 * ReferralManager handles all affiliate operations.
 * Uses the Cloudflare worker and userDetails collection for storage.
 */
class ReferralManager(private val context: Context) {

    companion object {
        private const val TAG = "ReferralManager"
        private const val WORKER_BASE_URL = "https://refer-earn-worker.aawuazer.workers.dev"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient = OkHttpClient()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun generateReferralCode(fullName: String): ReferralResult {
        return try {
            val response = requestJson(
                path = "/api/referrals/generate",
                method = "POST",
                body = JSONObject().put("fullName", fullName)
            )
            ReferralResult(
                success = response.optBoolean("success", false),
                referralCode = response.optStringOrNull("referralCode"),
                referralLink = response.optStringOrNull("referralLink"),
                message = response.optStringOrNull("message")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating affiliate code", e)
            ReferralResult(success = false, message = e.message)
        }
    }

    suspend fun processReferralCode(referralCode: String, installId: String? = null): ReferralResult {
        return try {
            val response = requestJson(
                path = "/api/referrals/claim",
                method = "POST",
                body = JSONObject().apply {
                    put("referralCode", referralCode)
                    if (!installId.isNullOrBlank()) {
                        put("installId", installId)
                    }
                }
            )
            ReferralResult(
                success = response.optBoolean("success", false),
                message = response.optStringOrNull("message"),
                referrerName = response.optStringOrNull("referrerName")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing affiliate code", e)
            ReferralResult(success = false, message = e.message)
        }
    }

    suspend fun trackAffiliateInstall(
        referralCode: String,
        source: String = "play_store_install",
        installId: String? = null
    ): ReferralResult {
        return try {
            val response = requestJson(
                path = "/api/referrals/install",
                method = "POST",
                body = JSONObject().apply {
                    put("referralCode", referralCode)
                    put("source", source)
                    if (!installId.isNullOrBlank()) {
                        put("installId", installId)
                    }
                }
            )
            ReferralResult(
                success = response.optBoolean("success", false),
                message = response.optStringOrNull("message"),
                referrerName = response.optStringOrNull("referrerName")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking affiliate install", e)
            ReferralResult(success = false, message = e.message)
        }
    }

    suspend fun trackAnonymousAffiliateInstall(
        referralCode: String,
        installId: String,
        source: String = "play_store_install"
    ): ReferralResult {
        return try {
            val response = requestPublicJson(
                path = "/api/referrals/install-public",
                method = "POST",
                body = JSONObject().apply {
                    put("referralCode", referralCode)
                    put("installId", installId)
                    put("source", source)
                }
            )
            ReferralResult(
                success = response.optBoolean("success", false),
                message = response.optStringOrNull("message")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking anonymous affiliate install", e)
            ReferralResult(success = false, message = e.message)
        }
    }

    suspend fun processReferralReward(planType: String, purchaseAmount: Int): ReferralResult {
        return try {
            val response = requestJson(
                path = "/api/referrals/reward",
                method = "POST",
                body = JSONObject().apply {
                    put("planType", planType)
                    put("purchaseAmount", purchaseAmount)
                }
            )
            ReferralResult(
                success = response.optBoolean("success", false),
                message = response.optStringOrNull("message"),
                commission = response.optIntOrNull("commission")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing affiliate reward", e)
            ReferralResult(success = false, message = e.message)
        }
    }

    suspend fun getReferralStats(): ReferralStatsResult {
        return try {
            val response = requestJson(path = "/api/referrals/stats")
            val success = response.optBoolean("success", false)

            if (success) {
                val stats = response.optJSONObject("stats")
                ReferralStatsResult(
                    success = true,
                    myReferralCode = stats?.optStringOrNull("myReferralCode"),
                    referralLink = stats?.optStringOrNull("referralLink"),
                    referralCount = stats?.optInt("referralCount") ?: 0,
                    referralLinkClicks = stats?.optInt("referralLinkClicks") ?: 0,
                    trackedInstalls = stats?.optInt("trackedInstalls") ?: 0,
                    trackedRegistrations = stats?.optInt("trackedRegistrations") ?: 0,
                    successfulReferrals = stats?.optInt("successfulReferrals") ?: 0,
                    totalReferralEarnings = stats?.optInt("totalReferralEarnings") ?: 0,
                    pendingEarnings = stats?.optInt("pendingEarnings") ?: 0,
                    withdrawnEarnings = stats?.optInt("withdrawnEarnings") ?: 0,
                    referredBy = stats?.optStringOrNull("referredBy")
                )
            } else {
                ReferralStatsResult(success = false, message = response.optStringOrNull("message"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting affiliate stats", e)
            val fallback = getReferralDataFromFirestore()
            if (fallback.success) fallback.copy(message = e.message)
            else ReferralStatsResult(success = false, message = e.message)
        }
    }

    fun generatePlayStoreLink(referralCode: String): String {
        return "$WORKER_BASE_URL/r/$referralCode"
    }

    fun shareReferralLink(referralCode: String, referralLink: String) {
        val finalLink = if (referralCode.isNotBlank()) {
            generatePlayStoreLink(referralCode)
        } else {
            referralLink
        }
        val shareText = """
            ChatsPromo Affiliate Program

            Use my affiliate code: $referralCode

            Install with my affiliate link:
            $finalLink

            Promote ChatsPromo on YouTube, Instagram, Facebook, Telegram, WhatsApp and earn 30% on every paid plan purchase.
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    suspend fun getReferralDataFromFirestore(): ReferralStatsResult {
        return try {
            val userId = auth.currentUser?.uid ?: return ReferralStatsResult(
                success = false,
                message = "User not logged in"
            )

            val doc = firestore.collection("userDetails").document(userId).get().await()

            if (doc.exists()) {
                val referralCode = doc.getString("myReferralCode")
                ReferralStatsResult(
                    success = true,
                    myReferralCode = referralCode,
                    referralLink = referralCode?.let { generatePlayStoreLink(it) }
                        ?: doc.getString("referralLink"),
                    referralCount = doc.getLong("referralCount")?.toInt() ?: 0,
                    referralLinkClicks = doc.getLong("referralLinkClicks")?.toInt() ?: 0,
                    trackedInstalls = doc.getLong("trackedInstalls")?.toInt() ?: 0,
                    trackedRegistrations = doc.getLong("trackedRegistrations")?.toInt() ?: 0,
                    successfulReferrals = doc.getLong("successfulReferrals")?.toInt() ?: 0,
                    totalReferralEarnings = doc.getLong("totalReferralEarnings")?.toInt() ?: 0,
                    pendingEarnings = doc.getLong("pendingEarnings")?.toInt() ?: 0,
                    withdrawnEarnings = doc.getLong("withdrawnEarnings")?.toInt() ?: 0,
                    referredBy = doc.getString("referredBy")
                )
            } else {
                ReferralStatsResult(success = false, message = "User data not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting affiliate data from Firestore", e)
            ReferralStatsResult(success = false, message = e.message)
        }
    }

    suspend fun getReferredUsersList(): ReferredUsersResult {
        return try {
            val response = requestJson(path = "/api/referrals/referred-users")
            val success = response.optBoolean("success", false)

            if (success) {
                val usersArray = response.optJSONArray("referredUsers")
                val referredUsers = buildList {
                    if (usersArray != null) {
                        for (index in 0 until usersArray.length()) {
                            val user = usersArray.optJSONObject(index) ?: continue
                            add(
                                ReferredUser(
                                    oderId = user.optStringOrNull("oderId") ?: "",
                                    fullName = user.optStringOrNull("fullName") ?: "Unknown",
                                    email = user.optStringOrNull("email") ?: "N/A",
                                    phoneNumber = user.optStringOrNull("phoneNumber") ?: "N/A",
                                    userStatus = user.optStringOrNull("userStatus") ?: "registered",
                                    referredAt = user.optStringOrNull("referredAt"),
                                    installTrackedAt = user.optStringOrNull("installTrackedAt"),
                                    registeredAt = user.optStringOrNull("registeredAt"),
                                    purchasedAt = user.optStringOrNull("purchasedAt"),
                                    installSource = user.optStringOrNull("installSource"),
                                    hasPurchased = user.optBoolean("hasPurchased", false),
                                    purchasedPlanType = user.optStringOrNull("purchasedPlanType"),
                                    purchaseAmount = user.optInt("purchaseAmount"),
                                    commissionEarned = user.optInt("commissionEarned")
                                )
                            )
                        }
                    }
                }

                ReferredUsersResult(
                    success = true,
                    referralCode = response.optStringOrNull("referralCode"),
                    totalReferred = response.optInt("totalReferred"),
                    referredUsers = referredUsers
                )
            } else {
                ReferredUsersResult(success = false, message = response.optStringOrNull("message"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting affiliate user list", e)
            ReferredUsersResult(success = false, message = e.message)
        }
    }

    suspend fun getReferralClicks(): ReferralClicksResult {
        return try {
            val response = requestJson(path = "/api/referrals/clicks")
            val success = response.optBoolean("success", false)

            if (success) {
                val clicksArray = response.optJSONArray("clickHistory")
                val clickHistory = buildList {
                    if (clicksArray != null) {
                        for (index in 0 until clicksArray.length()) {
                            val click = clicksArray.optJSONObject(index) ?: continue
                            add(
                                ReferralClick(
                                    clickId = click.optStringOrNull("clickId") ?: "",
                                    referralCode = click.optStringOrNull("referralCode"),
                                    clickedAt = click.optStringOrNull("clickedAt"),
                                    userAgent = click.optStringOrNull("userAgent")
                                )
                            )
                        }
                    }
                }

                ReferralClicksResult(
                    success = true,
                    totalClicks = response.optInt("totalClicks"),
                    clickHistory = clickHistory
                )
            } else {
                ReferralClicksResult(success = false, message = response.optStringOrNull("message"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting affiliate click history", e)
            ReferralClicksResult(success = false, message = e.message)
        }
    }

    suspend fun getReferralInstalls(): ReferralInstallsResult {
        return try {
            val response = requestJson(path = "/api/referrals/installs")
            val success = response.optBoolean("success", false)

            if (success) {
                val installsArray = response.optJSONArray("installHistory")
                val installHistory = buildList {
                    if (installsArray != null) {
                        for (index in 0 until installsArray.length()) {
                            val install = installsArray.optJSONObject(index) ?: continue
                            add(
                                ReferralInstall(
                                    installId = install.optStringOrNull("installId") ?: "",
                                    referralCode = install.optStringOrNull("referralCode"),
                                    installTrackedAt = install.optStringOrNull("installTrackedAt"),
                                    installSource = install.optStringOrNull("installSource"),
                                    linkedUserId = install.optStringOrNull("linkedUserId")
                                )
                            )
                        }
                    }
                }

                ReferralInstallsResult(
                    success = true,
                    totalInstalls = response.optInt("totalInstalls"),
                    installHistory = installHistory
                )
            } else {
                ReferralInstallsResult(success = false, message = response.optStringOrNull("message"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting affiliate install history", e)
            ReferralInstallsResult(success = false, message = e.message)
        }
    }

    private suspend fun requestJson(
        path: String,
        method: String = "GET",
        body: JSONObject? = null
    ): JSONObject = requestJsonInternal(
        path = path,
        method = method,
        body = body,
        includeAuth = true
    )

    private suspend fun requestPublicJson(
        path: String,
        method: String = "GET",
        body: JSONObject? = null
    ): JSONObject = requestJsonInternal(
        path = path,
        method = method,
        body = body,
        includeAuth = false
    )

    private suspend fun requestJsonInternal(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        includeAuth: Boolean
    ): JSONObject = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url("$WORKER_BASE_URL$path")
            .header("Accept", "application/json")

        if (includeAuth) {
            val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in")
            val idToken = currentUser.getIdToken(false).await().token
                ?: throw IllegalStateException("Unable to get Firebase ID token")
            requestBuilder.header("Authorization", "Bearer $idToken")
        }

        if (method == "GET") {
            requestBuilder.get()
        } else {
            val requestBody = (body?.toString() ?: "{}").toRequestBody(JSON_MEDIA_TYPE)
            requestBuilder.method(method, requestBody)
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            val json = parseJsonObject(responseText)

            if (!response.isSuccessful) {
                val errorMessage = json.optStringOrNull("error")
                    ?: json.optStringOrNull("message")
                    ?: "Request failed with code ${response.code}"
                throw IllegalStateException(errorMessage)
            }

            json
        }
    }

    private fun parseJsonObject(responseText: String): JSONObject {
        if (responseText.isBlank()) return JSONObject()
        return runCatching { JSONObject(responseText) }
            .getOrElse { JSONObject().put("message", responseText) }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }
}

data class ReferralResult(
    val success: Boolean,
    val referralCode: String? = null,
    val referralLink: String? = null,
    val message: String? = null,
    val referrerName: String? = null,
    val commission: Int? = null
)

data class ReferralStatsResult(
    val success: Boolean,
    val myReferralCode: String? = null,
    val referralLink: String? = null,
    val referralCount: Int = 0,
    val referralLinkClicks: Int = 0,
    val trackedInstalls: Int = 0,
    val trackedRegistrations: Int = 0,
    val successfulReferrals: Int = 0,
    val totalReferralEarnings: Int = 0,
    val pendingEarnings: Int = 0,
    val withdrawnEarnings: Int = 0,
    val referredBy: String? = null,
    val message: String? = null
)

data class ReferredUsersResult(
    val success: Boolean,
    val referralCode: String? = null,
    val totalReferred: Int = 0,
    val referredUsers: List<ReferredUser> = emptyList(),
    val message: String? = null
)

data class ReferralClicksResult(
    val success: Boolean,
    val totalClicks: Int = 0,
    val clickHistory: List<ReferralClick> = emptyList(),
    val message: String? = null
)

data class ReferralInstallsResult(
    val success: Boolean,
    val totalInstalls: Int = 0,
    val installHistory: List<ReferralInstall> = emptyList(),
    val message: String? = null
)

data class ReferralClick(
    val clickId: String,
    val referralCode: String?,
    val clickedAt: String?,
    val userAgent: String?
)

data class ReferralInstall(
    val installId: String,
    val referralCode: String?,
    val installTrackedAt: String?,
    val installSource: String?,
    val linkedUserId: String?
)

data class ReferredUser(
    val oderId: String,
    val fullName: String,
    val email: String,
    val phoneNumber: String,
    val userStatus: String,
    val referredAt: String?,
    val installTrackedAt: String?,
    val registeredAt: String?,
    val purchasedAt: String?,
    val installSource: String?,
    val hasPurchased: Boolean,
    val purchasedPlanType: String?,
    val purchaseAmount: Int,
    val commissionEarned: Int
)

package com.message.bulksend.auth

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.message.bulksend.data.DeviceInfo
import com.message.bulksend.data.UserData
import com.message.bulksend.data.UserPreferences
import com.message.bulksend.data.LoginHistoryItem
import com.message.bulksend.utils.DeviceUtils
import kotlinx.coroutines.tasks.await

class UserManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "UserManager"
        private const val EMAIL_DATA_COLLECTION = "email_data" // Store all data with email as key
    }

    /**
     * Check if user email can login on this device
     * Rule: Allow login on any device (no device restriction)
     * Email document ID is the email itself
     */
    suspend fun canUserLoginOnDevice(email: String, currentDeviceId: String): Boolean {
        // Always allow login - no device restriction
        Log.d(TAG, "Email $email login allowed on device $currentDeviceId")
        return true
    }

    /**
     * Create or update user data in Firestore using email as document ID
     */
    suspend fun createOrUpdateUser(firebaseUser: FirebaseUser): Result<UserData> {
        return try {
            val deviceId = DeviceUtils.getDeviceId(context)
            val email = firebaseUser.email ?: return Result.failure(Exception("Email not found"))
            val providerName = firebaseUser.displayName?.trim().orEmpty()
            val fallbackEmailName = email.substringBefore("@")
                .replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }

            // No device restriction - allow login on any device
            Log.d(TAG, "Creating/updating user for email: $email on device: $deviceId")

            // Check if user already exists using email as document ID
            val existingUserDoc = firestore.collection(EMAIL_DATA_COLLECTION)
                .document(email)
                .get()
                .await()

            // If user already filled the details form, prefer that full name for profile consistency.
            val userDetailsName = firestore.collection("userDetails")
                .document(firebaseUser.uid)
                .get()
                .await()
                .getString("fullName")
                ?.trim()
                .orEmpty()

            val currentTime = Timestamp.now()

            val userData = if (!existingUserDoc.exists()) {
                // New user
                val newLoginHistory = listOf(DeviceUtils.createLoginHistoryItem(context, deviceId))
                val resolvedDisplayName = when {
                    userDetailsName.isNotBlank() -> userDetailsName
                    providerName.isNotBlank() -> providerName
                    fallbackEmailName.isNotBlank() -> fallbackEmailName
                    else -> "User"
                }
                UserData(
                    email = email,
                    userId = firebaseUser.uid,
                    displayName = resolvedDisplayName,
                    profilePhotoUrl = firebaseUser.photoUrl?.toString() ?: "",
                    deviceId = deviceId,
                    deviceInfo = DeviceUtils.createDeviceInfo(context),
                    uniqueIdentifier = DeviceUtils.generateUniqueIdentifier(email, deviceId),
                    firstSignupDate = currentTime,
                    lastLoginDate = currentTime,
                    isActive = true,
                    accountState = "active",
                    pushToken = "", // Will be updated when FCM token is available
                    preferences = UserPreferences(),
                    loginHistory = newLoginHistory,
                    // Default subscription settings for new users
                    subscriptionType = "free",
                    subscriptionStartDate = null,
                    subscriptionEndDate = null,
                    contactsLimit = 10,
                    currentContactsCount = 0,
                    groupsLimit = 1,
                    currentGroupsCount = 0
                )
            } else {
                // Existing user - update data
                val existingUser = existingUserDoc.toObject(UserData::class.java)!!
                val newLoginHistoryItem = DeviceUtils.createLoginHistoryItem(context, deviceId)
                val updatedLoginHistory = (existingUser.loginHistory + newLoginHistoryItem).takeLast(10) // Keep last 10 logins
                val resolvedDisplayName = when {
                    userDetailsName.isNotBlank() -> userDetailsName
                    providerName.isNotBlank() -> providerName
                    existingUser.displayName.isNotBlank() -> existingUser.displayName
                    fallbackEmailName.isNotBlank() -> fallbackEmailName
                    else -> "User"
                }

                if (existingUser.deviceId != deviceId) {
                    Log.w(TAG, "Device change detected for ${existingUser.email}: ${existingUser.deviceId} -> $deviceId")
                }

                existingUser.copy(
                    deviceId = deviceId,
                    deviceInfo = DeviceUtils.createDeviceInfo(context),
                    lastLoginDate = currentTime,
                    isActive = true,
                    accountState = "active",
                    displayName = resolvedDisplayName,
                    profilePhotoUrl = firebaseUser.photoUrl?.toString() ?: existingUser.profilePhotoUrl,
                    loginHistory = updatedLoginHistory
                )
            }

            // Save user data using email as document ID
            firestore.collection(EMAIL_DATA_COLLECTION)
                .document(email)
                .set(userData)
                .await()

            Log.d(TAG, "User data saved successfully for email: $email")
            Result.success(userData)

        } catch (e: Exception) {
            Log.e(TAG, "Error creating/updating user", e)
            Result.failure(e)
        }
    }

    // Device info is now part of UserData, no separate collection needed

    /**
     * Update user preferences using email as document ID
     */
    suspend fun updateUserPreferences(email: String, preferences: UserPreferences): Boolean {
        return try {
            firestore.collection(EMAIL_DATA_COLLECTION)
                .document(email)
                .update("preferences", preferences)
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user preferences", e)
            false
        }
    }

    /**
     * Update push token using email as document ID
     */
    suspend fun updatePushToken(email: String, token: String): Boolean {
        return try {
            firestore.collection(EMAIL_DATA_COLLECTION)
                .document(email)
                .update("pushToken", token)
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating push token", e)
            false
        }
    }

    /**
     * Update account state using email as document ID
     */
    suspend fun updateAccountState(email: String, state: String): Boolean {
        return try {
            firestore.collection(EMAIL_DATA_COLLECTION)
                .document(email)
                .update("accountState", state)
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating account state", e)
            false
        }
    }

    /**
     * Get user login history using email
     */
    suspend fun getUserLoginHistory(email: String): List<LoginHistoryItem> {
        return try {
            val userData = getUserData(email)
            userData?.loginHistory ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting login history", e)
            emptyList()
        }
    }

    /**
     * Get user data from Firestore using email as document ID
     */
    suspend fun getUserData(email: String): UserData? {
        return try {
            val document = firestore.collection(EMAIL_DATA_COLLECTION)
                .document(email)
                .get()
                .await()

            document.toObject(UserData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user data", e)
            null
        }
    }

    /**
     * Logout user and mark as inactive using email
     */
    suspend fun logoutUser(email: String) {
        try {
            firestore.collection(EMAIL_DATA_COLLECTION)
                .document(email)
                .update(
                    mapOf(
                        "isActive" to false,
                        "lastLoginDate" to Timestamp.now()
                    )
                )
                .await()

            auth.signOut()
            Log.d(TAG, "User logged out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging out user", e)
        }
    }

    /**
     * Force logout user by email (admin function)
     * This will allow the user to login on a new device
     */
    suspend fun forceLogoutUserByEmail(email: String): Boolean {
        return try {
            val userDoc = firestore.collection(EMAIL_DATA_COLLECTION)
                .document(email)
                .get()
                .await()

            if (userDoc.exists()) {
                firestore.collection(EMAIL_DATA_COLLECTION)
                    .document(email)
                    .update(
                        mapOf(
                            "isActive" to false,
                            "accountState" to "force_logged_out",
                            "lastLoginDate" to Timestamp.now()
                        )
                    )
                    .await()

                Log.d(TAG, "Force logout successful for email: $email")
                true
            } else {
                Log.w(TAG, "User not found for email: $email")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error force logging out user", e)
            false
        }
    }

    /**
     * Check if email is already registered with different device
     */
    suspend fun isEmailRegisteredOnDifferentDevice(email: String): Boolean {
        return try {
            val currentDeviceId = DeviceUtils.getDeviceId(context)
            val userDoc = firestore.collection(EMAIL_DATA_COLLECTION)
                .document(email)
                .get()
                .await()

            if (!userDoc.exists()) {
                false
            } else {
                val userData = userDoc.toObject(UserData::class.java)
                userData?.isActive == true && userData.deviceId != currentDeviceId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking email registration", e)
            false
        }
    }

    /**
     * Get device information for an email (admin function)
     */
    suspend fun getDeviceInfoForEmail(email: String): String? {
        return try {
            val userDoc = firestore.collection(EMAIL_DATA_COLLECTION)
                .document(email)
                .get()
                .await()

            if (userDoc.exists()) {
                val userData = userDoc.toObject(UserData::class.java)
                "Device: ${userData?.deviceInfo?.model}\nDevice ID: ${userData?.deviceId}\nActive: ${userData?.isActive}\nLast Login: ${userData?.lastLoginDate?.toDate()}"
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device info for email", e)
            null
        }
    }

    /**
     * Get all active users on current device (for debugging)
     */
    suspend fun getActiveUsersOnCurrentDevice(): List<String> {
        return try {
            val currentDeviceId = DeviceUtils.getDeviceId(context)
            val userDocs = firestore.collection(EMAIL_DATA_COLLECTION)
                .whereEqualTo("deviceId", currentDeviceId)
                .whereEqualTo("isActive", true)
                .get()
                .await()

            userDocs.documents.mapNotNull { doc ->
                doc.toObject(UserData::class.java)?.email
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active users on device", e)
            emptyList()
        }
    }

    // ========== SUBSCRIPTION MANAGEMENT FUNCTIONS ==========

    /**
     * Check if user can add more contacts
     */
    suspend fun canAddContacts(email: String, contactsToAdd: Int = 1): Boolean {
        return try {
            val userData = getUserData(email) ?: return false

            // Check if premium is expired
            if (userData.subscriptionType == "premium" && !isPremiumExpired(email)) {
                // Active premium users have unlimited contacts
                return true
            }

            // Free users or expired premium users have limit of 10 contacts
            val currentCount = userData.currentContactsCount
            val newCount = currentCount + contactsToAdd

            newCount <= userData.contactsLimit
        } catch (e: Exception) {
            Log.e(TAG, "Error checking contact limit", e)
            false
        }
    }

    /**
     * Check if user can add more groups
     */
    suspend fun canAddGroups(email: String, groupsToAdd: Int = 1): Boolean {
        return try {
            val userData = getUserData(email) ?: return false

            // Check if premium is expired
            if (userData.subscriptionType == "premium" && !isPremiumExpired(email)) {
                // Active premium users have unlimited groups
                return true
            }

            // Free users or expired premium users have limit of 1 group
            val currentCount = userData.currentGroupsCount
            val newCount = currentCount + groupsToAdd

            newCount <= userData.groupsLimit
        } catch (e: Exception) {
            Log.e(TAG, "Error checking group limit", e)
            false
        }
    }

    /**
     * Update contact count for user
     */
    suspend fun updateContactCount(email: String, newCount: Int): Boolean {
        return try {
            firestore.collection(EMAIL_DATA_COLLECTION)
                .document(email)
                .update("currentContactsCount", newCount)
                .await()

            // Also update local preferences
            val sharedPref = context.getSharedPreferences("subscription_prefs", android.content.Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putInt("current_contacts", newCount)
                apply()
            }

            Log.d(TAG, "Contact count updated to $newCount for $email")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating contact count", e)
            false
        }
    }

    /**
     * Update group count for user
     */
    suspend fun updateGroupCount(email: String, newCount: Int): Boolean {
        return try {
            firestore.collection(EMAIL_DATA_COLLECTION)
                .document(email)
                .update("currentGroupsCount", newCount)
                .await()

            // Also update local preferences
            val sharedPref = context.getSharedPreferences("subscription_prefs", android.content.Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putInt("current_groups", newCount)
                apply()
            }

            Log.d(TAG, "Group count updated to $newCount for $email")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating group count", e)
            false
        }
    }

    /**
     * Upgrade user to premium for 30 days
     */
    suspend fun upgradeToPremium(email: String): Boolean {
        return try {
            val currentTime = Timestamp.now()
            val endTime = Timestamp(currentTime.seconds + (30 * 24 * 60 * 60), 0) // 30 days

            firestore.collection(EMAIL_DATA_COLLECTION)
                .document(email)
                .update(
                    mapOf(
                        "subscriptionType" to "premium",
                        "subscriptionStartDate" to currentTime,
                        "subscriptionEndDate" to endTime,
                        "contactsLimit" to -1, // -1 means unlimited
                        "groupsLimit" to -1 // -1 means unlimited
                    )
                )
                .await()

            Log.d(TAG, "User $email upgraded to premium for 30 days")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error upgrading user to premium", e)
            false
        }
    }

    /**
     * Downgrade user to free
     */
    suspend fun downgradeToFree(email: String): Boolean {
        return try {
            firestore.collection(EMAIL_DATA_COLLECTION)
                .document(email)
                .update(
                    mapOf(
                        "subscriptionType" to "free",
                        "subscriptionStartDate" to null,
                        "subscriptionEndDate" to null,
                        "contactsLimit" to 10,
                        "groupsLimit" to 1
                    )
                )
                .await()

            Log.d(TAG, "User $email downgraded to free")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error downgrading user to free", e)
            false
        }
    }

    /**
     * Check if premium subscription is expired
     */
    suspend fun isPremiumExpired(email: String): Boolean {
        return try {
            val userData = getUserData(email) ?: return true

            if (userData.subscriptionType != "premium") {
                return true
            }

            val endDate = userData.subscriptionEndDate
            if (endDate == null) {
                return true
            }

            val currentTime = Timestamp.now()
            currentTime.seconds > endDate.seconds
        } catch (e: Exception) {
            Log.e(TAG, "Error checking premium expiry", e)
            true
        }
    }

    /**
     * Get subscription info for user
     */
    suspend fun getSubscriptionInfo(email: String): Map<String, Any?> {
        return try {
            val userData = getUserData(email)
            if (userData == null) {
                mapOf(
                    "type" to "free",
                    "planType" to "",
                    "contactsLimit" to 10,
                    "currentContacts" to 0,
                    "groupsLimit" to 1,
                    "currentGroups" to 0,
                    "isExpired" to false
                )
            } else {
                val isExpired = if (userData.subscriptionType == "premium") {
                    isPremiumExpired(email)
                } else false

                mapOf(
                    "type" to userData.subscriptionType,
                    "planType" to userData.planType,
                    "contactsLimit" to userData.contactsLimit,
                    "currentContacts" to userData.currentContactsCount,
                    "groupsLimit" to userData.groupsLimit,
                    "currentGroups" to userData.currentGroupsCount,
                    "startDate" to userData.subscriptionStartDate,
                    "endDate" to userData.subscriptionEndDate,
                    "isExpired" to isExpired
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting subscription info", e)
            mapOf(
                "type" to "free",
                "planType" to "",
                "contactsLimit" to 10,
                "currentContacts" to 0,
                "groupsLimit" to 1,
                "currentGroups" to 0,
                "isExpired" to false
            )
        }
    }
}

package com.message.bulksend.data

import com.google.firebase.Timestamp
import java.util.*

/**
 * Email-based user data model - email contains all user information
 */
data class UserData(
    val email: String = "", // This will be the primary key and contain user info
    val userId: String = "",
    val displayName: String = "",
    val profilePhotoUrl: String = "",
    val deviceId: String = "",
    val deviceInfo: DeviceInfo = DeviceInfo(),
    val uniqueIdentifier: String = "",
    val firstSignupDate: Timestamp = Timestamp.now(),
    val lastLoginDate: Timestamp = Timestamp.now(),
    val isActive: Boolean = true,
    val accountState: String = "active", // active, suspended, inactive
    val pushToken: String = "",
    val preferences: UserPreferences = UserPreferences(),
    val loginHistory: List<LoginHistoryItem> = emptyList(),
    // Subscription related fields
    val subscriptionType: String = "free", // free, premium
    val planType: String = "", // monthly, lifetime (for premium users)
    val subscriptionStartDate: Timestamp? = null,
    val subscriptionEndDate: Timestamp? = null,
    val contactsLimit: Int = 10, // 10 for free, unlimited for premium
    val currentContactsCount: Int = 0,
    val groupsLimit: Int = 1, // 1 for free, unlimited for premium
    val currentGroupsCount: Int = 0
) {
    // Empty constructor for Firestore
    constructor() : this(
        "", "", "", "", "", DeviceInfo(), "",
        Timestamp.now(), Timestamp.now(), true, "active", "",
        UserPreferences(), emptyList(), "free", "", null, null, 10, 0, 1, 0
    )
}

/**
 * Enhanced Device information model
 */
data class DeviceInfo(
    val model: String = "",
    val osVersion: String = "",
    val appVersion: String = "",
    val lastActiveDate: Timestamp = Timestamp.now()
) {
    constructor() : this("", "", "", Timestamp.now())
}

/**
 * User preferences model
 */
data class UserPreferences(
    val language: String = "en",
    val darkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val emailNotifications: Boolean = true,
    val autoBackup: Boolean = true,
    val dataUsageOptimization: Boolean = false
) {
    constructor() : this("en", false, true, true, true, false)
}

/**
 * Login history item model
 */
data class LoginHistoryItem(
    val deviceId: String = "",
    val loginAt: Timestamp = Timestamp.now(),
    val deviceModel: String = "",
    val osVersion: String = "",
    val ipAddress: String = "",
    val location: String = ""
) {
    constructor() : this("", Timestamp.now(), "", "", "", "")
}
package com.message.bulksend.utils

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.message.bulksend.auth.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PreferencesSync {

    private const val TAG = "PreferencesSync"
    private const val PREFS_NAME = "subscription_prefs"
    private const val TYPE_FREE = "free"
    private const val TYPE_PREMIUM = "premium"
    private const val STATUS_PURCHASED = "purchased"
    private const val LIMIT_FREE_CONTACTS = 10
    private const val LIMIT_FREE_GROUPS = 1
    private const val LIMIT_UNLIMITED = -1

    private val listenerLock = Any()
    private var emailDataListener: ListenerRegistration? = null
    private var userDetailsListener: ListenerRegistration? = null
    private var activeUserKey: String? = null
    private var latestEmailSnapshot: EmailDataSubscriptionSnapshot? = null
    private var latestUserDetailsSnapshot: UserDetailsSubscriptionSnapshot? = null

    private data class EmailDataSubscriptionSnapshot(
        val exists: Boolean = false,
        val subscriptionType: String? = null,
        val planType: String? = null,
        val contactsLimit: Int? = null,
        val groupsLimit: Int? = null,
        val currentContacts: Int? = null,
        val currentGroups: Int? = null,
        val userEmail: String? = null,
        val subscriptionEndTimeMillis: Long? = null
    )

    private data class UserDetailsSubscriptionSnapshot(
        val exists: Boolean = false,
        val subscriptionType: String? = null,
        val planType: String? = null,
        val purchasedPlanType: String? = null,
        val userStatus: String? = null,
        val contactsLimit: Int? = null,
        val groupsLimit: Int? = null,
        val currentContacts: Int? = null,
        val currentGroups: Int? = null,
        val subscriptionEndTimeMillis: Long? = null
    )

    private data class ResolvedSubscriptionState(
        val source: String,
        val subscriptionType: String,
        val planType: String?,
        val contactsLimit: Int,
        val groupsLimit: Int,
        val currentContacts: Int?,
        val currentGroups: Int?,
        val userEmail: String?,
        val subscriptionEndTimeMillis: Long?
    )

    /**
     * Start realtime subscription sync (email_data primary, userDetails fallback).
     */
    fun startRealtimeSubscriptionSync(context: Context) {
        val appContext = context.applicationContext
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid?.trim().orEmpty()
        val userEmail = currentUser?.email?.trim().orEmpty()

        if (userId.isBlank() || userEmail.isBlank()) {
            Log.w(TAG, "Realtime sync skipped: user not logged in")
            stopRealtimeSubscriptionSync()
            return
        }

        val requestedKey = "$userId|$userEmail"
        val firestore = FirebaseFirestore.getInstance()
        val reusedExistingListener: Boolean

        synchronized(listenerLock) {
            reusedExistingListener =
                activeUserKey == requestedKey &&
                    emailDataListener != null &&
                    userDetailsListener != null
            if (reusedExistingListener) {
                return@synchronized
            }

            stopRealtimeSubscriptionSyncLocked()
            activeUserKey = requestedKey
            latestEmailSnapshot = null
            latestUserDetailsSnapshot = null

            emailDataListener =
                firestore.collection("email_data")
                    .document(userEmail)
                    .addSnapshotListener { snapshot, error ->
                        if (!isListenerStillActive(requestedKey)) return@addSnapshotListener

                        if (error != null) {
                            Log.e(TAG, "email_data listener error: ${error.message}", error)
                            return@addSnapshotListener
                        }

                        synchronized(listenerLock) {
                            latestEmailSnapshot = snapshot.toEmailDataSnapshot()
                        }

                        applyMergedSubscriptionToPrefs(appContext, fallbackEmail = userEmail)
                    }

            userDetailsListener =
                firestore.collection("userDetails")
                    .document(userId)
                    .addSnapshotListener { snapshot, error ->
                        if (!isListenerStillActive(requestedKey)) return@addSnapshotListener

                        if (error != null) {
                            Log.e(TAG, "userDetails listener error: ${error.message}", error)
                            return@addSnapshotListener
                        }

                        synchronized(listenerLock) {
                            latestUserDetailsSnapshot = snapshot.toUserDetailsSnapshot()
                        }

                        applyMergedSubscriptionToPrefs(appContext, fallbackEmail = userEmail)
                    }
        }

        if (reusedExistingListener) {
            Log.d(TAG, "Realtime subscription sync already active for same user")
            return
        }

        Log.d(TAG, "Realtime subscription sync started for user: $userEmail")
    }

    /**
     * Stop realtime subscription sync listeners.
     */
    fun stopRealtimeSubscriptionSync() {
        synchronized(listenerLock) {
            stopRealtimeSubscriptionSyncLocked()
        }
        Log.d(TAG, "Realtime subscription sync stopped")
    }

    /**
     * Sync local preferences with Firebase in background (non-blocking)
     */
    fun syncToFirebase(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                val userEmail = currentUser?.email

                if (userEmail == null) {
                    Log.w(TAG, "No user logged in, skipping Firebase sync")
                    return@launch
                }

                val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val currentContacts = sharedPref.getInt("current_contacts", 0)
                val currentGroups = sharedPref.getInt("current_groups", 0)

                val userManager = UserManager(context)
                userManager.updateContactCount(userEmail, currentContacts)
                userManager.updateGroupCount(userEmail, currentGroups)

                Log.d(TAG, "✅ Firebase sync completed: Contacts=$currentContacts, Groups=$currentGroups")
            } catch (e: Exception) {
                Log.w(TAG, "Firebase sync failed (non-critical): ${e.message}")
            }
        }
    }

    /**
     * Load subscription data from Firebase and update local preferences
     */
    fun loadFromFirebase(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                val userEmail = currentUser?.email

                if (userEmail == null) {
                    Log.w(TAG, "No user logged in, skipping Firebase load")
                    onComplete?.invoke(false)
                    return@launch
                }

                val userManager = UserManager(context)
                val userData = userManager.getUserData(userEmail)

                if (userData != null) {
                    val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putString("subscription_type", userData.subscriptionType)
                        putInt("contacts_limit", userData.contactsLimit)
                        putInt("current_contacts", userData.currentContactsCount)
                        putInt("groups_limit", userData.groupsLimit)
                        putInt("current_groups", userData.currentGroupsCount)
                        putString("user_email", userData.email)

                        if (userData.subscriptionType == "premium") {
                            userData.subscriptionEndDate?.let { endDate ->
                                putLong("subscription_end_time", endDate.seconds * 1000)
                            }
                        } else {
                            remove("subscription_end_time")
                        }

                        apply()
                    }

                    Log.d(TAG, "✅ Loaded from Firebase: ${userData.subscriptionType}, Contacts=${userData.currentContactsCount}, Groups=${userData.currentGroupsCount}")
                    onComplete?.invoke(true)
                } else {
                    Log.w(TAG, "User data not found in Firebase")
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load from Firebase: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * Update contact count in preferences (local only, sync to Firebase in background)
     */
    fun updateContactCount(context: Context, newCount: Int) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt("current_contacts", newCount)
            apply()
        }

        Log.d(TAG, "📊 Contact count updated locally: $newCount")

        // Sync to Firebase in background
        syncToFirebase(context)
    }

    /**
     * Update group count in preferences (local only, sync to Firebase in background)
     */
    fun updateGroupCount(context: Context, newCount: Int) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt("current_groups", newCount)
            apply()
        }

        Log.d(TAG, "📁 Group count updated locally: $newCount")

        // Sync to Firebase in background
        syncToFirebase(context)
    }

    private fun stopRealtimeSubscriptionSyncLocked() {
        emailDataListener?.remove()
        userDetailsListener?.remove()
        emailDataListener = null
        userDetailsListener = null
        latestEmailSnapshot = null
        latestUserDetailsSnapshot = null
        activeUserKey = null
    }

    private fun isListenerStillActive(listenerKey: String): Boolean {
        synchronized(listenerLock) {
            return activeUserKey == listenerKey
        }
    }

    private fun applyMergedSubscriptionToPrefs(context: Context, fallbackEmail: String?) {
        val (emailData, userDetails) =
            synchronized(listenerLock) { latestEmailSnapshot to latestUserDetailsSnapshot }
        val resolved = resolveSubscriptionState(emailData, userDetails, fallbackEmail)

        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingType = normalizeType(sharedPref.getString("subscription_type", TYPE_FREE))
        val existingContactsLimit = sharedPref.getInt("contacts_limit", LIMIT_FREE_CONTACTS)
        val existingGroupsLimit = sharedPref.getInt("groups_limit", LIMIT_FREE_GROUPS)
        val existingCurrentContacts = sharedPref.getInt("current_contacts", 0)
        val existingCurrentGroups = sharedPref.getInt("current_groups", 0)
        val existingPlanType = sharedPref.getString("plan_type", "") ?: ""
        val existingUserEmail = sharedPref.getString("user_email", "") ?: ""
        val existingHasEndTime = sharedPref.contains("subscription_end_time")
        val existingEndTime =
            if (existingHasEndTime) sharedPref.getLong("subscription_end_time", 0L) else null

        val mergedCurrentContacts = resolved.currentContacts ?: existingCurrentContacts
        val mergedCurrentGroups = resolved.currentGroups ?: existingCurrentGroups
        val mergedPlanType =
            if (resolved.subscriptionType == TYPE_PREMIUM) {
                resolved.planType ?: existingPlanType
            } else {
                ""
            }
        val mergedUserEmail = resolved.userEmail ?: existingUserEmail
        val shouldStoreEndTime =
            resolved.subscriptionType == TYPE_PREMIUM && resolved.subscriptionEndTimeMillis != null

        val unchanged =
            existingType == resolved.subscriptionType &&
                existingContactsLimit == resolved.contactsLimit &&
                existingGroupsLimit == resolved.groupsLimit &&
                existingCurrentContacts == mergedCurrentContacts &&
                existingCurrentGroups == mergedCurrentGroups &&
                existingPlanType == mergedPlanType &&
                existingUserEmail == mergedUserEmail &&
                if (shouldStoreEndTime) {
                    existingHasEndTime && existingEndTime == resolved.subscriptionEndTimeMillis
                } else {
                    !existingHasEndTime
                }

        if (unchanged) {
            Log.d(TAG, "Realtime sync deduped: no preference changes needed")
            return
        }

        with(sharedPref.edit()) {
            putString("subscription_type", resolved.subscriptionType)
            putInt("contacts_limit", resolved.contactsLimit)
            putInt("groups_limit", resolved.groupsLimit)
            putInt("current_contacts", mergedCurrentContacts)
            putInt("current_groups", mergedCurrentGroups)
            if (mergedPlanType.isNotBlank()) {
                putString("plan_type", mergedPlanType)
            } else {
                remove("plan_type")
            }
            putString("user_email", mergedUserEmail)

            if (shouldStoreEndTime) {
                putLong("subscription_end_time", resolved.subscriptionEndTimeMillis ?: 0L)
            } else {
                remove("subscription_end_time")
            }

            apply()
        }

        Log.d(
            TAG,
            "Realtime sync applied from ${resolved.source}: type=${resolved.subscriptionType}, contactsLimit=${resolved.contactsLimit}, groupsLimit=${resolved.groupsLimit}"
        )
    }

    private fun resolveSubscriptionState(
        emailData: EmailDataSubscriptionSnapshot?,
        userDetails: UserDetailsSubscriptionSnapshot?,
        fallbackEmail: String?
    ): ResolvedSubscriptionState {
        val emailType = normalizeType(emailData?.subscriptionType)
        val userDetailsType = normalizeType(userDetails?.subscriptionType)
        val userStatus = userDetails?.userStatus?.trim()?.lowercase().orEmpty()

        if (emailType == TYPE_PREMIUM) {
            return ResolvedSubscriptionState(
                source = "email_data",
                subscriptionType = TYPE_PREMIUM,
                planType =
                    firstNonBlank(
                        emailData?.planType,
                        userDetails?.planType,
                        userDetails?.purchasedPlanType
                    ),
                contactsLimit = normalizeLimit(emailData?.contactsLimit, LIMIT_UNLIMITED),
                groupsLimit = normalizeLimit(emailData?.groupsLimit, LIMIT_UNLIMITED),
                currentContacts = normalizeCurrentCount(emailData?.currentContacts),
                currentGroups = normalizeCurrentCount(emailData?.currentGroups),
                userEmail = firstNonBlank(emailData?.userEmail, fallbackEmail),
                subscriptionEndTimeMillis = emailData?.subscriptionEndTimeMillis
            )
        }

        val userDetailsClaimsPremium =
            userDetailsType == TYPE_PREMIUM || userStatus == STATUS_PURCHASED

        if (userDetailsClaimsPremium) {
            return ResolvedSubscriptionState(
                source = "userDetails_fallback",
                subscriptionType = TYPE_PREMIUM,
                planType =
                    firstNonBlank(
                        emailData?.planType,
                        userDetails?.planType,
                        userDetails?.purchasedPlanType
                    ),
                contactsLimit =
                    normalizeLimit(userDetails?.contactsLimit ?: emailData?.contactsLimit, LIMIT_UNLIMITED),
                groupsLimit =
                    normalizeLimit(userDetails?.groupsLimit ?: emailData?.groupsLimit, LIMIT_UNLIMITED),
                currentContacts =
                    normalizeCurrentCount(
                        emailData?.currentContacts ?: userDetails?.currentContacts
                    ),
                currentGroups =
                    normalizeCurrentCount(
                        emailData?.currentGroups ?: userDetails?.currentGroups
                    ),
                userEmail = firstNonBlank(emailData?.userEmail, fallbackEmail),
                subscriptionEndTimeMillis =
                    userDetails?.subscriptionEndTimeMillis ?: emailData?.subscriptionEndTimeMillis
            )
        }

        return ResolvedSubscriptionState(
            source = "free_default",
            subscriptionType = TYPE_FREE,
            planType = null,
            contactsLimit = normalizeLimit(emailData?.contactsLimit, LIMIT_FREE_CONTACTS),
            groupsLimit = normalizeLimit(emailData?.groupsLimit, LIMIT_FREE_GROUPS),
            currentContacts =
                normalizeCurrentCount(emailData?.currentContacts ?: userDetails?.currentContacts),
            currentGroups =
                normalizeCurrentCount(emailData?.currentGroups ?: userDetails?.currentGroups),
            userEmail = firstNonBlank(emailData?.userEmail, fallbackEmail),
            subscriptionEndTimeMillis = null
        )
    }

    private fun DocumentSnapshot?.toEmailDataSnapshot(): EmailDataSubscriptionSnapshot {
        if (this == null || !exists()) return EmailDataSubscriptionSnapshot()

        return EmailDataSubscriptionSnapshot(
            exists = true,
            subscriptionType = getStringOrNull("subscriptionType"),
            planType = getStringOrNull("planType"),
            contactsLimit = getIntOrNull("contactsLimit"),
            groupsLimit = getIntOrNull("groupsLimit"),
            currentContacts = getIntOrNull("currentContactsCount"),
            currentGroups = getIntOrNull("currentGroupsCount"),
            userEmail = getStringOrNull("email"),
            subscriptionEndTimeMillis = getTimeInMillis("subscriptionEndDate")
        )
    }

    private fun DocumentSnapshot?.toUserDetailsSnapshot(): UserDetailsSubscriptionSnapshot {
        if (this == null || !exists()) return UserDetailsSubscriptionSnapshot()

        return UserDetailsSubscriptionSnapshot(
            exists = true,
            subscriptionType = getStringOrNull("subscriptionType"),
            planType = getStringOrNull("planType"),
            purchasedPlanType = getStringOrNull("purchasedPlanType"),
            userStatus = getStringOrNull("userStatus"),
            contactsLimit = getIntOrNull("contactsLimit"),
            groupsLimit = getIntOrNull("groupsLimit"),
            currentContacts = getIntOrNull("currentContactsCount"),
            currentGroups = getIntOrNull("currentGroupsCount"),
            subscriptionEndTimeMillis = getTimeInMillis("subscriptionEndDate")
        )
    }

    private fun DocumentSnapshot.getStringOrNull(field: String): String? {
        return (get(field) as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun DocumentSnapshot.getIntOrNull(field: String): Int? {
        val number = get(field) as? Number ?: return null
        return number.toInt()
    }

    private fun DocumentSnapshot.getTimeInMillis(field: String): Long? {
        val value = get(field) ?: return null
        return when (value) {
            is Timestamp -> value.seconds * 1000L
            is java.util.Date -> value.time
            is Number -> value.toLong()
            else -> null
        }
    }

    private fun normalizeType(value: String?): String {
        return if (value?.trim()?.lowercase() == TYPE_PREMIUM) TYPE_PREMIUM else TYPE_FREE
    }

    private fun normalizeLimit(value: Int?, defaultValue: Int): Int {
        return if (value != null && value >= LIMIT_UNLIMITED) value else defaultValue
    }

    private fun normalizeCurrentCount(value: Int?): Int? {
        return if (value != null && value >= 0) value else null
    }

    private fun firstNonBlank(vararg values: String?): String? {
        for (value in values) {
            val normalized = value?.trim().orEmpty()
            if (normalized.isNotEmpty()) return normalized
        }
        return null
    }
}

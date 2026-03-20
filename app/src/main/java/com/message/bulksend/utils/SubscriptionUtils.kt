package com.message.bulksend.utils

import android.content.Context
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.message.bulksend.auth.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object SubscriptionUtils {

    /**
     * Get subscription info from local preferences (faster than Firebase)
     */
    fun getLocalSubscriptionInfo(context: Context): Map<String, Any> {
        val sharedPref = context.getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
        val subscriptionType = sharedPref.getString("subscription_type", "free") ?: "free"
        val contactsLimit = sharedPref.getInt("contacts_limit", 10)
        val currentContacts = sharedPref.getInt("current_contacts", 0)
        val groupsLimit = sharedPref.getInt("groups_limit", 1)
        val currentGroups = sharedPref.getInt("current_groups", 0)
        val userEmail = sharedPref.getString("user_email", "") ?: ""

        // Check if premium is expired
        var isExpired = false
        if (subscriptionType == "premium") {
            val endTime = sharedPref.getLong("subscription_end_time", 0)
            if (endTime > 0) {
                isExpired = System.currentTimeMillis() > endTime
            }
        }

        android.util.Log.d("SubscriptionUtils", "📊 Local Subscription Info:")
        android.util.Log.d("SubscriptionUtils", "  Type: $subscriptionType (Expired: $isExpired)")
        android.util.Log.d("SubscriptionUtils", "  Contacts: $currentContacts/$contactsLimit")
        android.util.Log.d("SubscriptionUtils", "  Groups: $currentGroups/$groupsLimit")
        android.util.Log.d("SubscriptionUtils", "  Email: $userEmail")

        return mapOf(
            "type" to if (isExpired) "free" else subscriptionType,
            "contactsLimit" to if (isExpired) 10 else contactsLimit,
            "currentContacts" to currentContacts,
            "groupsLimit" to if (isExpired) 1 else groupsLimit,
            "currentGroups" to currentGroups,
            "userEmail" to userEmail,
            "isExpired" to isExpired
        )
    }

    /**
     * Check if user can add contacts based on local preferences
     */
    fun canAddContactsLocal(context: Context, contactsToAdd: Int = 1): Boolean {
        val info = getLocalSubscriptionInfo(context)
        val type = info["type"] as String
        val currentContacts = info["currentContacts"] as Int
        val contactsLimit = info["contactsLimit"] as Int

        return if (type == "premium") {
            true // Unlimited for premium
        } else {
            (currentContacts + contactsToAdd) <= contactsLimit
        }
    }

    /**
     * Check if user can add groups based on local preferences
     */
    fun canAddGroupsLocal(context: Context, groupsToAdd: Int = 1): Boolean {
        val info = getLocalSubscriptionInfo(context)
        val type = info["type"] as String
        val currentGroups = info["currentGroups"] as Int
        val groupsLimit = info["groupsLimit"] as Int

        return if (type == "premium") {
            true // Unlimited for premium
        } else {
            (currentGroups + groupsToAdd) <= groupsLimit
        }
    }

    /**
     * Check if user can add contacts and show appropriate message
     */
    fun checkContactLimit(
        context: Context,
        scope: CoroutineScope,
        contactsToAdd: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit = { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    ) {
        val userManager = UserManager(context)
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userEmail = currentUser?.email

        if (userEmail == null) {
            onFailure("User not logged in")
            return
        }

        scope.launch {
            try {
                val canAdd = userManager.canAddContacts(userEmail, contactsToAdd)
                if (canAdd) {
                    onSuccess()
                } else {
                    val subscriptionInfo = userManager.getSubscriptionInfo(userEmail)
                    val currentCount = subscriptionInfo["currentContacts"] as? Int ?: 0
                    val limit = subscriptionInfo["contactsLimit"] as? Int ?: 10
                    val subscriptionType = subscriptionInfo["type"] as? String ?: "free"

                    val message = if (subscriptionType == "free") {
                        "🚫 Contact Limit Reached!\n\n" +
                                "Free Plan: $currentCount/$limit contacts\n" +
                                "Trying to add: $contactsToAdd contacts\n\n" +
                                "💎 Upgrade to Premium for unlimited contacts!"
                    } else {
                        "Contact limit exceeded: $currentCount + $contactsToAdd > $limit"
                    }

                    onFailure(message)
                }
            } catch (e: Exception) {
                onFailure("Error checking subscription: ${e.message}")
            }
        }
    }

    /**
     * Show subscription status
     */
    fun showSubscriptionStatus(context: Context, scope: CoroutineScope) {
        val userManager = UserManager(context)
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userEmail = currentUser?.email ?: return

        scope.launch {
            try {
                val subscriptionInfo = userManager.getSubscriptionInfo(userEmail)
                val type = subscriptionInfo["type"] as? String ?: "free"
                val currentCount = subscriptionInfo["currentContacts"] as? Int ?: 0
                val limit = subscriptionInfo["contactsLimit"] as? Int ?: 10

                val message = if (type == "premium") {
                    "💎 Premium User\n" +
                            "Contacts: $currentCount (Unlimited)\n" +
                            "Status: Active"
                } else {
                    "🆓 Free User\n" +
                            "Contacts: $currentCount/$limit\n" +
                            "Remaining: ${limit - currentCount}"
                }

                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
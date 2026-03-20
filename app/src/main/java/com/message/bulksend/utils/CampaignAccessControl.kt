package com.message.bulksend.utils

import android.content.Context
import com.message.bulksend.auth.UserManager
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.contactmanager.Group
import kotlinx.coroutines.flow.first

object CampaignAccessControl {

    /**
     * Get accessible groups for user based on subscription
     */
    suspend fun getAccessibleGroups(context: Context): List<Group> {
        try {
            val subscriptionInfo = SubscriptionUtils.getLocalSubscriptionInfo(context)
            val type = subscriptionInfo["type"] as String
            val isExpired = subscriptionInfo["isExpired"] as Boolean

            // Get all groups from database
            val contactGroupDao = AppDatabase.getInstance(context).contactGroupDao()
            val allGroups = contactGroupDao.getAllGroups().first()

            // Convert to Group objects
            val groups = allGroups.map { dbGroup ->
                Group(
                    id = dbGroup.id,
                    name = dbGroup.name,
                    contacts = dbGroup.contacts.map { dbContact ->
                        com.message.bulksend.contactmanager.Contact(
                            name = dbContact.name,
                            number = dbContact.number,
                            isWhatsApp = dbContact.isWhatsApp
                        )
                    },
                    timestamp = dbGroup.timestamp
                )
            }

            return if (type == "premium" && !isExpired) {
                // Premium users can access all groups
                groups
            } else {
                // Free users can access only 1 group with max 10 contacts each
                groups.take(1).map { group ->
                    group.copy(
                        contacts = group.contacts.take(10) // Limit to 10 contacts per group
                    )
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * Check if user can access a specific group
     */
    suspend fun canAccessGroup(context: Context, groupId: Long): Boolean {
        try {
            val accessibleGroups = getAccessibleGroups(context)
            return accessibleGroups.any { it.id == groupId }
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Get contact limit message for user
     */
    fun getContactLimitMessage(context: Context): String {
        val subscriptionInfo = SubscriptionUtils.getLocalSubscriptionInfo(context)
        val type = subscriptionInfo["type"] as String
        val currentContacts = subscriptionInfo["currentContacts"] as Int
        val contactsLimit = subscriptionInfo["contactsLimit"] as Int
        val currentGroups = subscriptionInfo["currentGroups"] as Int
        val groupsLimit = subscriptionInfo["groupsLimit"] as Int
        val isExpired = subscriptionInfo["isExpired"] as Boolean

        return if (type == "premium" && !isExpired) {
            "💎 Premium Plan\n" +
                    "✅ Unlimited contacts and groups\n" +
                    "Current: $currentContacts contacts, $currentGroups groups"
        } else {
            "🆓 Free Plan\n" +
                    "📞 Contacts: $currentContacts/$contactsLimit\n" +
                    "📁 Groups: $currentGroups/$groupsLimit\n" +
                    "\n💡 Upgrade to Premium for unlimited access!"
        }
    }
}
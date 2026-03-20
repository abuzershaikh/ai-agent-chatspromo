package com.message.bulksend.contactmanager

import android.content.Context
import android.widget.Toast
import com.message.bulksend.utils.SubscriptionUtils

object SubscriptionStatusHelper {

    /**
     * Show subscription status toast
     */
    fun showSubscriptionStatus(context: Context) {
        val info = SubscriptionUtils.getLocalSubscriptionInfo(context)
        val type = info["type"] as String
        val currentContacts = info["currentContacts"] as Int
        val contactsLimit = info["contactsLimit"] as Int
        val currentGroups = info["currentGroups"] as Int
        val groupsLimit = info["groupsLimit"] as Int
        val isExpired = info["isExpired"] as Boolean

        val message = if (type == "premium" && !isExpired) {
            "💎 Premium Plan Active\n\n" +
                    "✅ Unlimited Contacts & Groups\n" +
                    "📞 Current Contacts: $currentContacts\n" +
                    "📁 Current Groups: $currentGroups"
        } else {
            "🆓 Free Plan\n\n" +
                    "📞 Contacts: $currentContacts/$contactsLimit\n" +
                    "📁 Groups: $currentGroups/$groupsLimit\n\n" +
                    "💡 Upgrade to Premium for unlimited access!"
        }

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Check if user can add contacts before import
     */
    fun checkBeforeImport(context: Context, contactsToAdd: Int): Boolean {
        val info = SubscriptionUtils.getLocalSubscriptionInfo(context)
        val type = info["type"] as String
        val currentContacts = info["currentContacts"] as Int
        val contactsLimit = info["contactsLimit"] as Int
        val currentGroups = info["currentGroups"] as Int
        val groupsLimit = info["groupsLimit"] as Int
        val isExpired = info["isExpired"] as Boolean

        // Check group limit first
        if (type != "premium" || isExpired) {
            if (currentGroups >= groupsLimit) {
                Toast.makeText(
                    context,
                    "🚫 Group Limit Reached!\n\n" +
                            "Free users can create only $groupsLimit group.\n" +
                            "Current: $currentGroups/$groupsLimit\n\n" +
                            "💎 Upgrade to Premium for unlimited groups!",
                    Toast.LENGTH_LONG
                ).show()
                return false
            }
        }

        // Check contact limit
        if (type != "premium" || isExpired) {
            val availableSlots = contactsLimit - currentContacts
            if (availableSlots <= 0) {
                Toast.makeText(
                    context,
                    "🚫 Contact Limit Reached!\n\n" +
                            "Free users can have maximum $contactsLimit contacts.\n" +
                            "Current: $currentContacts/$contactsLimit\n\n" +
                            "💎 Upgrade to Premium for unlimited contacts!",
                    Toast.LENGTH_LONG
                ).show()
                return false
            }

            if (contactsToAdd > availableSlots) {
                Toast.makeText(
                    context,
                    "⚠️ Limited Import\n\n" +
                            "You're importing $contactsToAdd contacts.\n" +
                            "Only $availableSlots slots available.\n\n" +
                            "First $availableSlots contacts will be added.\n" +
                            "💎 Upgrade to Premium for unlimited!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        return true
    }

    /**
     * Get subscription badge text
     */
    fun getSubscriptionBadge(context: Context): String {
        val info = SubscriptionUtils.getLocalSubscriptionInfo(context)
        val type = info["type"] as String
        val isExpired = info["isExpired"] as Boolean

        return if (type == "premium" && !isExpired) {
            "💎 Premium"
        } else {
            "🆓 Free"
        }
    }
}
package com.message.bulksend.utils

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.message.bulksend.auth.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PreferencesSync {

    private const val TAG = "PreferencesSync"

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

                val sharedPref = context.getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
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
                    val sharedPref = context.getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
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
        val sharedPref = context.getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
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
        val sharedPref = context.getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt("current_groups", newCount)
            apply()
        }

        Log.d(TAG, "📁 Group count updated locally: $newCount")

        // Sync to Firebase in background
        syncToFirebase(context)
    }
}
